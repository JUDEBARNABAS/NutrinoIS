package com.example.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class SheetsResult {
    data class Success(val message: String) : SheetsResult()
    data class Error(val errorMessage: String) : SheetsResult()
}

sealed class CreateSheetResult {
    data class Success(val spreadsheetId: String, val spreadsheetUrl: String) : CreateSheetResult()
    data class Error(val errorMessage: String) : CreateSheetResult()
}

object SheetsClient {
    private const val TAG = "SheetsClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Creates a new Google Master Sheet with all 17 pre-configured soil diagnostic headers
    suspend fun createMasterSpreadsheet(
        accessToken: String,
        title: String = "Intelligent Soil - Master Sheet"
    ): CreateSheetResult = withContext(Dispatchers.IO) {
        val url = "https://sheets.googleapis.com/v1/spreadsheets"

        val headers = listOf(
            "Timestamp", "Farmer Name", "Phone", "Field Name", "Crop Type",
            "Location", "Moisture", "Temp", "EC", "pH", "Nitrogen",
            "Phosphorus", "Potassium", "TDS", "Weather", "Ground Truth Insights", "Sky Truth Insights"
        )

        try {
            val jsonRequest = JSONObject()
            val propertiesObj = JSONObject()
            propertiesObj.put("title", title)
            jsonRequest.put("properties", propertiesObj)

            val sheetsArray = JSONArray()
            val sheetObj = JSONObject()
            val sheetProps = JSONObject()
            sheetProps.put("title", "Soil Diagnostics")
            sheetObj.put("properties", sheetProps)

            val dataArray = JSONArray()
            val gridDataObj = JSONObject()
            gridDataObj.put("startRow", 0)
            gridDataObj.put("startColumn", 0)

            val rowDataArray = JSONArray()
            val headerRowObj = JSONObject()
            val valuesArray = JSONArray()

            for (h in headers) {
                val cellObj = JSONObject()
                val userVal = JSONObject()
                userVal.put("stringValue", h)
                cellObj.put("userEnteredValue", userVal)
                valuesArray.put(cellObj)
            }
            headerRowObj.put("values", valuesArray)
            rowDataArray.put(headerRowObj)

            gridDataObj.put("rowData", rowDataArray)
            dataArray.put(gridDataObj)
            sheetObj.put("data", dataArray)

            sheetsArray.put(sheetObj)
            jsonRequest.put("sheets", sheetsArray)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonRequest.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonResp = JSONObject(responseBody)
                    val spreadsheetId = jsonResp.optString("spreadsheetId", "")
                    val spreadsheetUrl = jsonResp.optString("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/$spreadsheetId/edit")
                    CreateSheetResult.Success(spreadsheetId, spreadsheetUrl)
                } else {
                    CreateSheetResult.Error("API Error (${response.code}): $responseBody")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create master sheet: ${e.message}", e)
            CreateSheetResult.Error("Network failure: ${e.message}")
        }
    }

    // Appenz a row of data directly to Google Sheets using the Sheets REST API v4
    suspend fun appendRowToSheetsApi(
        spreadsheetId: String,
        accessToken: String,
        sheetName: String,
        valuesList: List<Any>
    ): SheetsResult = withContext(Dispatchers.IO) {
        val sheetRange = "$sheetName!A1"
        val url = "https://sheets.googleapis.com/v1/spreadsheets/$spreadsheetId/values/$sheetRange:append?valueInputOption=USER_ENTERED"

        val jsonRequest = JSONObject()
        jsonRequest.put("range", sheetRange)
        jsonRequest.put("majorDimension", "ROWS")

        val valuesArray = JSONArray()
        val rowArray = JSONArray()
        for (value in valuesList) {
            rowArray.put(value)
        }
        valuesArray.put(rowArray)
        jsonRequest.put("values", valuesArray)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonRequest.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully logged to Google Sheets!")
                    SheetsResult.Success("Success! Logged data row to Google Sheet.")
                } else {
                    Log.e(TAG, "Google Sheets API Error: Code ${response.code}, Body: $responseBody")
                    SheetsResult.Error("Sheets API Error (${response.code}): $responseBody")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sheets connection failed: ${e.message}", e)
            SheetsResult.Error("Network failure: ${e.message}")
        }
    }

    // Appends a row of data using a Google Apps Script Web App URL deployment (supports both 9-column Master Sheet & 17-column schema)
    suspend fun sendTelemetryToAppsScript(
        url: String,
        timestamp: String,
        dataId: String,
        moisture: Double,
        ph: Double,
        nitrogen: Int,
        phosphorus: Int,
        potassium: Int,
        status: String = "OPTIMAL",
        agent: String = "FIELD-AGENT",
        farmerName: String = "",
        phone: String = "",
        fieldName: String = "",
        cropType: String = "",
        location: String = "",
        temp: Double = 0.0,
        ec: Int = 0,
        tds: Int = 0,
        weather: String = "",
        groundTruth: String = "",
        skyTruth: String = ""
    ): SheetsResult = withContext(Dispatchers.IO) {
        val jsonRequest = JSONObject()

        // 9-column Master Sheet keys (matching "Intelligent soil DB" spreadsheet structure)
        jsonRequest.put("TimeStamp", timestamp)
        jsonRequest.put("Timestamp", timestamp)
        jsonRequest.put("timestamp", timestamp)

        jsonRequest.put("Data ID", dataId)
        jsonRequest.put("dataId", dataId)
        jsonRequest.put("data_id", dataId)
        jsonRequest.put("id", dataId)

        jsonRequest.put("Moisture", moisture)
        jsonRequest.put("moisture", moisture)
        jsonRequest.put("humidity", moisture)
        jsonRequest.put("soilHumidity", moisture)

        jsonRequest.put("PH", ph)
        jsonRequest.put("pH", ph)
        jsonRequest.put("ph", ph)
        jsonRequest.put("soilPh", ph)

        jsonRequest.put("Nitrogen", nitrogen)
        jsonRequest.put("nitrogen", nitrogen)
        jsonRequest.put("N", nitrogen)
        jsonRequest.put("soilN", nitrogen)

        jsonRequest.put("Phosphorus", phosphorus)
        jsonRequest.put("phosphorus", phosphorus)
        jsonRequest.put("P", phosphorus)
        jsonRequest.put("soilP", phosphorus)

        jsonRequest.put("Potassium", potassium)
        jsonRequest.put("potassium", potassium)
        jsonRequest.put("K", potassium)
        jsonRequest.put("soilK", potassium)

        jsonRequest.put("Status", status)
        jsonRequest.put("status", status)

        jsonRequest.put("Agent", agent)
        jsonRequest.put("agent", agent)

        // 17-column metadata keys
        jsonRequest.put("Farmer Name", farmerName)
        jsonRequest.put("farmerName", farmerName)
        jsonRequest.put("Name", farmerName)
        jsonRequest.put("name", farmerName)

        jsonRequest.put("Phone", phone)
        jsonRequest.put("phone", phone)

        jsonRequest.put("Field Name", fieldName)
        jsonRequest.put("fieldName", fieldName)

        jsonRequest.put("Crop Type", cropType)
        jsonRequest.put("cropType", cropType)

        jsonRequest.put("Location", location)
        jsonRequest.put("location", location)

        jsonRequest.put("Temp", temp)
        jsonRequest.put("temp", temp)
        jsonRequest.put("temperature", temp)

        jsonRequest.put("EC", ec)
        jsonRequest.put("ec", ec)

        jsonRequest.put("TDS", tds)
        jsonRequest.put("tds", tds)

        jsonRequest.put("Weather", weather)
        jsonRequest.put("weather", weather)

        jsonRequest.put("Ground Truth Insights", groundTruth)
        jsonRequest.put("Sky Truth Insights", skyTruth)

        jsonRequest.put("action", "append")

        // 17-column array format (matching buildRowData order)
        val values17 = JSONArray().apply {
            put(timestamp)
            put(farmerName)
            put(phone)
            put(fieldName)
            put(cropType)
            put(location)
            put("$moisture%")
            put("$temp°C")
            put("$ec µs/cm")
            put(ph)
            put("$nitrogen mg/kg")
            put("$phosphorus mg/kg")
            put("$potassium mg/kg")
            put("$tds mg/L")
            put(weather)
            put(groundTruth)
            put(skyTruth)
        }

        // 9-column array format (matching 9-column summary layout)
        val values9 = JSONArray().apply {
            put(timestamp)
            put(dataId)
            put(moisture)
            put(ph)
            put(nitrogen)
            put(phosphorus)
            put(potassium)
            put(status)
            put(agent)
        }

        // Send 17 columns as default for values & row
        jsonRequest.put("values", values17)
        jsonRequest.put("row", values17)
        jsonRequest.put("values17", values17)
        jsonRequest.put("row17", values17)
        jsonRequest.put("values9", values9)
        jsonRequest.put("row9", values9)

        // Nested data object containing all fields
        val dataObj = JSONObject()
        val keys = jsonRequest.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key != "data") {
                dataObj.put(key, jsonRequest.get(key))
            }
        }
        jsonRequest.put("data", dataObj)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonRequest.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully logged to Google Apps Script!")
                    SheetsResult.Success("Success! Logged row via Web Script URL.")
                } else {
                    Log.e(TAG, "Apps Script Applet Error: Code ${response.code}, Body: $responseBody")
                    val errorDetail = if (response.code == 403) {
                        "403 Forbidden - Access Denied. In Google Apps Script, set 'Who has access' to 'Anyone' under Deploy > Manage Deployments and save a New Version."
                    } else {
                        "Apps Script Web Service Error (${response.code}): $responseBody"
                    }
                    SheetsResult.Error(errorDetail)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Apps Script upload failed: ${e.message}", e)
            SheetsResult.Error("Web App upload failure: ${e.message}")
        }
    }

    suspend fun sendToAppsScript(
        url: String,
        valuesList: List<Any>
    ): SheetsResult = withContext(Dispatchers.IO) {
        fun extractDouble(idx: Int, defaultVal: Double = 0.0): Double {
            if (idx >= valuesList.size) return defaultVal
            val raw = valuesList[idx].toString().replace(Regex("[^0-9.-]"), "")
            return raw.toDoubleOrNull() ?: defaultVal
        }
        fun extractInt(idx: Int, defaultVal: Int = 0): Int {
            if (idx >= valuesList.size) return defaultVal
            val raw = valuesList[idx].toString().replace(Regex("[^0-9-]"), "")
            return raw.toIntOrNull() ?: defaultVal
        }
        fun extractString(idx: Int, defaultVal: String = ""): String {
            if (idx >= valuesList.size) return defaultVal
            return valuesList[idx].toString()
        }

        val ts = extractString(0, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        val name = extractString(1, "Soil Test Spot")
        val phone = extractString(2, "N/A")
        val field = extractString(3, "Field A")
        val crop = extractString(4, "N/A")
        val loc = extractString(5, "Spot Location")
        val moisture = extractDouble(6, 0.0)
        val temp = extractDouble(7, 0.0)
        val ec = extractInt(8, 0)
        val ph = extractDouble(9, 7.0)
        val n = extractInt(10, 0)
        val p = extractInt(11, 0)
        val k = extractInt(12, 0)
        val tds = extractInt(13, 0)
        val weather = extractString(14, "N/A")
        val ground = extractString(15, "N/A")
        val sky = extractString(16, "N/A")

        val dataId = "SMP-${(1000..9999).random()}"
        val status = if (ph in 6.0..7.5 && n >= 15 && p >= 10 && k >= 100) "OPTIMAL" else if (n < 10 || p < 5 || k < 50) "DEFICIENT" else "ATTENTION"
        val agent = name.ifBlank { "FIELD-AGENT" }

        sendTelemetryToAppsScript(
            url = url,
            timestamp = ts,
            dataId = dataId,
            moisture = moisture,
            ph = ph,
            nitrogen = n,
            phosphorus = p,
            potassium = k,
            status = status,
            agent = agent,
            farmerName = name,
            phone = phone,
            fieldName = field,
            cropType = crop,
            location = loc,
            temp = temp,
            ec = ec,
            tds = tds,
            weather = weather,
            groundTruth = ground,
            skyTruth = sky
        )
    }

    // Factory helper to build row lists from farmer and test telemetry
    fun buildRowData(
        profileName: String,
        phone: String,
        fieldName: String,
        cropType: String,
        location: String,
        soilHumidity: Double,
        soilTemp: Double,
        soilEc: Int,
        soilPh: Double,
        soilN: Int,
        soilP: Int,
        soilK: Int,
        salinityTds: Int,
        localWeather: String,
        groundTruthInsights: String,
        skyTruthInsights: String
    ): List<Any> {
        val currentTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return listOf(
            currentTimestamp,
            profileName,
            phone,
            fieldName,
            cropType,
            location,
            "$soilHumidity%",
            "$soilTemp°C",
            "$soilEc µs/cm",
            soilPh,
            "$soilN mg/kg",
            "$soilP mg/kg",
            "$soilK mg/kg",
            "$salinityTds mg/L",
            localWeather,
            groundTruthInsights,
            skyTruthInsights
        )
    }
}
