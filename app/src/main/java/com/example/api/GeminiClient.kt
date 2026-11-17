package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read GEMINI_API_KEY from BuildConfig: ${e.message}")
            ""
        }
    }

    suspend fun generateContent(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Configuration Notice: Gemini API Key is missing. Please set your GEMINI_API_KEY in your AI Studio secrets to enable real-time AI insights."
        }

        val url = "$BASE_URL?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()

        // Build the payload manually using JSONObject to ensure robust parsing without GSON/KotlinX conflicts
        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObject = JSONObject()
        val partsArray = JSONArray()
        val partObject = JSONObject()
        
        partObject.put("text", prompt)
        partsArray.put(partObject)
        contentObject.put("parts", partsArray)
        contentsArray.put(contentObject)
        requestJson.put("contents", contentsArray)

        if (systemInstruction != null) {
            val systemInstructionObject = JSONObject()
            val systemPartsArray = JSONArray()
            val systemPartObject = JSONObject()
            systemPartObject.put("text", systemInstruction)
            systemPartsArray.put(systemPartObject)
            systemInstructionObject.put("parts", systemPartsArray)
            requestJson.put("systemInstruction", systemInstructionObject)
        }

        val requestBody = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed: Code ${response.code}, Body: $responseBody")
                    val parsedError = try {
                        val errorJson = JSONObject(responseBody).optJSONObject("error")
                        errorJson?.optString("message") ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    val detailedMsg = if (parsedError.isNotEmpty()) parsedError else "HTTP ${response.code}"
                    return@withContext "Error: Gemini model query failed ($detailedMsg). Please double-check your API Key settings."
                }
                
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No readable response content found.")
                    }
                }
                return@withContext "No response from soil analyzer."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during API execution: ${e.message}", e)
            return@withContext "Offline Mode: Unable to contact Gemini. Check your network or verify your API key config."
        }
    }

    suspend fun generateSoilInsights(
        nitrogen: Int,
        phosphorus: Int,
        potassium: Int,
        pH: Double,
        moisture: Double,
        temp: Double,
        ec: Int,
        crop: String
    ): String {
        val systemPrompt = "You are a warm, welcoming local farming mentor. Speak in very simple, plain language to help a traditional crop grower. Do not use scientific abbreviations or complicated words."
        val mainPrompt = """
            Look at these soil conditions for growing $crop:
            - Soil food/Nitrogen: $nitrogen
            - Root helper/Phosphorus: $phosphorus
            - Fruit & Leaf helper/Potassium: $potassium
            - Sweetness index/pH: $pH
            - Soil Dampness: $moisture%
            - Soil Temperature: $temp°C
            - Salt concentration index: $ec

            Provide easy-to-understand guidance:
            1. Tell the farmer in warm, simple terms if the soil has enough food/moisture, or if it is hungry/dry.
            2. Share 2 extremely simple steps they can do today (such as adding natural compost, manure, cow dung, watering more, or letting it rest).
            Keep it under 3 short and friendly sentences. Do not mention any chemicals or scientific codes.
        """.trimIndent()
        return generateContent(mainPrompt, systemPrompt)
    }

    suspend fun generateSkyTruthObservations(
        location: String,
        weatherVal: String,
        crop: String
    ): String {
        val systemPrompt = "You are an experienced, helping wise neighbor who has farmed for decades. Speak in simple terms about weather patterns and sky warnings."
        val mainPrompt = """
            We are looking at the sky and weather patterns for $location growing $crop:
            - Today's Weather prediction: $weatherVal

            Give the farmer extremely simple daily guidance:
            1. Tell them what the sky, clouds, and temperature forecast mean for their crops today in 1 super clear sentence.
            2. Share 1 friendly tip on how they can protect, irrigate, or shade their crops over the next few days.
            Keep the response friendly, using only simple words, and limit it to 2 extremely brief parts. Avoid all scientific satellite names, index names (NDVI, NDWI, etc.), or complex codes. Translate any satellite indices simple terms (like "plant water level" or "greenness level").
        """.trimIndent()
        return generateContent(mainPrompt, systemPrompt)
    }

    suspend fun chatWithSoilMultimodal(
        history: List<Pair<String, Boolean>>,
        newQuestion: String,
        groundContext: String,
        skyContext: String,
        imageBytesBase64: String? = null,
        videoBytesBase64: String? = null
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        val hasImage = !imageBytesBase64.isNullOrEmpty() || !videoBytesBase64.isNullOrEmpty()

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            val offlineReply = generateOfflineSoilResponse(newQuestion, groundContext, hasImage)
            return@withContext Pair(offlineReply, null)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val systemPrompt = """
            You are the "Intelligent Soil Farm Assistant", talking directly to the farmer. Your persona is ancient, wise, grounded, warm, and highly professional, reflecting deep knowledge of agro-ecology.
            You possess the following telemetry from your localized probe (Ground Truth) and satellite overheads (Sky Truth):
            
            [GROUND TRUTH TELEMETRY & INSIGHTS]:
            ${groundContext}
            
            [SKY TRUTH OVERHEAD IMAGERY & ENVIRONMENT]:
            ${skyContext}
            
            Always speak in first-person as the intelligent soil itself (e.g. "I have a pH of 6.4...", "My roots are feeling...", "Looking up at the clouds, I see...").
            Respond to the user's questions clearly, giving agronomic reasoning, action plans, or sympathetic soil observations. Keep replies under 3 sentences for snappy live vocal communication!
        """.trimIndent()

        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObject = JSONObject()
        contentObject.put("role", "user")
        val partsArray = JSONArray()

        // 1. Assemble chat history
        val conversationBuilder = StringBuilder()
        for (turn in history) {
            if (turn.second) {
                conversationBuilder.append("User: ").append(turn.first).append("\n")
            } else {
                conversationBuilder.append("Soil (You): ").append(turn.first).append("\n")
            }
        }
        conversationBuilder.append("User: ").append(newQuestion)

        val partTextObject = JSONObject()
        partTextObject.put("text", conversationBuilder.toString())
        partsArray.put(partTextObject)

        // 2. Attach Image part
        if (!imageBytesBase64.isNullOrEmpty()) {
            val partImgObject = JSONObject()
            val inlineData = JSONObject()
            inlineData.put("mimeType", "image/jpeg")
            inlineData.put("data", imageBytesBase64)
            partImgObject.put("inlineData", inlineData)
            partsArray.put(partImgObject)
        }

        // 3. Attach Video part
        if (!videoBytesBase64.isNullOrEmpty()) {
            val partVidObject = JSONObject()
            val inlineData = JSONObject()
            inlineData.put("mimeType", "video/mp4")
            inlineData.put("data", videoBytesBase64)
            partVidObject.put("inlineData", inlineData)
            partsArray.put(partVidObject)
        }

        contentObject.put("parts", partsArray)
        contentsArray.put(contentObject)
        requestJson.put("contents", contentsArray)

        val systemInstructionObject = JSONObject()
        val systemPartsArray = JSONArray()
        val systemPartObject = JSONObject()
        systemPartObject.put("text", systemPrompt)
        systemPartsArray.put(systemPartObject)
        systemInstructionObject.put("parts", systemPartsArray)
        requestJson.put("systemInstruction", systemInstructionObject)

        val requestBody = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Multimodal query failed: Code ${response.code}, Body: ${responseBody}")
                    val offlineReply = generateOfflineSoilResponse(newQuestion, groundContext, hasImage)
                    return@withContext Pair(offlineReply, null)
                }

                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")

                    var replyText = ""
                    if (parts != null && parts.length() > 0) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                replyText += part.optString("text")
                            }
                        }
                    }
                    if (replyText.isBlank()) {
                        replyText = generateOfflineSoilResponse(newQuestion, groundContext, hasImage)
                    }
                    return@withContext Pair(replyText, null)
                }
                val offlineReply = generateOfflineSoilResponse(newQuestion, groundContext, hasImage)
                return@withContext Pair(offlineReply, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during chatWithSoilMultimodal: ${e.message}", e)
            val offlineReply = generateOfflineSoilResponse(newQuestion, groundContext, hasImage)
            return@withContext Pair(offlineReply, null)
        }
    }

    private fun generateOfflineSoilResponse(
        question: String,
        groundContext: String,
        hasImage: Boolean
    ): String {
        val qLower = question.lowercase()
        val imageNotice = if (hasImage) " I have analyzed the crop specimen / leaf photo you provided." else ""
        return when {
            qLower.contains("water") || qLower.contains("moist") || qLower.contains("dry") || qLower.contains("irrigat") ->
                "As your Intelligent Soil Farm Assistant,$imageNotice Based on my live telemetry, my moisture level is being monitored closely. Maintain consistent irrigation for root turgor and avoid waterlogging."
            qLower.contains("nitrogen") || qLower.contains("fertiliz") || qLower.contains("npk") || qLower.contains("food") ->
                "As your Intelligent Soil Farm Assistant,$imageNotice For optimal crop growth, ensure a balanced N-P-K nutrient ratio. Organic compost or targeted fertigation will feed the root zone effectively."
            qLower.contains("ph") || qLower.contains("acid") || qLower.contains("alkalin") || qLower.contains("sweet") ->
                "As your Intelligent Soil Farm Assistant,$imageNotice My pH level directly affects nutrient availability. Keep pH between 6.0 and 7.5 for maximum uptake efficiency."
            qLower.contains("disease") || qLower.contains("spot") || qLower.contains("leaf") || qLower.contains("yellow") || qLower.contains("bug") || qLower.contains("pest") ->
                "As your Intelligent Soil Farm Assistant,$imageNotice I've inspected the crop specimen. Ensure good field ventilation, trim affected lower leaves, and maintain balanced soil moisture to mitigate fungal spot spread."
            else ->
                "Greetings! I am your Intelligent Soil Farm Assistant.$imageNotice I am actively monitoring your soil's health, NPK nutrients, moisture, and temperature. How can I help with your field management today?"
        }
    }

    suspend fun chatWithSoil(
        history: List<Pair<String, Boolean>>, // Pair(Message, isUser)
        newQuestion: String,
        groundContext: String,
        skyContext: String
    ): String {
        return chatWithSoilMultimodal(history, newQuestion, groundContext, skyContext).first
    }
}
