package com.example.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.example.modbus.ModbusRTU
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

private const val TAG = "UsbSerialManager"
private const val ACTION_USB_PERMISSION = "com.aistudio.soiltesting.USB_PERMISSION"

enum class ConnectionStatus {
    DISCONNECTED,
    PERMISSIONS_REQUESTED,
    CONNECTED_IDLE,
    READING_POLLING,
    ERROR_PORT_UNAVAILABLE
}

class UsbSerialManager(private val context: Context) {

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    // Status metrics
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _deviceName = MutableStateFlow("No device connected")
    val deviceName = _deviceName.asStateFlow()

    // Console Logs SharedFlow
    private val _consoleLogs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val consoleLogs = _consoleLogs.asSharedFlow()

    // Decoded data packet notification
    private val _incomingDataBytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    val incomingDataBytes = _incomingDataBytes.asSharedFlow()

    private var serialPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var readJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Live receiver for permission approval
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            logConsole("Permission granted for device: ${it.deviceName}")
                            connectToDevice(it)
                        }
                    } else {
                        logConsole("Permission denied for device")
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(usbPermissionReceiver, filter)
        }
    }

    /**
     * Scan for available USB serial drivers and setup connection triggers.
     */
    fun startUsbScanAndConnect() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            logConsole("No USB Serial devices found. Connect soil probe OTG cable.")
            return
        }

        val driver = availableDrivers[0] // Pick first device
        val device = driver.device
        
        logConsole("Detected device: VID=${device.vendorId} PID=${device.productId}")
        
        if (usbManager.hasPermission(device)) {
            connectToDevice(device)
        } else {
            logConsole("Requesting USB permission...")
            _connectionStatus.value = ConnectionStatus.PERMISSIONS_REQUESTED
            
            val intentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), intentFlags
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    /**
     * Connecting to probed USB device. Configure baud 9600 8N1.
     */
    private fun connectToDevice(device: UsbDevice) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return
        
        usbConnection = usbManager.openDevice(device)
        if (usbConnection == null) {
            logConsole("Failed to open USB Device Connection")
            _connectionStatus.value = ConnectionStatus.ERROR_PORT_UNAVAILABLE
            return
        }

        // Soil testing probes typically contain 1 port (index 0)
        serialPort = driver.ports[0]
        try {
            serialPort?.open(usbConnection)
            // Configure baud rate 9600, 8N1 (8 data bits, 1 stop bit, no parity)
            serialPort?.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            
            _connectionStatus.value = ConnectionStatus.CONNECTED_IDLE
            _deviceName.value = "Probe Connected (Vendor ID: ${device.vendorId})"
            logConsole("Port successfully opened! Params: 9600, 8N1.")
            
            startReadingLoop()
        } catch (e: Exception) {
            logConsole("Error opening port: ${e.message}")
            _connectionStatus.value = ConnectionStatus.ERROR_PORT_UNAVAILABLE
            closeConnection()
        }
    }

    /**
     * Transmit standard Modbus requests through serial.
     */
    fun sendBytes(bytes: ByteArray) {
        val port = serialPort
        if (port == null || _connectionStatus.value == ConnectionStatus.DISCONNECTED) {
            logConsole("Tx Fail: Serial Port is closed or disconnected.")
            return
        }
        managerScope.launch {
            try {
                logConsole("Tx: ${ModbusRTU.toHexString(bytes)}")
                port.write(bytes, 1000) // Timeout 1000ms
            } catch (e: Exception) {
                logConsole("Tx Error: ${e.message}")
            }
        }
    }

    /**
     * Background routine listening to incoming serial buffer.
     */
    private fun startReadingLoop() {
        readJob?.cancel()
        readJob = managerScope.launch {
            _connectionStatus.value = ConnectionStatus.READING_POLLING
            val buffer = ByteArray(256)
            while (isActive) {
                val port = serialPort ?: break
                try {
                    val numBytesRead = port.read(buffer, 800) // read block 800ms
                    if (numBytesRead > 0) {
                        val received = buffer.copyOfRange(0, numBytesRead)
                        logConsole("Rx: ${ModbusRTU.toHexString(received)}")
                        _incomingDataBytes.emit(received)
                    }
                } catch (e: IOException) {
                    logConsole("Rx read failure: ${e.message}. Closing stream.")
                    _connectionStatus.postValue(ConnectionStatus.DISCONNECTED)
                    closeConnection()
                    break
                }
                delay(100) // Polling debounce sleep
            }
        }
    }

    /**
     * Gracefully tear down USB serial.
     */
    fun closeConnection() {
        logConsole("Disconnecting serial link...")
        readJob?.cancel()
        readJob = null
        try {
            serialPort?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing serial port: ${e.message}")
        }
        serialPort = null
        
        try {
            usbConnection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing USB connection: ${e.message}")
        }
        usbConnection = null
        
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _deviceName.value = "No device connected"
    }

    fun release() {
        closeConnection()
        try {
            context.unregisterReceiver(usbPermissionReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    private fun logConsole(message: String) {
        managerScope.launch {
            _consoleLogs.emit("[${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}] $message")
        }
        Log.d(TAG, message)
    }
}

// Extension to post value from context threads
private fun <T> MutableStateFlow<T>.postValue(value: T) {
    this.value = value
}
