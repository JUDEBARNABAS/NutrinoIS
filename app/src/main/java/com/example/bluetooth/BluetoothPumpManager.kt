package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class BluetoothConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class PairedBluetoothDevice(
    val name: String,
    val address: String,
    val isLikelyHc05: Boolean
)

data class PumpActiveState(
    val nActive: Boolean = false,
    val pActive: Boolean = false,
    val kActive: Boolean = false
) {
    val anyActive: Boolean get() = nActive || pActive || kActive
}

class BluetoothPumpManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothPumpManager"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var readerJob: Job? = null
    private var nTimerJob: Job? = null
    private var pTimerJob: Job? = null
    private var kTimerJob: Job? = null

    private val _connectionState = MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow("Not Connected")
    val connectedDeviceName = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress = _connectedDeviceAddress.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedBluetoothDevice>>(emptyList())
    val pairedDevices = _pairedDevices.asStateFlow()

    private val _pumpActiveState = MutableStateFlow(PumpActiveState())
    val pumpActiveState = _pumpActiveState.asStateFlow()

    private val _bluetoothLogs = MutableStateFlow<List<String>>(emptyList())
    val bluetoothLogs = _bluetoothLogs.asStateFlow()

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices(): List<PairedBluetoothDevice> {
        if (!hasBluetoothPermission()) {
            addLog("Permission required: Grant Bluetooth permission to list paired HC-05 modules.")
            _pairedDevices.value = emptyList()
            return emptyList()
        }
        val adapter = bluetoothAdapter ?: run {
            addLog("Bluetooth adapter unavailable on this device.")
            _pairedDevices.value = emptyList()
            return emptyList()
        }
        val devices = try {
            adapter.bondedDevices.orEmpty().map { device ->
                val safeName = device.name?.takeIf { it.isNotBlank() } ?: "Unknown BT Device"
                val upper = safeName.uppercase(Locale.US)
                val isHc05 = upper.contains("HC-05") ||
                    upper.contains("HC05") ||
                    upper.contains("HC-06") ||
                    upper.contains("SOIL") ||
                    upper.contains("PUMP") ||
                    upper.contains("SPP")
                PairedBluetoothDevice(
                    name = safeName,
                    address = device.address,
                    isLikelyHc05 = isHc05
                )
            }.sortedWith(
                compareByDescending<PairedBluetoothDevice> { it.isLikelyHc05 }
                    .thenBy { it.name.lowercase(Locale.US) }
            )
        } catch (e: SecurityException) {
            addLog("Bluetooth security error: ${e.message}")
            emptyList()
        }
        _pairedDevices.value = devices
        return devices
    }

    fun connectToHc05Auto() {
        val paired = refreshPairedDevices()
        val target = paired.firstOrNull { it.isLikelyHc05 }
        if (target != null) {
            connectToDevice(target.address, target.name)
        } else {
            addLog("No paired HC-05 device found. Pair HC-05 in Android Bluetooth Settings or pick a device manually.")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String, displayName: String? = null) {
        if (!hasBluetoothPermission()) {
            _connectionState.value = BluetoothConnectionState.ERROR
            addLog("Cannot connect: BLUETOOTH_CONNECT permission not granted.")
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = BluetoothConnectionState.ERROR
            addLog("Cannot connect: Bluetooth is disabled on this phone.")
            return
        }

        scope.launch {
            disconnectInternal(silent = true)
            _connectionState.value = BluetoothConnectionState.CONNECTING
            val label = displayName ?: address
            addLog("Connecting via SPP to $label ($address)...")

            try {
                val device: BluetoothDevice = adapter.getRemoteDevice(address)
                val connectedSocket = openRfcommSocketWithFallback(device)
                socket = connectedSocket
                outputStream = connectedSocket.outputStream

                val resolvedName = try {
                    device.name ?: label
                } catch (_: SecurityException) {
                    label
                }

                _connectedDeviceName.value = resolvedName
                _connectedDeviceAddress.value = address
                _connectionState.value = BluetoothConnectionState.CONNECTED
                addLog("Connected to $resolvedName (HC-05 SPP ready).")

                startReaderLoop(connectedSocket)
                sendCommandInternal("STATUS")
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth connect failed: ${e.message}", e)
                disconnectInternal(silent = true)
                _connectionState.value = BluetoothConnectionState.ERROR
                addLog("Connection failed for $label: ${e.message ?: "Socket unreachable"}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRfcommSocketWithFallback(device: BluetoothDevice): BluetoothSocket {
        return try {
            val primary = device.createRfcommSocketToServiceRecord(SPP_UUID)
            primary.connect()
            primary
        } catch (primaryError: IOException) {
            // Standard fallback for HC-05 modules whose SDP channel requires direct RFCOMM channel 1
            Log.w(TAG, "Primary SPP connect failed, trying RFCOMM channel 1 fallback: ${primaryError.message}")
            val fallbackMethod = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val fallbackSocket = fallbackMethod.invoke(device, 1) as BluetoothSocket
            fallbackSocket.connect()
            fallbackSocket
        }
    }

    private fun startReaderLoop(activeSocket: BluetoothSocket) {
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(activeSocket.inputStream))
                while (isActive && activeSocket.isConnected) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        addLog("RX < $trimmed")
                        handleIncomingArduinoMessage(trimmed)
                    }
                }
            } catch (e: IOException) {
                if (isActive && _connectionState.value == BluetoothConnectionState.CONNECTED) {
                    addLog("HC-05 link dropped: ${e.message}")
                    disconnectInternal(silent = true)
                }
            }
        }
    }

    private fun handleIncomingArduinoMessage(message: String) {
        if (message.startsWith("DONE:", ignoreCase = true)) {
            val nutrient = message.substringAfter("DONE:", "").trim().uppercase(Locale.US)
            when (nutrient) {
                "N" -> _pumpActiveState.value = _pumpActiveState.value.copy(nActive = false)
                "P" -> _pumpActiveState.value = _pumpActiveState.value.copy(pActive = false)
                "K" -> _pumpActiveState.value = _pumpActiveState.value.copy(kActive = false)
                "ALL" -> _pumpActiveState.value = PumpActiveState()
            }
        } else if (message.startsWith("STATUS:", ignoreCase = true)) {
            val payload = message.substringAfter("STATUS:")
            val nOn = payload.contains("N=1")
            val pOn = payload.contains("P=1")
            val kOn = payload.contains("K=1")
            _pumpActiveState.value = PumpActiveState(nActive = nOn, pActive = pOn, kActive = kOn)
        }
    }

    fun sendDosingPlan(plan: NpkDosingPlan): Boolean {
        if (_connectionState.value != BluetoothConnectionState.CONNECTED || outputStream == null) {
            addLog("Cannot trigger pumps: HC-05 Bluetooth is not connected.")
            return false
        }
        if (!plan.anyPumpNeeded) {
            addLog("Evaluation (${plan.sourceLabel}): All N-P-K nutrients optimal. No pumps triggered.")
            return true
        }

        val cmd = plan.toArduinoDoseCommand()
        scope.launch {
            val sent = sendCommandInternal(cmd)
            if (sent) {
                scheduleUiPumpIndicator(plan.nitrogen.durationMs, 'N')
                scheduleUiPumpIndicator(plan.phosphorus.durationMs, 'P')
                scheduleUiPumpIndicator(plan.potassium.durationMs, 'K')
            }
        }
        return true
    }

    fun setManualPump(nutrient: Char, turnOn: Boolean): Boolean {
        if (_connectionState.value != BluetoothConnectionState.CONNECTED || outputStream == null) {
            addLog("Cannot toggle Pump $nutrient: HC-05 Bluetooth is not connected.")
            return false
        }
        val code = nutrient.uppercaseChar()
        val cmd = "PUMP:$code:${if (turnOn) "ON" else "OFF"}"
        scope.launch {
            val sent = sendCommandInternal(cmd)
            if (sent) {
                updateSingleActiveFlag(code, turnOn)
                if (turnOn) {
                    // Match Arduino 15s hardware safety timeout in UI state
                    scheduleUiPumpIndicator(15000, code)
                }
            }
        }
        return true
    }

    fun stopAllPumps(): Boolean {
        if (_connectionState.value != BluetoothConnectionState.CONNECTED || outputStream == null) {
            _pumpActiveState.value = PumpActiveState()
            addLog("Emergency Stop: Local pump states cleared (HC-05 disconnected).")
            return false
        }
        nTimerJob?.cancel()
        pTimerJob?.cancel()
        kTimerJob?.cancel()
        _pumpActiveState.value = PumpActiveState()
        scope.launch {
            sendCommandInternal("STOP_ALL")
        }
        return true
    }

    private fun scheduleUiPumpIndicator(durationMs: Int, nutrient: Char) {
        if (durationMs <= 0) return
        val job = scope.launch {
            updateSingleActiveFlag(nutrient, true)
            delay(durationMs.toLong())
            updateSingleActiveFlag(nutrient, false)
        }
        when (nutrient) {
            'N' -> {
                nTimerJob?.cancel()
                nTimerJob = job
            }
            'P' -> {
                pTimerJob?.cancel()
                pTimerJob = job
            }
            'K' -> {
                kTimerJob?.cancel()
                kTimerJob = job
            }
        }
    }

    private fun updateSingleActiveFlag(nutrient: Char, active: Boolean) {
        _pumpActiveState.value = when (nutrient) {
            'N' -> _pumpActiveState.value.copy(nActive = active)
            'P' -> _pumpActiveState.value.copy(pActive = active)
            'K' -> _pumpActiveState.value.copy(kActive = active)
            else -> _pumpActiveState.value
        }
    }

    private suspend fun sendCommandInternal(command: String): Boolean = withContext(Dispatchers.IO) {
        val stream = outputStream ?: return@withContext false
        return@withContext try {
            val payload = (command.trim() + "\n").toByteArray(Charsets.UTF_8)
            stream.write(payload)
            stream.flush()
            addLog("TX > $command")
            true
        } catch (e: IOException) {
            addLog("TX failed ($command): ${e.message}")
            disconnectInternal(silent = true)
            false
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal(silent = false)
        }
    }

    private fun disconnectInternal(silent: Boolean) {
        readerJob?.cancel()
        readerJob = null
        nTimerJob?.cancel()
        pTimerJob?.cancel()
        kTimerJob?.cancel()
        try {
            outputStream?.close()
        } catch (_: Exception) {
        }
        outputStream = null
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        _pumpActiveState.value = PumpActiveState()
        _connectedDeviceName.value = "Not Connected"
        _connectedDeviceAddress.value = null
        _connectionState.value = BluetoothConnectionState.DISCONNECTED
        if (!silent) {
            addLog("Disconnected from HC-05 Bluetooth module.")
        }
    }

    fun clearLogs() {
        _bluetoothLogs.value = emptyList()
    }

    fun addLog(message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _bluetoothLogs.value = (listOf("[$ts] $message") + _bluetoothLogs.value).take(40)
    }

    fun release() {
        disconnectInternal(silent = true)
    }
}
