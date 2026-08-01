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

class AppRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE ?: AppRepository(context.applicationContext).also { INSTANCE = it }
                instance
            }
        }
    }

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "daily_check_static_db"
        ).fallbackToDestructiveMigration()
         .build()
    }

    private val unitDao = database.unitDao()
    private val hmUpdateDao = database.hmUpdateDao()
    private val okHttpClient = OkHttpClient()

    val allUnits: Flow<List<UnitEntity>> = unitDao.getAllUnits()
    val allUpdates: Flow<List<HMUpdateEntity>> = hmUpdateDao.getAllUpdates()

    suspend fun deduplicateHMUpdates() {
        withContext(Dispatchers.IO) {
            val all = hmUpdateDao.getAllUpdatesList()
            if (all.isEmpty()) return@withContext

            val grouped = all.groupBy { it.nomorUnit.trim().uppercase() }
            for ((_, updates) in grouped) {
                if (updates.size > 1) {
                    val unsynced = updates.filter { !it.isSynced }.maxByOrNull { it.timestamp }
                    val best = unsynced ?: updates.maxByOrNull { it.timestamp }
                    if (best != null) {
                        hmUpdateDao.deleteHMUpdatesForUnit(best.nomorUnit)
                        hmUpdateDao.insertUpdate(best)
                    }
                }
            }
        }
    }

    suspend fun initializeDefaultUnitsIfEmpty() {
        withContext(Dispatchers.IO) {
            val currentUnits = unitDao.getAllUnits().first()
            val defaultNames = setOf("DT101", "DT102", "DT103", "DT104", "DT105", "EX201", "EX202", "DZ301", "DZ302", "GD401", "GD402", "LD501")
            for (unit in currentUnits) {
                if (unit.nomorUnit in defaultNames) {
                    unitDao.deleteUnit(unit.nomorUnit)
                }
            }
            deduplicateHMUpdates()
        }
    }

    fun getUpdatesToday(startOfDay: Long): Flow<List<HMUpdateEntity>> = hmUpdateDao.getUpdatesToday(startOfDay)

    suspend fun insertUpdate(update: HMUpdateEntity, isDateManuallyChanged: Boolean = false, expiresCommissioning: Long? = null) {
        withContext(Dispatchers.IO) {
            val existingUnit = unitDao.getUnitById(update.nomorUnit)
            
            // If update.sektor is blank, preserve existingUnit's sector
            val sectorToUse = if (update.sektor.isNotBlank()) update.sektor else (existingUnit?.lastSektor ?: "Others")
            val statusToUse = if (update.statusUnit.isNotBlank()) update.statusUnit else (existingUnit?.statusUnit ?: "ON HIRE")
            val conMonToUse = if (update.conMonData.isNotBlank()) update.conMonData else (existingUnit?.conMonData ?: "")

            val finalUpdate = update.copy(sektor = sectorToUse, statusUnit = statusToUse, conMonData = conMonToUse)

            // Delete previous updates for this unit
            hmUpdateDao.deleteHMUpdatesForUnit(finalUpdate.nomorUnit)

            // Save update
            hmUpdateDao.insertUpdate(finalUpdate)

            // Also update the Unit master record to match the update
            val expiresComm = expiresCommissioning ?: (existingUnit?.expiresCommissioning ?: 0L)
            val updatedUnit = UnitEntity(
                nomorUnit = finalUpdate.nomorUnit,
                lastUpdated = finalUpdate.timestamp,
                lastHM = finalUpdate.hoursMeter,
                lastSektor = finalUpdate.sektor,
                lastArea = finalUpdate.area,
                expiresCommissioning = expiresComm,
                lastNotes = finalUpdate.notes,
                statusUnit = finalUpdate.statusUnit,
                conMonData = finalUpdate.conMonData
            )
            unitDao.insertUnit(updatedUnit)
        }
    }

    suspend fun updateUnitStatus(nomorUnit: String, newStatus: String) {
        withContext(Dispatchers.IO) {
            val existingUnit = unitDao.getUnitById(nomorUnit)
            if (existingUnit != null) {
                unitDao.insertUnit(existingUnit.copy(statusUnit = newStatus))
            }
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
                      "nomorUnit": "$nomorUnit",
                      "nomor_unit": "$nomorUnit",
                      "unit": "$nomorUnit",
                      "cn_unit": "$nomorUnit"
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

    suspend fun editUnitOnServer(
        webAppUrl: String,
        oldNomorUnit: String,
        newNomorUnit: String,
        expiresCommissioning: Long,
        hoursMeter: Int = 0,
        sektor: String = "",
        area: String = "",
        notes: String = ""
    ): Boolean {
        return withContext(Dispatchers.IO) {
            if (webAppUrl.isEmpty() || !webAppUrl.startsWith("http")) return@withContext false
            try {
                val formattedExpires = if (expiresCommissioning > 0L) {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(expiresCommissioning))
                } else {
                    ""
                }
                val json = """
                    {
                      "action": "edit",
                      "oldNomorUnit": "$oldNomorUnit",
                      "newNomorUnit": "$newNomorUnit",
                      "nomorUnit": "$newNomorUnit",
                      "hoursMeter": $hoursMeter,
                      "sektor": "$sektor",
                      "area": "$area",
                      "notes": "$notes",
                      "expiresCommissioning": "$formattedExpires"
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
                Log.e("AppRepository", "Error editing unit on server", e)
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

    suspend fun updateUnit(oldNomorUnit: String, newNomorUnit: String, expiresCommissioning: Long) {
        withContext(Dispatchers.IO) {
            val existing = unitDao.getUnitById(oldNomorUnit)
            if (existing != null) {
                unitDao.deleteUnit(oldNomorUnit)
                unitDao.insertUnit(existing.copy(nomorUnit = newNomorUnit, expiresCommissioning = expiresCommissioning))
                hmUpdateDao.updateHMUpdatesUnitName(oldNomorUnit, newNomorUnit)
            } else {
                unitDao.insertUnit(UnitEntity(nomorUnit = newNomorUnit, expiresCommissioning = expiresCommissioning))
            }
        }
    }

    suspend fun syncPendingUpdates(webAppUrl: String): Pair<Int, String> {
        return withContext(Dispatchers.IO) {
            if (webAppUrl.isEmpty() || !webAppUrl.startsWith("http")) {
                return@withContext Pair(0, "URL Web App belum dikonfigurasi di pengaturan.")
            }

            val unsynced = hmUpdateDao.getUnsyncedUpdates().filter { it.nomorUnit.isNotBlank() }
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
                        val escapedNotes = update.notes.replace("\"", "\\\"")

                        val unitObj = unitDao.getUnitById(update.nomorUnit)
                        val expiresComm = unitObj?.expiresCommissioning ?: 0L
                        val formattedExpires = if (expiresComm > 0L) {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(expiresComm))
                        } else {
                            ""
                        }

                        val statusUnit = if (update.statusUnit.isNotBlank()) update.statusUnit else (unitObj?.statusUnit ?: "ON HIRE")
                        val conMonData = if (update.conMonData.isNotBlank()) update.conMonData else (unitObj?.conMonData ?: "")
                        val escapedConMon = conMonData.replace("\"", "\\\"")

                        val json = """
                            {
                              "timestamp": "$formattedTime",
                              "tanggal": "$formattedTime",
                              "tanggal_update": "$formattedTime",
                              "Tanggal_Waktu": "$formattedTime",
                              "Tanggal & Waktu": "$formattedTime",
                              "email": "$escapedEmail",
                              "nomorUnit": "$escapedUnit",
                              "nomor_unit": "$escapedUnit",
                              "unit": "$escapedUnit",
                              "cn_unit": "$escapedUnit",
                              "hoursMeter": ${update.hoursMeter},
                              "sektor": "$escapedSektor",
                              "area": "$escapedArea",
                              "notes": "$escapedNotes",
                              "catatan": "$escapedNotes",
                              "keterangan": "$escapedNotes",
                              "expiresCommissioning": "$formattedExpires",
                              "exp_commissioning": "$formattedExpires",
                              "expires_commissioning": "$formattedExpires",
                              "statusUnit": "$statusUnit",
                              "status_unit": "$statusUnit",
                              "STATUS UNIT": "$statusUnit",
                              "conMonData": "$escapedConMon",
                              "conmon_data": "$escapedConMon",
                              "conmon": "$escapedConMon",
                              "conmon_unit": "$escapedConMon",
                              "ConMon": "$escapedConMon",
                              "ConMon Unit": "$escapedConMon",
                              "Condition Monitoring": "$escapedConMon"
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
                val downloadUrl = if (webAppUrl.contains("?")) {
                    if (!webAppUrl.contains("action=")) "$webAppUrl&action=get_data" else webAppUrl
                } else {
                    "$webAppUrl?action=get_data"
                }
                val fetchRequest = Request.Builder()
                    .url(downloadUrl)
                    .get()
                    .build()

                okHttpClient.newCall(fetchRequest).execute().use { response ->
                    if (response.isSuccessful || response.code == 200) {
                        val responseBody = response.body?.string() ?: ""
                        val trimmedBody = responseBody.trim()
                        
                        val jsonArray = if (trimmedBody.startsWith("[")) {
                            org.json.JSONArray(trimmedBody)
                        } else if (trimmedBody.startsWith("{")) {
                            val jsonObj = org.json.JSONObject(trimmedBody)
                            jsonObj.optJSONArray("data") ?: org.json.JSONArray()
                        } else {
                            null
                        }

                        if (jsonArray != null) {
                            if (jsonArray.length() > 0) {
                                // Fetch local updates before deleting to match and preserve correct timestamps
                                val localUpdates = hmUpdateDao.getAllUpdatesList()

                                // Keep local units, only clear previously synced updates to prevent piling up
                                hmUpdateDao.deleteOldSyncedUpdates(Long.MAX_VALUE)

                                val tempUpdatesList = mutableListOf<HMUpdateEntity>()
                                val unitCommissioningMap = mutableMapOf<String, Long>()
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val timestampStr = findTimestampValue(obj)
                                    val emailVal = findEmailValue(obj)
                                    val nomorUnitVal = findUnitValue(obj).trim().uppercase()
                                    val hoursMeterVal = findHoursMeterValue(obj)
                                    val sektorVal = findSektorValue(obj)
                                    val areaVal = findAreaValue(obj)
                                    val notesVal = findNotesValue(obj)
                                    val expiresCommVal = findExpiresCommissioningValue(obj)
                                    val statusVal = findStatusUnitValue(obj)
                                    val conMonVal = findConMonValue(obj)

                                    if (nomorUnitVal.isEmpty()) continue

                                    if (expiresCommVal > 0L) {
                                        unitCommissioningMap[nomorUnitVal] = expiresCommVal
                                    }

                                    var timeMillis = parseTimestamp(timestampStr)

                                    // Match against local updates to preserve the accurate user-selected timestamp
                                    val matchedLocalUpdate = localUpdates.find { localUpd ->
                                        localUpd.nomorUnit.equals(nomorUnitVal, ignoreCase = true) &&
                                        localUpd.hoursMeter == hoursMeterVal &&
                                        localUpd.sektor.equals(sektorVal, ignoreCase = true) &&
                                        localUpd.area.equals(areaVal, ignoreCase = true)
                                    }

                                    if (matchedLocalUpdate != null) {
                                        timeMillis = matchedLocalUpdate.timestamp
                                    }

                                    tempUpdatesList.add(
                                        HMUpdateEntity(
                                            timestamp = timeMillis,
                                            email = emailVal,
                                            nomorUnit = nomorUnitVal,
                                            hoursMeter = hoursMeterVal,
                                            sektor = sektorVal,
                                            area = areaVal,
                                            isSynced = true,
                                            notes = notesVal,
                                            statusUnit = statusVal,
                                            conMonData = conMonVal
                                        )
                                    )
                                }

                                val newList = mutableMapOf<String, HMUpdateEntity>()
                                val newUnits = mutableMapOf<String, UnitEntity>()

                                // Group by unit name to accurately rebuild current master state
                                val grouped = tempUpdatesList.groupBy { it.nomorUnit }
                                for ((nomorUnitVal, updates) in grouped) {
                                    // Sort chronologically ascending to reconstruct the state transitions
                                    val sortedUpdates = updates.sortedBy { it.timestamp }
                                    
                                    // The latest update record is simply the last chronologically
                                    val latestUpdate = sortedUpdates.last()
                                    newList[nomorUnitVal] = latestUpdate
                                    
                                    val existingUnit = unitDao.getUnitById(nomorUnitVal)
                                    val localUnsyncedUpdates = hmUpdateDao.getUnsyncedUpdates().filter { it.nomorUnit.equals(nomorUnitVal, ignoreCase = true) }
                                    val latestLocalUnsynced = localUnsyncedUpdates.maxByOrNull { it.timestamp }

                                    val expiresComm = unitCommissioningMap[nomorUnitVal] ?: existingUnit?.expiresCommissioning ?: 0L

                                    // Select the latest update from spreadsheet that actually contains HM > 0 and non-blank fields
                                    val latestHMUpdate = sortedUpdates.filter { it.hoursMeter > 0 }.maxByOrNull { it.timestamp }
                                    val latestSektorUpdate = sortedUpdates.filter { it.sektor.isNotBlank() }.maxByOrNull { it.timestamp }
                                    val latestAreaUpdate = sortedUpdates.filter { it.area.isNotBlank() }.maxByOrNull { it.timestamp }
                                    val latestNotesUpdate = sortedUpdates.filter { it.notes.isNotBlank() }.maxByOrNull { it.timestamp }
                                    val latestConMonUpdate = sortedUpdates.filter { it.conMonData.isNotBlank() }.maxByOrNull { it.timestamp }

                                    // HM calculation: preserve local if local timestamp >= spreadsheet timestamp or unsynced
                                    val sheetHM = latestHMUpdate?.hoursMeter ?: 0
                                    val sheetHMTimestamp = latestHMUpdate?.timestamp ?: 0L

                                    val localHM = latestLocalUnsynced?.hoursMeter ?: existingUnit?.lastHM ?: 0
                                    val localHMTimestamp = latestLocalUnsynced?.timestamp ?: existingUnit?.lastUpdated ?: 0L

                                    val finalHM = if (localHMTimestamp >= sheetHMTimestamp && localHM > 0) {
                                        localHM
                                    } else if (sheetHM > 0) {
                                        sheetHM
                                    } else {
                                        localHM
                                    }

                                    // Sektor calculation
                                    val sheetSektor = latestSektorUpdate?.sektor ?: ""
                                    val sheetSektorTimestamp = latestSektorUpdate?.timestamp ?: 0L

                                    val localSektor = if (latestLocalUnsynced?.sektor?.isNotBlank() == true) latestLocalUnsynced.sektor else (existingUnit?.lastSektor ?: "")
                                    val localSektorTimestamp = if (latestLocalUnsynced?.sektor?.isNotBlank() == true) latestLocalUnsynced.timestamp else (existingUnit?.lastUpdated ?: 0L)

                                    val finalSektor = if (localSektorTimestamp >= sheetSektorTimestamp && localSektor.isNotBlank()) {
                                        localSektor
                                    } else if (sheetSektor.isNotBlank()) {
                                        sheetSektor
                                    } else {
                                        localSektor
                                    }

                                    // Area calculation
                                    val sheetArea = latestAreaUpdate?.area ?: ""
                                    val sheetAreaTimestamp = latestAreaUpdate?.timestamp ?: 0L

                                    val localArea = if (latestLocalUnsynced?.area?.isNotBlank() == true) latestLocalUnsynced.area else (existingUnit?.lastArea ?: "")
                                    val localAreaTimestamp = if (latestLocalUnsynced?.area?.isNotBlank() == true) latestLocalUnsynced.timestamp else (existingUnit?.lastUpdated ?: 0L)

                                    val finalArea = if (localAreaTimestamp >= sheetAreaTimestamp && localArea.isNotBlank()) {
                                        localArea
                                    } else if (sheetArea.isNotBlank()) {
                                        sheetArea
                                    } else {
                                        localArea
                                    }

                                    // Notes calculation
                                    val sheetNotes = latestNotesUpdate?.notes ?: ""
                                    val sheetNotesTimestamp = latestNotesUpdate?.timestamp ?: 0L

                                    val localNotes = if (latestLocalUnsynced?.notes?.isNotBlank() == true) latestLocalUnsynced.notes else (existingUnit?.lastNotes ?: "")
                                    val localNotesTimestamp = if (latestLocalUnsynced?.notes?.isNotBlank() == true) latestLocalUnsynced.timestamp else (existingUnit?.lastUpdated ?: 0L)

                                    val finalNotes = if (localNotesTimestamp >= sheetNotesTimestamp && localNotes.isNotBlank()) {
                                        localNotes
                                    } else if (sheetNotes.isNotBlank()) {
                                        sheetNotes
                                    } else {
                                        localNotes
                                    }

                                    val finalLastUpdated = maxOf(latestUpdate.timestamp, existingUnit?.lastUpdated ?: 0L, latestLocalUnsynced?.timestamp ?: 0L)

                                    val finalStatusUnit = if (latestLocalUnsynced?.statusUnit?.isNotBlank() == true) latestLocalUnsynced.statusUnit else (latestUpdate.statusUnit.ifBlank { existingUnit?.statusUnit ?: "ON HIRE" })
                                    val sheetConMon = latestConMonUpdate?.conMonData ?: ""
                                    val sheetConMonTimestamp = latestConMonUpdate?.timestamp ?: 0L

                                    val localConMon = if (latestLocalUnsynced?.conMonData?.isNotBlank() == true) latestLocalUnsynced.conMonData else (existingUnit?.conMonData ?: "")
                                    val localConMonTimestamp = if (latestLocalUnsynced?.conMonData?.isNotBlank() == true) latestLocalUnsynced.timestamp else (existingUnit?.lastUpdated ?: 0L)

                                    val finalConMonData = if (localConMonTimestamp >= sheetConMonTimestamp && localConMon.isNotBlank()) {
                                        localConMon
                                    } else if (sheetConMon.isNotBlank()) {
                                        sheetConMon
                                    } else {
                                        localConMon
                                    }

                                    newUnits[nomorUnitVal] = UnitEntity(
                                        nomorUnit = nomorUnitVal,
                                        lastUpdated = finalLastUpdated,
                                        lastHM = finalHM,
                                        lastSektor = finalSektor,
                                        lastArea = finalArea,
                                        expiresCommissioning = expiresComm,
                                        lastNotes = finalNotes,
                                        statusUnit = finalStatusUnit,
                                        conMonData = finalConMonData
                                    )
                                }

                                for (upd in newList.values) {
                                    hmUpdateDao.deleteHMUpdatesForUnit(upd.nomorUnit)
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

                                // Ensure database is fully deduplicated
                                deduplicateHMUpdates()

                                downloadSuccess = true
                                downloadMessage = "Berhasil mendownload ${jsonArray.length()} unit terbaru dari spreadsheet!"
                            } else {
                                downloadSuccess = true
                                downloadMessage = "Spreadsheet kosong, data lokal tetap dipertahankan."
                            }
                        } else {
                            if (trimmedBody.startsWith("<") || trimmedBody.contains("<!DOCTYPE", ignoreCase = true) || trimmedBody.contains("<html", ignoreCase = true)) {
                                downloadMessage = "Respon server berupa HTML (Halaman Web/Login). Pastikan Web App di-deploy dengan Akses: 'Siapa saja' (Anyone) dan Kode Google Apps Script mengembalikan JSON."
                            } else {
                                downloadMessage = "Format response dari server bukan JSON array. Isi respon: ${trimmedBody.take(120)}"
                            }
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

    private fun parseTimestamp(timestampStr: String): Long {
        val trimmed = timestampStr.trim()
        if (trimmed.isBlank()) return System.currentTimeMillis()
        
        // 1. Check if it is a spreadsheet serial number or Unix timestamp (Double)
        try {
            val doubleVal = trimmed.toDoubleOrNull()
            if (doubleVal != null) {
                if (doubleVal > 30000.0 && doubleVal < 60000.0) { // Google Sheets date serial number (e.g., 46205.41)
                    // Google Sheets epoch is 1899-12-30. Days between 1899-12-30 and 1970-01-01 is 25569.
                    val millis = ((doubleVal - 25569.0) * 24.0 * 3600.0 * 1000.0).toLong()
                    // Adjust for local timezone offset so it displays exactly the local time from the spreadsheet
                    val tz = java.util.TimeZone.getDefault()
                    val offset = tz.getOffset(millis)
                    return millis - offset
                } else if (doubleVal > 1000000000.0) { // Unix timestamp
                    return if (doubleVal < 9999999999.0) { // in seconds
                        (doubleVal * 1000.0).toLong()
                    } else { // in milliseconds
                        doubleVal.toLong()
                    }
                }
            }
        } catch (e: Exception) {
            // ignore and proceed to string parsing
        }

        // 2. Try parsing using Java 8 Time APIs (extremely robust for ISO-8601 formats)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                return java.time.Instant.parse(trimmed).toEpochMilli()
            } catch (e: Exception) {}
            try {
                return java.time.OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
            } catch (e: Exception) {}
            try {
                return java.time.ZonedDateTime.parse(trimmed).toInstant().toEpochMilli()
            } catch (e: Exception) {}
            try {
                return java.time.LocalDateTime.parse(trimmed).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) {}

            // Try explicit patterns for java.time (fast, robust)
            val patterns = listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm"
            )
            for (pat in patterns) {
                try {
                    val dtf = java.time.format.DateTimeFormatter.ofPattern(pat)
                    val ldt = java.time.LocalDateTime.parse(trimmed, dtf)
                    return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (e: Exception) {}
            }
        }
        
        // 3. Fallback to SimpleDateFormat with standard formats
        val formats = listOf(
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH.mm.ss",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH.mm.ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy hh:mm:ss a",
            "dd/MM/yyyy hh:mm:ss a",
            "d/M/yyyy H:mm:ss",
            "M/d/yyyy H:mm:ss",
            "d/M/yyyy HH:mm:ss",
            "M/d/yyyy HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy HH.mm",
            "MM/dd/yyyy HH:mm",
            "d/M/yyyy H:mm",
            "M/d/yyyy H:mm",
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "yyyy/MM/dd",
            "dd-MM-yyyy",
            "dd MMMM yyyy",
            "d MMMM yyyy",
            "dd MMM yyyy",
            "d MMM yyyy",
            "MMMM dd, yyyy",
            "MMM dd, yyyy",
            "d MMM yyyy HH:mm:ss",
            "dd MMM yyyy HH:mm:ss",
            "d MMMM yyyy HH:mm:ss",
            "dd MMMM yyyy HH:mm:ss",
            "d MMM yyyy HH.mm.ss",
            "dd MMM yyyy HH.mm.ss",
            "d MMMM yyyy HH.mm.ss",
            "dd MMMM yyyy HH.mm.ss",
            "d MMM yyyy, HH:mm",
            "dd MMM yyyy, HH:mm",
            "d MMMM yyyy, HH:mm",
            "dd MMMM yyyy, HH:mm",
            "d MMM yyyy, HH.mm",
            "dd MMM yyyy, HH.mm",
            "d MMMM yyyy, HH.mm",
            "dd MMMM yyyy, HH.mm",
            "EEE, d MMM yyyy HH:mm:ss",
            "EEEE, d MMMM yyyy HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH.mm",
            "yyyy-MM-dd HH.mm.ss",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd HH.mm",
            "yyyy/MM/dd HH.mm.ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy HH.mm"
        )
        
        val locales = listOf(Locale("id", "ID"), Locale.US, Locale.getDefault())
        for (format in formats) {
            for (locale in locales) {
                try {
                    val sdf = SimpleDateFormat(format, locale)
                    sdf.isLenient = true
                    val date = sdf.parse(trimmed)
                    if (date != null) {
                        return date.time
                    }
                } catch (e: Exception) {
                    // Try next format/locale
                }
            }
        }
        
        Log.w("AppRepository", "Failed to parse timestamp string: '$trimmed', defaulting to current time.")
        return System.currentTimeMillis()
    }

    private fun findTimestampValue(obj: org.json.JSONObject): String {
        // Flat prioritized list of possible date/time keys for maximum compatibility
        val preferredKeys = listOf(
            "Tanggal & Waktu",
            "tanggal & waktu",
            "Tanggal_Waktu",
            "tanggal_waktu",
            "tanggal waktu",
            "Tanggal dan Waktu",
            "tanggal dan waktu",
            "tanggal_update",
            "tanggal update",
            "terakhir_update",
            "terakhir update",
            "tanggal_pengecekan",
            "tanggal pengecekan",
            "pengecekan",
            "check_date",
            "check date",
            "tanggal",
            "waktu_update",
            "waktu update",
            "timestamp",
            "stempel waktu",
            "stempel_waktu",
            "date",
            "time",
            "waktu",
            "created_at",
            "created",
            "A", "a", "0", "col0", "col_0", "column0", "column_0"
        )
        for (key in preferredKeys) {
            val value = optStringCaseInsensitive(obj, key)
            if (value.isNotBlank()) {
                return value
            }
        }

        val iterator = obj.keys()
        val keyList = mutableListOf<String>()
        while (iterator.hasNext()) {
            keyList.add(iterator.next())
        }

        // Check for any key containing date/time keywords
        val keywords = listOf("tanggal", "time", "date", "waktu", "created", "stempel")
        for (key in keyList) {
            val lowerKey = key.lowercase()
            if (keywords.any { lowerKey.contains(it) }) {
                val value = obj.optString(key)
                if (value.isNotBlank()) return value
            }
        }

        if (keyList.isNotEmpty()) {
            return obj.optString(keyList.first())
        }
        return ""
    }

    private fun findUnitValue(obj: org.json.JSONObject): String {
        val explicitKeys = listOf(
            "Nomor Unit", "nomorUnit", "nomor_unit", "nomor unit", "unit", "no_unit", "no unit", "nomor",
            "Kode Unit", "kode_unit", "CN Unit", "cn_unit", "cn",
            "C", "c", "col2", "col_2", "column2", "column_2"
        )
        for (key in explicitKeys) {
            val value = optStringCaseInsensitive(obj, key)
            if (value.isNotBlank()) return value
        }
        
        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val lowerKey = key.lowercase()
            if ((lowerKey.contains("unit") || lowerKey.contains("nomor") || lowerKey.contains("kode") || lowerKey.contains("cn")) && !lowerKey.contains("status")) {
                val value = obj.optString(key)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    private fun findEmailValue(obj: org.json.JSONObject): String {
        val explicitKeys = listOf(
            "email", "mekanik", "nama", "name", "user", "pic", "PIC",
            "B", "b", "col1", "col_1", "column1", "column_1"
        )
        for (key in explicitKeys) {
            val value = optStringCaseInsensitive(obj, key)
            if (value.isNotBlank()) return value
        }
        
        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val lowerKey = key.lowercase()
            if (lowerKey.contains("email") || lowerKey.contains("mekanik") || lowerKey.contains("name") || lowerKey.contains("nama") || lowerKey.contains("pic")) {
                val value = obj.optString(key)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    private fun findHoursMeterValue(obj: org.json.JSONObject): Int {
        val explicitKeys = listOf(
            "hoursMeter", "hours_meter", "hours meter", "hm", "hoursmeter",
            "hours meter (hm)", "hours_meter_(hm)", "hours_meter_hm", "hours meter hm",
            "D", "d", "col3", "col_3", "column3", "column_3"
        )
        for (key in explicitKeys) {
            val value = optDoubleCaseInsensitive(obj, key)
            if (!value.isNaN() && value > 0.0) return Math.round(value).toInt()
        }
        
        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val lowerKey = key.lowercase()
            if (lowerKey.contains("hm") || lowerKey.contains("hours") || lowerKey.contains("meter")) {
                val value = obj.optDouble(key, 0.0)
                if (!value.isNaN() && value > 0.0) return Math.round(value).toInt()
            }
        }
        return 0
    }

    private fun findSektorValue(obj: org.json.JSONObject): String {
        val explicitKeys = listOf(
            "sektor", "sector", "lokasi", "location",
            "E", "e", "col4", "col_4", "column4", "column_4"
        )
        for (key in explicitKeys) {
            val value = optStringCaseInsensitive(obj, key)
            if (value.isNotBlank()) return value
        }
        
        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val lowerKey = key.lowercase()
            if (lowerKey.contains("sektor") || lowerKey.contains("sector")) {
                val value = obj.optString(key)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    private fun findAreaValue(obj: org.json.JSONObject): String {
        val explicitKeys = listOf(
            "area", "lokasi", "location", "detail_area", "detail area",
            "F", "f", "col5", "col_5", "column5", "column_5"
        )
        for (key in explicitKeys) {
            val value = optStringCaseInsensitive(obj, key)
            if (value.isNotBlank()) return value
        }
        
        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val lowerKey = key.lowercase()
            if (lowerKey.contains("area") || lowerKey.contains("detail")) {
                val value = obj.optString(key)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    private fun findNotesValue(obj: org.json.JSONObject): String {
        val explicitKeys = listOf(
            "notes", "catatan", "keterangan", "note", "G", "g", "col6", "col_6", "column6", "column_6"
        )
        for (key in explicitKeys) {
            val value = optStringCaseInsensitive(obj, key)
            if (value.isNotBlank()) return value
        }
        
        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val lowerKey = key.lowercase()
            if (lowerKey.contains("note") || lowerKey.contains("catat") || lowerKey.contains("ket")) {
                val value = obj.optString(key)
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    private fun optStringCaseInsensitive(obj: org.json.JSONObject, vararg keys: String): String {
        for (key in keys) {
            val normKey = key.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (obj.has(key)) {
                return obj.optString(key)
            }
            // Case-insensitive & normalized check
            val iterator = obj.keys()
            while (iterator.hasNext()) {
                val currentKey = iterator.next()
                val normCurrentKey = currentKey.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (normCurrentKey == normKey || currentKey.equals(key, ignoreCase = true)) {
                    return obj.optString(currentKey)
                }
            }
        }
        return ""
    }

    private fun parseCommissioningDate(dateStr: String): Long {
        val trimmed = dateStr.trim()
        if (trimmed.isBlank()) return 0L
        try {
            return parseTimestamp(trimmed)
        } catch (e: Exception) {
            return 0L
        }
    }

    private fun findExpiresCommissioningValue(obj: org.json.JSONObject): Long {
        val preferredKeys = listOf(
            "Commissioning Expires",
            "commissioning expires",
            "Exp Commissioning",
            "exp commissioning",
            "exp_commissioning",
            "expires_commissioning",
            "expiresCommissioning",
            "commissioning_expires",
            "commissioning",
            "expires",
            "exp"
        )
        for (key in preferredKeys) {
            val value = optStringCaseInsensitive(obj, key)
            if (value.isNotBlank()) {
                return parseCommissioningDate(value)
            }
        }
        return 0L
    }

    private fun optDoubleCaseInsensitive(obj: org.json.JSONObject, vararg keys: String): Double {
        for (key in keys) {
            val normKey = key.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (obj.has(key)) {
                return obj.optDouble(key, 0.0)
            }
            // Case-insensitive & normalized check
            val iterator = obj.keys()
            while (iterator.hasNext()) {
                val currentKey = iterator.next()
                val normCurrentKey = currentKey.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (normCurrentKey == normKey || currentKey.equals(key, ignoreCase = true)) {
                    val value = obj.optDouble(currentKey, 0.0)
                    if (!value.isNaN()) return value
                }
            }
        }
        return 0.0
    }

    private fun findStatusUnitValue(obj: org.json.JSONObject): String {
        val keys = listOf("statusUnit", "status_unit", "STATUS UNIT", "status", "Status", "H", "h", "col7")
        for (key in keys) {
            val v = optStringCaseInsensitive(obj, key).trim()
            if (v.equals("OFF HIRE", ignoreCase = true)) return "OFF HIRE"
            if (v.equals("ON HIRE", ignoreCase = true)) return "ON HIRE"
        }
        return "ON HIRE"
    }

    private fun findConMonValue(obj: org.json.JSONObject): String {
        val keys = listOf(
            "conMonData", "conmon_data", "conmon", "conMon", "ConMon Data", "conmon data",
            "ConMon", "ConMonData", "conmon_unit", "conmon unit", "ConMon Unit", "CONMON", "CONMON UNIT",
            "condition_monitoring", "condition monitoring", "Condition Monitoring",
            "I", "i", "col8", "col_8", "column8", "column_8"
        )
        for (key in keys) {
            val v = optStringCaseInsensitive(obj, key).trim()
            if (v.isNotBlank()) return v
        }

        val knownItems = listOf(
            "Battery", "Engine", "Bareshaft", "Wiring Harness", "Wet End", "Vacum Pump", "Poonton",
            "Fuel Tank", "Lamp", "Tower", "Cabin", "Bejana Tekan", "Compressor", "Kabel & Stang Welding"
        )
        val itemsArray = org.json.JSONArray()
        for (item in knownItems) {
            val valStr = optStringCaseInsensitive(obj, item).trim()
            if (valStr.isNotBlank()) {
                val itemObj = org.json.JSONObject()
                itemObj.put("name", item)
                
                var status = "GOOD"
                var note = ""
                val upperVal = valStr.uppercase()
                when {
                    upperVal.startsWith("BAD") -> {
                        status = "BAD"
                        note = valStr.substring(3).trimStart(' ', '-', ':', '(', ')', ',')
                    }
                    upperVal.startsWith("GOOD") -> {
                        status = "GOOD"
                        note = valStr.substring(4).trimStart(' ', '-', ':', '(', ')', ',')
                    }
                    upperVal.startsWith("NA") || upperVal.startsWith("N/A") -> {
                        status = "NA"
                        note = if (upperVal.startsWith("N/A")) valStr.substring(3).trimStart(' ', '-', ':', '(', ')', ',') else valStr.substring(2).trimStart(' ', '-', ':', '(', ')', ',')
                    }
                    upperVal.contains("BAD") -> status = "BAD"
                    upperVal.contains("NA") -> status = "NA"
                }
                itemObj.put("status", status)
                itemObj.put("notes", note)
                itemsArray.put(itemObj)
            }
        }
        if (itemsArray.length() > 0) {
            return itemsArray.toString()
        }

        val iterator = obj.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val lowerKey = key.lowercase()
            if (lowerKey.contains("conmon") || lowerKey.contains("monitoring") || lowerKey.contains("condition")) {
                val value = obj.optString(key).trim()
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }
}
