package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.HMUpdateEntity
import com.example.data.UnitEntity
import com.example.data.GeminiService
import com.example.data.ParsedUnitUpdate
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
    private val repository = AppRepository(application)
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
    private val _sektorOptions = MutableStateFlow<List<String>>(listOf("Sektor A", "Sektor B", "Sektor C", "Sektor D"))
    val sektorOptions: StateFlow<List<String>> = _sektorOptions.asStateFlow()

    private val _areaSuggestions = MutableStateFlow<List<String>>(listOf("Front Barat", "Stockpile 2", "Front Timur", "Disposal Utara", "Crusher 1", "Jalan Utama KM 5", "Inpit Timur", "Ramp Barat"))
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

    init {
        // Load persisted values
        _userEmail.value = sharedPrefs.getString("user_nrp", null) ?: sharedPrefs.getString("user_email", null)
        _userName.value = sharedPrefs.getString("user_name", null)
        _webAppUrl.value = sharedPrefs.getString("web_app_url", "") ?: ""

        val savedSektors = sharedPrefs.getString("sektor_options", "Sektor A, Sektor B, Sektor C, Sektor D") ?: "Sektor A, Sektor B, Sektor C, Sektor D"
        _sektorOptions.value = savedSektors.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val savedAreas = sharedPrefs.getString("area_suggestions", "Front Barat, Stockpile 2, Front Timur, Disposal Utara, Crusher 1, Jalan Utama KM 5, Inpit Timur, Ramp Barat") ?: "Front Barat, Stockpile 2, Front Timur, Disposal Utara, Crusher 1, Jalan Utama KM 5, Inpit Timur, Ramp Barat"
        _areaSuggestions.value = savedAreas.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        viewModelScope.launch {
            repository.initializeDefaultUnitsIfEmpty()
            // Wait slightly for smooth UI startup, then sync from master spreadsheet
            kotlinx.coroutines.delay(1000L)
            val url = _webAppUrl.value
            if (url.isNotEmpty() && url.startsWith("http")) {
                syncData()
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

    fun addHMUpdate(nomorUnit: String, hoursMeter: Double, sektor: String, area: String) {
        val mechanicName = _userName.value ?: _userEmail.value ?: "Mekanik-UNKNOWN"
        val timestamp = System.currentTimeMillis()
        val cleanUnit = nomorUnit.filter { it.isLetterOrDigit() }.uppercase()
        val update = HMUpdateEntity(
            timestamp = timestamp,
            email = mechanicName,
            nomorUnit = cleanUnit,
            hoursMeter = hoursMeter,
            sektor = sektor,
            area = area,
            isSynced = false
        )
        viewModelScope.launch {
            repository.insertUpdate(update)

            // Persist registered area as a suggestion for future usage
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

            // Trigger auto-sync if URL is set
            val url = _webAppUrl.value
            if (url.isNotEmpty() && url.startsWith("http")) {
                syncData()
            }
        }
    }

    fun addNewUnit(nomorUnit: String) {
        if (nomorUnit.isBlank()) return
        val cleanName = nomorUnit.filter { it.isLetterOrDigit() }.uppercase()
        viewModelScope.launch {
            repository.insertUnit(UnitEntity(nomorUnit = cleanName))
        }
    }

    fun syncData() {
        if (_isSyncing.value) return // Prevent concurrent sync runs
        val url = _webAppUrl.value
        if (url.isEmpty()) {
            _syncStatus.value = "Silakan atur URL Web App terlebih dahulu di Pengaturan."
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

    fun saveParsedAIUpdates(updates: List<ParsedUnitUpdate>) {
        viewModelScope.launch {
            val mechanicName = _userName.value ?: _userEmail.value ?: "Mekanik-UNKNOWN"
            val timestamp = System.currentTimeMillis()
            
            updates.forEach { update ->
                val cleanUnit = update.nomorUnit.filter { it.isLetterOrDigit() }.uppercase()
                val entity = HMUpdateEntity(
                    timestamp = timestamp,
                    email = mechanicName,
                    nomorUnit = cleanUnit,
                    hoursMeter = update.hoursMeter,
                    sektor = update.sektor.ifBlank { "Sektor A" },
                    area = update.area.ifBlank { "Front Barat" },
                    isSynced = false
                )
                repository.insertUpdate(entity)
                
                // Add area suggestion
                val area = update.area.ifBlank { "Front Barat" }
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
            
            _aiParsingState.value = AiParsingState.Idle
            _syncStatus.value = "Berhasil memperbarui ${updates.size} unit dari Laporan AI!"
            
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
