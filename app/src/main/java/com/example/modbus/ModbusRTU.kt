package com.example.modbus

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Soil metrics extracted from Modbus RTU reaction.
 */
data class SoilReading(
    val humidityPercent: Double,  // Address 0x0000: e.g. 38.5%
    val temperatureCelsius: Double, // Address 0x0001: e.g. 24.2°C
    val electricalConductivity: Int, // Address 0x0002: e.g. 180 us/cm
    val pH: Double,               // Address 0x0003: e.g. 6.8
    val nitrogen: Int,            // Address 0x0004: e.g. 45 mg/kg (N)
    val phosphorus: Int,          // Address 0x0005: e.g. 22 mg/kg (P)
    val potassium: Int,           // Address 0x0006: e.g. 112 mg/kg (K)
    val salinityTds: Int,         // Address 0x0007: e.g. 90 mg/L or ppm
    val timestamp: Long = System.currentTimeMillis()
)

object ModbusRTU {

    /**
     * Calculates the standard CRC-16 Modbus (LCR) algorithm.
     * Polynomial: 0xA001 (reversed 0x8005)
     */
    fun calculateCRC(bytes: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until length) {
            crc = crc xor (bytes[i].toInt() and 0xFF)
            for (j in 0 until 8) {
                if ((crc and 1) != 0) {
                    crc = (crc ushr 1) xor 0xA001
                } else {
                    crc = crc ushr 1
                }
            }
        }
        return crc
    }

    /**
     * Builds a Modbus RTU query frame to read holding registers (Function code 0x03).
     * @param slaveAddress Address of soil probe (usually 0x01)
     * @param startAddress Starting register address (usually 0x0000)
     * @param registerCount Number of registers to query (commonly 8 for full dataset)
     */
    fun buildReadRequest(slaveAddress: Byte, startAddress: Short, registerCount: Short): ByteArray {
        val frame = ByteArray(8)
        frame[0] = slaveAddress
        frame[1] = 0x03 // FC: Read Holding Registers
        
        // Start Address high, low
        frame[2] = ((startAddress.toInt() ushr 8) and 0xFF).toByte()
        frame[3] = (startAddress.toInt() and 0xFF).toByte()
        
        // Register Count high, low
        frame[4] = ((registerCount.toInt() ushr 8) and 0xFF).toByte()
        frame[5] = (registerCount.toInt() and 0xFF).toByte()
        
        // Calculate and add CRC16
        val crc = calculateCRC(frame, 6)
        frame[6] = (crc and 0xFF).toByte()        // Low byte first
        frame[7] = ((crc ushr 8) and 0xFF).toByte() // High byte second
        
        return frame
    }

    /**
     * Parses an incoming response frame from the soil sensor.
     * Response package format:
     * [0] Slave Addr
     * [1] Function Code (0x03)
     * [2] Byte count (e.g. 14 or 16 bytes for 7/8 registers)
     * [3..N-3] High/Low byte pairs of telemetry registers
     * [N-2, N-1] CRC16 checksum (Low, High)
     */
    fun parseResponse(response: ByteArray): SoilReading? {
        if (response.size < 5) return null // Minimum frame size: Slave (1) + FC (1) + Count (1) + CRC (2)

        val slaveId = response[0]
        val fundCode = response[1]
        
        // Exclude parsing if it's an error code response
        if (fundCode.toInt() and 0xFF > 0x80) {
            return null
        }

        val byteCount = response[2].toInt() and 0xFF
        val expectedLength = byteCount + 5
        
        if (response.size < expectedLength) {
            return null // Incomplete buffer
        }

        // Validate CRC
        val crcReceived = ((response[expectedLength - 1].toInt() and 0xFF) shl 8) or 
                           (response[expectedLength - 2].toInt() and 0xFF)
        val crcCalculated = calculateCRC(response, expectedLength - 2)
        
        if (crcReceived != crcCalculated) {
            return null // CRC Mismatch
        }

        // Extract integer register words (each register takes 2 bytes)
        val registers = IntArray(byteCount / 2)
        for (i in 0 until registers.size) {
            val highByte = response[3 + i * 2].toInt() and 0xFF
            val lowByte = response[4 + i * 2].toInt() and 0xFF
            registers[i] = (highByte shl 8) or lowByte
        }

        // Sensor Register Mapping for general 5-pin / 7-pin sensors:
        // Register 0 (at 0x0000): Temp (Scale 10, e.g. 234 -> 23.4°C, or signed short for below zero)
        // Register 1 (at 0x0001): Moisture / Humidity (Scale 10, e.g. 452 -> 45.2%)
        // Register 2 (at 0x0002): EC (1 us/cm)
        // Register 3 (at 0x0003): pH (Scale 10, e.g. 68 -> 6.8)
        // Register 4 (at 0x0004): Nitrogen (N - mg/kg)
        // Register 5 (at 0x0005): Phosphorus (P - mg/kg)
        // Register 6 (at 0x0006): Potassium (K - mg/kg)
        // Register 7 (at 0x0007): Salinity or TDS (ppm or mg/L)

        val tempRaw = getRegisterValueSafely(registers, 0)
        val moistureRaw = getRegisterValueSafely(registers, 1)
        val ec = getRegisterValueSafely(registers, 2)
        val phRaw = getRegisterValueSafely(registers, 3)
        val nitrogen = getRegisterValueSafely(registers, 4)
        val phosphorus = getRegisterValueSafely(registers, 5)
        val potassium = getRegisterValueSafely(registers, 6)
        val salinity = getRegisterValueSafely(registers, 7)

        // Convert scaled readings
        var moistureVal = moistureRaw / 10.0
        if (moistureVal > 100.0) moistureVal = 100.0

        // Handle negative temperatures (2's complement on 16-bit)
        val tempSigned = if (tempRaw > 32767) tempRaw - 65536 else tempRaw
        val tempVal = tempSigned / 10.0

        val phVal = phRaw / 10.0

        return SoilReading(
            humidityPercent = moistureVal,
            temperatureCelsius = tempVal,
            electricalConductivity = ec,
            pH = phVal,
            nitrogen = nitrogen,
            phosphorus = phosphorus,
            potassium = potassium,
            salinityTds = salinity
        )
    }

    private fun getRegisterValueSafely(regs: IntArray, index: Int): Int {
        if (index < 0 || index >= regs.size) return 0
        return regs[index]
    }

    /**
     * Formats bytes as a visual hex string for diagnostics console display.
     */
    fun toHexString(bytes: ByteArray, length: Int = bytes.size): String {
        val sb = StringBuilder()
        for (i in 0 until length) {
            sb.append(String.format("%02X ", bytes[i]))
        }
        return sb.toString().trim()
    }
}
