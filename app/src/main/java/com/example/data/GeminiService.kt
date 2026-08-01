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
    val hoursMeter: Int,
    val sektor: String = "",
    val area: String = "",
    val notes: String = ""
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
            Log.i("GeminiService", "Gemini API Key is empty or placeholder. Falling back to local offline parser.")
            return@withContext parseDailyReportLocally(reportText)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val promptText = """
            You are an expert mining operations assistant. Parse the following unstructured daily check report text from the admin.
            Extract all heavy machinery hours meter (HM) update entries.
            Return a JSON array of objects. Each object MUST contain:
            - "nomorUnit": String (normalised to uppercase, strictly alphanumeric only with NO spaces, NO hyphens, and NO symbols, e.g. "DT-101" -> "DT101", "GS 794" -> "GS794")
            - "hoursMeter": Int (integer numeric value of Hours Meter, rounded to nearest whole number, e.g. 45100)
            - "sektor": String (the sector name if specified or abbreviated like S1, S2, S4, S7, S8 or Sek1, Sek2, Sek4, Sek7, Sek8 or Sektor 1, Sektor 2, Sektor 4, Sektor 7, Sektor 8 -> map to "Sektor 1", "Sektor 2", "Sektor 4", "Sektor 7", "Sektor 8". If no sector abbreviation or number is present, return "Others").
            - "area": String (the location area. Any text besides unit number, HM, sector, and location prefixes like "lok", "lokasi", "di", "at" is the area. E.g. for "lok S4 Combat", sector is "Sektor 4" and area is "Combat". If no additional area text exists, return empty string "").
            - "notes": String (any notes, remarks or conditions, or empty string if none)

            CRITICAL SECTOR & AREA REASONING RULES:
            1. Sector can be abbreviated as S1, S2, S4, S7, S8 or Sek1, Sek2, Sek4, Sek7, Sek8 or Sektor 1, Sektor 2, Sektor 4, Sektor 7, Sektor 8. Always map these abbreviations to "Sektor 1", "Sektor 2", "Sektor 4", "Sektor 7", "Sektor 8".
            2. Any remaining location text that is NOT the sector abbreviation/number is the AREA. For example, in "GS794 HM 45100 lok S4 Combat", "S4" is Sector "Sektor 4" and "Combat" is Area "Combat".
            3. If no sector abbreviation or number (S1, S2, S4, S7, S8, Sektor 1, etc.) is found in the entry line, set "sektor" to "Others".

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
                    Log.e("GeminiService", "API Error: ${response.code} $errorMsg. Falling back to local parser.")
                    return@withContext parseDailyReportLocally(reportText)
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    return@withContext parseDailyReportLocally(reportText)
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.getJSONArray("candidates")
                if (candidates.length() == 0) {
                    return@withContext parseDailyReportLocally(reportText)
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
                parsedList ?: parseDailyReportLocally(reportText)
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Error parsing report with Gemini API, falling back to local offline parser.", e)
            return@withContext parseDailyReportLocally(reportText)
        }
    }

    private fun parseDailyReportLocally(reportText: String): List<ParsedUnitUpdate> {
        val result = mutableListOf<ParsedUnitUpdate>()
        val lines = reportText.split("\n")
        
        // Regex to match a unit number, e.g. DT101, EX201, DZ301, GD401, GS794, LD501, etc.
        val unitRegex = Regex("""\b([A-Za-z]{2,3}\s*\d{2,4})\b""")
        
        // Regex to find HM number, e.g. "HM 1435.2" or "HM: 1435.2" or "1435.2 HM" or "HM 45100"
        val hmRegex = Regex("""(?i)hm\s*:?\s*(\d+(?:[.,]\d+)?)""")
        
        // Regex for sector - supports "Sektor 1", "Sek 1", "Sek1", "S1", "Sektor A", etc.
        val sektorRegex = Regex("""(?i)\b(?:sektor|sek|s)\s*([12478a-d]|others)\b""")
        
        for (line in lines) {
            if (line.isBlank()) continue
            
            val unitMatch = unitRegex.find(line)
            if (unitMatch != null) {
                val nomorUnit = unitMatch.groupValues[1].replace(" ", "").uppercase()
                
                // Extract HM
                var hmValue = 0
                val hmMatch = hmRegex.find(line)
                if (hmMatch != null) {
                    val doubleVal = hmMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    hmValue = Math.round(doubleVal).toInt()
                } else {
                    val numberRegex = Regex("""\b(\d+(?:[.,]\d+)?)\b""")
                    val numbers = numberRegex.findAll(line).map { it.value }.toList()
                    for (numStr in numbers) {
                        val cleanNum = numStr.replace(",", ".")
                        if (!nomorUnit.contains(numStr)) {
                            val parsedNum = cleanNum.toDoubleOrNull()
                            if (parsedNum != null) {
                                hmValue = Math.round(parsedNum).toInt()
                                break
                            }
                        }
                    }
                }
                
                // Extract Sektor
                var sektor = ""
                val sektorMatch = sektorRegex.find(line)
                if (sektorMatch != null) {
                    val matchedSektorVal = sektorMatch.groupValues[1].uppercase()
                    sektor = when (matchedSektorVal) {
                        "1", "A" -> "Sektor 1"
                        "2", "B" -> "Sektor 2"
                        "4", "C" -> "Sektor 4"
                        "7", "D" -> "Sektor 7"
                        "8" -> "Sektor 8"
                        "OTHERS" -> "Others"
                        else -> "Sektor $matchedSektorVal"
                    }
                } else {
                    val lowerLine = line.lowercase()
                    if (lowerLine.contains("sektor 1")) sektor = "Sektor 1"
                    else if (lowerLine.contains("sektor 2")) sektor = "Sektor 2"
                    else if (lowerLine.contains("sektor 4")) sektor = "Sektor 4"
                    else if (lowerLine.contains("sektor 7")) sektor = "Sektor 7"
                    else if (lowerLine.contains("sektor 8")) sektor = "Sektor 8"
                    else if (lowerLine.contains("others")) sektor = "Others"
                }
                
                // Extract Area (everything else in line excluding unit, HM, sector, and location prefixes)
                var cleanLine = line
                cleanLine = cleanLine.replace(unitMatch.value, "", ignoreCase = true)
                if (hmMatch != null) {
                    cleanLine = cleanLine.replace(hmMatch.value, "", ignoreCase = true)
                } else if (hmValue > 0) {
                    cleanLine = cleanLine.replace(hmValue.toString(), "", ignoreCase = true)
                }
                if (sektorMatch != null) {
                    cleanLine = cleanLine.replace(sektorMatch.value, "", ignoreCase = true)
                }
                
                // Remove prefixes like "lokasi", "lok.", "lok:", "lok", "di", "at", "hm"
                cleanLine = cleanLine.replace("(?i)\\b(lokasi|lok|di|at|hm)\\b".toRegex(), "")
                    .replace(":", " ")
                    .replace("-", " ")
                    .replace(",", " ")
                    .trim()
                
                var area = cleanLine.replace("\\s+".toRegex(), " ").trim()
                if (area.lowercase().startsWith("sektor") || area.lowercase().startsWith("sek")) {
                    area = ""
                }
                
                if (sektor.isBlank()) {
                    sektor = "Others"
                }

                if (nomorUnit.isNotBlank()) {
                    result.add(ParsedUnitUpdate(
                        nomorUnit = nomorUnit,
                        hoursMeter = hmValue,
                        sektor = sektor,
                        area = area,
                        notes = ""
                    ))
                }
            }
        }
        return result
    }
}
