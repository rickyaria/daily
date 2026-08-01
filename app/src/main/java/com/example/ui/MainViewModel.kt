package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.HMUpdateEntity
import com.example.data.UnitEntity
import com.example.data.GeminiService
import com.example.data.ParsedUnitUpdate
import com.example.worker.SyncWorker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface AiParsingState {
    object Idle : AiParsingState
    object Loading : AiParsingState
    data class Success(val parsedUpdates: List<ParsedUnitUpdate>) : AiParsingState
    data class Error(val message: String) : AiParsingState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository.getInstance(application)
    private val geminiService = GeminiService()
    private val sharedPrefs: SharedPreferences = application.getSharedPreferences("daily_check_prefs", Context.MODE_PRIVATE)

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()
    // Alias for NRP (Nomor Registrasi Pokok)
    val userNrp: StateFlow<String?> = _userEmail

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _webAppUrl = MutableStateFlow<String>("")
    val webAppUrl: StateFlow<String> = _webAppUrl.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _aiParsingState = MutableStateFlow<AiParsingState>(AiParsingState.Idle)
    val aiParsingState: StateFlow<AiParsingState> = _aiParsingState.asStateFlow()

    // Dynamic Sektors and Areas
    private val _sektorOptions = MutableStateFlow<List<String>>(listOf("Sektor 1", "Sektor 2", "Sektor 4", "Sektor 7", "Sektor 8", "Others"))
    val sektorOptions: StateFlow<List<String>> = _sektorOptions.asStateFlow()

    private val _areaSuggestions = MutableStateFlow<List<String>>(emptyList())
    val areaSuggestions: StateFlow<List<String>> = _areaSuggestions.asStateFlow()

    val allUnits: StateFlow<List<UnitEntity>> = repository.allUnits
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allUpdates: StateFlow<List<HMUpdateEntity>> = repository.allUpdates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private fun observeNetwork(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            trySend(true)
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            trySend(isConnected)
        } catch (e: Exception) {
            trySend(true)
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {}
        }
    }.distinctUntilChanged()

    init {
        // Load persisted values
        _userEmail.value = sharedPrefs.getString("user_nrp", null) ?: sharedPrefs.getString("user_email", null)
        _userName.value = sharedPrefs.getString("user_name", null)
        val savedUrl = sharedPrefs.getString("web_app_url", null)
        val defaultUrl = "https://script.google.com/macros/s/AKfycbyZ4An-7rYwXwYWbhKxQ3WBjRj7XQizl31g51eGlzNupK1Y6GsTuqikxTvJ1NnNua4B9A/exec"
        val oldDefaultUrl = "https://script.google.com/macros/s/AKfycbxyLJrFw09XQ9PnBlhpJ2xoXpxQoU-forBxVZRN51c4MZPUfYvQJJsvwiiDdLHHUDI9/exec"
        val activeUrl = if (savedUrl.isNullOrEmpty() || savedUrl == oldDefaultUrl) defaultUrl else savedUrl
        _webAppUrl.value = activeUrl
        if (savedUrl != activeUrl) {
            sharedPrefs.edit().putString("web_app_url", activeUrl).apply()
        }

        val savedSektors = sharedPrefs.getString("sektor_options", "Sektor 1, Sektor 2, Sektor 4, Sektor 7, Sektor 8, Others") ?: "Sektor 1, Sektor 2, Sektor 4, Sektor 7, Sektor 8, Others"
        val cleanSektors = if (savedSektors == "Sektor A, Sektor B, Sektor C, Sektor D" || savedSektors == "Sektor 1, Sektor 2, Sektor 4, Sektor 7, Others") {
            "Sektor 1, Sektor 2, Sektor 4, Sektor 7, Sektor 8, Others"
        } else {
            savedSektors
        }
        _sektorOptions.value = cleanSektors.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val savedAreas = sharedPrefs.getString("area_suggestions", "") ?: ""
        val cleanAreas = if (savedAreas == "Front Barat, Stockpile 2, Front Timur, Disposal Utara, Crusher 1, Jalan Utama KM 5, Inpit Timur, Ramp Barat") {
            ""
        } else {
            savedAreas
        }
        _areaSuggestions.value = cleanAreas.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        viewModelScope.launch {
            repository.initializeDefaultUnitsIfEmpty()
            // Smooth UI startup, then sync from master spreadsheet
            kotlinx.coroutines.delay(200L)
            val url = _webAppUrl.value
            if (url.isNotEmpty() && url.startsWith("http")) {
                syncData()
            }
        }

        // Network state observer to auto-sync pending data when internet becomes available
        viewModelScope.launch {
            observeNetwork(application).collect { online ->
                val previousState = _isOnline.value
                _isOnline.value = online
                if (!online) {
                    _syncStatus.value = "Koneksi jaringan buruk/terputus. Sinkronisasi ditunda..."
                } else if (online && !previousState) {
                    val url = _webAppUrl.value
                    if (url.isNotEmpty() && url.startsWith("http")) {
                        _syncStatus.value = "Koneksi terhubung kembali. Melanjutkan sinkronisasi data..."
                        syncData()
                    }
                }
            }
        }

        // Automatic sync every 10 minutes
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10 * 60 * 1000L) // 10 minutes in ms
                val url = _webAppUrl.value
                if (url.isNotEmpty() && url.startsWith("http")) {
                    syncData()
                }
            }
        }
    }

    fun login(nrp: String, name: String) {
        _userEmail.value = nrp
        _userName.value = name
        sharedPrefs.edit()
            .putString("user_nrp", nrp)
            .remove("user_email")
            .putString("user_name", name)
            .apply()
    }

    fun logout() {
        _userEmail.value = null
        _userName.value = null
        sharedPrefs.edit()
            .remove("user_nrp")
            .remove("user_email")
            .remove("user_name")
            .apply()
    }

    fun saveWebAppUrl(url: String) {
        _webAppUrl.value = url
        sharedPrefs.edit().putString("web_app_url", url).apply()
        // Immediately sync on save to download/upload data for a new user
        if (url.isNotEmpty() && url.startsWith("http")) {
            syncData()
        }
    }

    fun saveSektorOptions(sektorsString: String) {
        val list = sektorsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        _sektorOptions.value = list
        sharedPrefs.edit().putString("sektor_options", sektorsString).apply()
    }

    fun addSektor(newSektor: String) {
        val clean = newSektor.trim()
        if (clean.isEmpty()) return
        val current = _sektorOptions.value.toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            _sektorOptions.value = current
            sharedPrefs.edit().putString("sektor_options", current.joinToString(", ")).apply()
        }
    }

    fun updateSektor(oldSektor: String, newSektorName: String) {
        val cleanNew = newSektorName.trim()
        if (cleanNew.isEmpty()) return
        val current = _sektorOptions.value.toMutableList()
        val index = current.indexOf(oldSektor)
        if (index != -1) {
            current[index] = cleanNew
            _sektorOptions.value = current
            sharedPrefs.edit().putString("sektor_options", current.joinToString(", ")).apply()
        }
    }

    fun deleteSektor(sektorName: String) {
        val current = _sektorOptions.value.toMutableList()
        if (current.remove(sektorName)) {
            _sektorOptions.value = current
            sharedPrefs.edit().putString("sektor_options", current.joinToString(", ")).apply()
        }
    }

    fun saveAreaSuggestions(areasString: String) {
        val list = areasString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        _areaSuggestions.value = list
        sharedPrefs.edit().putString("area_suggestions", areasString).apply()
    }

    fun deleteUnit(nomorUnit: String) {
        viewModelScope.launch {
            repository.deleteUnit(nomorUnit)
            val url = _webAppUrl.value
            if (url.isNotEmpty() && url.startsWith("http")) {
                _isSyncing.value = true
                _syncStatus.value = "Menghapus unit dari spreadsheet..."
                repository.deleteUnitFromServer(url, nomorUnit)
                // Trigger full download refresh to confirm spreadsheet matches local DB
                val result = repository.syncPendingUpdates(url)
                _isSyncing.value = false
                _syncStatus.value = "Unit berhasil dihapus. " + result.second
            }
        }
    }

    fun deleteMultipleUnits(nomorUnits: List<String>) {
        if (nomorUnits.isEmpty()) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatus.value = "Menghapus ${nomorUnits.size} unit..."
            repository.deleteMultipleUnits(nomorUnits)
            val url = _webAppUrl.value
            if (url.isNotEmpty() && url.startsWith("http")) {
                _syncStatus.value = "Menghapus unit di spreadsheet..."
                nomorUnits.forEach { nomorUnit ->
                    repository.deleteUnitFromServer(url, nomorUnit)
                }
                // Refresh local data to match spreadsheet
                val result = repository.syncPendingUpdates(url)
                _isSyncing.value = false
                _syncStatus.value = "Berhasil menghapus ${nomorUnits.size} unit. " + result.second
            } else {
                _isSyncing.value = false
                _syncStatus.value = "Berhasil menghapus ${nomorUnits.size} unit secara lokal."
            }
        }
    }

    fun renameUnit(oldNomorUnit: String, newNomorUnit: String) {
        if (newNomorUnit.isBlank()) return
        val cleanNewName = newNomorUnit.filter { it.isLetterOrDigit() }.uppercase()
        viewModelScope.launch {
            repository.renameUnit(oldNomorUnit, cleanNewName)
        }
    }

    fun updateUnitStatus(nomorUnit: String, newStatus: String) {
        viewModelScope.launch {
            repository.updateUnitStatus(nomorUnit, newStatus)
            val url = _webAppUrl.value
            if (url.isNotEmpty() && url.startsWith("http")) {
                syncData()
            }
        }
    }

    fun addHMUpdate(
        nomorUnit: String,
        hoursMeter: Int,
        sektor: String,
        area: String,
        customTimestamp: Long? = null,
        isDateManuallyChanged: Boolean = false,
        notes: String = "",
        expiresCommissioning: Long? = null,
        statusUnit: String = "ON HIRE",
        conMonData: String = ""
    ) {
        val mechanicName = _userName.value ?: _userEmail.value ?: "Mekanik-UNKNOWN"
        val timestamp = customTimestamp ?: System.currentTimeMillis()
        val cleanUnit = nomorUnit.filter { it.isLetterOrDigit() }.uppercase()
        if (cleanUnit.isBlank()) return
        val update = HMUpdateEntity(
            timestamp = timestamp,
            email = mechanicName,
            nomorUnit = cleanUnit,
            hoursMeter = hoursMeter,
            sektor = sektor,
            area = area,
            isSynced = false,
            notes = notes,
            statusUnit = statusUnit,
            conMonData = conMonData
        )
        viewModelScope.launch {
            repository.insertUpdate(update, isDateManuallyChanged, expiresCommissioning)

            // Enqueue background WorkManager sync so data syncs even if app is closed/swiped away
            SyncWorker.enqueueOneTimeSync(getApplication())

            // Trigger immediate foreground sync if URL is set
            val url = _webAppUrl.value
            if (url.isNotEmpty() && url.startsWith("http")) {
                syncData()
            }
        }
    }

    fun addNewUnit(nomorUnit: String, expiresCommissioning: Long = 0) {
        if (nomorUnit.isBlank()) return
        val cleanName = nomorUnit.filter { it.isLetterOrDigit() }.uppercase()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatus.value = "Menambah unit baru..."
            repository.insertUnit(UnitEntity(nomorUnit = cleanName, expiresCommissioning = expiresCommissioning))
            val url = _webAppUrl.value
            if (url.isNotEmpty() && url.startsWith("http")) {
                _syncStatus.value = "Menambah unit di spreadsheet..."
                val success = repository.editUnitOnServer(url, "", cleanName, expiresCommissioning)
                if (success) {
                    val syncResult = repository.syncPendingUpdates(url)
                    _syncStatus.value = "Berhasil menambah unit & " + syncResult.second
                } else {
                    _syncStatus.value = "Berhasil menambah unit secara lokal, gagal sinkronisasi ke spreadsheet."
                }
            } else {
                _syncStatus.value = "Berhasil menambah unit secara lokal."
            }
            _isSyncing.value = false
        }
    }

    fun updateUnit(oldNomorUnit: String, newNomorUnit: String, expiresCommissioning: Long) {
        val cleanNewName = newNomorUnit.filter { it.isLetterOrDigit() }.uppercase()
        if (cleanNewName.isBlank()) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatus.value = "Mengubah data unit..."
            
            val existingUnit = repository.allUnits.first().find { it.nomorUnit.equals(oldNomorUnit, ignoreCase = true) }
            
            repository.updateUnit(oldNomorUnit, cleanNewName, expiresCommissioning)
            val url = _webAppUrl.value
            if (url.isNotEmpty() && url.startsWith("http")) {
                _syncStatus.value = "Mengubah data unit di spreadsheet..."
                val success = repository.editUnitOnServer(
                    webAppUrl = url,
                    oldNomorUnit = oldNomorUnit,
                    newNomorUnit = cleanNewName,
                    expiresCommissioning = expiresCommissioning,
                    hoursMeter = existingUnit?.lastHM ?: 0,
                    sektor = existingUnit?.lastSektor ?: "",
                    area = existingUnit?.lastArea ?: "",
                    notes = existingUnit?.lastNotes ?: ""
                )
                if (success) {
                    val syncResult = repository.syncPendingUpdates(url)
                    _syncStatus.value = "Berhasil mengubah unit & " + syncResult.second
                } else {
                    _syncStatus.value = "Berhasil mengubah secara lokal, namun gagal sinkronisasi ke spreadsheet."
                }
            } else {
                _syncStatus.value = "Berhasil mengubah unit secara lokal."
            }
            _isSyncing.value = false
        }
    }

    fun syncData() {
        // Enqueue WorkManager background sync as background fallback
        SyncWorker.enqueueOneTimeSync(getApplication())

        if (_isSyncing.value) return // Prevent concurrent sync runs
        val url = _webAppUrl.value
        if (url.isEmpty()) {
            _syncStatus.value = "Silakan atur URL Web App terlebih dahulu di Pengaturan."
            return
        }
        if (!_isOnline.value) {
            _syncStatus.value = "Koneksi terputus. Data tersimpan secara lokal & akan disinkronkan otomatis saat online."
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatus.value = "Menyinkronkan data..."
            val result = repository.syncPendingUpdates(url)
            _isSyncing.value = false
            _syncStatus.value = result.second
        }
    }

    fun clearSyncStatus() {
        _syncStatus.value = null
    }

    fun parseDailyReportWithAI(reportText: String, instructions: String = "") {
        if (reportText.isBlank()) return
        _aiParsingState.value = AiParsingState.Loading
        viewModelScope.launch {
            try {
                val parsed = geminiService.parseDailyReport(reportText, instructions)
                if (parsed.isEmpty()) {
                    _aiParsingState.value = AiParsingState.Error("Tidak menemukan data unit yang valid pada teks.")
                } else {
                    _aiParsingState.value = AiParsingState.Success(parsed)
                }
            } catch (e: Exception) {
                _aiParsingState.value = AiParsingState.Error(e.message ?: "Terjadi kesalahan tidak diketahui")
            }
        }
    }

    fun saveParsedAIUpdates(updates: List<ParsedUnitUpdate>, customTimestamp: Long? = null) {
        viewModelScope.launch {
            val mechanicName = _userName.value ?: _userEmail.value ?: "Mekanik-UNKNOWN"
            val timestamp = customTimestamp ?: System.currentTimeMillis()
            var savedCount = 0
            
            updates.forEach { update ->
                val cleanUnit = update.nomorUnit.filter { it.isLetterOrDigit() }.uppercase()
                if (cleanUnit.isNotBlank()) {
                    val entity = HMUpdateEntity(
                        timestamp = timestamp,
                        email = mechanicName,
                        nomorUnit = cleanUnit,
                        hoursMeter = update.hoursMeter,
                        sektor = update.sektor.ifBlank { "Others" },
                        area = update.area.ifBlank { "" },
                        isSynced = false,
                        notes = update.notes
                    )
                    repository.insertUpdate(entity)
                    savedCount++
                    
                    // Add area suggestion
                    val area = update.area.ifBlank { "" }
                    if (area.isNotBlank()) {
                        val currentList = _areaSuggestions.value.toMutableList()
                        val cleanArea = area.trim()
                        if (!currentList.any { it.equals(cleanArea, ignoreCase = true) }) {
                            currentList.add(0, cleanArea)
                            val limitedList = currentList.distinctBy { it.lowercase() }.take(15)
                            _areaSuggestions.value = limitedList
                            sharedPrefs.edit().putString("area_suggestions", limitedList.joinToString(",")).apply()
                        }
                    }
                }
            }
            
            _aiParsingState.value = AiParsingState.Idle
            _syncStatus.value = "Berhasil memperbarui $savedCount unit dari Laporan AI!"
            
            // Trigger auto-sync once after all insertions are complete
            val url = _webAppUrl.value
            if (url.isNotEmpty() && url.startsWith("http")) {
                syncData()
            }
        }
    }

    fun clearAiParsingState() {
        _aiParsingState.value = AiParsingState.Idle
    }

    fun getStartOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
