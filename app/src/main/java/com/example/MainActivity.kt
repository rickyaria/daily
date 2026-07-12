package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.HMUpdateEntity
import com.example.data.UnitEntity
import com.example.data.ParsedUnitUpdate
import com.example.ui.MainViewModel
import com.example.ui.AiParsingState
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
                val userEmail by viewModel.userEmail.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (userEmail == null) {
                            LoginScreen(viewModel = viewModel)
                        } else {
                            DashboardScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var nrpInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Banner Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.mining_site_banner_1783764318633),
                contentDescription = "Mining Banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Text(
                    text = "DAILY CHECK STATIC",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "SPEX PAMA ASMI",
                    color = Color(0xFFFACC15),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.spex_asmi_logo_1783817487808),
                    contentDescription = "Logo Aplikasi",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Login Karyawan",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Masuk menggunakan NRP dan nama lengkap Anda untuk merekam identitas mekanik pada setiap input.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = nrpInput,
                    onValueChange = { nrpInput = it; showError = false },
                    label = { Text("NRP Karyawan (Nomor Registrasi Pokok)") },
                    placeholder = { Text("Contoh: 34230037") },
                    leadingIcon = { Icon(Icons.Default.AccountBox, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it; showError = false },
                    label = { Text("Nama Lengkap Mekanik") },
                    placeholder = { Text("Ketik nama lengkap Anda") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (showError) {
                    Text(
                        text = "Harap isi NRP dan nama mekanik dengan benar!",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        textAlign = TextAlign.Start
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Custom Styled Sign-In Button
                Button(
                    onClick = {
                        if (nrpInput.isNotBlank() && nameInput.isNotBlank()) {
                            viewModel.login(nrpInput.trim(), nameInput.trim())
                        } else {
                            showError = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = "Login icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "MASUK APLIKASI",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAiUploadDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var activeUpdateUnit by remember { mutableStateOf<UnitEntity?>(null) }
    var activeEditUnit by remember { mutableStateOf<UnitEntity?>(null) }
    var activeDeleteUnit by remember { mutableStateOf<UnitEntity?>(null) }
    var showAddUnitDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()

    // Clear sync status using Toast
    LaunchedEffect(syncStatus) {
        syncStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSyncStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFFDDE3EA).copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Logo image
                Image(
                    painter = painterResource(id = R.drawable.spex_asmi_logo_1783817487808),
                    contentDescription = "Logo Aplikasi",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DAILY CHECK STATIC",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "SPEX PAMA ASMI",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }

                // Clean Premium Profile Indicator & Shortcut to Alat & AI tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedTab == 3) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                        .clickable { selectedTab = 3 }
                        .border(
                            width = 1.dp,
                            color = if (selectedTab == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF10B981), RoundedCornerShape(3.dp))
                            )
                        }
                        
                        Text(
                            text = getInitials(userName),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Custom Navigation Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFDDE3EA),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabs = listOf("Daftar", "Inaktif", "Rekap", "Alat & AI")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF566066)
                    )
                }
            }
        }

        // Active Tab Screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> UnitsTabScreen(
                    viewModel = viewModel,
                    onUpdateUnitClick = { activeUpdateUnit = it },
                    onEditUnitClick = { activeEditUnit = it },
                    onDeleteUnitClick = { activeDeleteUnit = it },
                    onAddNewUnitClick = { showAddUnitDialog = true }
                )
                1 -> MonitoringTabScreen(
                    viewModel = viewModel,
                    onUpdateUnitClick = { activeUpdateUnit = it },
                    onEditUnitClick = { activeEditUnit = it },
                    onDeleteUnitClick = { activeDeleteUnit = it }
                )
                2 -> ShiftReportTabScreen(
                    viewModel = viewModel
                )
                3 -> AlatAiTabScreen(
                    viewModel = viewModel
                )
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Add Unit Dialog
    if (showAddUnitDialog) {
        AddUnitDialog(
            viewModel = viewModel,
            onDismiss = { showAddUnitDialog = false }
        )
    }

    // Update Form Dialog
    activeUpdateUnit?.let { unit ->
        UpdateFormDialog(
            unit = unit,
            viewModel = viewModel,
            onDismiss = { activeUpdateUnit = null }
        )
    }

    // Edit Unit Dialog
    activeEditUnit?.let { unit ->
        EditUnitDialog(
            unit = unit,
            viewModel = viewModel,
            onDismiss = { activeEditUnit = null }
        )
    }

    // Delete Unit Dialog
    activeDeleteUnit?.let { unit ->
        DeleteUnitDialog(
            unit = unit,
            viewModel = viewModel,
            onDismiss = { activeDeleteUnit = null }
        )
    }

    // AI Upload Dialog
    if (showAiUploadDialog) {
        AiUploadDialog(
            viewModel = viewModel,
            onDismiss = { showAiUploadDialog = false }
        )
    }

    // Profile Dialog
    if (showProfileDialog) {
        ProfileDialog(
            viewModel = viewModel,
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Composable
fun UnitsTabScreen(
    viewModel: MainViewModel,
    onUpdateUnitClick: (UnitEntity) -> Unit,
    onEditUnitClick: (UnitEntity) -> Unit,
    onDeleteUnitClick: (UnitEntity) -> Unit,
    onAddNewUnitClick: () -> Unit
) {
    val allUnits by viewModel.allUnits.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val distinctAreas = remember(allUnits) {
        val areas = allUnits.map { it.lastArea }.filter { it.isNotBlank() }.distinct()
        listOf("Semua Area") + areas
    }
    var selectedAreaFilter by remember { mutableStateOf("Semua Area") }
    var sortByAgingDaily by remember { mutableStateOf(false) }
    var showAreaDropdown by remember { mutableStateOf(false) }

    // Multi-selection states
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedUnits by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }

    val filteredUnits = remember(allUnits, searchQuery, selectedAreaFilter, sortByAgingDaily) {
        var list = allUnits.filter {
            it.nomorUnit.contains(searchQuery, ignoreCase = true)
        }
        if (selectedAreaFilter != "Semua Area") {
            list = list.filter { it.lastArea.equals(selectedAreaFilter, ignoreCase = true) }
        }
        if (sortByAgingDaily) {
            // Sort by oldest lastUpdated first (aging). Never updated (0L) comes first as it is the oldest aging.
            list = list.sortedWith(compareBy { if (it.lastUpdated == 0L) 0L else it.lastUpdated })
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari Nomor Unit (misal: DT101)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = onAddNewUnitClick,
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Unit")
            }
        }

        // Horizontal filter bar (scrollable to prevent overflow)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dropdown Area Filter Box
            Box {
                Row(
                    modifier = Modifier
                        .background(
                            color = if (selectedAreaFilter != "Semua Area") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selectedAreaFilter != "Semua Area") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { showAreaDropdown = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = if (selectedAreaFilter != "Semua Area") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Area: ${if (selectedAreaFilter == "Semua Area") "Semua" else selectedAreaFilter}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedAreaFilter != "Semua Area") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = showAreaDropdown,
                    onDismissRequest = { showAreaDropdown = false }
                ) {
                    distinctAreas.forEach { area ->
                        DropdownMenuItem(
                            text = { Text(area, fontSize = 13.sp) },
                            onClick = {
                                selectedAreaFilter = area
                                showAreaDropdown = false
                            }
                        )
                    }
                }
            }

            // Aging Toggle Box
            Row(
                modifier = Modifier
                    .background(
                        color = if (sortByAgingDaily) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (sortByAgingDaily) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { sortByAgingDaily = !sortByAgingDaily }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = if (sortByAgingDaily) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Aging Terlama",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sortByAgingDaily) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            // Selection/Mark Mode Toggle Box
            Row(
                modifier = Modifier
                    .background(
                        color = if (isSelectionMode) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelectionMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        isSelectionMode = !isSelectionMode
                        if (!isSelectionMode) {
                            selectedUnits = emptySet()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isSelectionMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isSelectionMode) "Selesai Menandai" else "Tandai Unit",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelectionMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bulk Actions Bar
        if (isSelectionMode) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedUnits.size} unit ditandai",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                isSelectionMode = false
                                selectedUnits = emptySet()
                            }
                        ) {
                            Text("Batal", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                if (selectedUnits.isNotEmpty()) {
                                    showBulkDeleteConfirmDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = selectedUnits.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hapus Terpilih", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (filteredUnits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Unit tidak ditemukan.",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredUnits) { unit ->
                    UnitCard(
                        unit = unit,
                        onUpdateClick = {
                            if (isSelectionMode) {
                                val isChecked = !selectedUnits.contains(unit.nomorUnit)
                                if (isChecked) {
                                    selectedUnits = selectedUnits + unit.nomorUnit
                                } else {
                                    selectedUnits = selectedUnits - unit.nomorUnit
                                }
                            } else {
                                onUpdateUnitClick(unit)
                            }
                        },
                        onEditClick = { onEditUnitClick(unit) },
                        onDeleteClick = { onDeleteUnitClick(unit) },
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedUnits.contains(unit.nomorUnit),
                        onSelectedChange = { isChecked ->
                            if (isChecked) {
                                selectedUnits = selectedUnits + unit.nomorUnit
                            } else {
                                selectedUnits = selectedUnits - unit.nomorUnit
                            }
                        }
                    )
                }
            }
        }
    }

    if (showBulkDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmDialog = false },
            title = { Text("Hapus Unit Terpilih?") },
            text = { Text("Tindakan ini akan menghapus ${selectedUnits.size} unit yang ditandai beserta seluruh riwayat update HM dan lokasi unit tersebut secara permanen dari database local dan server spreadsheet.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMultipleUnits(selectedUnits.toList())
                        selectedUnits = emptySet()
                        isSelectionMode = false
                        showBulkDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Semua")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmDialog = false }) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun UnitCard(
    unit: UnitEntity,
    onUpdateClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {}
) {
    val isUpdatedToday = unit.lastUpdated >= getStartOfTodayTimestamp()
    val isOverdue = if (unit.lastUpdated == 0L) true else {
        val daysDiff = (System.currentTimeMillis() - unit.lastUpdated) / (1000 * 60 * 60 * 24L)
        daysDiff > 7
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUpdateClick)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFFDDE3EA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectedChange(it) },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
            // Top section: Name and Sektor tag with Options Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = unit.nomorUnit,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF191C1E)
                    )
                    Text(
                        text = detectUnitType(unit.nomorUnit),
                        fontSize = 12.sp,
                        color = Color(0xFF566066)
                    )
                }

                // Sektor tag and Three-dots Options Menu
                val tagText = if (unit.lastSektor.isEmpty()) "UMUM" else unit.lastSektor.uppercase()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE0F3F8), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tagText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF001F24)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu Opsi Unit",
                                tint = Color(0xFF566066)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("Pembaruan HM", fontSize = 13.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onUpdateClick()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("Edit Nama Unit", fontSize = 13.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onEditClick()
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                                text = { Text("Hapus Unit", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF1F4F8), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Grid columns: Hours Meter and Last Update
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hours Meter
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HOURS METER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF566066)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (unit.lastHM == 0.0) "-" else String.format(Locale.US, "%,.1f", unit.lastHM),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF191C1E)
                    )
                }

                // Last Update status
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TERAKHIR UPDATE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverdue) Color(0xFF93000A) else Color(0xFF566066)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    
                    val timeText = when {
                        unit.lastUpdated == 0L -> "Belum pernah"
                        isUpdatedToday -> "Hari ini"
                        else -> {
                            val daysDiff = (System.currentTimeMillis() - unit.lastUpdated) / (1000 * 60 * 60 * 24L)
                            if (daysDiff == 1L) "Kemarin" else "$daysDiff Hari Lalu"
                        }
                    }

                    Text(
                        text = timeText,
                        fontSize = 15.sp,
                        fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium,
                        color = if (isOverdue) Color(0xFF93000A) else Color(0xFF191C1E)
                    )
                }

                // Arrow indicator
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color(0xFF566066).copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
}

@Composable
fun EditUnitDialog(
    unit: UnitEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var unitNumberInput by remember { mutableStateOf(unit.nomorUnit) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Edit Nomor Unit",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ubah nama atau nomor seri unit ${unit.nomorUnit}. Riwayat update unit ini akan otomatis disesuaikan.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = unitNumberInput,
                    onValueChange = {
                        unitNumberInput = it
                        errorText = null
                    },
                    label = { Text("Nomor Unit Baru") },
                    placeholder = { Text("Contoh: DT-106") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorText != null,
                    shape = RoundedCornerShape(12.dp)
                )
                errorText?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (unitNumberInput.isBlank()) {
                                errorText = "Nomor unit tidak boleh kosong!"
                            } else {
                                viewModel.renameUnit(unit.nomorUnit, unitNumberInput.trim().uppercase())
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Simpan Perubahan")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteUnitDialog(
    unit: UnitEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Hapus Unit dari Fleet?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Apakah Anda yakin ingin menghapus unit ${unit.nomorUnit}? Semua riwayat pembaruan unit ini juga akan dihapus secara permanen.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            viewModel.deleteUnit(unit.nomorUnit)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Hapus Permanen")
                    }
                }
            }
        }
    }
}

@Composable
fun MonitoringTabScreen(
    viewModel: MainViewModel,
    onUpdateUnitClick: (UnitEntity) -> Unit,
    onEditUnitClick: (UnitEntity) -> Unit,
    onDeleteUnitClick: (UnitEntity) -> Unit
) {
    val allUnits by viewModel.allUnits.collectAsState()

    val overdueUnits = allUnits.filter { unit ->
        if (unit.lastUpdated == 0L) {
            true
        } else {
            val daysDiff = (System.currentTimeMillis() - unit.lastUpdated) / (1000 * 60 * 60 * 24L)
            daysDiff > 7
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Warning Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFDAD6)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF93000A).copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF93000A), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${overdueUnits.size} Unit Belum Update",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF410002)
                    )
                    Text(
                        text = "Data HM tidak diperbarui > 7 hari",
                        fontSize = 12.sp,
                        color = Color(0xFF690005)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (overdueUnits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Semua unit terupdate dengan baik! Mantap.",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(overdueUnits) { unit ->
                    UnitCard(
                        unit = unit,
                        onUpdateClick = { onUpdateUnitClick(unit) },
                        onEditClick = { onEditUnitClick(unit) },
                        onDeleteClick = { onDeleteUnitClick(unit) }
                    )
                }
            }
        }
    }
}

@Composable
fun ShiftReportTabScreen(viewModel: MainViewModel) {
    val allUpdates by viewModel.allUpdates.collectAsState()
    val context = LocalContext.current

    val todayStart = viewModel.getStartOfToday()
    val todayUpdates = allUpdates.filter { it.timestamp >= todayStart }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Shift Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFDDE3EA)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Laporan Akhir Shift Mekanik",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF191C1E)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Berikut daftar unit tambang yang berhasil diupdate hari ini. Silakan klik tombol di bawah untuk mengekspor rekapnya langsung ke WhatsApp grup perusahaan.",
                    fontSize = 12.sp,
                    color = Color(0xFF566066)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE0F3F8), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${todayUpdates.size} Unit Diupdate",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF001F24)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Shift: Hari Ini",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF191C1E)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Big WhatsApp Export button
        Button(
            onClick = {
                if (todayUpdates.isEmpty()) {
                    Toast.makeText(context, "Tidak ada data update untuk hari ini!", Toast.LENGTH_SHORT).show()
                } else {
                    val sb = java.lang.StringBuilder()
                    sb.append("*DAILY CHECK STATIC - LAPORAN SHIFT*\n")
                    sb.append("Tanggal: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}\n")
                    sb.append("Mekanik: ${viewModel.userName.value} (${viewModel.userEmail.value})\n")
                    sb.append("===================================\n")
                    todayUpdates.forEach { update ->
                        sb.append("Nomor Unit: *${update.nomorUnit}* | HM: *${update.hoursMeter}* | Lokasi: *${update.sektor}* - *${update.area}*\n")
                    }
                    sb.append("===================================\n")
                    sb.append("_Laporan dikirim otomatis via Aplikasi Android_")

                    try {
                        val whatsappUri = Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(sb.toString()))
                        val waIntent = Intent(Intent.ACTION_VIEW, whatsappUri)
                        context.startActivity(waIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Tidak dapat membuka WhatsApp: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "EKSPOR REKAP HARI INI",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "DAFTAR AKTIVITAS HARI INI",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (todayUpdates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Belum ada update unit hari ini.",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(todayUpdates) { update ->
                    HistoryItemCard(update = update)
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(update: HMUpdateEntity) {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = formatter.format(Date(update.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = timeStr,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unit: ${update.nomorUnit}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "HM: ${update.hoursMeter} | Lokasi: ${update.sektor} - ${update.area}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Sync Status Icon
            Icon(
                imageVector = if (update.isSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                contentDescription = if (update.isSynced) "Synced" else "Unsynced",
                tint = if (update.isSynced) Color(0xFF10B981) else Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateFormDialog(
    unit: UnitEntity,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val sektorOptions by viewModel.sektorOptions.collectAsState()
    val areaSuggestions by viewModel.areaSuggestions.collectAsState()

    var hmInput by remember { mutableStateOf(if (unit.lastHM == 0.0) "" else unit.lastHM.toString()) }
    var selectedSektor by remember(sektorOptions) { 
        mutableStateOf(if (unit.lastSektor.isNotEmpty() && unit.lastSektor in sektorOptions) unit.lastSektor else (sektorOptions.firstOrNull() ?: "Sektor A")) 
    }
    var areaInput by remember { mutableStateOf(unit.lastArea) }
    var hmError by remember { mutableStateOf<String?>(null) }
    var areaError by remember { mutableStateOf<String?>(null) }

    var expandedDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Update Unit: ${unit.nomorUnit}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Hours Meter
                Text(
                    text = "Hours Meter (HM) Unit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (unit.lastHM > 0.0) {
                    Text(
                        text = "HM Sebelumnya: ${unit.lastHM}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                OutlinedTextField(
                    value = hmInput,
                    onValueChange = {
                        hmInput = it
                        hmError = null
                    },
                    placeholder = { Text("Ketik angka HM baru...") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = hmError != null
                )
                hmError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Sektor Dropdown
                Text(
                    text = "Sektor",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = selectedSektor, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        sektorOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedSektor = option
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Area
                Text(
                    text = "Detail Area / Lokasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = areaInput,
                    onValueChange = {
                        areaInput = it
                        areaError = null
                    },
                    placeholder = { Text("Nama area detail lokasi unit...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = areaError != null
                )
                areaError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick suggestions for area
                Text(
                    text = "Rekomendasi Area Cepat:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    areaSuggestions.forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                .border(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clickable { areaInput = suggestion }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(text = suggestion, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val hmVal = hmInput.toDoubleOrNull()
                            if (hmVal == null) {
                                hmError = "Masukkan nilai HM berupa angka!"
                            } else if (hmVal <= 0) {
                                hmError = "HM harus lebih besar dari 0!"
                            } else if (unit.lastHM > 0.0 && hmVal < unit.lastHM) {
                                hmError = "HM baru tidak boleh lebih kecil dari HM sebelumnya (${unit.lastHM})!"
                            }

                            if (areaInput.isBlank()) {
                                areaError = "Harap masukkan detail area lokasi!"
                            }

                            if (hmError == null && areaError == null && hmVal != null) {
                                viewModel.addHMUpdate(
                                    nomorUnit = unit.nomorUnit,
                                    hoursMeter = hmVal,
                                    sektor = selectedSektor,
                                    area = areaInput.trim()
                                )
                                onDismiss()
                            }
                        },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Simpan Pembaruan")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val webAppUrl by viewModel.webAppUrl.collectAsState()
    val sektorOptions by viewModel.sektorOptions.collectAsState()
    val areaSuggestions by viewModel.areaSuggestions.collectAsState()

    var urlInput by remember { mutableStateOf(webAppUrl) }
    var sektorInput by remember { mutableStateOf(sektorOptions.joinToString(", ")) }
    var areaInput by remember { mutableStateOf(areaSuggestions.joinToString(", ")) }

    val spreadsheetLink = "https://docs.google.com/spreadsheets/d/1UBO5J2rYmfDLkUTsm-rtIm8-dRMAZQHSP0jsp2bg6EU/edit"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Konfigurasi Aplikasi",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sesuaikan URL database Google Sheets, daftar sektor kerja, serta rekomendasi wilayah operasional tambang.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Input URL Web App
                Text(
                    text = "URL Web App (Google Sheets Database)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Input Daftar Sektor (Comma-separated)
                Text(
                    text = "Daftar Sektor Operasional (Pisahkan dengan Koma)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = sektorInput,
                    onValueChange = { sektorInput = it },
                    placeholder = { Text("Contoh: Sektor A, Sektor B, Sektor C") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Input Daftar Rekomendasi Area (Comma-separated)
                Text(
                    text = "Rekomendasi Area Lokasi (Pisahkan dengan Koma)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = areaInput,
                    onValueChange = { areaInput = it },
                    placeholder = { Text("Contoh: Stockpile 1, Front Barat, Disposal") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Google Sheet Perusahaan Aktif:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = spreadsheetLink,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            // Copy or open sheet url
                        }
                        .padding(vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Cara Sinkronisasi Google Sheets:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. Buka spreadsheet perusahaan di browser.\n2. Klik Ekstensi -> Apps Script.\n3. Tempel kode script penambahan baris (tersedia di panduan).\n4. Deploy sebagai Web App (akses: Anyone).\n5. Copy URL-nya ke input di atas.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            viewModel.saveWebAppUrl(urlInput.trim())
                            viewModel.saveSektorOptions(sektorInput)
                            viewModel.saveAreaSuggestions(areaInput)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}

@Composable
fun AddUnitDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var unitNumberInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Tambah Unit Baru ke Fleet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tambahkan nomor seri unit baru (misal: DT-106, EX-204) ke dalam master database internal.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = unitNumberInput,
                    onValueChange = {
                        unitNumberInput = it
                        errorText = null
                    },
                    label = { Text("Nomor Unit") },
                    placeholder = { Text("Contoh: DT-106") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorText != null
                )
                errorText?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (unitNumberInput.isBlank()) {
                                errorText = "Nomor unit tidak boleh kosong!"
                            } else {
                                viewModel.addNewUnit(unitNumberInput)
                                onDismiss()
                            }
                        }
                    ) {
                        Text("Tambah")
                    }
                }
            }
        }
    }
}

fun getStartOfTodayTimestamp(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun getFormattedDateString(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(date)
}

fun getInitials(name: String?): String {
    if (name.isNullOrBlank()) return "MK"
    val cleanName = name.trim()
    val parts = cleanName.split("\\s+".toRegex())
    if (parts.size >= 2) {
        val firstChar = parts[0].take(1)
        val secondChar = parts[1].take(1)
        return (firstChar + secondChar).uppercase()
    }
    return cleanName.take(2).uppercase()
}

@Composable
fun AiUploadDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val aiState by viewModel.aiParsingState.collectAsState()
    var rawText by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = {
        viewModel.clearAiParsingState()
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Upload Laporan AI",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                when (val state = aiState) {
                    is AiParsingState.Idle -> {
                        var aiInstructionInput by remember { mutableStateOf("") }

                        Text(
                            text = "Tempel teks laporan harian dari Admin yang dibagikan (WhatsApp/Telegram/Email). AI akan mengurai nomor unit, nilai hours meter, sektor, dan area untuk diperbarui otomatis.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        OutlinedTextField(
                            value = rawText,
                            onValueChange = { rawText = it },
                            placeholder = {
                                Text(
                                    "Tempel pesan di sini...\nContoh:\n- DT101: HM 1435.2 di Sektor A, Front Barat\n- EX201: HM 5422.0 di Sektor B, Stockpile 2",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 6
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = aiInstructionInput,
                            onValueChange = { aiInstructionInput = it },
                            label = { Text("Petunjuk / Perintah Tambahan AI (Opsional)", fontSize = 11.sp) },
                            placeholder = { Text("Contoh: 'Hanya update unit TL' atau 'Ubah semua area ke Stockpile'...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Batal")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (rawText.isNotBlank()) {
                                        viewModel.parseDailyReportWithAI(rawText, aiInstructionInput)
                                    }
                                },
                                enabled = rawText.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Proses AI")
                            }
                        }
                    }
                    
                    is AiParsingState.Loading -> {
                        Spacer(modifier = Modifier.height(30.dp))
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Sedang Menganalisis Laporan...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Menghubungi Gemini AI untuk mengurai data unit...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                    
                    is AiParsingState.Success -> {
                        Text(
                            text = "Berhasil Mengurai Laporan!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Ditemukan ${state.parsedUpdates.size} pembaruan unit dari teks Anda. Harap verifikasi sebelum menyimpan:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Table Header
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Unit", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.5f), color = MaterialTheme.colorScheme.onSurface)
                                Text("HM", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.2f), color = MaterialTheme.colorScheme.onSurface)
                                Text("Sektor", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.5f), color = MaterialTheme.colorScheme.onSurface)
                                Text("Area", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.8f), color = MaterialTheme.colorScheme.onSurface)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            
                            state.parsedUpdates.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.nomorUnit, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), color = MaterialTheme.colorScheme.onSurface)
                                    Text(item.hoursMeter.toString(), fontSize = 12.sp, modifier = Modifier.weight(1.2f), color = MaterialTheme.colorScheme.onSurface)
                                    Text(item.sektor.ifBlank { "-" }, fontSize = 11.sp, modifier = Modifier.weight(1.5f), color = MaterialTheme.colorScheme.secondary)
                                    Text(item.area.ifBlank { "-" }, fontSize = 11.sp, modifier = Modifier.weight(1.8f), color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.clearAiParsingState() }) {
                                Text("Ubah Teks")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    viewModel.saveParsedAIUpdates(state.parsedUpdates)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Simpan (${state.parsedUpdates.size})")
                            }
                        }
                    }
                    
                    is AiParsingState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error Icon",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(vertical = 8.dp)
                        )
                        Text(
                            text = "Gagal Mengurai Laporan",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Batal")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { viewModel.clearAiParsingState() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Coba Lagi")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val currentNrp by viewModel.userEmail.collectAsState()
    val currentName by viewModel.userName.collectAsState()

    var nrpInput by remember { mutableStateOf(currentNrp ?: "") }
    var nameInput by remember { mutableStateOf(currentName ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Profil Mekanik",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Perbarui nomor registrasi pokok (NRP) atau nama lengkap Anda. Setiap data HM yang Anda masukkan akan otomatis merekam identitas terbaru Anda.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // NRP Input
                OutlinedTextField(
                    value = nrpInput,
                    onValueChange = {
                        nrpInput = it
                        errorText = null
                    },
                    label = { Text("NRP Karyawan") },
                    placeholder = { Text("Contoh: 34230037") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Name Input
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        errorText = null
                    },
                    label = { Text("Nama Lengkap Mekanik") },
                    placeholder = { Text("Ketik nama lengkap Anda") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                errorText?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (nrpInput.isBlank() || nameInput.isBlank()) {
                                errorText = "NRP dan Nama tidak boleh kosong!"
                            } else {
                                viewModel.login(nrpInput.trim(), nameInput.trim())
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Simpan Profil")
                    }
                }
            }
        }
    }
}

fun detectUnitType(nomorUnit: String): String {
    val clean = nomorUnit.trim().uppercase()
    if (clean.length < 2) return "Unit Tambang"
    val prefix = clean.take(2)
    return when (prefix) {
        "TL" -> "Towerlamp"
        "WP" -> "Pompa"
        "CM" -> "Compressor"
        "WS" -> "Mesin Las"
        "GS" -> "Genset"
        "AM" -> "Anfo Truck"
        "AX" -> "Steming Truck"
        "CB" -> "Crane Basket"
        "CN" -> "Tadano"
        "CT" -> "Crane Truck"
        "FL" -> "Forklift"
        "FM" -> "Jonder"
        "LB" -> "Low Boy"
        "ST" -> "Crane Truck"
        "FT" -> "Fuel Truck"
        "FR" -> "Fire Truck"
        "LO" -> "Lube Truck"
        "TW" -> "Water Truck"
        "DT" -> "Dump Truck"
        "EX" -> "Excavator"
        "DZ" -> "Dozer"
        "GD" -> "Grader"
        "LD" -> "Loader"
        else -> "Unit Tambang"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlatAiTabScreen(viewModel: MainViewModel) {
    // States from ViewModel
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState() // This is the NRP
    val webAppUrl by viewModel.webAppUrl.collectAsState()
    val sektorOptions by viewModel.sektorOptions.collectAsState()
    val areaSuggestions by viewModel.areaSuggestions.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val aiState by viewModel.aiParsingState.collectAsState()

    // Internal input states
    var nrpInput by remember { mutableStateOf(userEmail ?: "") }
    var nameInput by remember { mutableStateOf(userName ?: "") }
    var urlInput by remember { mutableStateOf(webAppUrl) }
    var sektorInput by remember { mutableStateOf(sektorOptions.joinToString(", ")) }
    var areaInput by remember { mutableStateOf(areaSuggestions.joinToString(", ")) }
    var rawAiText by remember { mutableStateOf("") }
    var aiInstructionInput by remember { mutableStateOf("") }

    var profileError by remember { mutableStateOf<String?>(null) }
    var profileSuccess by remember { mutableStateOf<String?>(null) }
    var settingsSuccess by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // CARD 1: PROFIL MEKANIK AKTIF
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Profil Mekanik",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nrpInput,
                    onValueChange = { 
                        nrpInput = it
                        profileError = null
                        profileSuccess = null
                    },
                    label = { Text("NRP Karyawan", fontSize = 12.sp) },
                    placeholder = { Text("Contoh: 34230037") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { 
                        nameInput = it
                        profileError = null
                        profileSuccess = null
                    },
                    label = { Text("Nama Lengkap Mekanik", fontSize = 12.sp) },
                    placeholder = { Text("Ketik nama Anda...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                profileError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                profileSuccess?.let {
                    Text(text = it, color = Color(0xFF10B981), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (nrpInput.isBlank() || nameInput.isBlank()) {
                            profileError = "NRP dan Nama tidak boleh kosong!"
                        } else {
                            viewModel.login(nrpInput.trim(), nameInput.trim())
                            profileSuccess = "Profil berhasil diperbarui!"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Simpan Perubahan Profil", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // CARD 2: PENGURAIAN LAPORAN DENGAN AI (GEMINI)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Asisten AI (Urai Laporan Harian)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Tempel laporan dari WhatsApp Admin. AI (Gemini) akan otomatis mengekstrak nomor unit, HM, Sektor, dan Area ke database lokal.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = rawAiText,
                    onValueChange = { rawAiText = it },
                    placeholder = {
                        Text(
                            "Tempel pesan di sini...\nContoh:\n- DT101 HM 1435.2 di Sektor A\n- EX201 HM 5422.0 Sektor B, Front Barat",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = aiInstructionInput,
                    onValueChange = { aiInstructionInput = it },
                    label = { Text("Petunjuk Khusus AI (Opsional)", fontSize = 11.sp) },
                    placeholder = { Text("Contoh: 'Hanya update unit DT'", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                when (val state = aiState) {
                    is AiParsingState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("AI sedang membaca & mengurai laporan...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    is AiParsingState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(
                            onClick = { viewModel.clearAiParsingState() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                    is AiParsingState.Success -> {
                        Text(
                            text = "Ditemukan ${state.parsedUpdates.size} update unit dari teks!",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Show quick preview of parsed units
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                for (upd in state.parsedUpdates.take(5)) {
                                    Text(
                                        text = "• ${upd.nomorUnit}: HM ${upd.hoursMeter} (${upd.sektor} - ${upd.area})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (state.parsedUpdates.size > 5) {
                                    Text(text = "...dan ${state.parsedUpdates.size - 5} unit lainnya", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.clearAiParsingState() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Batal", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    viewModel.saveParsedAIUpdates(state.parsedUpdates)
                                    rawAiText = ""
                                    aiInstructionInput = ""
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("Simpan Semua", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                    is AiParsingState.Idle -> {
                        Button(
                            onClick = {
                                if (rawAiText.isNotBlank()) {
                                    viewModel.parseDailyReportWithAI(rawAiText, aiInstructionInput)
                                }
                            },
                            enabled = rawAiText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Urai & Tinjau Laporan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // CARD 3: SINKRONISASI & SPREADSHEET DATABASE
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sinkronisasi Google Sheets",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { 
                        urlInput = it
                        settingsSuccess = null
                    },
                    label = { Text("URL Web App Google Sheets") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Spreadsheet link info
                Text(
                    text = "Spreadsheet Tambang Aktif:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "https://docs.google.com/spreadsheets/d/1UBO5J2rYmfDLkUTsm-rtIm8-dRMAZQHSP0jsp2bg6EU/edit",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = sektorInput,
                    onValueChange = { 
                        sektorInput = it
                        settingsSuccess = null
                    },
                    label = { Text("Daftar Sektor (Pisahkan dengan koma)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = areaInput,
                    onValueChange = { 
                        areaInput = it
                        settingsSuccess = null
                    },
                    label = { Text("Daftar Rekomendasi Area (Pisahkan dengan koma)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                settingsSuccess?.let {
                    Text(text = it, color = Color(0xFF10B981), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveWebAppUrl(urlInput.trim())
                            viewModel.saveSektorOptions(sektorInput)
                            viewModel.saveAreaSuggestions(areaInput)
                            settingsSuccess = "Konfigurasi database berhasil disimpan!"
                        },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Simpan Konfigurasi", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.syncData() },
                        enabled = !isSyncing && urlInput.startsWith("http"),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // CARD 4: LOGOUT / KELUAR APLIKASI
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.logout() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Logout",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Keluar dari Aplikasi (Logout)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}
