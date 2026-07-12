package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppRepository(private val context: Context) {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "daily_check_static_db"
        ).build()
    }

    private val unitDao = database.unitDao()
    private val hmUpdateDao = database.hmUpdateDao()
    private val okHttpClient = OkHttpClient()

    val allUnits: Flow<List<UnitEntity>> = unitDao.getAllUnits()
    val allUpdates: Flow<List<HMUpdateEntity>> = hmUpdateDao.getAllUpdates()

    suspend fun initializeDefaultUnitsIfEmpty() {
        withContext(Dispatchers.IO) {
            val currentUnits = unitDao.getAllUnits().first()
            if (currentUnits.isEmpty()) {
                val now = System.currentTimeMillis()
                val defaultUnits = listOf(
                    UnitEntity("DT101", lastUpdated = now - 8 * 24 * 3600 * 1000L, lastHM = 1420.5, lastSektor = "Sektor A", lastArea = "Front Barat"),
                    UnitEntity("DT102", lastUpdated = now - 2 * 24 * 3600 * 1000L, lastHM = 2310.1, lastSektor = "Sektor B", lastArea = "Stockpile 2"),
                    UnitEntity("DT103", lastUpdated = now - 10 * 24 * 3600 * 1000L, lastHM = 980.4, lastSektor = "Sektor A", lastArea = "Front Timur"),
                    UnitEntity("DT104", lastUpdated = 0, lastHM = 0.0, lastSektor = "", lastArea = ""),
                    UnitEntity("DT105", lastUpdated = now - 1 * 24 * 3600 * 1000L, lastHM = 3120.0, lastSektor = "Sektor C", lastArea = "Disposal Utara"),
                    UnitEntity("EX201", lastUpdated = now - 12 * 24 * 3600 * 1000L, lastHM = 5410.8, lastSektor = "Sektor B", lastArea = "Front Tengah"),
                    UnitEntity("EX202", lastUpdated = now - 3 * 24 * 3600 * 1000L, lastHM = 4120.2, lastSektor = "Sektor D", lastArea = "Inpit Timur"),
                    UnitEntity("DZ301", lastUpdated = now - 1 * 24 * 3600 * 1000L, lastHM = 6210.5, lastSektor = "Sektor A", lastArea = "Front Barat"),
                    UnitEntity("DZ302", lastUpdated = 0, lastHM = 0.0, lastSektor = "", lastArea = ""),
                    UnitEntity("GD401", lastUpdated = now - 5 * 24 * 3600 * 1000L, lastHM = 2780.3, lastSektor = "Sektor C", lastArea = "Jalan Utama KM 5"),
                    UnitEntity("GD402", lastUpdated = now - 15 * 24 * 3600 * 1000L, lastHM = 1150.9, lastSektor = "Sektor D", lastArea = "Ramp Barat"),
                    UnitEntity("LD501", lastUpdated = now - 4 * 24 * 3600 * 1000L, lastHM = 3340.6, lastSektor = "Sektor B", lastArea = "Crusher 1")
                )
                unitDao.insertUnits(defaultUnits)
            }
        }
    }

    fun getUpdatesToday(startOfDay: Long): Flow<List<HMUpdateEntity>> = hmUpdateDao.getUpdatesToday(startOfDay)

    suspend fun insertUpdate(update: HMUpdateEntity) {
        withContext(Dispatchers.IO) {
            // Delete previous updates for this unit
            hmUpdateDao.deleteHMUpdatesForUnit(update.nomorUnit)

            // Save update
            hmUpdateDao.insertUpdate(update)

            // Also update the Unit master record
            val existing = unitDao.getUnitById(update.nomorUnit)
            val updatedUnit = UnitEntity(
                nomorUnit = update.nomorUnit,
                lastUpdated = update.timestamp,
                lastHM = update.hoursMeter,
                lastSektor = update.sektor,
                lastArea = update.area
            )
            unitDao.insertUnit(updatedUnit)
        }
    }

    suspend fun insertUnit(unit: UnitEntity) {
        withContext(Dispatchers.IO) {
            unitDao.insertUnit(unit)
        }
    }

    suspend fun deleteUnit(nomorUnit: String) {
        withContext(Dispatchers.IO) {
            unitDao.deleteUnit(nomorUnit)
            hmUpdateDao.deleteHMUpdatesForUnit(nomorUnit)
        }
    }

    suspend fun deleteMultipleUnits(nomorUnits: List<String>) {
        withContext(Dispatchers.IO) {
            nomorUnits.forEach { nomorUnit ->
                unitDao.deleteUnit(nomorUnit)
                hmUpdateDao.deleteHMUpdatesForUnit(nomorUnit)
            }
        }
    }

    suspend fun deleteUnitFromServer(webAppUrl: String, nomorUnit: String): Boolean {
        return withContext(Dispatchers.IO) {
            if (webAppUrl.isEmpty() || !webAppUrl.startsWith("http")) return@withContext false
            try {
                val json = """
                    {
                      "action": "delete",
                      "nomorUnit": "$nomorUnit"
                    }
                """.trimIndent()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = json.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(webAppUrl)
                    .post(body)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 200 || response.code == 302
                }
            } catch (e: Exception) {
                Log.e("AppRepository", "Error deleting unit $nomorUnit from server", e)
                false
            }
        }
    }

    suspend fun renameUnit(oldNomorUnit: String, newNomorUnit: String) {
        withContext(Dispatchers.IO) {
            val existing = unitDao.getUnitById(oldNomorUnit)
            if (existing != null) {
                unitDao.deleteUnit(oldNomorUnit)
                unitDao.insertUnit(existing.copy(nomorUnit = newNomorUnit))
                hmUpdateDao.updateHMUpdatesUnitName(oldNomorUnit, newNomorUnit)
            }
        }
    }

    suspend fun syncPendingUpdates(webAppUrl: String): Pair<Int, String> {
        return withContext(Dispatchers.IO) {
            if (webAppUrl.isEmpty() || !webAppUrl.startsWith("http")) {
                return@withContext Pair(0, "URL Web App belum dikonfigurasi di pengaturan.")
            }

            val unsynced = hmUpdateDao.getUnsyncedUpdates()
            var successCount = 0
            var errorMessage = ""
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            // 1. Upload Phase (1-way to Cloud)
            if (unsynced.isNotEmpty()) {
                for (update in unsynced) {
                    try {
                        val formattedTime = dateFormat.format(Date(update.timestamp))
                        val escapedEmail = update.email.replace("\"", "\\\"")
                        val escapedUnit = update.nomorUnit.replace("\"", "\\\"")
                        val escapedSektor = update.sektor.replace("\"", "\\\"")
                        val escapedArea = update.area.replace("\"", "\\\"")

                        val json = """
                            {
                              "timestamp": "$formattedTime",
                              "email": "$escapedEmail",
                              "nomorUnit": "$escapedUnit",
                              "hoursMeter": ${update.hoursMeter},
                              "sektor": "$escapedSektor",
                              "area": "$escapedArea"
                            }
                        """.trimIndent()

                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val body = json.toRequestBody(mediaType)
                        val request = Request.Builder()
                            .url(webAppUrl)
                            .post(body)
                            .build()

                        okHttpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful || response.code == 302 || response.code == 200) {
                                hmUpdateDao.markSynced(update.id)
                                successCount++
                            } else {
                                errorMessage = "Server error: ${response.code} ${response.message}"
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Koneksi gagal"
                        Log.e("AppRepository", "Error syncing update ${update.id}", e)
                    }
                }
            }

            // 2. Download Phase (Fetch latest rows from Spreadsheet)
            var downloadSuccess = false
            var downloadMessage = ""
            try {
                val fetchRequest = Request.Builder()
                    .url(webAppUrl)
                    .get()
                    .build()

                okHttpClient.newCall(fetchRequest).execute().use { response ->
                    if (response.isSuccessful || response.code == 200) {
                        val responseBody = response.body?.string() ?: ""
                        if (responseBody.trim().startsWith("[")) {
                            val jsonArray = org.json.JSONArray(responseBody)
                            if (jsonArray.length() > 0) {
                                // Keep local units, only clear previously synced updates to prevent piling up
                                hmUpdateDao.deleteOldSyncedUpdates(Long.MAX_VALUE)

                                val newList = mutableMapOf<String, HMUpdateEntity>()
                                val newUnits = mutableMapOf<String, UnitEntity>()

                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val timestampStr = obj.optString("timestamp")
                                    val emailVal = obj.optString("email")
                                    val nomorUnitVal = obj.optString("nomorUnit").trim().uppercase()
                                    val hoursMeterVal = obj.optDouble("hoursMeter", 0.0)
                                    val sektorVal = obj.optString("sektor")
                                    val areaVal = obj.optString("area")

                                    if (nomorUnitVal.isEmpty()) continue

                                    val timeMillis = try {
                                        dateFormat.parse(timestampStr)?.time ?: System.currentTimeMillis()
                                    } catch (e: Exception) {
                                        System.currentTimeMillis()
                                    }

                                    // Only keep the latest update record per unit
                                    val existingUpdate = newList[nomorUnitVal]
                                    if (existingUpdate == null || timeMillis > existingUpdate.timestamp) {
                                        newList[nomorUnitVal] = HMUpdateEntity(
                                            timestamp = timeMillis,
                                            email = emailVal,
                                            nomorUnit = nomorUnitVal,
                                            hoursMeter = hoursMeterVal,
                                            sektor = sektorVal,
                                            area = areaVal,
                                            isSynced = true
                                        )
                                    }

                                    val existingUnit = newUnits[nomorUnitVal]
                                    if (existingUnit == null || timeMillis > existingUnit.lastUpdated) {
                                        newUnits[nomorUnitVal] = UnitEntity(
                                            nomorUnit = nomorUnitVal,
                                            lastUpdated = timeMillis,
                                            lastHM = hoursMeterVal,
                                            lastSektor = sektorVal,
                                            lastArea = areaVal
                                        )
                                    }
                                }

                                for (upd in newList.values) {
                                    hmUpdateDao.insertUpdate(upd)
                                }
                                unitDao.insertUnits(newUnits.values.toList())

                                // Clean up old synced updates (older than today)
                                val calendar = java.util.Calendar.getInstance()
                                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                calendar.set(java.util.Calendar.MINUTE, 0)
                                calendar.set(java.util.Calendar.SECOND, 0)
                                calendar.set(java.util.Calendar.MILLISECOND, 0)
                                hmUpdateDao.deleteOldSyncedUpdates(calendar.timeInMillis)

                                downloadSuccess = true
                                downloadMessage = "Berhasil mendownload ${jsonArray.length()} unit terbaru dari spreadsheet!"
                            } else {
                                downloadSuccess = true
                                downloadMessage = "Spreadsheet kosong, data lokal tetap dipertahankan."
                            }
                        } else {
                            downloadMessage = "Format response dari server bukan JSON array."
                        }
                    } else {
                        downloadMessage = "Gagal mendownload data terbaru: HTTP ${response.code}"
                    }
                }
            } catch (e: Exception) {
                downloadMessage = "Gagal mendownload data terbaru: ${e.message}"
                Log.e("AppRepository", "Error fetching data from sheet", e)
            }

            // Combine messages
            val finalStatus = when {
                unsynced.isEmpty() && downloadSuccess -> "Sinkronisasi 2-arah berhasil: $downloadMessage"
                unsynced.isEmpty() -> "Gagal sinkronisasi 2-arah: $downloadMessage"
                successCount == unsynced.size && downloadSuccess -> "Berhasil mengunggah $successCount data & $downloadMessage"
                successCount == unsynced.size -> "Berhasil mengunggah $successCount data, namun gagal mengunduh data terbaru: $downloadMessage"
                else -> "Gagal sinkronisasi penuh. Unggah sebagian ($successCount/${unsynced.size}): $errorMessage. Unduh: $downloadMessage"
            }

            Pair(successCount, finalStatus)
        }
    }
}
