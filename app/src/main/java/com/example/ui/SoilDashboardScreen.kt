package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.viewmodel.MOCK_SOIL_MOLD_BASE64
import com.example.viewmodel.MOCK_SICK_LEAF_BASE64
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.FarmerProfile
import com.example.database.SoilRecord
import com.example.modbus.SoilReading
import com.example.usb.ConnectionStatus
import com.example.viewmodel.SimulatedProfile
import com.example.viewmodel.SoilViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing

import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoilDashboardScreen(
    viewModel: SoilViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Core State Flows from SoilViewModel
    val currentReading by viewModel.currentReading.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.usbSerialManager.connectionStatus.collectAsStateWithLifecycle()
    val deviceName by viewModel.usbSerialManager.deviceName.collectAsStateWithLifecycle()
    val slaveId by viewModel.slaveId.collectAsStateWithLifecycle()
    val isPolling by viewModel.isPollingActive.collectAsStateWithLifecycle()
    val isSimulating by viewModel.isSimulationEnabled.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val uiLogs by viewModel.uiConsoleLogs.collectAsStateWithLifecycle()
    val records by viewModel.historicalRecords.collectAsStateWithLifecycle()

    // Modern Precision Ag States
    val allFarmerProfiles by viewModel.allFarmerProfiles.collectAsStateWithLifecycle()
    val selectedFarmerProfile by viewModel.selectedFarmerProfile.collectAsStateWithLifecycle()
    val liveLocationAddress by viewModel.liveLocationAddress.collectAsStateWithLifecycle()
    val firebaseSyncLogs by viewModel.firebaseSyncLogs.collectAsStateWithLifecycle()
    val firestoreSyncStatus by viewModel.firestoreSyncStatus.collectAsStateWithLifecycle()
    val isDiagnosticsRunning by viewModel.isDiagnosticsRunning.collectAsStateWithLifecycle()
    val currentAgent by viewModel.currentAgent.collectAsStateWithLifecycle()


    // Chat states
    val chatHistory by viewModel.soilChatHistory.collectAsStateWithLifecycle()
    val chatIsLoading by viewModel.chatIsLoading.collectAsStateWithLifecycle()
    val isSoilSpeaking by viewModel.isSoilSpeaking.collectAsStateWithLifecycle()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsStateWithLifecycle()
    val isSttListening by viewModel.isSttListening.collectAsStateWithLifecycle()
    val attachedImageBase64 by viewModel.attachedImageBase64.collectAsStateWithLifecycle()
    val attachedVideoBase64 by viewModel.attachedVideoBase64.collectAsStateWithLifecycle()

    // NPK Bluetooth Pump states
    val btConnectionState by viewModel.bluetoothPumpManager.connectionState.collectAsStateWithLifecycle()
    val btDeviceName by viewModel.bluetoothPumpManager.connectedDeviceName.collectAsStateWithLifecycle()
    val btPairedDevices by viewModel.bluetoothPumpManager.pairedDevices.collectAsStateWithLifecycle()
    val btPumpActiveState by viewModel.bluetoothPumpManager.pumpActiveState.collectAsStateWithLifecycle()
    val btLogs by viewModel.bluetoothPumpManager.bluetoothLogs.collectAsStateWithLifecycle()
    val npkDosingPlan by viewModel.npkDosingPlan.collectAsStateWithLifecycle()
    val npkThresholds by viewModel.npkThresholds.collectAsStateWithLifecycle()
    val autoTriggerPumps by viewModel.autoTriggerPumpsOnGroundTruth.collectAsStateWithLifecycle()
    val lastPumpNotice by viewModel.lastPumpTriggerNotice.collectAsStateWithLifecycle()

    // Local Compose dialog toggles
    var showProfileDropdown by remember { mutableStateOf(false) }
    var showTerminalLogs by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }
    var showManageProfilesDialog by remember { mutableStateOf(false) }
    var showChatDialog by remember { mutableStateOf(false) }
    var locationInput by remember { mutableStateOf("") }
    var showAdminPinDialog by remember { mutableStateOf(false) }
    var showAdminPanelDialog by remember { mutableStateOf(false) }
    var adminPinInput by remember { mutableStateOf("") }
    var adminPinError by remember { mutableStateOf(false) }
    var showBluetoothDeviceDialog by remember { mutableStateOf(false) }
    var showNpkThresholdDialog by remember { mutableStateOf(false) }
    var selectedMajorTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val isBackHandled = showAdminPanelDialog || showAdminPinDialog ||
        showBluetoothDeviceDialog || showNpkThresholdDialog ||
        showChatDialog || showCreateProfileDialog || showManageProfilesDialog ||
        showTerminalLogs || showSignInDialog || showProfileDropdown || (selectedMajorTab != 0)

    BackHandler(enabled = isBackHandled) {
        when {
            showAdminPanelDialog -> showAdminPanelDialog = false
            showAdminPinDialog -> showAdminPinDialog = false
            showBluetoothDeviceDialog -> showBluetoothDeviceDialog = false
            showNpkThresholdDialog -> showNpkThresholdDialog = false
            showChatDialog -> showChatDialog = false
            showCreateProfileDialog -> showCreateProfileDialog = false
            showManageProfilesDialog -> showManageProfilesDialog = false
            showTerminalLogs -> showTerminalLogs = false
            showSignInDialog -> showSignInDialog = false
            showProfileDropdown -> showProfileDropdown = false
            selectedMajorTab != 0 -> selectedMajorTab = 0
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Processing selected image...", Toast.LENGTH_SHORT).show()
                }
                try {
                    val b64 = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                                if (bitmap != null) {
                                    val maxDimension = 1024
                                    val width = bitmap.width
                                    val height = bitmap.height
                                    val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                                        val ratio = width.toDouble() / height.toDouble()
                                        val (newWidth, newHeight) = if (ratio > 1.0) {
                                            (maxDimension to (maxDimension / ratio).toInt())
                                        } else {
                                            (((maxDimension * ratio).toInt()) to maxDimension)
                                        }
                                        android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                                    } else {
                                        bitmap
                                    }
                                    
                                    val baos = java.io.ByteArrayOutputStream()
                                    scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, baos)
                                    val bytes = baos.toByteArray()
                                    
                                    if (scaledBitmap != bitmap) {
                                        scaledBitmap.recycle()
                                    }
                                    bitmap.recycle()
                                    
                                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                } else {
                                    null
                                }
                            }
                        } catch (e: Throwable) {
                            null
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (b64 != null) {
                            viewModel.attachImageBase64(b64)
                            Toast.makeText(context, "Crop Image Attached and Compressed Successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Could not decode or resize crop image data.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Throwable) {
                    Toast.makeText(context, "Could not attach image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Analyzing crop video...", Toast.LENGTH_SHORT).show()
                }
                try {
                    val b64 = withContext(Dispatchers.IO) {
                        var isSizeValid = true
                        try {
                            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                val sizeInBytes = afd.length
                                if (sizeInBytes > 5 * 1024 * 1024) { // 5MB Limit
                                    isSizeValid = false
                                }
                            }
                        } catch (ex: Throwable) {
                            // Fall-safe if descriptor length unavailable
                        }
                        
                        if (!isSizeValid) {
                            null
                        } else {
                            try {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    val bytes = stream.readBytes()
                                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                }
                            } catch (e: Throwable) {
                                null
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (b64 != null) {
                            viewModel.attachVideoBase64(b64)
                            Toast.makeText(context, "Crop Video Attached Successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Crop video too large (>5MB) or could not be read.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Throwable) {
                    Toast.makeText(context, "Could not attach video: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Processing camera photo...", Toast.LENGTH_SHORT).show()
                }
                val b64 = withContext(Dispatchers.IO) {
                    try {
                        val baos = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, baos)
                        val bytes = baos.toByteArray()
                        bitmap.recycle()
                        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    } catch (e: Throwable) {
                        null
                    }
                }
                withContext(Dispatchers.Main) {
                    if (b64 != null) {
                        viewModel.attachImageBase64(b64)
                        Toast.makeText(context, "Camera Photo Captured & Attached Successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to capture photo.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.askSoil(spokenText)
                Toast.makeText(context, "Voice Input Received: \"$spokenText\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startVoiceListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to your Intelligent Soil Farm Assistant...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Speech recognition is not supported or active on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.fetchLiveLocation(context)
            Toast.makeText(context, "Location permission granted. Capturing real GPS coordinates...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Location permission denied. Cannot capture real GPS coordinates.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestGpsWithPermission() {
        if (com.example.api.LocationHelper.hasLocationPermission(context)) {
            viewModel.fetchLiveLocation(context)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val btGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions[android.Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }
        if (btGranted) {
            viewModel.bluetoothPumpManager.refreshPairedDevices()
            showBluetoothDeviceDialog = true
        } else {
            Toast.makeText(context, "Bluetooth Connect permission is required to link HC-05 pumps.", Toast.LENGTH_LONG).show()
        }
    }

    fun openHc05BluetoothPicker() {
        if (!viewModel.bluetoothPumpManager.isBluetoothSupported()) {
            Toast.makeText(context, "Bluetooth is not supported on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        if (viewModel.bluetoothPumpManager.hasBluetoothPermission()) {
            viewModel.bluetoothPumpManager.refreshPairedDevices()
            showBluetoothDeviceDialog = true
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    LaunchedEffect(Unit) {
        requestGpsWithPermission()
    }

    val configuration = LocalConfiguration.current
    val columnCount = if (configuration.screenWidthDp >= 600) 4 else 2

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF6FBF1))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. App Header Status Segment (Persistent Top Header)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFDCE5D8))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Very Top Action Row: Google Sign In + Admin Portal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Google Account Sign-In / Profile Badge
                        if (currentAgent == null) {
                            Button(
                                onClick = { showSignInDialog = true },
                                modifier = Modifier
                                    .testTag("google_login_button")
                                    .height(34.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1A73E8),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Google Account Login",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Google Sign In",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Surface(
                                onClick = { showSignInDialog = true },
                                modifier = Modifier
                                    .testTag("google_account_profile_badge")
                                    .height(34.dp),
                                color = Color(0xFFE8F0FE),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFD2E3FC))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1A73E8)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = currentAgent!!.name.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentAgent!!.name.split(" ").firstOrNull() ?: "Google User",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A73E8)
                                    )
                                }
                            }
                        }

                        // Highly Visible Admin Portal Lock Button
                        Button(
                            onClick = {
                                adminPinInput = ""
                                adminPinError = false
                                showAdminPinDialog = true
                            },
                            modifier = Modifier
                                .testTag("admin_portal_button")
                                .height(34.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Admin Portal Lock",
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Admin Portal",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE8ECE7), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Brand Heading Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isConnected = connectionStatus == ConnectionStatus.CONNECTED_IDLE || 
                                          connectionStatus == ConnectionStatus.READING_POLLING
                        val logoTint = if (isConnected) Color(0xFF386A20) else Color(0xFFC62828)

                        Icon(
                            imageVector = Icons.Default.Spa,
                            contentDescription = "Soil Probe Logo",
                            tint = logoTint,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Intelligent Soil",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF191D17),
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Stop guessing, Start farming.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF42493F)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Location Display below tagline
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF2F5F0))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Live GPS Coordinates",
                            tint = Color(0xFF386A20),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = liveLocationAddress,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF386A20),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { requestGpsWithPermission() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Capture Real GPS Location",
                                tint = Color(0xFF386A20),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Connection Status & Hardware Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Link Status: " + when (connectionStatus) {
                                ConnectionStatus.DISCONNECTED -> "disconnected from OTG cable"
                                ConnectionStatus.PERMISSIONS_REQUESTED -> "requesting system USB approval"
                                ConnectionStatus.CONNECTED_IDLE -> "handshaking (idle)"
                                ConnectionStatus.READING_POLLING -> "actively scheduling query payloads"
                                ConnectionStatus.ERROR_PORT_UNAVAILABLE -> "error: sensor serial port busy/unavailable"
                            }.replaceFirstChar { it.uppercase() },
                            color = when (connectionStatus) {
                                ConnectionStatus.READING_POLLING -> Color(0xFF386A20)
                                ConnectionStatus.CONNECTED_IDLE -> Color(0xFF386A20)
                                ConnectionStatus.ERROR_PORT_UNAVAILABLE -> Color(0xFFC62828)
                                else -> Color(0xFF42493F)
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = {
                                    if (isSimulating) {
                                        viewModel.addConsoleLog("Simulator runs completely local.")
                                    } else {
                                        if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                                            viewModel.connectUsb()
                                        } else {
                                            viewModel.disconnectUsb()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSimulating) Color(0xFFE0E4DB) else if (connectionStatus == ConnectionStatus.DISCONNECTED) Color(0xFF386A20) else Color(0xFFC62828),
                                    contentColor = if (isSimulating) Color(0xFF42493F) else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isSimulating) "Port Locked" else if (connectionStatus == ConnectionStatus.DISCONNECTED) "Connect OTG" else "Disconnect",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. PRIMARY 3 MAJOR TABS NAVIGATION BAR
        item {
            TabRow(
                selectedTabIndex = selectedMajorTab,
                containerColor = Color.White,
                contentColor = Color(0xFF386A20),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedMajorTab]),
                        color = Color(0xFF386A20),
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFDCE5D8), RoundedCornerShape(16.dp))
            ) {
                Tab(
                    selected = selectedMajorTab == 0,
                    onClick = { selectedMajorTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Grass, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ground Truth", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedMajorTab == 1,
                    onClick = { selectedMajorTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SatelliteAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sky Truth", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedMajorTab == 2,
                    onClick = { selectedMajorTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Talk to Soil", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
            }
        }

        // 3. SCREEN TAB CONTENT SELECTION
        when (selectedMajorTab) {
            0 -> {
                // ==============================================================
                // SCREEN 1: GROUND TRUTH (Physiological Soil Telemetry & Diagnostics)
                // ==============================================================

                // 1A. ACTIVE FARMER & FIELD PROFILE MANAGER
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 8.dp, bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFDCE5D8))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Active Field & Farmer Profile",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF191D17)
                                )
                                TextButton(
                                    onClick = { showManageProfilesDialog = true },
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF386A20))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Switch Plot", fontSize = 11.sp, color = Color(0xFF386A20), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            if (selectedFarmerProfile == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF2F5F0))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Select or create a farmer profile to log ground truth telemetry.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF42493F),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { showCreateProfileDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20)),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Add Lot", modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("New Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            if (allFarmerProfiles.isNotEmpty()) {
                                                Button(
                                                    onClick = { showManageProfilesDialog = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF72796F)),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Default.People, contentDescription = "Browse Lots", modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Browse (${allFarmerProfiles.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                val profile = selectedFarmerProfile!!
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF2F5F0))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = profile.name,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp,
                                                color = Color(0xFF386A20)
                                            )
                                            Text("Plot: ${profile.fieldName} • Crop: ${profile.cropType}", fontSize = 11.sp, color = Color(0xFF191D17), fontWeight = FontWeight.Bold)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    val plotAgg = viewModel.getPlotAggregation(selectedFarmerProfile, allFarmerProfiles, records, currentReading)
                                                    viewModel.shareSoilTestReport(context, profile, currentReading, plotAgg)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Export Profile Report",
                                                    tint = Color(0xFF386A20),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (isDiagnosticsRunning) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                                color = Color(0xFF386A20),
                                                trackColor = Color(0xFFDCE5D8)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Connecting Google Alpha Earth & Soil AI Agronomist...", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF386A20))
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.runCollectTruthTest(context) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.OnlinePrediction, contentDescription = "Run Diagnostic", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (profile.hasTestData) "Re-Collect Ground & Sky Truth" else "Collect Ground & Sky Truth Diagnostics",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 1B. PLOT AGGREGATION & GENERAL PICTURE OF THE PLOT (MULTIPLE TEST SPOTS)
                item {
                    val plotAgg = viewModel.getPlotAggregation(selectedFarmerProfile, allFarmerProfiles, records, currentReading)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFC5E1A5))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Map, contentDescription = "Plot Picture", tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "General Picture of the Plot",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFDCEDC8))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${plotAgg.sampleCount} Spot Samples",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Ratings Chips
                            Text(text = plotAgg.overallHealthRating, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = plotAgg.spatialUniformity, fontSize = 10.sp, color = Color(0xFF33691E))

                            Spacer(modifier = Modifier.height(8.dp))

                            // General Picture Summary Paragraph
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFDCEDC8), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = plotAgg.generalPictureSummary,
                                    fontSize = 11.sp,
                                    color = Color(0xFF191D17),
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Aggregated Metrics Table
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("NPK AVG", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Text("${plotAgg.avgN}-${plotAgg.avgP}-${plotAgg.avgK}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                                    Text("mg/kg", fontSize = 8.sp, color = Color(0xFF42493F))
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("AVG pH", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Text(String.format(Locale.US, "%.1f", plotAgg.avgPh), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                                    Text("pH level", fontSize = 8.sp, color = Color(0xFF42493F))
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("AVG MOISTURE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Text("${String.format(Locale.US, "%.1f", plotAgg.avgHumidity)}%", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                                    Text("water %", fontSize = 8.sp, color = Color(0xFF42493F))
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("AVG EC", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Text("${plotAgg.avgEc}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                                    Text("µS/cm", fontSize = 8.sp, color = Color(0xFF42493F))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    viewModel.shareSoilTestReport(context, selectedFarmerProfile, currentReading, plotAgg)
                                },
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                                border = BorderStroke(1.dp, Color(0xFF81C784))
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Plot Synthesis Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 1C. LIVE SENSOR TELEMETRY METRICS GRID (8 Core Gauges)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Probe Telemetry Metrics",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF191D17)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        viewModel.calibrateAirZero()
                                        Toast.makeText(context, "Probe Calibrated: Open Air set to 0.0% Moisture", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Air (0%)", fontSize = 9.sp) },
                                    leadingIcon = { Icon(Icons.Default.Air, null, modifier = Modifier.size(12.dp)) },
                                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFFE3F2FD))
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        viewModel.calibrateWater100()
                                        Toast.makeText(context, "Probe Calibrated: Water Submerged set to 100.0% Moisture", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Water (100%)", fontSize = 9.sp) },
                                    leadingIcon = { Icon(Icons.Default.Water, null, modifier = Modifier.size(12.dp)) },
                                    colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFFE1F5FE))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        val metrics = listOf(
                            SensorMetric(
                                label = "Soil Moisture",
                                value = "${String.format(Locale.US, "%.1f", currentReading.humidityPercent)}%",
                                target = "Optimal: 30 - 70%",
                                accentColor = if (currentReading.humidityPercent in 30.0..70.0) Color(0xFF1565C0) else Color(0xFFC62828),
                                containerColor = Color(0xFFE3F2FD),
                                borderColor = Color(0xFFBBDEFB),
                                icon = Icons.Default.WaterDrop
                            ),
                            SensorMetric(
                                label = "Soil Temperature",
                                value = "${String.format(Locale.US, "%.1f", currentReading.temperatureCelsius)}°C",
                                target = "Optimal: 18 - 32°C",
                                accentColor = if (currentReading.temperatureCelsius in 18.0..32.0) Color(0xFFE65100) else Color(0xFFC62828),
                                containerColor = Color(0xFFFFF3E0),
                                borderColor = Color(0xFFFFE0B2),
                                icon = Icons.Default.Thermostat
                            ),
                            SensorMetric(
                                label = "pH Level",
                                value = "${currentReading.pH}",
                                target = "Optimal: 6.0 - 7.5",
                                accentColor = if (currentReading.pH in 6.0..7.5) Color(0xFF7B1FA2) else Color(0xFFC62828),
                                containerColor = Color(0xFFF3E5F5),
                                borderColor = Color(0xFFE1BEE7),
                                icon = Icons.Default.Science
                            ),
                            SensorMetric(
                                label = "EC (Conductivity)",
                                value = "${currentReading.electricalConductivity} µS/cm",
                                target = "Optimal: 200 - 1200",
                                accentColor = Color(0xFF00838F),
                                containerColor = Color(0xFFE0F7FA),
                                borderColor = Color(0xFFB2EBF2),
                                icon = Icons.Default.ElectricBolt
                            ),
                            SensorMetric(
                                label = "Nitrogen (N)",
                                value = "${currentReading.nitrogen} mg/kg",
                                target = "Target: 40 - 100",
                                accentColor = if (currentReading.nitrogen >= 35) Color(0xFF2E7D32) else Color(0xFFC62828),
                                containerColor = Color(0xFFE8F5E9),
                                borderColor = Color(0xFFC8E6C9),
                                icon = Icons.Default.Eco
                            ),
                            SensorMetric(
                                label = "Phosphorus (P)",
                                value = "${currentReading.phosphorus} mg/kg",
                                target = "Target: 15 - 50",
                                accentColor = Color(0xFFC2185B),
                                containerColor = Color(0xFFFCE4EC),
                                borderColor = Color(0xFFF8BBD0),
                                icon = Icons.Default.Park
                            ),
                            SensorMetric(
                                label = "Potassium (K)",
                                value = "${currentReading.potassium} mg/kg",
                                target = "Target: 80 - 250",
                                accentColor = Color(0xFF283593),
                                containerColor = Color(0xFFE8EAF6),
                                borderColor = Color(0xFFC5CAE9),
                                icon = Icons.Default.Grass
                            ),
                            SensorMetric(
                                label = "Salinity (TDS)",
                                value = "${currentReading.salinityTds} mg/L",
                                target = "Safe < 800 mg/L",
                                accentColor = Color(0xFF4E342E),
                                containerColor = Color(0xFFEFEBE9),
                                borderColor = Color(0xFFD7CCC8),
                                icon = Icons.Default.Grain
                            )
                        )

                        metrics.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { metric ->
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = metric.containerColor),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.2.dp, metric.borderColor)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = metric.label,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = metric.accentColor
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .background(metric.borderColor.copy(alpha = 0.6f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = metric.icon,
                                                        contentDescription = null,
                                                        tint = metric.accentColor,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = metric.value,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = metric.accentColor
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = metric.target,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = metric.accentColor.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 1D. GROUND TRUTH AGRONOMIC PRESCRIPTION INSIGHTS
                if (selectedFarmerProfile?.hasTestData == true) {
                    val profile = selectedFarmerProfile!!
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFDCE5D8))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, "AI", tint = Color(0xFF386A20), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "🌾 Ground Truth Agronomic Prescriptions",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF191D17)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFEBF3E7))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = profile.groundTruthInsights,
                                        fontSize = 11.sp,
                                        color = Color(0xFF191D17),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 1D-B. HC-05 BLUETOOTH NPK NUTRIENT PUMP AUTOMATION
                item {
                    NpkPumpControlCard(
                        connectionState = btConnectionState,
                        connectedDeviceName = btDeviceName,
                        dosingPlan = npkDosingPlan,
                        pumpActiveState = btPumpActiveState,
                        autoTriggerEnabled = autoTriggerPumps,
                        lastNotice = lastPumpNotice,
                        bluetoothLogs = btLogs,
                        onConnectClick = { openHc05BluetoothPicker() },
                        onDisconnectClick = { viewModel.bluetoothPumpManager.disconnect() },
                        onAutoTriggerChange = { viewModel.setAutoTriggerPumps(it) },
                        onTriggerNeededPumps = { viewModel.triggerRecommendedNpkPumps() },
                        onEmergencyStopAll = { viewModel.emergencyStopAllPumps() },
                        onManualPumpToggle = { nutrient, turnOn -> viewModel.toggleManualPump(nutrient, turnOn) },
                        onEditThresholdsClick = { showNpkThresholdDialog = true }
                    )
                }

                // 1E. SPOT RECORDING & LOGGING TOOLS
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "Record Spot Telemetry Data",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF191D17)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = locationInput,
                                onValueChange = { locationInput = it },
                                placeholder = { Text("E.g., Greenhouse A Bed 3", color = Color.Black, fontSize = 12.sp) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 13.sp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("username_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = blackTextFieldColors()
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.saveCurrentReading(locationInput)
                                    locationInput = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20), contentColor = Color.White),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("task_item_card")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save Reading")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Log Spot", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Diagnostic Console Terminal Drawer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTerminalLogs = !showTerminalLogs }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "OTG Console logs toggle",
                                    tint = Color(0xFF386A20)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Serial & Sync Log Terminal (${uiLogs.size + firebaseSyncLogs.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF191D17)
                                )
                            }
                            Icon(
                                imageVector = if (showTerminalLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle diagnostic log drawer",
                                tint = Color(0xFF191D17)
                            )
                        }

                        AnimatedVisibility(
                            visible = showTerminalLogs,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF191D17))
                                    .border(1.dp, Color(0xFFDCE5D8), RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            ) {
                                val state = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .verticalScroll(state)
                                        .fillMaxSize()
                                ) {
                                    Text("--- FIREBASE FIRESTORE SYNC LOGS ---", color = Color(0xFF81D4FA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    if (firebaseSyncLogs.isEmpty()) {
                                        Text("[System] Firestore offline cache synchronization initialized.", color = Color(0xFF72796F), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    } else {
                                        firebaseSyncLogs.forEach { log ->
                                            Text(text = log, color = Color(0xFFA5D6A7), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("--- MODBUS SERIAL TRANSFERS ---", color = Color(0xFFFFF59D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    if (uiLogs.isEmpty()) {
                                        Text("No serial signals yet.", color = Color(0xFF72796F), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    } else {
                                        uiLogs.forEach { log ->
                                            Text(text = log, color = Color(0xFFB0BEC5), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Historic Spot readings
                        Text(
                            text = "Historical Spot Logs (${records.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF191D17)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (records.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF2F5F0))
                                    .border(1.dp, Color(0xFFDCE5D8), RoundedCornerShape(16.dp))
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No saved logs. Click 'Log Spot' to save current sensor telemetry to local memory.",
                                    color = Color(0xFF42493F),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Erase DB Records",
                                    color = Color(0xFFC62828),
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable { viewModel.clearAllHistory() }
                                        .padding(4.dp)
                                )
                            }

                            records.forEach { record ->
                                HistoricalRecordItem(
                                    record = record,
                                    onDelete = { viewModel.deleteRecord(record.id) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            1 -> {
                // ==============================================================
                // SCREEN 2: SKY TRUTH (Satellite Remote Sensing, Microclimate & NDVI)
                // ==============================================================

                // 2A. SATELLITE PASS & REMOTE SENSING OVERVIEW
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 8.dp, bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFBBDEFB))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SatelliteAlt, contentDescription = "Satellite", tint = Color(0xFF1976D2), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Sky Truth Remote Sensing",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF0D47A1)
                                        )
                                        Text(
                                            text = "Sentinel-2 Multi-Spectral & Google Alpha Earth Pass",
                                            fontSize = 10.sp,
                                            color = Color(0xFF1565C0)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE3F2FD))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("LIVE ORBIT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF5F5F5))
                                        .padding(10.dp)
                                ) {
                                    Text("Orbital Pass", fontSize = 10.sp, color = Color(0xFF616161))
                                    Text("Sentinel-2B", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                                    Text("10m Resolution", fontSize = 9.sp, color = Color(0xFF757575))
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF5F5F5))
                                        .padding(10.dp)
                                ) {
                                    Text("Cloud Coverage", fontSize = 10.sp, color = Color(0xFF616161))
                                    Text("2.4% Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    Text("Optimal Visibility", fontSize = 9.sp, color = Color(0xFF757575))
                                }
                            }
                        }
                    }
                }

                // 2B. SPECTRAL VEGETATION HEALTH INDEX GAUGES (NDVI & NDWI)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFDCE5D8))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Spectral Canopy Index Gauges",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF191D17)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // NDVI Card
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE8F5E9))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Eco, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("NDVI (Canopy Health Index)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1B5E20))
                                    }
                                    Text("0.72", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { 0.72f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = Color(0xFF2E7D32),
                                    trackColor = Color(0xFFC8E6C9)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Dense, healthy photosynthetic active biomass across plot.", fontSize = 10.sp, color = Color(0xFF33691E))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // NDWI Card
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE3F2FD))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Water, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("NDWI (Water Turgor Index)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0D47A1))
                                    }
                                    Text("0.28", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF1976D2))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { 0.58f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = Color(0xFF1976D2),
                                    trackColor = Color(0xFFBBDEFB)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Adequate leaf water turgor. No acute canopy drought stress.", fontSize = 10.sp, color = Color(0xFF1565C0))
                            }
                        }
                    }
                }

                // 2C. MICROCLIMATE & WEATHER FORECAST CARD
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFDCE5D8))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WbSunny, contentDescription = "Weather", tint = Color(0xFFF57C00), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Plot Microclimate & Forecast", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF191D17))
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFFF3E0))
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Air Temp", fontSize = 10.sp, color = Color(0xFFE65100))
                                    Text("29°C", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEF6C00))
                                    Text("Partly Sunny", fontSize = 9.sp, color = Color(0xFFF57C00))
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFE0F7FA))
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Humidity", fontSize = 10.sp, color = Color(0xFF006064))
                                    Text("64%", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF00838F))
                                    Text("Dew Pt 18°C", fontSize = 9.sp, color = Color(0xFF0097A7))
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF3E5F5))
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Evapotransp.", fontSize = 10.sp, color = Color(0xFF4A148C))
                                    Text("4.2 mm", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6A1B9A))
                                    Text("Daily ETo Rate", fontSize = 9.sp, color = Color(0xFF7B1FA2))
                                }
                            }
                        }
                    }
                }

                // 2D. SKY TRUTH AI PREDICTIONS & INSIGHTS
                if (selectedFarmerProfile?.hasTestData == true) {
                    val profile = selectedFarmerProfile!!
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFDCE5D8))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, "AI", tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "☀️ Sky Truth Satellite Agronomic Advice",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF191D17)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFE3F2FD))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = profile.skyTruthInsights,
                                        fontSize = 11.sp,
                                        color = Color(0xFF191D17),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // ==============================================================
                // SCREEN 3: TALK TO YOUR SOIL (Multimodal Gemini AI Assistant)
                // ==============================================================

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 8.dp, bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFE1BEE7))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Title
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Forum, contentDescription = "Talk to soil", tint = Color(0xFF8E24AA), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Talk To Your Soil",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF4A148C)
                                        )
                                        Text(
                                            text = "Intelligent soil Farm assistant",
                                            fontSize = 10.sp,
                                            color = Color(0xFF7B1FA2)
                                        )
                                    }
                                }

                                // Audio Voice Synthesizer Toggle
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.toggleTtsEnabled() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTtsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                            contentDescription = "Toggle Voice Synthesizer",
                                            tint = if (isTtsEnabled) Color(0xFF8E24AA) else Color(0xFF72796F),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Speech Visualizer Banner
                            if (isSoilSpeaking || isSttListening) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF3E5F5))
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isSttListening) "🎙️ Listening to your voice..." else "🔊 Soil AI speaking...",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF6A1B9A)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            repeat(5) { index ->
                                                AudioVisualizerBar(index = index, isSpeaking = isSoilSpeaking || isSttListening)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // Preset Quick Action Chips
                            Text("Suggested Agricultural Queries:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF42493F))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val suggestions = listOf(
                                    "What fertilizer to apply?",
                                    "Diagnose leaf photo sample",
                                    "Is moisture ideal for sowing?",
                                    "How to balance soil pH?"
                                )
                                suggestions.forEach { prompt ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFFF3E5F5))
                                            .border(1.dp, Color(0xFFE1BEE7), RoundedCornerShape(16.dp))
                                            .clickable { viewModel.askSoil(prompt) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(prompt, fontSize = 10.sp, color = Color(0xFF6A1B9A), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Chat Messages Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFAFAFA))
                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            ) {
                                val scrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                ) {
                                    if (chatHistory.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "Soil AI is ready. Ask: 'What fertilizer is needed?' or attach a leaf photo for visual diagnosis.",
                                                textAlign = TextAlign.Center,
                                                fontSize = 11.sp,
                                                color = Color(0xFF757575),
                                                modifier = Modifier.padding(20.dp)
                                            )
                                        }
                                    } else {
                                        chatHistory.forEach { turn ->
                                            val isUser = turn.second
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(
                                                            RoundedCornerShape(
                                                                topStart = 12.dp,
                                                                topEnd = 12.dp,
                                                                bottomStart = if (isUser) 12.dp else 0.dp,
                                                                bottomEnd = if (isUser) 0.dp else 12.dp
                                                            )
                                                        )
                                                        .background(if (isUser) Color(0xFFE8F5E9) else Color(0xFFF3E5F5))
                                                        .padding(10.dp)
                                                        .widthIn(max = 240.dp)
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = turn.first,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF212121)
                                                        )
                                                        if (!isUser) {
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Row(
                                                                modifier = Modifier.clickable { viewModel.speakText(turn.first) },
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Listen", tint = Color(0xFF8E24AA), modifier = Modifier.size(12.dp))
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Text("Listen", fontSize = 9.sp, color = Color(0xFF8E24AA), fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (chatIsLoading) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFFFF9C4))
                                                    .padding(8.dp)
                                            ) {
                                                Text("Decoding biosignals...", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                                            }
                                        }
                                    }
                                }
                                LaunchedEffect(chatHistory.size, chatIsLoading) {
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Attached Specimen Previews
                            if (attachedImageBase64 != null || attachedVideoBase64 != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (attachedImageBase64 != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFE8F5E9))
                                                .border(1.dp, Color(0xFF81C784), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Photo, "Photo Attached", tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Photo Clip", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove Photo",
                                                    tint = Color(0xFFC62828),
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { viewModel.attachImageBase64(null) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Specimen & Camera Photo Chooser Buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { cameraLauncher.launch(null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8EAF6), contentColor = Color(0xFF283593)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, "Take Phone Camera Photo", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("📷 Phone Camera", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3E5F5), contentColor = Color(0xFF6A1B9A)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Photo, "Gallery Photo", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🖼️ Gallery Crop Photo", fontSize = 9.sp)
                                }

                                Button(
                                    onClick = { viewModel.attachImageBase64(MOCK_SOIL_MOLD_BASE64) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Grass, "Attach Probe Scan", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🌱 Soil Specimen", fontSize = 9.sp)
                                }

                                Button(
                                    onClick = { viewModel.attachImageBase64(MOCK_SICK_LEAF_BASE64) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEDE7F6), contentColor = Color(0xFF5E35B1)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Eco, "Attach Leaf Specimen", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🍂 Leaf Spot Check", fontSize = 9.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Interactive Text & Voice Chat Input Row
                            var chatInputText by remember { mutableStateOf("") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Microphone Button to Listen to Voice
                                IconButton(
                                    onClick = { startVoiceListening() },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            if (isSttListening) Color(0xFFFFEBEE) else Color(0xFFF3E5F5),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSttListening) Color(0xFFC62828) else Color(0xFFE1BEE7),
                                            RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Microphone - Listen to voice",
                                        tint = if (isSttListening) Color(0xFFC62828) else Color(0xFF8E24AA),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Text Conversation Input Field
                                OutlinedTextField(
                                    value = chatInputText,
                                    onValueChange = { chatInputText = it },
                                    placeholder = { Text("Ask Intelligent Soil Assistant...", fontSize = 11.sp, color = Color.Black) },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 12.sp),
                                    colors = blackTextFieldColors(),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // Text Send Button
                                IconButton(
                                    onClick = {
                                        if (chatInputText.isNotBlank() || attachedImageBase64 != null) {
                                            viewModel.askSoil(chatInputText)
                                            chatInputText = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF8E24AA), RoundedCornerShape(12.dp))
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Message", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS REGION ---

    if (showBluetoothDeviceDialog) {
        BluetoothDevicePickerDialog(
            pairedDevices = btPairedDevices,
            onRefresh = { viewModel.bluetoothPumpManager.refreshPairedDevices() },
            onSelectDevice = { device ->
                viewModel.bluetoothPumpManager.connectToDevice(device.address, device.name)
                showBluetoothDeviceDialog = false
            },
            onDismiss = { showBluetoothDeviceDialog = false }
        )
    }

    if (showNpkThresholdDialog) {
        NpkThresholdDialog(
            currentThresholds = npkThresholds,
            onSave = { minN, minP, minK ->
                viewModel.updateNpkThresholds(minN, minP, minK)
                Toast.makeText(context, "NPK Targets updated: N>=$minN, P>=$minP, K>=$minK mg/kg", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showNpkThresholdDialog = false }
        )
    }

    // 1. SIGN IN DIALOG (Firebase Google Auth Sim)
    if (showSignInDialog) {
        Dialog(onDismissRequest = { showSignInDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Security, "Google Security", tint = Color(0xFF386A20), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Secure Field Agent Sign-In", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF191D17))
                    Text("Powered by Firebase Auth & Google Identity Services", fontSize = 10.sp, color = Color(0xFF72796F), textAlign = TextAlign.Center)
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    var agentName by remember { mutableStateOf("") }
                    var agentEmail by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = agentName,
                        onValueChange = { agentName = it },
                        label = { Text("Agent Full Name", fontSize = 11.sp, color = Color.Black) },
                        singleLine = true,
                        placeholder = { Text("E.g., Dr. Emily Carter", fontSize = 12.sp, color = Color.Black) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                        colors = blackTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = agentEmail,
                        onValueChange = { agentEmail = it },
                        label = { Text("Registered Agent Email", fontSize = 11.sp, color = Color.Black) },
                        singleLine = true,
                        placeholder = { Text("Emily@intelligentsoil.org", fontSize = 12.sp, color = Color.Black) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                        colors = blackTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showSignInDialog = false }) {
                            Text("Cancel", color = Color(0xFF72796F))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.loginFieldAgent(context, agentName, agentEmail) {}
                                showSignInDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20))
                        ) {
                            Text("Authenticate")
                        }
                    }
                }
            }
        }
    }


    // 2. CREATE FARMER PROFILE DIALOG
    if (showCreateProfileDialog) {
        Dialog(onDismissRequest = { showCreateProfileDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add Farmer & Field Lot", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF191D17))
                    Text("Synchronizes with Firestore Cloud Realtime Schema", fontSize = 10.sp, color = Color(0xFF72796F))
                    
                    Spacer(modifier = Modifier.height(14.dp))

                    var farmerName by remember { mutableStateOf("") }
                    var farmerPhone by remember { mutableStateOf("") }
                    var fieldName by remember { mutableStateOf("") }
                    var cropType by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = farmerName,
                        onValueChange = { farmerName = it },
                        label = { Text("Farmer Name", color = Color.Black) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                        colors = blackTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = farmerPhone,
                        onValueChange = { farmerPhone = it },
                        label = { Text("Phone Number", color = Color.Black) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                        colors = blackTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = fieldName,
                        onValueChange = { fieldName = it },
                        label = { Text("Field / Lot Name", color = Color.Black) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                        colors = blackTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = cropType,
                        onValueChange = { cropType = it },
                        label = { Text("Crops (e.g., Bananas, Wheat, Coffee)", color = Color.Black) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                        colors = blackTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Live GPS Resolved Link:\n$liveLocationAddress",
                            fontSize = 10.sp,
                            color = Color(0xFF386A20),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { requestGpsWithPermission() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Get Real GPS Coordinates",
                                tint = Color(0xFF386A20),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showCreateProfileDialog = false }) {
                            Text("Cancel", color = Color(0xFF72796F))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.createFarmerProfile(context, farmerName, farmerPhone, fieldName, cropType)
                                showCreateProfileDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20))
                        ) {
                            Text("Create & Focus")
                        }
                    }
                }
            }
        }
    }

    // 3. SECURE BROWSE AND MANAGE MOUNTED LOTS DIALOG
    if (showManageProfilesDialog) {
        Dialog(onDismissRequest = { showManageProfilesDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Registered Lots", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF191D17))
                        IconButton(onClick = { showCreateProfileDialog = true; showManageProfilesDialog = false }) {
                            Icon(Icons.Default.AddCircle, "Add", tint = Color(0xFF386A20))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFDCE5D8))
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.heightIn(max = 280.dp)) {
                        if (allFarmerProfiles.isEmpty()) {
                            Text("No farmer profiles added.", fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(allFarmerProfiles) { profile ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.selectFarmerProfile(profile)
                                                showManageProfilesDialog = false
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedFarmerProfile?.id == profile.id) Color(0xFFD7E8CD) else Color(0xFFF2F5F0)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Lot: ${profile.fieldName} (${profile.cropType})", fontSize = 11.sp, color = Color(0xFF42493F))
                                                Text(if (profile.hasTestData) "Status: Diagnostics OK" else "Status: Clean Record", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (profile.hasTestData) Color(0xFF386A20) else Color(0xFF72796F))
                                            }
                                            IconButton(onClick = { viewModel.deleteFarmerProfile(profile.id) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Delete, "Remove Lot", tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { showManageProfilesDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF72796F))) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }

    // --- ADMIN PIN AUTHORIZATION DIALOG ---
    if (showAdminPinDialog) {
        Dialog(onDismissRequest = { showAdminPinDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(16.dp),
                border = BorderStroke(1.dp, Color(0xFFDCE5D8))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFF2F5F0), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Secure",
                            tint = Color(0xFF386A20),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Admin Authorization",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = Color(0xFF191D17)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Enter administrative PIN code to manage databases and synchronization properties.",
                        fontSize = 11.sp,
                        color = Color(0xFF42493F),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    OutlinedTextField(
                        value = adminPinInput,
                        onValueChange = { input -> 
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                adminPinInput = input
                                if (adminPinError) adminPinError = false
                            }
                        },
                        label = { Text("4-Digit PIN", fontSize = 12.sp, color = Color.Black) },
                        placeholder = { Text("0000", fontSize = 12.sp, color = Color.Black) },
                        singleLine = true,
                        isError = adminPinError,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                        colors = blackTextFieldColors(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("admin_pin_input_field")
                    )
                    
                    if (adminPinError) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Access Denied: Invalid Administrative PIN Code",
                            color = Color(0xFFC62828),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { showAdminPinDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color(0xFF42493F), fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = {
                                if (adminPinInput == "0001" || adminPinInput == "1234") {
                                    showAdminPinDialog = false
                                    showAdminPanelDialog = true
                                    adminPinError = false
                                } else {
                                    adminPinError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_authenticate_submit"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Unlock", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // --- ADMIN PANEL AND DATABASE VISUALIZER DIALOG ---
    if (showAdminPanelDialog) {
        Dialog(onDismissRequest = { showAdminPanelDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFDCE5D8)),
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security, 
                                contentDescription = "Admin Area",
                                tint = Color(0xFF386A20),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Admin Portal & Databases",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color(0xFF191D17)
                            )
                        }
                        IconButton(onClick = { showAdminPanelDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Panel", tint = Color(0xFF72796F))
                        }
                    }
                    
                    HorizontalDivider(color = Color(0xFFDCE5D8), modifier = Modifier.padding(vertical = 8.dp))
                    
                    var adminSelectedTab by remember { mutableStateOf(0) }
                    
                    // Custom Tab Segment Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Lots DB", "Spot DB", "Sync Setup", "Calibrate").forEachIndexed { index, label ->
                            val isSelected = adminSelectedTab == index
                            val containerCol = if (isSelected) Color(0xFF386A20) else Color(0xFFF2F5F0)
                            val contentCol = if (isSelected) Color.White else Color(0xFF42493F)
                            val borderCol = if (isSelected) Color(0xFF386A20) else Color(0xFFDCE5D8)
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(containerCol)
                                    .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                                    .clickable { adminSelectedTab = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentCol,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Main Scrollable Area containing Tabs
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 420.dp)
                    ) {
                        // Local remember state values for synchronizing config inputs
                        var inputSpreadsheetId by remember { mutableStateOf("") }
                        var inputAccessToken by remember { mutableStateOf("") }
                        var inputSheetName by remember { mutableStateOf("") }
                        var inputAppsScriptUrl by remember { mutableStateOf("") }
                        var inputSyncMode by remember { mutableStateOf("") }

                        LaunchedEffect(showAdminPanelDialog) {
                            if (showAdminPanelDialog) {
                                inputSpreadsheetId = viewModel.sheetSpreadsheetId.value
                                inputAccessToken = viewModel.sheetAccessToken.value
                                inputSheetName = viewModel.sheetName.value
                                inputAppsScriptUrl = viewModel.sheetAppsScriptUrl.value
                                inputSyncMode = viewModel.sheetSyncMode.value
                            }
                        }

                        when (adminSelectedTab) {
                            0 -> {
                                // LOTS / FARMER PROFILES TABLE
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    var profileQuery by remember { mutableStateOf("") }
                                    var showProfilePurgeConfirm by remember { mutableStateOf(false) }
                                    
                                    // Search Input
                                    OutlinedTextField(
                                        value = profileQuery,
                                        onValueChange = { profileQuery = it },
                                        placeholder = { Text("Filter lots by name/crop...", fontSize = 11.sp, color = Color.Black) },
                                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(14.dp), tint = Color.Black) },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp),
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = blackTextFieldColors()
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Table: farmer_profiles (${allFarmerProfiles.size} items)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF72796F)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    val filteredProfiles = allFarmerProfiles.filter {
                                        it.name.contains(profileQuery, ignoreCase = true) ||
                                        it.fieldName.contains(profileQuery, ignoreCase = true) ||
                                        it.cropType.contains(profileQuery, ignoreCase = true)
                                    }
                                    
                                    Box(modifier = Modifier.weight(1f, fill = false).heightIn(max = 280.dp)) {
                                        if (filteredProfiles.isEmpty()) {
                                            Text(
                                                text = "No matching records found in farmer_profiles table.",
                                                fontSize = 11.sp,
                                                color = Color(0xFF72796F),
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        } else {
                                            LazyColumn(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(filteredProfiles) { profile ->
                                                    var isProfileExpanded by remember { mutableStateOf(false) }
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F5F0)),
                                                        shape = RoundedCornerShape(12.dp),
                                                        border = BorderStroke(1.dp, Color(0xFFDCE5D8)),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        text = profile.name,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 13.sp,
                                                                        color = Color(0xFF191D17)
                                                                    )
                                                                    Text(
                                                                        text = "Lot: ${profile.fieldName} | Crop: ${profile.cropType}",
                                                                        fontSize = 11.sp,
                                                                        color = Color(0xFF42493F)
                                                                    )
                                                                    Text(
                                                                        text = "Id: ${profile.id} • Phone: ${profile.phone}",
                                                                        fontSize = 10.sp,
                                                                        color = Color(0xFF72796F)
                                                                    )
                                                                }
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    IconButton(
                                                                        onClick = { isProfileExpanded = !isProfileExpanded },
                                                                        modifier = Modifier.size(28.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = if (isProfileExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                                            contentDescription = "Expand Data",
                                                                            tint = Color(0xFF386A20)
                                                                        )
                                                                    }
                                                                    IconButton(
                                                                        onClick = { viewModel.deleteFarmerProfile(profile.id) },
                                                                        modifier = Modifier.size(28.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Delete,
                                                                            contentDescription = "Delete from DB",
                                                                            tint = Color(0xFFC62828),
                                                                            modifier = Modifier.size(16.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            
                                                            if (isProfileExpanded) {
                                                                Spacer(modifier = Modifier.height(8.dp))
                                                                HorizontalDivider(color = Color(0xFFDCE5D8))
                                                                Spacer(modifier = Modifier.height(8.dp))
                                                                
                                                                Text("Database Entry JSON Telemetry:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF386A20))
                                                                
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .background(Color(0xFFE8EDE4), RoundedCornerShape(8.dp))
                                                                        .padding(8.dp)
                                                                ) {
                                                                    Column {
                                                                        Text("• GPS Location: ${profile.location}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                                        Text("• Created: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(profile.timestamp)), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                                        Text("• Test Status: " + if(profile.hasTestData) "DIAGNOSTICS RECORDED" else "NO RECENT SENSOR DATA", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                                        if (profile.hasTestData) {
                                                                            Text("[Ground Truth Readings]", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD84315), modifier = Modifier.padding(top = 4.dp))
                                                                            Text(" - Nitrogen (N): ${profile.soilN} mg/kg", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                            Text(" - Phosphorus (P): ${profile.soilP} mg/kg", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                            Text(" - Potassium (K): ${profile.soilK} mg/kg", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                            Text(" - pH value: ${profile.soilPh}", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                            Text(" - Humidity: ${profile.soilHumidity}%", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                            Text(" - Temperature: ${profile.soilTemp}°C", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                            Text(" - EC Conductivity: ${profile.soilEc} uS/cm", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                            Text(" - Local Ground Insights: ${profile.groundTruthInsights}", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                            
                                                                            Text("[Sky Truth Readings]", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1), modifier = Modifier.padding(top = 4.dp))
                                                                            Text(" - NOAA Weather State: ${profile.localWeather}", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                            Text(" - Satellite Earth Orbit: ${profile.alphaEarthData}", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                            Text(" - Sky Analytics Insights: ${profile.skyTruthInsights}", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = Color(0xFFDCE5D8))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Table Purge Operations
                                    if (showProfilePurgeConfirm) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFFEBEE), RoundedCornerShape(10.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = "⚠️ EXTREME ACTION: Truncate All Lots. This action will purge all registered lot histories. Proceed?",
                                                color = Color(0xFFC62828),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(
                                                    onClick = { 
                                                        viewModel.clearAllFarmerProfiles()
                                                        showProfilePurgeConfirm = false
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                                    modifier = Modifier.weight(1f).height(30.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("Yes, Truncate Profiles", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedButton(
                                                    onClick = { showProfilePurgeConfirm = false },
                                                    modifier = Modifier.weight(1f).height(30.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("Abort", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { showProfilePurgeConfirm = true },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                                            border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp), tint = Color(0xFFC62828))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Truncate Table: farmer_profiles", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // SPOT LOG RECORDS DATABASE
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    var showRecordsPurgeConfirm by remember { mutableStateOf(false) }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Table: soil_records (${records.size} logs stored)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF72796F)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Box(modifier = Modifier.weight(1f, fill = false).heightIn(max = 280.dp)) {
                                        if (records.isEmpty()) {
                                            Text(
                                                text = "No log records found in soil_records table.",
                                                fontSize = 11.sp,
                                                color = Color(0xFF72796F),
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        } else {
                                            LazyColumn(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(records) { record ->
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F5F0)),
                                                        shape = RoundedCornerShape(10.dp),
                                                        border = BorderStroke(1.dp, Color(0xFFDCE5D8)),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(10.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text("Spot Log ID #${record.id} • Label: ${record.locationLabel}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                                                                    Text("N: ${record.nitrogen} P: ${record.phosphorus} K: ${record.potassium}", fontSize = 10.sp, color = Color(0xFFD84315), fontWeight = FontWeight.Bold)
                                                                    Text("pH: ${record.pH}", fontSize = 10.sp, color = Color(0xFF00695C), fontWeight = FontWeight.Bold)
                                                                    Text("EC: ${record.electricalConductivity} uS", fontSize = 10.sp, color = Color(0xFF283593), fontWeight = FontWeight.Bold)
                                                                }
                                                                Text(
                                                                    text = "Humidity: ${record.humidityPercent}%  Temp: ${record.temperatureCelsius}°C  Salinity: ${record.salinityTds} ppm\nLogged: " + try {
                                                                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(record.timestamp))
                                                                    } catch(ex: Throwable) { "-" },
                                                                    fontSize = 10.sp,
                                                                    color = Color(0xFF42493F)
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = { viewModel.deleteRecord(record.id) },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = Color(0xFFDCE5D8))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Purge Records Table
                                    if (showRecordsPurgeConfirm) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFFEBEE), RoundedCornerShape(10.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = "⚠️ EXTREME ACTION: Truncate All Soil Records. Proceed with database purge?",
                                                color = Color(0xFFC62828),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(
                                                    onClick = { 
                                                        viewModel.clearAllHistory()
                                                        showRecordsPurgeConfirm = false
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                                    modifier = Modifier.weight(1f).height(30.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("Yes, Truncate", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedButton(
                                                    onClick = { showRecordsPurgeConfirm = false },
                                                    modifier = Modifier.weight(1f).height(30.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("Abort", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { showRecordsPurgeConfirm = true },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                                            border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp), tint = Color(0xFFC62828))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Truncate Table: soil_records", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            2 -> {
                                // GOOGLE SHEETS SYNC TUNING & ADMIN DUTIES
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = "Remote API Sync Settings",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF191D17)
                                    )
                                    Text(
                                        text = "Push diagnostic telemetry to Google sheets tables.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF72796F)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    OutlinedTextField(
                                        value = inputSpreadsheetId,
                                        onValueChange = { inputSpreadsheetId = it },
                                        label = { Text("Google Spreadsheet ID", fontSize = 10.sp, color = Color.Black) },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = blackTextFieldColors()
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        color = Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFA5D6A7))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Master Sheet Direct Link", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                                Text(
                                                    text = "Auto-Export: ACTIVE (Lots & Spots)",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Button(
                                                        onClick = {
                                                            if (inputAccessToken.isBlank()) {
                                                                Toast.makeText(context, "Enter OAuth Access Token below or use sheets.new!", Toast.LENGTH_LONG).show()
                                                            } else {
                                                                viewModel.createMasterSheetViaApi(inputAccessToken) { success, idOrError, sheetUrl ->
                                                                    if (success) {
                                                                        inputSpreadsheetId = idOrError
                                                                        Toast.makeText(context, "New Master Sheet Created! ID: $idOrError", Toast.LENGTH_LONG).show()
                                                                    } else {
                                                                        Toast.makeText(context, "Creation Failed: $idOrError", Toast.LENGTH_LONG).show()
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.height(26.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(10.dp))
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text("New Sheet API", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://sheets.new"))
                                                            try {
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Open https://sheets.new in browser", Toast.LENGTH_LONG).show()
                                                            }
                                                        },
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1B5E20)),
                                                        border = BorderStroke(1.dp, Color(0xFF2E7D32)),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.height(26.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(10.dp))
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text("sheets.new", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Button(
                                                onClick = {
                                                    val sheetId = inputSpreadsheetId.ifBlank { SoilViewModel.DEFAULT_MASTER_SHEET_ID }
                                                    val url = "https://docs.google.com/spreadsheets/d/$sheetId/edit"
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Master Sheet URL: $url", Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Launch, null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Open Sheet", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    OutlinedTextField(
                                        value = inputAccessToken,
                                        onValueChange = { inputAccessToken = it },
                                        label = { Text("OAuth Access Token", fontSize = 10.sp, color = Color.Black) },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = blackTextFieldColors()
                                    )
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    OutlinedTextField(
                                        value = inputSheetName,
                                        onValueChange = { inputSheetName = it },
                                        label = { Text("Target Worksheet Name", fontSize = 10.sp, color = Color.Black) },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = blackTextFieldColors()
                                    )
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    OutlinedTextField(
                                        value = inputAppsScriptUrl,
                                        onValueChange = { inputAppsScriptUrl = it },
                                        label = { Text("Apps Script Webhook URL", fontSize = 10.sp, color = Color.Black) },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = blackTextFieldColors()
                                    )
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    // Sync Mode Selector Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Sync Mode:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF42493F))
                                        
                                        listOf("script", "api").forEach { mode ->
                                            val isModeSelected = inputSyncMode == mode
                                            FilterChip(
                                                selected = isModeSelected,
                                                onClick = { inputSyncMode = mode },
                                                label = { Text(if (mode == "script") "Apps Script" else "Direct API", fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFF386A20),
                                                    selectedLabelColor = Color.White
                                                )
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.updateSheetsConfig(
                                                    spreadsheetId = inputSpreadsheetId,
                                                    accessToken = inputAccessToken,
                                                    sheetNameVal = inputSheetName,
                                                    appsScriptUrl = inputAppsScriptUrl,
                                                    syncMode = inputSyncMode
                                                )
                                                Toast.makeText(context, "Google Sync Parameters Stored Successfully!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1.2f).height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("Save Configuration", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val configText = "Intelligent Soil Central Config:\nSheet ID: $inputSpreadsheetId\nWebhook URL: $inputAppsScriptUrl"
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Intelligent Soil Master Sheet Config")
                                                    putExtra(android.content.Intent.EXTRA_TEXT, configText)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Master Sheet Config with Field Devices"))
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF386A20)),
                                            border = BorderStroke(1.dp, Color(0xFF386A20)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Share Config", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val defaultUrl = SoilViewModel.DEFAULT_WEBHOOK_URL
                                                val defaultId = SoilViewModel.DEFAULT_MASTER_SHEET_ID
                                                inputAppsScriptUrl = defaultUrl
                                                inputSpreadsheetId = defaultId
                                                inputSyncMode = "script"
                                                inputSheetName = "Soil Diagnostics"
                                                viewModel.updateSheetsConfig(defaultId, inputAccessToken, "Soil Diagnostics", defaultUrl, "script")
                                                Toast.makeText(context, "Reset to Central Organization Master Sheet!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF72796F)),
                                            border = BorderStroke(1.dp, Color(0xFFBFC9C0)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(0.9f).height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(11.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Reset Org", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = Color(0xFFDCE5D8))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Text("Administrative Run Commands", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191D17))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.syncAllProfilesToGoogleSheets() },
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20)),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Export Lots", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        
                                        Button(
                                            onClick = { viewModel.syncAllSoilRecordsToGoogleSheets() },
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20)),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Sync, null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Export Spots", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    val syncLogs by viewModel.sheetLogLines.collectAsStateWithLifecycle()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Active Sync Logging Stream", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF42493F))
                                        TextButton(onClick = { viewModel.clearSheetLogs() }, modifier = Modifier.height(24.dp)) {
                                            Text("Clear Log Stream", fontSize = 10.sp, color = Color(0xFFC62828))
                                        }
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .background(Color(0xFF1E231C), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFF42493F), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        if (syncLogs.isEmpty()) {
                                            Text(
                                                text = "[SYSTEM OK] System idle. Logs of remote exports will print here...",
                                                color = Color(0xFF81C784),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(2.dp)
                                            )
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(syncLogs) { logLine ->
                                                    Text(
                                                        text = logLine,
                                                        color = if (logLine.contains("failed", ignoreCase = true) || logLine.contains("Error", ignoreCase = true)) Color(0xFFE57373) else Color(0xFFAED581),
                                                        fontSize = 9.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            3 -> {
                                // AUTO-CALIBRATION AND SENSOR ADJUSTMENT
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = "Active Auto-Calibration Setup",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF191D17)
                                    )
                                    Text(
                                        text = "Plugging probe in a soil of known readings? Enter references below to calibrate internal Modbus scaling offset/multipliers.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF42493F)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // 1. Current Offsets/Multipliers Info Card
                                    val hOffset by viewModel.humidityOffset.collectAsStateWithLifecycle()
                                    val tOffset by viewModel.tempOffset.collectAsStateWithLifecycle()
                                    val ecMult by viewModel.ecMultiplier.collectAsStateWithLifecycle()
                                    val phOff by viewModel.phOffset.collectAsStateWithLifecycle()
                                    val nMult by viewModel.nitrogenMultiplier.collectAsStateWithLifecycle()
                                    val pMult by viewModel.phosphorusMultiplier.collectAsStateWithLifecycle()
                                    val kMult by viewModel.potassiumMultiplier.collectAsStateWithLifecycle()
                                    val salMult by viewModel.salinityMultiplier.collectAsStateWithLifecycle()
                                    val rawSample by viewModel.rawReading.collectAsStateWithLifecycle()
                                    val currentReading by viewModel.currentReading.collectAsStateWithLifecycle()

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F5F0)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Sensor Tuning Matrix", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF386A20))
                                                Text(
                                                    text = if (rawSample != null) "Physical telemetry stream online" else "Simulator telemetry active",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF5D4037)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))

                                            val factors = listOf(
                                                "Moisture" to "Offset: ${String.format(Locale.US, "%+.1f", hOffset)}%",
                                                "Temperature" to "Offset: ${String.format(Locale.US, "%+.1f", tOffset)}°C",
                                                "Soil pH" to "Offset: ${String.format(Locale.US, "%+.1f", phOff)}",
                                                "Elec Cond" to "Multiplier: ${String.format(Locale.US, "%.2f", ecMult)}x",
                                                "Nitrogen" to "Multiplier: ${String.format(Locale.US, "%.2f", nMult)}x",
                                                "Phosphorus" to "Multiplier: ${String.format(Locale.US, "%.2f", pMult)}x",
                                                "Potassium" to "Multiplier: ${String.format(Locale.US, "%.2f", kMult)}x",
                                                "TDS Salinity" to "Multiplier: ${String.format(Locale.US, "%.2f", salMult)}x"
                                            )

                                            factors.chunked(2).forEach { pair ->
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    pair.forEach { (label, value) ->
                                                        Row(
                                                            modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(label, fontSize = 10.sp, color = Color(0xFF42493F))
                                                            Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191D17))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Current Live vs Raw comparison values
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEBF3E7)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Live Calibrated vs Raw Reading", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF386A20))
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("CALIBRATED (DISPLAYED)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF386A20))
                                                    Text("pH: ${currentReading.pH}", fontSize = 11.sp, color = Color(0xFF191D17))
                                                    Text("Moist: ${currentReading.humidityPercent}%", fontSize = 11.sp, color = Color(0xFF191D17))
                                                    Text("EC: ${currentReading.electricalConductivity} us/cm", fontSize = 11.sp, color = Color(0xFF191D17))
                                                    Text("N-P-K: ${currentReading.nitrogen}-${currentReading.phosphorus}-${currentReading.potassium}", fontSize = 11.sp, color = Color(0xFF191D17))
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    val raw = rawSample ?: currentReading
                                                    Text("RAW (OFF-THE-WIRE)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                                    Text("pH: ${raw.pH}", fontSize = 11.sp, color = Color(0xFF191D17))
                                                    Text("Moist: ${raw.humidityPercent}%", fontSize = 11.sp, color = Color(0xFF191D17))
                                                    Text("EC: ${raw.electricalConductivity} us/cm", fontSize = 11.sp, color = Color(0xFF191D17))
                                                    Text("N-P-K: ${raw.nitrogen}-${raw.phosphorus}-${raw.potassium}", fontSize = 11.sp, color = Color(0xFF191D17))
                                                }
                                            }
                                        }
                                    }

                                    // Calibration Input Form
                                    Text("Reference Known Sample Values:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF42493F))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    var refMoisture by remember { mutableStateOf("") }
                                    var refTemp by remember { mutableStateOf("") }
                                    var refPh by remember { mutableStateOf("") }
                                    var refEc by remember { mutableStateOf("") }
                                    var refN by remember { mutableStateOf("") }
                                    var refP by remember { mutableStateOf("") }
                                    var refK by remember { mutableStateOf("") }
                                    var refSalinity by remember { mutableStateOf("") }

                                    // Grid of text fields
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = refMoisture,
                                                onValueChange = { refMoisture = it },
                                                label = { Text("Known Moisture (%)", fontSize = 10.sp, color = Color.Black) },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp),
                                                colors = blackTextFieldColors(),
                                                modifier = Modifier.weight(1f).height(50.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            OutlinedTextField(
                                                value = refTemp,
                                                onValueChange = { refTemp = it },
                                                label = { Text("Known Temp (°C)", fontSize = 10.sp, color = Color.Black) },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp),
                                                colors = blackTextFieldColors(),
                                                modifier = Modifier.weight(1f).height(50.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = refPh,
                                                onValueChange = { refPh = it },
                                                label = { Text("Known pH", fontSize = 10.sp, color = Color.Black) },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp),
                                                colors = blackTextFieldColors(),
                                                modifier = Modifier.weight(1f).height(50.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            OutlinedTextField(
                                                value = refEc,
                                                onValueChange = { refEc = it },
                                                label = { Text("Known EC (us/cm)", fontSize = 10.sp, color = Color.Black) },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp),
                                                colors = blackTextFieldColors(),
                                                modifier = Modifier.weight(1f).height(50.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = refN,
                                                onValueChange = { refN = it },
                                                label = { Text("Known N (mg/kg)", fontSize = 10.sp, color = Color.Black) },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp),
                                                colors = blackTextFieldColors(),
                                                modifier = Modifier.weight(1f).height(50.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            OutlinedTextField(
                                                value = refP,
                                                onValueChange = { refP = it },
                                                label = { Text("Known P (mg/kg)", fontSize = 10.sp, color = Color.Black) },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp),
                                                colors = blackTextFieldColors(),
                                                modifier = Modifier.weight(1f).height(50.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = refK,
                                                onValueChange = { refK = it },
                                                label = { Text("Known K (mg/kg)", fontSize = 10.sp, color = Color.Black) },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp),
                                                colors = blackTextFieldColors(),
                                                modifier = Modifier.weight(1f).height(50.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            OutlinedTextField(
                                                value = refSalinity,
                                                onValueChange = { refSalinity = it },
                                                label = { Text("Known Sal (mg/L)", fontSize = 10.sp, color = Color.Black) },
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 11.sp),
                                                colors = blackTextFieldColors(),
                                                modifier = Modifier.weight(1f).height(50.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.runAutoCalibration(
                                                    knownHumidity = refMoisture.toDoubleOrNull(),
                                                    knownTemp = refTemp.toDoubleOrNull(),
                                                    knownEc = refEc.toIntOrNull(),
                                                    knownPh = refPh.toDoubleOrNull(),
                                                    knownN = refN.toIntOrNull(),
                                                    knownP = refP.toIntOrNull(),
                                                    knownK = refK.toIntOrNull(),
                                                    knownSalinity = refSalinity.toIntOrNull()
                                                )
                                                refMoisture = ""
                                                refTemp = ""
                                                refPh = ""
                                                refEc = ""
                                                refN = ""
                                                refP = ""
                                                refK = ""
                                                refSalinity = ""
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            enabled = refMoisture.isNotBlank() || refTemp.isNotBlank() || refPh.isNotBlank() || refEc.isNotBlank() || refN.isNotBlank() || refP.isNotBlank() || refK.isNotBlank() || refSalinity.isNotBlank(),
                                            modifier = Modifier.weight(1f).testTag("autocalibrate_submit_button")
                                        ) {
                                            Text("Calibrate to Sample", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.resetCalibration()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).testTag("reset_calibration_button"),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                                        ) {
                                            Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showAdminPanelDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF72796F))
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 4. TALK TO YOUR SOIL DIALOG (GORGEOUS ASYNC CHAT SURFACE)
    if (showChatDialog && selectedFarmerProfile != null) {
        val activeProfile = selectedFarmerProfile!!

        val context = LocalContext.current
        var isSttListening by remember { mutableStateOf(false) }
        var voiceStatusMsg by remember { mutableStateOf("Tap status bar to speak...") }

        val speechRecognizer = remember {
            try {
                SpeechRecognizer.createSpeechRecognizer(context)
            } catch (e: Exception) {
                null
            }
        }

        val startListeningSpeech: () -> Unit = {
            try {
                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isSttListening = true
                        voiceStatusMsg = "Soil listening... speak now"
                    }
                    override fun onBeginningOfSpeech() {
                        voiceStatusMsg = "I am listening..."
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isSttListening = false
                        voiceStatusMsg = "Transcribing..."
                    }
                    override fun onError(error: Int) {
                        isSttListening = false
                        val errMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording failure."
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap to talk."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone is busy."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. Tap to talk."
                            else -> "Tap status bar to speak..."
                        }
                        voiceStatusMsg = errMsg
                    }
                    override fun onResults(results: Bundle?) {
                        isSttListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val transcript = matches[0]
                            if (transcript.isNotBlank()) {
                                voiceStatusMsg = "Sent: $transcript"
                                viewModel.askSoil(transcript)
                            }
                        } else {
                            voiceStatusMsg = "Tap status bar to speak..."
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            voiceStatusMsg = "Matched: ${matches[0]}"
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(recognizerIntent)
                isSttListening = true
                voiceStatusMsg = "Microphone active..."
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to start voice: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        val audioPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startListeningSpeech()
            } else {
                Toast.makeText(context, "Microphone permission is required for voice query.", Toast.LENGTH_SHORT).show()
            }
        }

        val triggerSpeechToText = {
            val audioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            if (audioPermission == PackageManager.PERMISSION_GRANTED) {
                startListeningSpeech()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        val stopListeningSpeech = {
            try {
                speechRecognizer?.stopListening()
                isSttListening = false
                voiceStatusMsg = "Tap status bar to speak..."
            } catch (e: Exception) {}
        }

        DisposableEffect(showChatDialog) {
            onDispose {
                try {
                    speechRecognizer?.stopListening()
                    speechRecognizer?.destroy()
                } catch (e: Exception) {}
            }
        }

        // Mock Base64 images for instant crop and soil diagnostics inside sandbox emulators
        val MOCK_SOIL_MOLD_BASE64 = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="
        val MOCK_SICK_LEAF_BASE64 = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA="

        Dialog(onDismissRequest = { showChatDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Chat, "Chat", tint = Color(0xFFBA68C8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Converse with ${activeProfile.fieldName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        IconButton(onClick = { showChatDialog = false }) {
                            Icon(Icons.Default.Close, "Dismiss")
                        }
                    }
                    Text("Chat context dynamically populated from ground truth & Google alpha overhead insights.", fontSize = 10.sp, color = Color(0xFF72796F))
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    // Live Speech wave/status bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (isSttListening) {
                                    stopListeningSpeech()
                                } else {
                                    triggerSpeechToText()
                                }
                            }
                            .background(
                                when {
                                    chatIsLoading -> Color(0xFFFFF9C4) // Loading/Decoding (Yellow)
                                    isSoilSpeaking -> Color(0xFFFFCC80) // Soil speaking (Orange)
                                    isSttListening -> Color(0xFFE8F5E9) // Listening (Green)
                                    else -> Color(0xFFF1F3F0)          // Idle (Neutral Slate)
                                }
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                chatIsLoading -> Icons.Default.Sync
                                isSoilSpeaking -> Icons.AutoMirrored.Filled.VolumeUp
                                isSttListening -> Icons.Default.Mic
                                else -> Icons.Default.MicNone
                            },
                            contentDescription = "Soil Speech Status",
                            tint = when {
                                chatIsLoading -> Color(0xFFF57F17)
                                isSoilSpeaking -> Color(0xFFE65100)
                                isSttListening -> Color(0xFF2E7D32)
                                else -> Color(0xFF72796F)
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                chatIsLoading -> "Soil is thinking... analyzing crop layers"
                                isSoilSpeaking -> "Soil is talking back... listen carefully"
                                isSttListening -> voiceStatusMsg
                                else -> "Voice Live: Tap status bar to speak to soil!"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                chatIsLoading -> Color(0xFF5D4037)
                                isSoilSpeaking -> Color(0xFFE65100)
                                isSttListening -> Color(0xFF2E7D32)
                                else -> Color(0xFF191D17)
                            }
                        )

                        if (isSoilSpeaking || isSttListening) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { index ->
                                    AudioVisualizerBar(index = index, isSpeaking = isSoilSpeaking || isSttListening)
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    // Messages LazyColumn
                    Box(modifier = Modifier.weight(1f).heightIn(min = 200.dp, max = 320.dp)) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            if (chatHistory.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Soil is listening. Ask: 'How are my nitrogen levels?' or 'Is my water supply adequate?'",
                                        textAlign = TextAlign.Center,
                                        fontSize = 11.sp,
                                        color = Color(0xFF72796F),
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }
                            } else {
                                chatHistory.forEach { turn ->
                                    val isUser = turn.second
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(
                                                    RoundedCornerShape(
                                                        topStart = 12.dp,
                                                        topEnd = 12.dp,
                                                        bottomStart = if (isUser) 12.dp else 0.dp,
                                                        bottomEnd = if (isUser) 0.dp else 12.dp
                                                    )
                                                )
                                                .background(if (isUser) Color(0xFFEBF3E7) else Color(0xFFF2F5F0))
                                                .padding(10.dp)
                                                .widthIn(max = 240.dp)
                                        ) {
                                            Text(
                                                text = turn.first,
                                                fontSize = 11.sp,
                                                color = Color(0xFF191D17)
                                            )
                                        }
                                    }
                                }
                            }

                            if (chatIsLoading) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFFFEB3B))
                                            .padding(8.dp)
                                    ) {
                                        Text("Decoding biosignals...", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191D17))
                                    }
                                }
                            }
                        }
                        LaunchedEffect(chatHistory.size, chatIsLoading) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }

                    // Media Previews (Image & Video clips)
                    if (attachedImageBase64 != null || attachedVideoBase64 != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (attachedImageBase64 != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .border(1.dp, Color(0xFF81C784), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Photo, "Photo Attached", tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Photo Clip", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove Photo",
                                            tint = Color(0xFFC62828),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.attachImageBase64(null) }
                                        )
                                    }
                                }
                            }
                            if (attachedVideoBase64 != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFEDE7F6))
                                        .border(1.dp, Color(0xFFB39DDB), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Videocam, "Video Attached", tint = Color(0xFF5E35B1), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Video Clip", fontSize = 10.sp, color = Color(0xFF5E35B1), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove Video",
                                            tint = Color(0xFFC62828),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.attachVideoBase64(null) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Media Chooser Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2F5F0), contentColor = Color(0xFF386A20)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Photo, "Upload Crop File", modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Crop Photo", fontSize = 9.sp)
                        }

                        Button(
                            onClick = { videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2F5F0), contentColor = Color(0xFF386A20)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Videocam, "Upload Crop Video", modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Leaf Video", fontSize = 9.sp)
                        }

                        Button(
                            onClick = { viewModel.attachImageBase64(MOCK_SOIL_MOLD_BASE64) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Grass, "Attach Mock Specimen", modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("🌱 Probe Scan", fontSize = 9.sp)
                        }

                        Button(
                            onClick = { viewModel.attachImageBase64(MOCK_SICK_LEAF_BASE64) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEDE7F6), contentColor = Color(0xFF5E35B1)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Eco, "Attach Sick Leaf Specimen", modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("🍂 Leaf Spot", fontSize = 9.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Input Row
                    var chatInput by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Ask your Soil...", fontSize = 11.sp, color = Color.Black) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 12.sp),
                            colors = blackTextFieldColors(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (isSttListening) {
                                        stopListeningSpeech()
                                    } else {
                                        triggerSpeechToText()
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (isSttListening) Icons.Default.Mic else Icons.Default.MicNone,
                                        contentDescription = "Trigger microphone input",
                                        tint = if (isSttListening) Color(0xFF2E7D32) else Color(0xFF72796F)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (chatInput.isNotBlank()) {
                                    viewModel.askSoil(chatInput)
                                    chatInput = ""
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFBA68C8), contentColor = Color.White),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Command")
                        }
                    }
                }
            }
        }
    }

    // 5. GOOGLE ACCOUNT SIGN-IN DIALOG
    if (showSignInDialog) {
        var inputName by remember { mutableStateOf(currentAgent?.name ?: "Intelligent Soil Admin") }
        var inputEmail by remember { mutableStateOf(currentAgent?.email ?: "intelligentsoil.ai@gmail.com") }

        Dialog(onDismissRequest = { showSignInDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFD2E3FC)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F0FE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Google Account",
                                    tint = Color(0xFF1A73E8),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Google Account",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF202124)
                                )
                                Text(
                                    text = "OAuth2 & Cloud Database Sync",
                                    fontSize = 11.sp,
                                    color = Color(0xFF5F6368)
                                )
                            }
                        }
                        IconButton(onClick = { showSignInDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF5F6368))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentAgent != null) {
                        // CURRENTLY LOGGED IN STATE
                        val agent = currentAgent!!
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE8EAED)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1A73E8)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = agent.name.take(1).uppercase(),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = agent.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF202124)
                                        )
                                        Text(
                                            text = agent.email,
                                            fontSize = 12.sp,
                                            color = Color(0xFF5F6368)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFE6F4EA))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Google Authenticated",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF137333)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = Color(0xFFE8EAED))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Google UID:", fontSize = 10.sp, color = Color(0xFF5F6368))
                                    Text(agent.uid, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Auth Token:", fontSize = 10.sp, color = Color(0xFF5F6368))
                                    Text("Valid (OAuth 2.0)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF137333))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Firestore Cloud:", fontSize = 10.sp, color = Color(0xFF5F6368))
                                    Text(firestoreSyncStatus, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A73E8))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Recent Firebase Cloud Sync Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF202124))
                                .padding(10.dp)
                        ) {
                            if (firebaseSyncLogs.isEmpty()) {
                                Text("No sync actions recorded yet.", fontSize = 10.sp, color = Color(0xFFBDC1C6))
                            } else {
                                LazyColumn {
                                    items(firebaseSyncLogs) { log ->
                                        Text(log, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8AB4F8))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.logoutFieldAgent(context)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD93025)),
                                border = BorderStroke(1.dp, Color(0xFFFCE8E6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showSignInDialog = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                    } else {
                        // LOGGED OUT STATE - SIGN IN FORM & QUICK SELECT
                        Text(
                            text = "Sign in with your Google account to enable multi-user cloud backup and access shared test reports across devices.",
                            fontSize = 11.sp,
                            color = Color(0xFF5F6368),
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Quick Select Account:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick Account Option 1
                        Card(
                            onClick = {
                                inputName = "Intelligent Soil Admin"
                                inputEmail = "intelligentsoil.ai@gmail.com"
                            },
                            colors = CardDefaults.cardColors(containerColor = if (inputEmail == "intelligentsoil.ai@gmail.com") Color(0xFFE8F0FE) else Color(0xFFF8F9FA)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (inputEmail == "intelligentsoil.ai@gmail.com") Color(0xFF1A73E8) else Color(0xFFE8EAED)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF1A73E8), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("intelligentsoil.ai@gmail.com", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
                                    Text("Intelligent Soil Admin / Owner", fontSize = 10.sp, color = Color(0xFF5F6368))
                                }
                                if (inputEmail == "intelligentsoil.ai@gmail.com") {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color(0xFF1A73E8), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Account Option 2
                        Card(
                            onClick = {
                                inputName = "Field Specialist"
                                inputEmail = "agronomy.lead@intelligentsoil.ai"
                            },
                            colors = CardDefaults.cardColors(containerColor = if (inputEmail == "agronomy.lead@intelligentsoil.ai") Color(0xFFE8F0FE) else Color(0xFFF8F9FA)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (inputEmail == "agronomy.lead@intelligentsoil.ai") Color(0xFF1A73E8) else Color(0xFFE8EAED)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Engineering, contentDescription = null, tint = Color(0xFF137333), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("agronomy.lead@intelligentsoil.ai", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
                                    Text("Field Specialist Lead", fontSize = 10.sp, color = Color(0xFF5F6368))
                                }
                                if (inputEmail == "agronomy.lead@intelligentsoil.ai") {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color(0xFF1A73E8), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Or enter Google Account details:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("Display Name", fontSize = 11.sp, color = Color.Black) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = blackTextFieldColors()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = inputEmail,
                            onValueChange = { inputEmail = it },
                            label = { Text("Google Account Email", fontSize = 11.sp, color = Color.Black) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = blackTextFieldColors()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.loginFieldAgent(context, inputName, inputEmail) {
                                    showSignInDialog = false
                                    Toast.makeText(context, "Google Sign-In Successful: Welcome $inputName!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("google_signin_submit"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A73E8),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            enabled = inputEmail.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Sign In", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In with Google", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7 Compiles Composable values display
 */
@Composable
fun SoilSensorItem(
    label: String,
    valueStr: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191D17)
            )
        }
        Text(
            text = if (valueStr.contains("null") || valueStr.startsWith("0")) "--" else valueStr,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF386A20),
            fontFamily = FontFamily.SansSerif
        )
    }
}

/**
 * Historical Data Component Card
 */
@Composable
fun HistoricalRecordItem(
    record: SoilRecord,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFDCE5D8))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = record.locationLabel,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF386A20),
                        fontSize = 14.sp
                    )
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp)),
                        fontSize = 10.sp,
                        color = Color(0xFF72796F)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove single log entry",
                        tint = Color(0xFFC62828),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Short summary values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("NPK: ${record.nitrogen}-${record.phosphorus}-${record.potassium}", fontSize = 11.sp, color = Color(0xFF191D17), fontWeight = FontWeight.SemiBold)
                Text("pH: ${record.pH}", fontSize = 11.sp, color = Color(0xFF191D17), fontWeight = FontWeight.SemiBold)
                Text("Temp: ${record.temperatureCelsius}°C", fontSize = 11.sp, color = Color(0xFF191D17), fontWeight = FontWeight.SemiBold)
                Text("Moist: ${record.humidityPercent}%", fontSize = 11.sp, color = Color(0xFF191D17), fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text("EC: ${record.electricalConductivity} us/cm", fontSize = 11.sp, color = Color(0xFF42493F))
                Spacer(modifier = Modifier.width(16.dp))
                Text("TDS: ${record.salinityTds} mg/L", fontSize = 11.sp, color = Color(0xFF42493F))
            }
        }
    }
}

@Composable
fun AudioVisualizerBar(index: Int, isSpeaking: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_bar_anim")
    val heightDp by if (isSpeaking) {
        infiniteTransition.animateValue(
            initialValue = 4.dp,
            targetValue = (12 + index * 4).dp,
            typeConverter = Dp.VectorConverter,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 250 + (index * 90),
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_height_$index"
        )
    } else {
        remember { mutableStateOf(4.dp) }
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 1.5.dp)
            .width(2.5.dp)
            .height(heightDp)
            .background(Color(0xFFE65100))
    )
}

data class SensorMetric(
    val label: String,
    val value: String,
    val target: String,
    val accentColor: Color,
    val containerColor: Color,
    val borderColor: Color,
    val icon: ImageVector
)

@Composable
fun blackTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    disabledTextColor = Color.Black,
    errorTextColor = Color.Black,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White,
    errorContainerColor = Color.White,
    cursorColor = Color.Black,
    errorCursorColor = Color.Black,
    focusedBorderColor = Color.Black,
    unfocusedBorderColor = Color.Black,
    disabledBorderColor = Color.Black,
    errorBorderColor = Color.Black,
    focusedLabelColor = Color.Black,
    unfocusedLabelColor = Color.Black,
    disabledLabelColor = Color.Black,
    errorLabelColor = Color.Black,
    focusedPlaceholderColor = Color.Black,
    unfocusedPlaceholderColor = Color.Black,
    disabledPlaceholderColor = Color.Black,
    errorPlaceholderColor = Color.Black,
    focusedLeadingIconColor = Color.Black,
    unfocusedLeadingIconColor = Color.Black,
    focusedTrailingIconColor = Color.Black,
    unfocusedTrailingIconColor = Color.Black
)

