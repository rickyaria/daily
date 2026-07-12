package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ParsedUnitUpdate(
    val nomorUnit: String,
    val hoursMeter: Double,
    val sektor: String = "",
    val area: String = ""
)

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    suspend fun parseDailyReport(reportText: String, instructions: String = ""): List<ParsedUnitUpdate> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiService", "Gemini API Key is empty or placeholder.")
            throw IllegalStateException("API Key Gemini belum dikonfigurasi di panel Secrets.")
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val promptText = """
            You are an expert mining operations assistant. Parse the following unstructured daily check report text from the admin.
            Extract all heavy machinery hours meter (HM) update entries.
            Return a JSON array of objects. Each object MUST contain:
            - "nomorUnit": String (normalised to uppercase, strictly alphanumeric only with NO spaces, NO hyphens, and NO symbols, e.g. "DT-101" must become "DT101", "EX-201" must become "EX201", "TL 02" must become "TL02")
            - "hoursMeter": Double (the numeric value of Hours Meter)
            - "sektor": String (the sector name e.g. "Sektor A", "Sektor B" or empty string if not found)
            - "area": String (the location area e.g. "Front Barat", "Stockpile 2" or empty string if not found)

            ${if (instructions.isNotBlank()) "User-specified custom parsing command: $instructions" else ""}

            Be robust in matching different spelling variations, abbreviations, or numbers.
            Strictly return a JSON array only. No markdown formatting, no code block backticks.

            Report text to parse:
            $reportText
        """.trimIndent()

        val jsonRequest = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", promptText)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonRequest.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = response.body?.string() ?: ""
                    Log.e("GeminiService", "API Error: ${response.code} $errorMsg")
                    throw Exception("Gagal menghubungi server Gemini: ${response.code}")
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    throw Exception("Response body empty")
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.getJSONArray("candidates")
                if (candidates.length() == 0) {
                    throw Exception("Tidak ada respon dari Gemini")
                }

                val text = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                Log.d("GeminiService", "Raw response: $text")

                val listType = Types.newParameterizedType(List::class.java, ParsedUnitUpdate::class.java)
                val adapter: JsonAdapter<List<ParsedUnitUpdate>> = moshi.adapter(listType)
                
                val cleanJson = text.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val parsedList = adapter.fromJson(cleanJson)
                parsedList ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Error parsing report", e)
            throw e
        }
    }
}
