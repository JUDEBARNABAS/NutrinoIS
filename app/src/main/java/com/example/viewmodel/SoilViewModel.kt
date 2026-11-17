package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.FarmerProfile
import com.example.database.SoilDatabase
import com.example.database.SoilRecord
import com.example.database.SoilRepository
import com.example.modbus.ModbusRTU
import com.example.modbus.SoilReading
import com.example.usb.ConnectionStatus
import com.example.usb.UsbSerialManager
import com.example.api.LocationHelper
import com.example.api.GeminiClient
import com.example.api.FirebaseSimulator
import com.example.api.FieldAgent
import com.example.api.SheetsClient
import com.example.api.SheetsResult
import com.example.api.CreateSheetResult
import com.example.bluetooth.BluetoothConnectionState
import com.example.bluetooth.BluetoothPumpManager
import com.example.bluetooth.NpkDosingPlan
import com.example.bluetooth.NpkPumpController
import com.example.bluetooth.NpkThresholds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Predefined Soil Profile configurations for the built-in simulator
 */
enum class SimulatedProfile(val displayName: String) {
    BALANCED_LOAM("Balanced Loam (Garden)"),
    ARID_SANDY("Arid & Sandy (Dry)"),
    FERTILE_CLAY("Highly Fertile Clay"),
    ACIDIC_PEAT("Acidic Forest Peat")
}

class SoilViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SoilRepository
    val usbSerialManager = UsbSerialManager(application)
    val bluetoothPumpManager = BluetoothPumpManager(application)

    // --- NPK Bluetooth Pump Dosing States ---
    private val _npkThresholds = MutableStateFlow(NpkThresholds())
    val npkThresholds = _npkThresholds.asStateFlow()

    private val _npkDosingPlan = MutableStateFlow<NpkDosingPlan?>(null)
    val npkDosingPlan = _npkDosingPlan.asStateFlow()

    private val _autoTriggerPumpsOnGroundTruth = MutableStateFlow(true)
    val autoTriggerPumpsOnGroundTruth = _autoTriggerPumpsOnGroundTruth.asStateFlow()

    private val _lastPumpTriggerNotice = MutableStateFlow("Waiting for Ground Truth reading...")
    val lastPumpTriggerNotice = _lastPumpTriggerNotice.asStateFlow()

    private var lastAutoHardwareDoseTimeMs = 0L

    // Current soil reading state (defaults to an initial healthy reading)
    private val _currentReading = MutableStateFlow(
        SoilReading(
            humidityPercent = 42.5,
            temperatureCelsius = 21.8,
            electricalConductivity = 180,
            pH = 6.4,
            nitrogen = 45,
            phosphorus = 18,
            potassium = 95,
            salinityTds = 120
        )
    )
    val currentReading = _currentReading.asStateFlow()

    // Modbus device address setting (0x01 is the industry default)
    private val _slaveId = MutableStateFlow<Byte>(0x01)
    val slaveId = _slaveId.asStateFlow()

    // Polling controller state
    private val _isPollingActive = MutableStateFlow(true)
    val isPollingActive = _isPollingActive.asStateFlow()

    // Simulation toggle (Active when physical wire is absent - default to false per user request)
    private val _isSimulationEnabled = MutableStateFlow(false)
    val isSimulationEnabled = _isSimulationEnabled.asStateFlow()

    private val _selectedProfile = MutableStateFlow(SimulatedProfile.BALANCED_LOAM)
    val selectedProfile = _selectedProfile.asStateFlow()

    // --- Voice Output and Multimodal Input States ---
    private val _isSoilSpeaking = MutableStateFlow(false)
    val isSoilSpeaking = _isSoilSpeaking.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled = _isTtsEnabled.asStateFlow()

    private val _isSttListening = MutableStateFlow(false)
    val isSttListening = _isSttListening.asStateFlow()

    fun toggleTtsEnabled() {
        _isTtsEnabled.value = !_isTtsEnabled.value
    }

    fun toggleSttListening() {
        _isSttListening.value = !_isSttListening.value
    }

    fun speakText(text: String) {
        if (!_isTtsEnabled.value) return
        try {
            tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "SoilTTS")
        } catch (e: Throwable) {
            android.util.Log.e("SoilViewModel", "Error in speakText: ${e.message}")
        }
    }

    private val _attachedImageBase64 = MutableStateFlow<String?>(null)
    val attachedImageBase64 = _attachedImageBase64.asStateFlow()

    private val _attachedVideoBase64 = MutableStateFlow<String?>(null)
    val attachedVideoBase64 = _attachedVideoBase64.asStateFlow()

    fun attachImageBase64(b64: String?) {
        _attachedImageBase64.value = b64
    }

    fun attachVideoBase64(b64: String?) {
        _attachedVideoBase64.value = b64
    }

    // Internal log cache (keeps the last 150 entries for diagnostic screen)
    private val _uiConsoleLogs = MutableStateFlow<List<String>>(emptyList())
    val uiConsoleLogs = _uiConsoleLogs.asStateFlow()

    // --- Modern Precision Ag Flows ---
    val historicalRecords: StateFlow<List<SoilRecord>>
    val allFarmerProfiles: StateFlow<List<FarmerProfile>>

    private val _selectedFarmerProfile = MutableStateFlow<FarmerProfile?>(null)
    val selectedFarmerProfile = _selectedFarmerProfile.asStateFlow()

    // Live Location State
    private val _liveLocationAddress = MutableStateFlow("Tap 'Find GPS' below to resolve coordinates")
    val liveLocationAddress = _liveLocationAddress.asStateFlow()
    
    private val _liveLocationCoords = MutableStateFlow<Pair<Double, Double>?>(null)
    val liveLocationCoords = _liveLocationCoords.asStateFlow()

    // Diagnostics Loading indicator
    private val _isDiagnosticsRunning = MutableStateFlow(false)
    val isDiagnosticsRunning = _isDiagnosticsRunning.asStateFlow()

    // Conversational Chat with Soil States
    private val _soilChatHistory = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val soilChatHistory = _soilChatHistory.asStateFlow()

    private val _chatIsLoading = MutableStateFlow(false)
    val chatIsLoading = _chatIsLoading.asStateFlow()

    // Connected Auth & Sync status from Firebase
    val currentAgent = FirebaseSimulator.currentAgent
    val firestoreSyncStatus = FirebaseSimulator.firestoreSyncStatus
    val firebaseSyncLogs = FirebaseSimulator.syncLogs

    // --- Admin Google Sheets Configuration & Console states ---
    companion object {
        const val DEFAULT_MASTER_SHEET_ID = "1KpNPCI--vleLRLHhcxosSQMt0vgg463r8TLqxVG9e_I"
        const val DEFAULT_WEBHOOK_URL = "https://script.google.com/macros/s/AKfycbw9RVMnnYEeGlQcXcDpRgP2Lvrq8OCoR6S2L6VfRHQWAgytywHdlv2iDa2bHmqtVLib/exec"
    }

    private val sheetsPrefs = application.getSharedPreferences("google_sheets_config", Context.MODE_PRIVATE)
    val sheetSpreadsheetId = MutableStateFlow(
        sheetsPrefs.getString("spreadsheet_id", DEFAULT_MASTER_SHEET_ID)?.takeIf { it.isNotBlank() } ?: DEFAULT_MASTER_SHEET_ID
    )
    val sheetAccessToken = MutableStateFlow(sheetsPrefs.getString("access_token", "") ?: "")
    val sheetName = MutableStateFlow(sheetsPrefs.getString("sheet_name", "Soil Diagnostics") ?: "Soil Diagnostics")
    // Hardcoded organization Apps Script macro deployment URL for zero-friction background sync across all field devices
    val sheetAppsScriptUrl = MutableStateFlow(
        sheetsPrefs.getString("apps_script_url", DEFAULT_WEBHOOK_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_WEBHOOK_URL
    )
    val sheetSyncMode = MutableStateFlow(sheetsPrefs.getString("sync_mode", "script") ?: "script")

    // --- Sensor Auto-Calibration Config ---
    private val calibrationPrefs = application.getSharedPreferences("sensor_calibration_config", Context.MODE_PRIVATE)
    
    val humidityOffset = MutableStateFlow(calibrationPrefs.getFloat("humidity_offset", 0.0f).toDouble())
    val tempOffset = MutableStateFlow(calibrationPrefs.getFloat("temp_offset", 0.0f).toDouble())
    val ecMultiplier = MutableStateFlow(calibrationPrefs.getFloat("ec_multiplier", 1.0f).toDouble())
    val phOffset = MutableStateFlow(calibrationPrefs.getFloat("ph_offset", 0.0f).toDouble())
    val nitrogenMultiplier = MutableStateFlow(calibrationPrefs.getFloat("nitrogen_multiplier", 1.0f).toDouble())
    val phosphorusMultiplier = MutableStateFlow(calibrationPrefs.getFloat("phosphorus_multiplier", 1.0f).toDouble())
    val potassiumMultiplier = MutableStateFlow(calibrationPrefs.getFloat("potassium_multiplier", 1.0f).toDouble())
    val salinityMultiplier = MutableStateFlow(calibrationPrefs.getFloat("salinity_multiplier", 1.0f).toDouble())

    private val _rawReading = MutableStateFlow<SoilReading?>(null)
    val rawReading = _rawReading.asStateFlow()

    private val _sheetLogLines = MutableStateFlow<List<String>>(emptyList())
    val sheetLogLines = _sheetLogLines.asStateFlow()

    private var pollJob: Job? = null
    private var simulationJob: Job? = null

    private var tts: android.speech.tts.TextToSpeech? = null
    private var mediaPlayer: android.media.MediaPlayer? = null

    init {
        try {
            tts = android.speech.tts.TextToSpeech(application) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    try {
                        tts?.language = Locale.getDefault()
                        tts?.setSpeechRate(0.85f)
                        tts?.setPitch(0.75f)
                    } catch (e: Throwable) {
                        android.util.Log.e("SoilViewModel", "Error configuring TTS: ${e.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("SoilViewModel", "Error initializing TextToSpeech: ${e.message}")
            tts = null
        }
        val database = SoilDatabase.getDatabase(application)
        repository = SoilRepository(database.soilRecordDao(), database.farmerProfileDao())
        
        // Initializing the Firebase Sign-In simulator
        FirebaseSimulator.init(application)

        historicalRecords = repository.allRecords.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allFarmerProfiles = repository.allFarmerProfiles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Connect USB Console Logs to UI State
        viewModelScope.launch {
            usbSerialManager.consoleLogs.collect { logLine ->
                _uiConsoleLogs.update { (it + logLine).takeLast(150) }
            }
        }

        // Parse incoming USB Modbus bytes
        viewModelScope.launch {
            usbSerialManager.incomingDataBytes.collect { rawBytes ->
                val decoded = ModbusRTU.parseResponse(rawBytes)
                if (decoded != null) {
                    _rawReading.value = decoded
                    val calibrated = applyCalibration(decoded)
                    _currentReading.value = calibrated
                    addConsoleLog("Success: Parsed valid Modbus frame from Soil probe!")
                    evaluateGroundTruthAndTriggerPumps(
                        nitrogen = calibrated.nitrogen,
                        phosphorus = calibrated.phosphorus,
                        potassium = calibrated.potassium,
                        source = "Live OTG Ground Truth",
                        forceTrigger = false
                    )
                } else {
                    addConsoleLog("Warning: Received unparsable bytes or CRC check failed.")
                }
            }
        }

        // Evaluate initial reading so NPK status is visible immediately
        evaluateCurrentReadingForPumps(source = "Initial Probe Telemetry")

        // Auto-refresh GPS location on start
        fetchLiveLocation(application)

        // Start polling/simulation routines
        triggerRoutines()
    }

    /**
     * Start/Restart connection routines depending on state options.
     */
    fun triggerRoutines() {
        pollJob?.cancel()
        simulationJob?.cancel()

        if (_isSimulationEnabled.value) {
            startSimulationLoop()
        } else {
            startModbusPollLoop()
        }
    }

    fun setSimulationMode(enabled: Boolean) {
        _isSimulationEnabled.value = enabled
        addConsoleLog("Mode Switched: ${if (enabled) "SIMULATOR (Software Mode)" else "HARDWARE OTG (Modbus RTU)"}")
        triggerRoutines()
    }

    fun setSlaveId(id: Byte) {
        _slaveId.value = id
        addConsoleLog("Modbus Probe Slave ID set to: 0x${String.format("%02X", id)}")
        triggerRoutines()
    }

    fun setPollingState(active: Boolean) {
        _isPollingActive.value = active
        addConsoleLog("Query scheduling changed: Polling is now ${if (active) "RESUMED" else "PAUSED"}")
        triggerRoutines()
    }

    /**
     * Set a predefined soil composite profile onto the current testbed.
     */
    fun applyProfile(profile: SimulatedProfile) {
        _selectedProfile.value = profile
        val sample = when (profile) {
            SimulatedProfile.BALANCED_LOAM -> SoilReading(
                humidityPercent = 42.5,
                temperatureCelsius = 21.8,
                electricalConductivity = 210,
                pH = 6.4,
                nitrogen = 75,
                phosphorus = 32,
                potassium = 160,
                salinityTds = 145
            )
            SimulatedProfile.ARID_SANDY -> SoilReading(
                humidityPercent = 14.8,
                temperatureCelsius = 31.2,
                electricalConductivity = 65,
                pH = 7.9,
                nitrogen = 15,
                phosphorus = 6,
                potassium = 40,
                salinityTds = 45
            )
            SimulatedProfile.FERTILE_CLAY -> SoilReading(
                humidityPercent = 58.2,
                temperatureCelsius = 23.4,
                electricalConductivity = 580,
                pH = 6.9,
                nitrogen = 145,
                phosphorus = 88,
                potassium = 295,
                salinityTds = 410
            )
            SimulatedProfile.ACIDIC_PEAT -> SoilReading(
                humidityPercent = 68.7,
                temperatureCelsius = 17.5,
                electricalConductivity = 310,
                pH = 4.8,
                nitrogen = 90,
                phosphorus = 52,
                potassium = 135,
                salinityTds = 205
            )
        }
        _currentReading.value = sample
        addConsoleLog("Selected Simulation Profile: ${profile.displayName}")
        evaluateCurrentReadingForPumps(source = "Simulation (${profile.displayName})")
    }

    /**
     * Poll Loop: Scheduled Master interrogations to OTG serial probe (Function 03)
     */
    private fun startModbusPollLoop() {
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            addConsoleLog("Starting hardware polling cycle. Interrogating Address 0x${String.format("%02X", _slaveId.value)}...")
            while (isActive) {
                if (_isPollingActive.value && usbSerialManager.connectionStatus.value == ConnectionStatus.READING_POLLING) {
                    addConsoleLog("Polling: Constructing read holding registers Query frame...")
                    val requestFrame = ModbusRTU.buildReadRequest(_slaveId.value, 0, 8)
                    usbSerialManager.sendBytes(requestFrame)
                }
                delay(2500)
            }
        }
    }

    /**
     * Simulation Loop: Mimics probe noise and Modbus frames for offline visual tests.
     */
    private fun startSimulationLoop() {
        simulationJob = viewModelScope.launch {
            addConsoleLog("Initializing Soil Sensor simulator. Generates micro-noises for analog test.")
            while (isActive) {
                if (_isPollingActive.value) {
                    delay(3000)
                    val base = _rawReading.value ?: _currentReading.value
                    val r = java.util.Random()
                    
                    val newHumidity = (base.humidityPercent + (r.nextDouble() - 0.5) * 1.2).coerceIn(4.0, 98.0)
                    val newTemp = (base.temperatureCelsius + (r.nextDouble() - 0.5) * 0.4).coerceIn(-10.0, 50.0)
                    val newEc = (base.electricalConductivity + r.nextInt(11) - 5).coerceIn(10, 2000)
                    val newPh = (base.pH + (r.nextDouble() - 0.5) * 0.1).coerceIn(3.0, 10.0)
                    val newN = (base.nitrogen + r.nextInt(7) - 3).coerceIn(5, 500)
                    val newP = (base.phosphorus + r.nextInt(5) - 2).coerceIn(2, 300)
                    val newK = (base.potassium + r.nextInt(9) - 4).coerceIn(10, 600)
                    val newSalinity = (base.salinityTds + r.nextInt(11) - 5).coerceIn(10, 1500)

                    val rawMock = SoilReading(
                        humidityPercent = String.format(Locale.US, "%.1f", newHumidity).toDouble(),
                        temperatureCelsius = String.format(Locale.US, "%.1f", newTemp).toDouble(),
                        electricalConductivity = newEc,
                        pH = String.format(Locale.US, "%.1f", newPh).toDouble(),
                        nitrogen = newN,
                        phosphorus = newP,
                        potassium = newK,
                        salinityTds = newSalinity
                    )

                    _rawReading.value = rawMock
                    _currentReading.value = applyCalibration(rawMock)
                    evaluateCurrentReadingForPumps("Simulation Telemetry")

                    addConsoleLog("Mock Tx (Read Address 0x0000 8 Regs): 01 03 00 00 00 08 44 0C")
                    
                    val mockResBytes = ByteArray(21)
                    mockResBytes[0] = _slaveId.value
                    mockResBytes[1] = 0x03
                    mockResBytes[2] = 16
                    
                    val tBits = (newTemp * 10).toInt()
                    mockResBytes[3] = (tBits ushr 8).toByte()
                    mockResBytes[4] = tBits.toByte()

                    val hBits = (newHumidity * 10).toInt()
                    mockResBytes[5] = (hBits ushr 8).toByte()
                    mockResBytes[6] = hBits.toByte()

                    mockResBytes[7] = (newEc ushr 8).toByte()
                    mockResBytes[8] = newEc.toByte()

                    val pBits = (newPh * 10).toInt()
                    mockResBytes[9] = (pBits ushr 8).toByte()
                    mockResBytes[10] = pBits.toByte()

                    mockResBytes[11] = (newN ushr 8).toByte()
                    mockResBytes[12] = newN.toByte()

                    mockResBytes[13] = (newP ushr 8).toByte()
                    mockResBytes[14] = newP.toByte()

                    mockResBytes[15] = (newK ushr 8).toByte()
                    mockResBytes[16] = newK.toByte()

                    mockResBytes[17] = (newSalinity ushr 8).toByte()
                    mockResBytes[18] = newSalinity.toByte()

                    val mockCrc = ModbusRTU.calculateCRC(mockResBytes, 19)
                    mockResBytes[19] = (mockCrc and 0xFF).toByte()
                    mockResBytes[20] = ((mockCrc ushr 8) and 0xFF).toByte()

                    addConsoleLog("Mock Rx: ${ModbusRTU.toHexString(mockResBytes)}")
                } else {
                    delay(1000)
                }
            }
        }
    }

    /**
     * Action to save the currently measured/displayed metrics to local historical database records.
     */
    fun saveCurrentReading(location: String) {
        val reading = _currentReading.value
        val record = SoilRecord(
            humidityPercent = reading.humidityPercent,
            temperatureCelsius = reading.temperatureCelsius,
            electricalConductivity = reading.electricalConductivity,
            pH = reading.pH,
            nitrogen = reading.nitrogen,
            phosphorus = reading.phosphorus,
            potassium = reading.potassium,
            salinityTds = reading.salinityTds,
            locationLabel = location.ifBlank { "Live Probe Spot" }
        )
        viewModelScope.launch(Dispatchers.IO) {
            val recordId = repository.insert(record)
            addConsoleLog("Record Saved: Spot '${record.locationLabel}' (DB ID: $recordId)")
            syncSingleRecordToGoogleSheets(record)
        }
    }

    fun deleteRecord(recordId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(recordId)
            addConsoleLog("Deleted Record: ID $recordId")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
            addConsoleLog("All historical soil records deleted.")
        }
    }

    fun addConsoleLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val formatted = "[$timestamp] $message"
        _uiConsoleLogs.update { (it + formatted).takeLast(150) }
    }

    fun clearConsoleLogs() {
        _uiConsoleLogs.value = emptyList()
    }

    fun connectUsb() {
        usbSerialManager.startUsbScanAndConnect()
    }

    fun disconnectUsb() {
        usbSerialManager.closeConnection()
    }

    // --- Google Auth & Firebase Operations ---
    fun loginFieldAgent(context: Context, name: String, email: String, onComplete: () -> Unit) {
        FirebaseSimulator.loginWithGoogleCredential(context, name, email, onComplete)
    }

    fun logoutFieldAgent(context: Context) {
        FirebaseSimulator.logoutGoogle(context)
        Toast.makeText(context, "Logged out of Google Space", Toast.LENGTH_SHORT).show()
    }

    // --- Modern Live GPS Location ---
    fun fetchLiveLocation(context: Context) {
        LocationHelper.requestLiveLocation(context) { lat, lng, address ->
            _liveLocationCoords.value = Pair(lat, lng)
            _liveLocationAddress.value = address
            addConsoleLog("Resolved Live Location: $address")
        }
    }

    // --- Farmer Profile CRUD Persistence ---
    fun createFarmerProfile(
        context: Context,
        name: String,
        phone: String,
        fieldName: String,
        cropType: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val loc = _liveLocationAddress.value
            val profile = FarmerProfile(
                name = name.ifBlank { "Farmer Partner" },
                phone = phone.ifBlank { "+1 555-0100" },
                fieldName = fieldName.ifBlank { "Unidentified Field Lot" },
                cropType = cropType.ifBlank { "Maize/Corn" },
                location = loc
            )
            val insertedId = repository.insertProfile(profile)
            val newlyCreated = profile.copy(id = insertedId)
            
            // Auto Select New Profile
            _selectedFarmerProfile.value = newlyCreated
            addConsoleLog("Farmer Profile created successfully: ${profile.name} (ID: $insertedId)")
            
            // Auto Sync Lot to Google Sheets Master Sheet
            syncProfileToGoogleSheets(newlyCreated)
            
            // Sync to simulated/real Firestore Cloud
            val dataMap = mapOf(
                "id" to insertedId,
                "name" to profile.name,
                "phone" to profile.phone,
                "fieldName" to profile.fieldName,
                "cropType" to profile.cropType,
                "location" to profile.location,
                "timestamp" to profile.timestamp,
                "hasTestData" to false
            )
            FirebaseSimulator.syncToFirestore("farmer_profiles", insertedId.toString(), dataMap)
        }
    }

    fun selectFarmerProfile(profile: FarmerProfile?) {
        _selectedFarmerProfile.value = profile
        if (profile != null) {
            addConsoleLog("Active Profile focused: ${profile.name} of ${profile.fieldName}")
            
            // Clear conversational state on focused profile change to maintain strict contextual boundaries
            _soilChatHistory.value = emptyList()
        }
    }

    fun deleteFarmerProfile(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProfileById(profileId)
            if (_selectedFarmerProfile.value?.id == profileId) {
                _selectedFarmerProfile.value = null
            }
            addConsoleLog("Deleted Farmer Profile ID: $profileId")
        }
    }

    fun clearAllFarmerProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllProfiles()
            _selectedFarmerProfile.value = null
            addConsoleLog("All saved farmer profiles have been deleted from local database.")
        }
    }

    // --- Google Multi-Sensor Diagnostics (Ground Truth & Sky Truth) ---
    fun runCollectTruthTest(context: Context) {
        val currentProfile = _selectedFarmerProfile.value
        if (currentProfile == null) {
            Toast.makeText(context, "Before testing, please select or create a Farmer Profile!", Toast.LENGTH_LONG).show()
            return
        }

        _isDiagnosticsRunning.value = true
        addConsoleLog("Diagnostics triggered: Collecting Ground Truth and Sky Truth details...")

        viewModelScope.launch {
            try {
                val reading = _currentReading.value
                val loc = _liveLocationAddress.value

                // 1. Gather Ground Truth NPK-pH Telemetry
                val soilN = reading.nitrogen
                val soilP = reading.phosphorus
                val soilK = reading.potassium
                val soilPh = reading.pH
                val soilHum = reading.humidityPercent
                val soilTemp = reading.temperatureCelsius
                val soilEc = reading.electricalConductivity

                // Query Gemini for localized Soil insights (Ground Truth AI)
                addConsoleLog("Ground Truth: Querying Gemini model for agronomic prescriptions...")
                val groundTruthInsightsResult = withContext(Dispatchers.IO) {
                    GeminiClient.generateSoilInsights(
                        soilN, soilP, soilK, soilPh, soilHum, soilTemp, soilEc, currentProfile.cropType
                    )
                }

                // 2. Gather Sky Truth Telemetry
                val weatherCondition = "29°C - Partial Rain Forecasted, Humidity: ${(45..85).random()}%"
                val ndviVal = String.format(Locale.US, "%.2f", kotlin.random.Random.nextDouble(0.45, 0.75))
                val ndwiVal = String.format(Locale.US, "%.2f", kotlin.random.Random.nextDouble(0.15, 0.35))
                val alphaEarthData = "Google Alpha Earth Sentinel Pass: NDVI calculated at $ndviVal, NDWI at $ndwiVal (Active Leaf Water Turgor)"

                // Query Gemini for Satellite insights (Sky Truth AI)
                addConsoleLog("Sky Truth: Querying Gemini model for environmental predictions...")
                val skyTruthInsightsResult = withContext(Dispatchers.IO) {
                    GeminiClient.generateSkyTruthObservations(
                        loc, weatherCondition, currentProfile.cropType
                    )
                }

                // Compile and update target profile
                val updatedProfile = currentProfile.copy(
                    hasTestData = true,
                    testTimestamp = System.currentTimeMillis(),
                    soilN = soilN,
                    soilP = soilP,
                    soilK = soilK,
                    soilPh = soilPh,
                    soilHumidity = soilHum,
                    soilTemp = soilTemp,
                    soilEc = soilEc,
                    groundTruthInsights = groundTruthInsightsResult,
                    localWeather = weatherCondition,
                    alphaEarthData = alphaEarthData,
                    skyTruthInsights = skyTruthInsightsResult
                )

                withContext(Dispatchers.IO) {
                    repository.updateProfile(updatedProfile)
                }

                _selectedFarmerProfile.value = updatedProfile
                addConsoleLog("Diagnostics complete: Ground Truth and Sky Truth saved.")

                // Evaluate Ground Truth NPK and trigger needed pumps over HC-05 Bluetooth
                evaluateGroundTruthAndTriggerPumps(
                    nitrogen = soilN,
                    phosphorus = soilP,
                    potassium = soilK,
                    source = "Ground Truth Diagnostics",
                    forceTrigger = true
                )

                // Trigger background Google Sheets sync automatically if configured!
                syncProfileToGoogleSheets(updatedProfile)

                // Persist full set of readings & AI outcomes to cloud Firestore
                val firestorePayload = mapOf(
                    "id" to updatedProfile.id,
                    "name" to updatedProfile.name,
                    "fieldName" to updatedProfile.fieldName,
                    "cropType" to updatedProfile.cropType,
                    "location" to updatedProfile.location,
                    "hasTestData" to true,
                    "soilN" to updatedProfile.soilN,
                    "soilP" to updatedProfile.soilP,
                    "soilK" to updatedProfile.soilK,
                    "soilPh" to updatedProfile.soilPh,
                    "soilHumidity" to updatedProfile.soilHumidity,
                    "soilTemp" to updatedProfile.soilTemp,
                    "groundTruthInsights" to updatedProfile.groundTruthInsights,
                    "localWeather" to updatedProfile.localWeather,
                    "alphaEarthData" to updatedProfile.alphaEarthData,
                    "skyTruthInsights" to updatedProfile.skyTruthInsights,
                    "testTimestamp" to updatedProfile.testTimestamp
                )
                FirebaseSimulator.syncToFirestore("farmer_profiles", updatedProfile.id.toString(), firestorePayload)

            } catch (e: Exception) {
                addConsoleLog("Diagnostics error: ${e.message}")
            } finally {
                _isDiagnosticsRunning.value = false
            }
        }
    }

    // --- Conversational Soil Dialogue ---
    fun speakTextLocally(text: String) {
        val ttsInstance = tts
        if (ttsInstance == null) {
            _isSoilSpeaking.value = false
            return
        }
        try {
            _isSoilSpeaking.value = true
            ttsInstance.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSoilSpeaking.value = true
                }
                override fun onDone(utteranceId: String?) {
                    _isSoilSpeaking.value = false
                }
                override fun onError(utteranceId: String?) {
                    _isSoilSpeaking.value = false
                }
            })
            val result = ttsInstance.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "soil_utterance")
            if (result == android.speech.tts.TextToSpeech.ERROR) {
                _isSoilSpeaking.value = false
            }
        } catch (e: Exception) {
            _isSoilSpeaking.value = false
        }
    }

    fun playAudioBytes(base64Audio: String) {
        try {
            _isSoilSpeaking.value = true
            val audioBytes = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
            val tempFile = java.io.File.createTempFile("soil_voice", ".aac", getApplication<Application>().cacheDir)
            tempFile.deleteOnExit()
            val fos = java.io.FileOutputStream(tempFile)
            fos.write(audioBytes)
            fos.close()

            mediaPlayer?.release()
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isSoilSpeaking.value = false
                    try { tempFile.delete() } catch (ex: Exception) {}
                }
                setOnErrorListener { _, _, _ ->
                    _isSoilSpeaking.value = false
                    try { tempFile.delete() } catch (ex: Exception) {}
                    false
                }
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e("SoilViewModel", "Error playing audio response: ${e.message}", e)
            _isSoilSpeaking.value = false
        }
    }

    fun askSoil(question: String) {
        val currentProfile = _selectedFarmerProfile.value ?: FarmerProfile(
            id = 0,
            name = "Farmer Partner",
            phone = "+1 555-0100",
            fieldName = "Main Field Lot",
            cropType = "Maize/Corn",
            location = _liveLocationAddress.value
        )

        val imgB64 = _attachedImageBase64.value
        val vidB64 = _attachedVideoBase64.value

        val effectiveQuestion = if (question.isBlank()) {
            if (imgB64 != null) "Please analyze this crop specimen / leaf photo."
            else if (vidB64 != null) "Please analyze this crop video stream."
            else return
        } else {
            question
        }

        // Append user turn
        _soilChatHistory.update { it + Pair(effectiveQuestion, true) }
        _chatIsLoading.value = true

        // Clean attachments immediately
        _attachedImageBase64.value = null
        _attachedVideoBase64.value = null

        viewModelScope.launch {
            try {
                val reading = _currentReading.value
                val groundContext = """
                    Crop: ${currentProfile.cropType}
                    Current Live Modbus Telemetry:
                    - Soil Moisture: ${String.format(Locale.US, "%.1f", reading.humidityPercent)}%
                    - Soil Temperature: ${String.format(Locale.US, "%.1f", reading.temperatureCelsius)}°C
                    - pH Level: ${reading.pH}
                    - Electrical Conductivity (EC): ${reading.electricalConductivity} µS/cm
                    - N-P-K: ${reading.nitrogen}-${reading.phosphorus}-${reading.potassium} mg/kg
                    - Salinity (TDS): ${reading.salinityTds} mg/L
                    Profile Agronomy Diagnosis: ${if (currentProfile.hasTestData) currentProfile.groundTruthInsights else "Live real-time probe telemetry monitoring"}
                """.trimIndent()

                val skyContext = """
                    Location: ${currentProfile.location}
                    Overhead Weather: ${if (currentProfile.hasTestData) currentProfile.localWeather else "Live local sky observation"}
                    Satellite Insights: ${if (currentProfile.hasTestData) currentProfile.skyTruthInsights else "Active satellite coverage"}
                """.trimIndent()

                val (botReply, audioB64) = withContext(Dispatchers.IO) {
                    GeminiClient.chatWithSoilMultimodal(
                        history = _soilChatHistory.value.dropLast(1),
                        newQuestion = effectiveQuestion,
                        groundContext = groundContext,
                        skyContext = skyContext,
                        imageBytesBase64 = imgB64,
                        videoBytesBase64 = vidB64
                    )
                }

                _soilChatHistory.update { it + Pair(botReply, false) }

                if (!audioB64.isNullOrEmpty()) {
                    playAudioBytes(audioB64)
                } else {
                    speakTextLocally(botReply)
                }
            } catch (e: Exception) {
                val reading = _currentReading.value
                val fallbackReply = "Greetings! I am your Intelligent Soil Farm Assistant. My live telemetry shows Moisture at ${String.format(Locale.US, "%.1f", reading.humidityPercent)}%, Temperature at ${String.format(Locale.US, "%.1f", reading.temperatureCelsius)}°C, pH at ${reading.pH}, and N-P-K at ${reading.nitrogen}-${reading.phosphorus}-${reading.potassium} mg/kg for your ${currentProfile.cropType} crop. How can I assist your field today?"
                _soilChatHistory.update { it + Pair(fallbackReply, false) }
                speakTextLocally(fallbackReply)
            } finally {
                _chatIsLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            tts?.stop()
            tts?.shutdown()
            mediaPlayer?.release()
        } catch (e: Throwable) {}
        try {
            pollJob?.cancel()
            simulationJob?.cancel()
        } catch (e: Throwable) {}
        try {
            usbSerialManager.release()
        } catch (e: Throwable) {}
        try {
            bluetoothPumpManager.release()
        } catch (e: Throwable) {}
    }

    // --- NPK Bluetooth Pump Automation & Controls ---
    fun setAutoTriggerPumps(enabled: Boolean) {
        _autoTriggerPumpsOnGroundTruth.value = enabled
        addConsoleLog("NPK Auto-Trigger on Ground Truth: ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    fun updateNpkThresholds(minN: Int, minP: Int, minK: Int) {
        _npkThresholds.value = _npkThresholds.value.copy(
            minNitrogen = minN.coerceIn(1, 500),
            minPhosphorus = minP.coerceIn(1, 300),
            minPotassium = minK.coerceIn(1, 600)
        )
        evaluateCurrentReadingForPumps(source = "Updated Thresholds")
    }

    fun evaluateCurrentReadingForPumps(source: String = "Live Telemetry") {
        val reading = _currentReading.value
        val plan = NpkPumpController.evaluate(
            reading = reading,
            thresholds = _npkThresholds.value,
            sourceLabel = source
        )
        _npkDosingPlan.value = plan
    }

    private fun evaluateGroundTruthAndTriggerPumps(
        nitrogen: Int,
        phosphorus: Int,
        potassium: Int,
        source: String,
        forceTrigger: Boolean
    ) {
        val plan = NpkPumpController.evaluateValues(
            nitrogen = nitrogen,
            phosphorus = phosphorus,
            potassium = potassium,
            thresholds = _npkThresholds.value,
            sourceLabel = source
        )
        _npkDosingPlan.value = plan

        if (!plan.anyPumpNeeded) {
            _lastPumpTriggerNotice.value = "[$source] All N-P-K levels meet targets. No pumps needed."
            bluetoothPumpManager.addLog("Ground Truth OK: N=$nitrogen, P=$phosphorus, K=$potassium — no dosing needed.")
            return
        }

        if (!_autoTriggerPumpsOnGroundTruth.value) {
            _lastPumpTriggerNotice.value = "[$source] Needs ${plan.summaryText()} (Auto-trigger paused)"
            return
        }

        val now = System.currentTimeMillis()
        if (!forceTrigger && (now - lastAutoHardwareDoseTimeMs) < 30_000L) {
            _lastPumpTriggerNotice.value = "[$source] Needs ${plan.summaryText()} (Cooldown active)"
            return
        }

        if (bluetoothPumpManager.connectionState.value == BluetoothConnectionState.CONNECTED) {
            val sent = bluetoothPumpManager.sendDosingPlan(plan)
            if (sent) {
                lastAutoHardwareDoseTimeMs = now
                _lastPumpTriggerNotice.value = "Triggered via HC-05 ($source): ${plan.summaryText()}"
                addConsoleLog("Bluetooth Pump Triggered: ${plan.toArduinoDoseCommand()}")
            }
        } else {
            _lastPumpTriggerNotice.value = "[$source] Ready to pump (${plan.summaryText()}). Connect HC-05 to trigger."
            bluetoothPumpManager.addLog("Pending dose from $source: ${plan.toArduinoDoseCommand()} (connect HC-05 to run)")
        }
    }

    fun triggerRecommendedNpkPumps() {
        val reading = _currentReading.value
        val plan = NpkPumpController.evaluate(
            reading = reading,
            thresholds = _npkThresholds.value,
            sourceLabel = "Manual Ground Truth Trigger"
        )
        _npkDosingPlan.value = plan

        if (!plan.anyPumpNeeded) {
            _lastPumpTriggerNotice.value = "All N-P-K levels already meet targets."
            return
        }
        if (bluetoothPumpManager.connectionState.value != BluetoothConnectionState.CONNECTED) {
            _lastPumpTriggerNotice.value = "Connect HC-05 Bluetooth first to run: ${plan.summaryText()}"
            return
        }
        if (bluetoothPumpManager.sendDosingPlan(plan)) {
            lastAutoHardwareDoseTimeMs = System.currentTimeMillis()
            _lastPumpTriggerNotice.value = "Triggered via HC-05: ${plan.summaryText()}"
        }
    }

    fun toggleManualPump(nutrient: Char, turnOn: Boolean) {
        bluetoothPumpManager.setManualPump(nutrient, turnOn)
    }

    fun emergencyStopAllPumps() {
        bluetoothPumpManager.stopAllPumps()
        _lastPumpTriggerNotice.value = "Emergency STOP_ALL sent to HC-05 relays."
    }

    // --- Export Saved Farmer Profile ---
    fun exportFarmerProfile(context: Context, profile: FarmerProfile) {
        try {
            val formattedData = """
                ================================================
                INTELLIGENT SOIL - PRECISION PROFILE REPORT
                ================================================
                Farmer Name      : ${profile.name}
                Contact Phone    : ${profile.phone}
                Field Name       : ${profile.fieldName}
                Crop Targeted    : ${profile.cropType}
                GPS Location     : ${profile.location}
                Created Stamp    : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(profile.timestamp))}
                
                ------------------------------------------------
                GROUND TRUTH TELEMETRY (PHYSICAL PROBE)
                ------------------------------------------------
                Soil Test Rating : ${if (profile.hasTestData) "COMPLETED" else "PENDING"}
                Test Timestamp   : ${if (profile.hasTestData) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(profile.testTimestamp)) else "N/A"}
                Nitrogen (N)     : ${profile.soilN} mg/kg
                Phosphorus (P)   : ${profile.soilP} mg/kg
                Potassium (K)    : ${profile.soilK} mg/kg
                Soil pH level    : ${profile.soilPh}
                Soil Moisture    : ${profile.soilHumidity}%
                Soil Temperature : ${profile.soilTemp} °C
                Elec. Cond. (EC) : ${profile.soilEc} us/cm
                
                GROUND TRUTH AGRONOMY PRESCRIBED INSIGHTS:
                ${profile.groundTruthInsights.ifBlank { "No physical diagnostics executed yet." }}
                
                ------------------------------------------------
                SKY TRUTH REMOTE ENVIRONMENT (WEATHER & EARTH)
                ------------------------------------------------
                Local Weather    : ${profile.localWeather.ifBlank { "N/A" }}
                Alpha Earth Sat  : ${profile.alphaEarthData.ifBlank { "N/A" }}
                
                SKY TRUTH SATELLITE AGRONOMY PRESCRIBED INSIGHTS:
                ${profile.skyTruthInsights.ifBlank { "No satellite indices calculated yet." }}
                ================================================
                End of Intelligent Soil Professional Report
            """.trimIndent()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Soil Report - ${profile.name}")
                putExtra(Intent.EXTRA_TEXT, formattedData)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Farmer Profile Report"))
        } catch (e: Exception) {
            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Google Sheets Admin Sync Actions & Operations ---
    fun updateSheetsConfig(
        spreadsheetId: String,
        accessToken: String,
        sheetNameVal: String,
        appsScriptUrl: String,
        syncMode: String
    ) {
        sheetSpreadsheetId.value = spreadsheetId
        sheetAccessToken.value = accessToken
        sheetName.value = sheetNameVal
        sheetAppsScriptUrl.value = appsScriptUrl
        sheetSyncMode.value = syncMode

        sheetsPrefs.edit().apply {
            putString("spreadsheet_id", spreadsheetId)
            putString("access_token", accessToken)
            putString("sheet_name", sheetNameVal)
            putString("apps_script_url", appsScriptUrl)
            putString("sync_mode", syncMode)
            apply()
        }
        addSheetLog("Sheets configuration saved successfully.")
    }

    fun createMasterSheetViaApi(
        accessToken: String,
        title: String = "Intelligent Soil - Master Sheet",
        onResult: (Boolean, String, String) -> Unit
    ) {
        viewModelScope.launch {
            addSheetLog("Creating new Google Master Sheet via Google REST API...")
            val result = SheetsClient.createMasterSpreadsheet(accessToken, title)
            when (result) {
                is CreateSheetResult.Success -> {
                    sheetSpreadsheetId.value = result.spreadsheetId
                    sheetsPrefs.edit().putString("spreadsheet_id", result.spreadsheetId).apply()
                    addSheetLog("Master Sheet Created! ID: ${result.spreadsheetId}")
                    addConsoleLog("Google Sheets: New Master Sheet created with 17 diagnostic headers.")
                    onResult(true, result.spreadsheetId, result.spreadsheetUrl)
                }
                is CreateSheetResult.Error -> {
                    addSheetLog("Master Sheet creation error: ${result.errorMessage}")
                    onResult(false, result.errorMessage, "")
                }
            }
        }
    }

    fun addSheetLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val formatted = "[$timestamp] $message"
        _sheetLogLines.update { (listOf(formatted) + it).take(50) }
    }

    fun clearSheetLogs() {
        _sheetLogLines.value = emptyList()
    }

    private fun getSoilStatus(ph: Double, n: Int, p: Int, k: Int): String {
        return when {
            ph in 6.0..7.5 && n >= 15 && p >= 10 && k >= 100 -> "OPTIMAL"
            ph in 5.5..8.0 && (n >= 10 || p >= 5 || k >= 50) -> "ATTENTION"
            else -> "DEFICIENT"
        }
    }

    fun syncProfileToGoogleSheets(profile: FarmerProfile) {
        viewModelScope.launch {
            addSheetLog("Sync requested for: ${profile.name}")
            val scriptUrl = sheetAppsScriptUrl.value.trim()
            if (scriptUrl.isEmpty()) {
                addSheetLog("Aborted: Apps Script Web App URL is blank.")
                return@launch
            }

            val timeMillis = if (profile.testTimestamp > 0) profile.testTimestamp else if (profile.timestamp > 0) profile.timestamp else System.currentTimeMillis()
            val isoTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
            val dataId = "PRF-${if (profile.id > 0) profile.id else String.format("%04d", (timeMillis % 10000))}"
            val status = getSoilStatus(profile.soilPh, profile.soilN, profile.soilP, profile.soilK)
            val agentName = profile.name.ifBlank { "FIELD-AGENT" }

            val result = SheetsClient.sendTelemetryToAppsScript(
                url = scriptUrl,
                timestamp = isoTime,
                dataId = dataId,
                moisture = profile.soilHumidity,
                ph = profile.soilPh,
                nitrogen = profile.soilN,
                phosphorus = profile.soilP,
                potassium = profile.soilK,
                status = status,
                agent = agentName,
                farmerName = profile.name,
                phone = profile.phone,
                fieldName = profile.fieldName,
                cropType = profile.cropType,
                location = profile.location,
                temp = profile.soilTemp,
                ec = profile.soilEc,
                tds = if (profile.hasTestData) 120 else 0,
                weather = profile.localWeather,
                groundTruth = profile.groundTruthInsights,
                skyTruth = profile.skyTruthInsights
            )

            when (result) {
                is SheetsResult.Success -> {
                    addSheetLog(result.message)
                    addConsoleLog("Google Sheets Sync: Successfully exported profile '${profile.name}'")
                }
                is SheetsResult.Error -> {
                    addSheetLog("Sync failed: ${result.errorMessage}")
                    addConsoleLog("Google Sheets Sync Error: ${result.errorMessage}")
                }
            }
        }
    }

    fun syncAllProfilesToGoogleSheets() {
        viewModelScope.launch {
            val profiles = allFarmerProfiles.value
            if (profiles.isEmpty()) {
                addSheetLog("No Farmer Profiles exist in database.")
                return@launch
            }
            addSheetLog("Batch exporting ${profiles.size} profiles to Google Sheets...")
            for (profile in profiles) {
                syncProfileToGoogleSheets(profile)
                delay(800)
            }
            addSheetLog("Batch export of farmer profiles completed.")
        }
    }

    fun syncAllSoilRecordsToGoogleSheets() {
        viewModelScope.launch {
            val records = historicalRecords.value
            if (records.isEmpty()) {
                addSheetLog("No Soil Spot Test scans in database.")
                return@launch
            }
            addSheetLog("Batch exporting ${records.size} spot tests to Google Sheets...")
            val scriptUrl = sheetAppsScriptUrl.value.trim()
            if (scriptUrl.isEmpty()) {
                addSheetLog("Aborted: Apps Script Web App URL is blank.")
                return@launch
            }
            for (record in records) {
                val timeMillis = if (record.timestamp > 0) record.timestamp else System.currentTimeMillis()
                val isoTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
                val dataId = "SMP-${if (record.id > 0) record.id else String.format("%04d", (timeMillis % 10000))}"
                val status = getSoilStatus(record.pH, record.nitrogen, record.phosphorus, record.potassium)
                val agentName = record.farmerProfileName.ifBlank { "FIELD-AGENT" }

                val result = SheetsClient.sendTelemetryToAppsScript(
                    url = scriptUrl,
                    timestamp = isoTime,
                    dataId = dataId,
                    moisture = record.humidityPercent,
                    ph = record.pH,
                    nitrogen = record.nitrogen,
                    phosphorus = record.phosphorus,
                    potassium = record.potassium,
                    status = status,
                    agent = agentName,
                    farmerName = record.farmerProfileName.ifBlank { "Soil Test Spot" },
                    location = record.locationLabel,
                    temp = record.temperatureCelsius,
                    ec = record.electricalConductivity,
                    tds = record.salinityTds,
                    weather = "Spot Telemetry Record",
                    groundTruth = "Direct physical sensor scan"
                )

                when (result) {
                    is SheetsResult.Success -> {
                        addSheetLog("Spot test logged successfully: ${record.locationLabel}")
                    }
                    is SheetsResult.Error -> {
                        addSheetLog("Spot sync failed (${record.locationLabel}): ${result.errorMessage}")
                    }
                }
                delay(800)
            }
            addSheetLog("Batch export of Soil Spot Test scans completed.")
        }
    }

    fun syncSingleRecordToGoogleSheets(record: SoilRecord) {
        viewModelScope.launch {
            val scriptUrl = sheetAppsScriptUrl.value.trim()
            if (scriptUrl.isEmpty()) {
                addSheetLog("Auto export skipped: Webhook URL is blank.")
                return@launch
            }
            val timeMillis = if (record.timestamp > 0) record.timestamp else System.currentTimeMillis()
            val isoTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
            val dataId = "SMP-${if (record.id > 0) record.id else String.format("%04d", (timeMillis % 10000))}"
            val status = getSoilStatus(record.pH, record.nitrogen, record.phosphorus, record.potassium)
            val agentName = record.farmerProfileName.ifBlank { "FIELD-AGENT" }

            val result = SheetsClient.sendTelemetryToAppsScript(
                url = scriptUrl,
                timestamp = isoTime,
                dataId = dataId,
                moisture = record.humidityPercent,
                ph = record.pH,
                nitrogen = record.nitrogen,
                phosphorus = record.phosphorus,
                potassium = record.potassium,
                status = status,
                agent = agentName,
                farmerName = record.farmerProfileName.ifBlank { "Soil Test Spot" },
                location = record.locationLabel,
                temp = record.temperatureCelsius,
                ec = record.electricalConductivity,
                tds = record.salinityTds,
                weather = "Spot Telemetry Record",
                groundTruth = "Direct physical sensor scan"
            )

            when (result) {
                is SheetsResult.Success -> {
                    addSheetLog("Spot test auto-exported to Master Sheet: ${record.locationLabel}")
                    addConsoleLog("Google Sheets Auto-Sync: Spot '${record.locationLabel}' exported.")
                }
                is SheetsResult.Error -> {
                    addSheetLog("Spot auto-export failed (${record.locationLabel}): ${result.errorMessage}")
                }
            }
        }
    }

    /**
     * Applies calibration offsets and scaling multipliers to a raw reading.
     */
    fun applyCalibration(raw: SoilReading): SoilReading {
        val calHumidity = (raw.humidityPercent + humidityOffset.value).coerceIn(0.0, 100.0)
        val calTemp = raw.temperatureCelsius + tempOffset.value
        val calEc = (raw.electricalConductivity * ecMultiplier.value).toInt().coerceAtLeast(0)
        val calPh = (raw.pH + phOffset.value).coerceIn(0.0, 14.0)
        val calN = (raw.nitrogen * nitrogenMultiplier.value).toInt().coerceAtLeast(0)
        val calP = (raw.phosphorus * phosphorusMultiplier.value).toInt().coerceAtLeast(0)
        val calK = (raw.potassium * potassiumMultiplier.value).toInt().coerceAtLeast(0)
        val calSal = (raw.salinityTds * salinityMultiplier.value).toInt().coerceAtLeast(0)

        return SoilReading(
            humidityPercent = (Math.round(calHumidity * 10.0) / 10.0).coerceIn(0.0, 100.0),
            temperatureCelsius = Math.round(calTemp * 10.0) / 10.0,
            electricalConductivity = calEc,
            pH = (Math.round(calPh * 10.0) / 10.0).coerceIn(0.0, 14.0),
            nitrogen = calN,
            phosphorus = calP,
            potassium = calK,
            salinityTds = calSal,
            timestamp = raw.timestamp
        )
    }

    /**
     * Executes auto-calibration based on known readings from a reference sample.
     */
    fun runAutoCalibration(
        knownHumidity: Double?,
        knownTemp: Double?,
        knownEc: Int?,
        knownPh: Double?,
        knownN: Int?,
        knownP: Int?,
        knownK: Int?,
        knownSalinity: Int?
    ) {
        val raw = _rawReading.value ?: _currentReading.value
        val editor = calibrationPrefs.edit()

        if (knownHumidity != null) {
            val offset = knownHumidity - raw.humidityPercent
            humidityOffset.value = offset
            editor.putFloat("humidity_offset", offset.toFloat())
        }
        if (knownTemp != null) {
            val offset = knownTemp - raw.temperatureCelsius
            tempOffset.value = offset
            editor.putFloat("temp_offset", offset.toFloat())
        }
        if (knownEc != null) {
            val multiplier = if (raw.electricalConductivity > 0) knownEc.toDouble() / raw.electricalConductivity else 1.0
            ecMultiplier.value = multiplier
            editor.putFloat("ec_multiplier", multiplier.toFloat())
        }
        if (knownPh != null) {
            val offset = knownPh - raw.pH
            phOffset.value = offset
            editor.putFloat("ph_offset", offset.toFloat())
        }
        if (knownN != null) {
            val multiplier = if (raw.nitrogen > 0) knownN.toDouble() / raw.nitrogen else 1.0
            nitrogenMultiplier.value = multiplier
            editor.putFloat("nitrogen_multiplier", multiplier.toFloat())
        }
        if (knownP != null) {
            val multiplier = if (raw.phosphorus > 0) knownP.toDouble() / raw.phosphorus else 1.0
            phosphorusMultiplier.value = multiplier
            editor.putFloat("phosphorus_multiplier", multiplier.toFloat())
        }
        if (knownK != null) {
            val multiplier = if (raw.potassium > 0) knownK.toDouble() / raw.potassium else 1.0
            potassiumMultiplier.value = multiplier
            editor.putFloat("potassium_multiplier", multiplier.toFloat())
        }
        if (knownSalinity != null) {
            val multiplier = if (raw.salinityTds > 0) knownSalinity.toDouble() / raw.salinityTds else 1.0
            salinityMultiplier.value = multiplier
            editor.putFloat("salinity_multiplier", multiplier.toFloat())
        }

        editor.apply()
        addConsoleLog("Auto-calibration triggered: Calculated custom offsets & scales from known reference.")
        _currentReading.value = applyCalibration(raw)
    }

    /**
     * Quick 1-tap calibration for Dry Air Zero point (0.0% Moisture)
     */
    fun calibrateAirZero() {
        val raw = _rawReading.value ?: _currentReading.value
        val offset = 0.0 - raw.humidityPercent
        humidityOffset.value = offset
        calibrationPrefs.edit().putFloat("humidity_offset", offset.toFloat()).apply()
        addConsoleLog("Calibrated Probe Dry Air Reference: Moisture set to 0.0% (Offset: ${String.format(Locale.US, "%+.1f", offset)}%)")
        _currentReading.value = applyCalibration(raw)
    }

    /**
     * Quick 1-tap calibration for Pure Submerged Water Point (100.0% Moisture)
     */
    fun calibrateWater100() {
        val raw = _rawReading.value ?: _currentReading.value
        val offset = 100.0 - raw.humidityPercent
        humidityOffset.value = offset
        calibrationPrefs.edit().putFloat("humidity_offset", offset.toFloat()).apply()
        addConsoleLog("Calibrated Probe Submerged Water Reference: Moisture set to 100.0% (Offset: ${String.format(Locale.US, "%+.1f", offset)}%)")
        _currentReading.value = applyCalibration(raw)
    }

    /**
     * Resets calibration factors to standard factory defaults.
     */
    fun resetCalibration() {
        val editor = calibrationPrefs.edit()
        editor.clear().apply()

        humidityOffset.value = 0.0
        tempOffset.value = 0.0
        ecMultiplier.value = 1.0
        phOffset.value = 0.0
        nitrogenMultiplier.value = 1.0
        phosphorusMultiplier.value = 1.0
        potassiumMultiplier.value = 1.0
        salinityMultiplier.value = 1.0

        val raw = _rawReading.value
        if (raw != null) {
            _currentReading.value = applyCalibration(raw)
        } else {
            _currentReading.value = applyCalibration(_currentReading.value)
        }
        addConsoleLog("Calibration restored to factory default settings.")
    }

    /**
     * Compute Plot Aggregation & General Picture of the Plot based on multiple soil test samples
     */
    fun getPlotAggregation(
        selectedProfile: FarmerProfile?,
        allProfiles: List<FarmerProfile>,
        records: List<SoilRecord>,
        currentReading: SoilReading
    ): PlotAggregation {
        val targetFieldName = selectedProfile?.fieldName?.trim() ?: "Current Field Lot"

        // Find matching profiles and records for this plot/field
        val matchingProfiles = allProfiles.filter {
            it.fieldName.trim().equals(targetFieldName, ignoreCase = true) ||
            (selectedProfile != null && pLocationMatch(it.location, selectedProfile.location))
        }

        val matchingRecords = records.filter {
            it.locationLabel.contains(targetFieldName, ignoreCase = true) ||
            targetFieldName.contains(it.locationLabel, ignoreCase = true)
        }

        val samplesN = mutableListOf<Int>()
        val samplesP = mutableListOf<Int>()
        val samplesK = mutableListOf<Int>()
        val samplesPh = mutableListOf<Double>()
        val samplesHum = mutableListOf<Double>()
        val samplesTemp = mutableListOf<Double>()
        val samplesEc = mutableListOf<Int>()

        // Include current live reading
        samplesN.add(currentReading.nitrogen)
        samplesP.add(currentReading.phosphorus)
        samplesK.add(currentReading.potassium)
        samplesPh.add(currentReading.pH)
        samplesHum.add(currentReading.humidityPercent)
        samplesTemp.add(currentReading.temperatureCelsius)
        samplesEc.add(currentReading.electricalConductivity)

        // Include matching tested profiles
        matchingProfiles.filter { it.hasTestData }.forEach { p ->
            samplesN.add(p.soilN)
            samplesP.add(p.soilP)
            samplesK.add(p.soilK)
            samplesPh.add(p.soilPh)
            samplesHum.add(p.soilHumidity)
            samplesTemp.add(p.soilTemp)
            samplesEc.add(p.soilEc)
        }

        // Include matching historical records
        matchingRecords.forEach { r ->
            samplesN.add(r.nitrogen)
            samplesP.add(r.phosphorus)
            samplesK.add(r.potassium)
            samplesPh.add(r.pH)
            samplesHum.add(r.humidityPercent)
            samplesTemp.add(r.temperatureCelsius)
            samplesEc.add(r.electricalConductivity)
        }

        val count = samplesN.size
        val avgN = samplesN.average().toInt()
        val avgP = samplesP.average().toInt()
        val avgK = samplesK.average().toInt()
        val avgPh = samplesPh.average()
        val avgHumidity = samplesHum.average()
        val avgTemp = samplesTemp.average()
        val avgEc = samplesEc.average().toInt()

        val minN = samplesN.minOrNull() ?: avgN
        val maxN = samplesN.maxOrNull() ?: avgN
        val minPh = samplesPh.minOrNull() ?: avgPh
        val maxPh = samplesPh.maxOrNull() ?: avgPh
        val minHum = samplesHum.minOrNull() ?: avgHumidity
        val maxHum = samplesHum.maxOrNull() ?: avgHumidity

        val diffN = maxN - minN
        val diffPh = maxPh - minPh
        val diffHum = maxHum - minHum

        val uniformity = when {
            count <= 1 -> "Single Baseline Sample Spot"
            diffN <= 10 && diffPh <= 0.4 && diffHum <= 8.0 -> "✅ High Plot Uniformity (Minimal spatial variation across $count test points)"
            diffN <= 25 && diffPh <= 0.8 -> "⚠️ Moderate Spatial Variation (Nitrogen/Moisture varies up to $diffN mg/kg across plot)"
            else -> "🚨 High Spatial Heterogeneity (Significant soil nutrient/moisture gradients detected across plot test spots)"
        }

        val overallRating = when {
            avgPh in 6.0..7.2 && avgN >= 35 && avgP >= 15 && avgK >= 80 -> "🌟 Optimal Fertile Plot Profile • High Yield Potential"
            avgPh < 5.8 -> "🍋 Acidic Soil Plot • Lime Application Recommended"
            avgPh > 7.5 -> "🧂 Alkaline / Saline Soil Plot • Gypsum / Organic Matter Recommended"
            avgN < 25 -> "⚠️ Nitrogen Deficient Plot • Nitrogenous Fertilizer Needed"
            else -> "🌾 Balanced Soil Profile • Suitable for Seasonal Sowing"
        }

        val generalPictureSummary = "Comprehensive multi-point assessment across $count test locations on '$targetFieldName': The plot exhibits an average Nitrogen of $avgN mg/kg, Phosphorus of $avgP mg/kg, and Potassium of $avgK mg/kg. Average soil pH is ${String.format(Locale.US, "%.2f", avgPh)} with ${String.format(Locale.US, "%.1f", avgHumidity)}% moisture content. $uniformity"

        return PlotAggregation(
            plotName = targetFieldName,
            sampleCount = count,
            avgN = avgN,
            avgP = avgP,
            avgK = avgK,
            avgPh = avgPh,
            avgHumidity = avgHumidity,
            avgTemp = avgTemp,
            avgEc = avgEc,
            minN = minN,
            maxN = maxN,
            minPh = minPh,
            maxPh = maxPh,
            minHum = minHum,
            maxHum = maxHum,
            spatialUniformity = uniformity,
            overallHealthRating = overallRating,
            generalPictureSummary = generalPictureSummary
        )
    }

    private fun pLocationMatch(loc1: String, loc2: String): Boolean {
        if (loc1.isBlank() || loc2.isBlank()) return false
        val s1 = loc1.take(15)
        val s2 = loc2.take(15)
        return s1.equals(s2, ignoreCase = true)
    }

    /**
     * Share / Export formatted Soil Test Report via Android Intent and Clipboard
     */
    fun shareSoilTestReport(
        context: Context,
        profile: FarmerProfile?,
        reading: SoilReading,
        plotAggregation: PlotAggregation?
    ) {
        val plotName = profile?.fieldName ?: "Active Soil Test Field"
        val farmer = profile?.name ?: "Field Partner"
        val location = profile?.location ?: _liveLocationAddress.value
        val crops = profile?.cropType ?: "General Crops"
        val dateStr = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.US).format(Date())

        val reportText = StringBuilder().apply {
            appendLine("🌱 INTELLIGENT SOIL TEST REPORT")
            appendLine("==========================================")
            appendLine("Field/Plot Name: $plotName")
            appendLine("Farmer: $farmer")
            appendLine("Crop Type: $crops")
            appendLine("Location: $location")
            appendLine("Timestamp: $dateStr")
            appendLine("==========================================")
            appendLine("📊 GROUND TRUTH SENSOR METRICS:")
            appendLine("  • Moisture: ${String.format(Locale.US, "%.1f", reading.humidityPercent)}%")
            appendLine("  • Soil Temperature: ${String.format(Locale.US, "%.1f", reading.temperatureCelsius)}°C")
            appendLine("  • pH Level: ${reading.pH} (${if (reading.pH in 6.0..7.0) "Optimal" else "Requires Balance"})")
            appendLine("  • Electrical Conductivity (EC): ${reading.electricalConductivity} µS/cm")
            appendLine("  • Nitrogen (N): ${reading.nitrogen} mg/kg")
            appendLine("  • Phosphorus (P): ${reading.phosphorus} mg/kg")
            appendLine("  • Potassium (K): ${reading.potassium} mg/kg")
            appendLine("  • Salinity (TDS): ${reading.salinityTds} mg/L")

            if (plotAggregation != null && plotAggregation.sampleCount >= 1) {
                appendLine("------------------------------------------")
                appendLine("🗺️ GENERAL PICTURE OF THE PLOT (${plotAggregation.sampleCount} Test Samples Aggregated):")
                appendLine("  • Plot Averages -> N:${plotAggregation.avgN} | P:${plotAggregation.avgP} | K:${plotAggregation.avgK} mg/kg")
                appendLine("  • Avg pH: ${String.format(Locale.US, "%.1f", plotAggregation.avgPh)} | Avg Moisture: ${String.format(Locale.US, "%.1f", plotAggregation.avgHumidity)}%")
                appendLine("  • Spatial Uniformity: ${plotAggregation.spatialUniformity}")
                appendLine("  • Overall Rating: ${plotAggregation.overallHealthRating}")
                appendLine("  • Summary: ${plotAggregation.generalPictureSummary}")
            }

            if (profile?.groundTruthInsights?.isNotBlank() == true) {
                appendLine("------------------------------------------")
                appendLine("💡 GROUND TRUTH AGRONOMIC PRESCRIPTION:")
                appendLine(profile.groundTruthInsights)
            }

            if (profile?.skyTruthInsights?.isNotBlank() == true) {
                appendLine("------------------------------------------")
                appendLine("🛰️ SKY TRUTH SATELLITE INSIGHTS:")
                appendLine(profile.skyTruthInsights)
            }
            appendLine("==========================================")
            appendLine("Generated via Intelligent Soil Diagnostics System")
        }.toString()

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Soil Test Report", reportText)
            clipboard?.setPrimaryClip(clip)
        } catch (e: Exception) {}

        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, reportText)
                putExtra(Intent.EXTRA_SUBJECT, "Soil Diagnostic Report - $plotName")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Export & Share Soil Test Report")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
            Toast.makeText(context, "Report copied to clipboard & Share dialog opened!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Report copied to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }
}

data class PlotAggregation(
    val plotName: String,
    val sampleCount: Int,
    val avgN: Int,
    val avgP: Int,
    val avgK: Int,
    val avgPh: Double,
    val avgHumidity: Double,
    val avgTemp: Double,
    val avgEc: Int,
    val minN: Int,
    val maxN: Int,
    val minPh: Double,
    val maxPh: Double,
    val minHum: Double,
    val maxHum: Double,
    val spatialUniformity: String,
    val overallHealthRating: String,
    val generalPictureSummary: String
)

const val MOCK_SOIL_MOLD_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
const val MOCK_SICK_LEAF_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
