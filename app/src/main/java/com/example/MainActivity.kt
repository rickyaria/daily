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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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

val GOOGLE_APPS_SCRIPT_CODE = """
function doPost(e) {
  try {
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    var data = JSON.parse(e.postData.contents);
    
    // Ambil data dari JSON request
    var action = data.action || "";
    var timestamp = data.timestamp || data.tanggal || data.tanggal_update || new Date();
    var email = data.email || data.pic || data.mekanik || "";
    var nomorUnit = (data.nomorUnit || data.nomor_unit || data.unit || data.cn_unit || data.cn || "").toString().trim().toUpperCase();
    var hoursMeter = Math.round(parseFloat(data.hoursMeter)) || 0;
    var sektor = data.sektor || "";
    var area = data.area || "";
    var notes = data.notes || data.catatan || data.keterangan || "";
    var expiresCommissioning = data.expiresCommissioning || data.expCommissioning || data.expires_commissioning || data.exp_commissioning || "";
    var statusUnit = (data.statusUnit || data.status_unit || data.status || "ON HIRE").toString().trim().toUpperCase();
    var conMonData = data.conMonData || data.conmon_data || "";
    
    var cleanTargetUnit = cleanUnitCode(nomorUnit);

    // Pastikan header utama (kolom 1-10) terisi
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["Tanggal & Waktu", "PIC", "Nomor Unit", "Hours Meter (HM)", "Sektor", "Area", "Notes", "Exp Commissioning", "STATUS UNIT", "ConMon Data"]);
      sheet.getRange(1, 1, 1, 10).setFontWeight("bold").setBackground("#E2E8F0").setHorizontalAlignment("center");
    } else {
      if (sheet.getRange(1, 7).getValue().toString().trim() === "") sheet.getRange(1, 7).setValue("Notes");
      if (sheet.getRange(1, 8).getValue().toString().trim() === "") sheet.getRange(1, 8).setValue("Exp Commissioning");
      if (sheet.getRange(1, 9).getValue().toString().trim() === "") sheet.getRange(1, 9).setValue("STATUS UNIT");
      if (sheet.getRange(1, 10).getValue().toString().trim() === "") sheet.getRange(1, 10).setValue("ConMon Data");
    }
    
    var headers = sheet.getRange(1, 1, 1, Math.max(sheet.getLastColumn(), 10)).getValues()[0];
    
    var colTimestamp = 1, colEmail = 2, colUnit = 3, colHM = 4, colSektor = 5, colArea = 6, colNotes = 7, colExpiresCommissioning = 8, colStatusUnit = 9, colConMonData = 10;
    
    for (var i = 0; i < headers.length; i++) {
      var h = headers[i].toString().toLowerCase().trim();
      if (h.includes("tanggal") || h.includes("time") || h.includes("stempel") || h.includes("timestamp")) colTimestamp = i + 1;
      else if (h.includes("email") || h.includes("mekanik") || h.includes("nama") || h.includes("pic")) colEmail = i + 1;
      else if (h.includes("status")) colStatusUnit = i + 1;
      else if (h.includes("conmon")) colConMonData = i + 1;
      else if ((h.includes("unit") || h.includes("nomor") || h.includes("kode") || h.includes("cn")) && !h.includes("status")) colUnit = i + 1;
      else if (h.includes("hm") || h.includes("hours") || h.includes("meter")) colHM = i + 1;
      else if (h.includes("sektor") || h.includes("sector") || h.includes("lokasi")) colSektor = i + 1;
      else if (h.includes("area") || h.includes("detail")) colArea = i + 1;
      else if (h.includes("notes") || h.includes("catatan") || h.includes("keterangan")) colNotes = i + 1;
      else if (h.includes("expires") || h.includes("commissioning") || h.includes("exp")) colExpiresCommissioning = i + 1;
    }

    if (action === "delete") {
      var mainLastRow = getMainTableLastRow(sheet, colUnit);
      var deletedRows = 0;
      if (mainLastRow > 1 && cleanTargetUnit) {
        var unitValues = sheet.getRange(2, colUnit, mainLastRow - 1, 1).getValues();
        for (var r = unitValues.length - 1; r >= 0; r--) {
          if (cleanUnitCode(unitValues[r][0]) === cleanTargetUnit) {
            sheet.deleteRow(r + 2);
            deletedRows++;
          }
        }
      }
      cleanAndDeduplicateSheet(sheet, colUnit);
      updateLateChecksTable(sheet);
      return ContentService.createTextOutput(JSON.stringify({status: "success", action: "delete", deletedCount: deletedRows}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    // Jika nomor unit kosong pada operasi insert/update, tolak request
    if (!cleanTargetUnit) {
      return ContentService.createTextOutput(JSON.stringify({status: "error", message: "Nomor Unit tidak boleh kosong"})).setMimeType(ContentService.MimeType.JSON);
    }

    function applyStatusStyling(cellRange, stVal) {
      cellRange.setValue(stVal);
      if (stVal === "OFF HIRE") {
        cellRange.setBackground("#F8D7DA").setFontColor("#842029").setFontWeight("bold");
      } else {
        cellRange.setBackground("#D1E7DD").setFontColor("#0F5132").setFontWeight("bold");
      }
    }

    var mainLastRow = getMainTableLastRow(sheet, colUnit);
    var foundRow = -1;
    if (mainLastRow > 1) {
      var unitValues = sheet.getRange(2, colUnit, mainLastRow - 1, 1).getValues();
      for (var r = 0; r < unitValues.length; r++) {
        var existingClean = cleanUnitCode(unitValues[r][0]);
        if (existingClean !== "" && existingClean === cleanTargetUnit) {
          foundRow = r + 2;
          break;
        }
      }
    }
    
    if (foundRow !== -1) {
      sheet.getRange(foundRow, colTimestamp).setValue(timestamp);
      sheet.getRange(foundRow, colUnit).setValue(nomorUnit);
      if (email) sheet.getRange(foundRow, colEmail).setValue(email);
      if (hoursMeter > 0) sheet.getRange(foundRow, colHM).setValue(hoursMeter);
      if (sektor) sheet.getRange(foundRow, colSektor).setValue(sektor);
      if (area) sheet.getRange(foundRow, colArea).setValue(area);
      if (notes) sheet.getRange(foundRow, colNotes).setValue(notes);
      if (expiresCommissioning) sheet.getRange(foundRow, colExpiresCommissioning).setValue(expiresCommissioning);
      if (conMonData) sheet.getRange(foundRow, colConMonData).setValue(conMonData);
      applyStatusStyling(sheet.getRange(foundRow, colStatusUnit), statusUnit);
    } else {
      var maxCol = Math.max(colTimestamp, colEmail, colUnit, colHM, colSektor, colArea, colNotes, colExpiresCommissioning, colStatusUnit, colConMonData);
      var newRow = new Array(maxCol).fill("");
      newRow[colTimestamp - 1] = timestamp;
      newRow[colEmail - 1] = email;
      newRow[colUnit - 1] = nomorUnit;
      newRow[colHM - 1] = hoursMeter;
      newRow[colSektor - 1] = sektor;
      newRow[colArea - 1] = area;
      newRow[colNotes - 1] = notes;
      newRow[colExpiresCommissioning - 1] = expiresCommissioning;
      newRow[colStatusUnit - 1] = statusUnit;
      newRow[colConMonData - 1] = conMonData;

      var insertRow = mainLastRow + 1;
      sheet.getRange(insertRow, 1, 1, newRow.length).setValues([newRow]);
      applyStatusStyling(sheet.getRange(insertRow, colStatusUnit), statusUnit);
    }

    if (conMonData) {
      processConMonSheet(nomorUnit, sektor, email, conMonData, timestamp);
    }
    
    cleanAndDeduplicateSheet(sheet, colUnit);
    updateLateChecksTable(sheet);
    
    return ContentService.createTextOutput(JSON.stringify({status: "success", action: foundRow !== -1 ? "update" : "insert"}))
      .setMimeType(ContentService.MimeType.JSON);
  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({status: "error", message: err.message}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function cleanUnitCode(str) {
  if (!str) return "";
  return str.toString().replace(/[^A-Za-z0-9]/g, "").toUpperCase();
}

function getMainTableLastRow(sheet, colUnit) {
  colUnit = colUnit || 3;
  var maxRows = sheet.getMaxRows();
  if (maxRows === 0) return 0;
  var values = sheet.getRange(1, colUnit, maxRows, 1).getValues();
  for (var r = values.length - 1; r >= 0; r--) {
    if (values[r][0] !== null && values[r][0].toString().trim() !== "") {
      return r + 1;
    }
  }
  return 1;
}

function cleanAndDeduplicateSheet(sheet, colUnit) {
  try {
    colUnit = colUnit || 3;
    var lastRow = sheet.getLastRow();
    if (lastRow <= 1) return;
    
    var values = sheet.getRange(2, 1, lastRow - 1, Math.max(sheet.getLastColumn(), 9)).getValues();
    var seenUnits = {};
    var rowsToDelete = [];
    
    // Iterasi dari bawah ke atas untuk mendeteksi unit duplikat & baris unit kosong
    for (var r = values.length - 1; r >= 0; r--) {
      var rowNum = r + 2;
      var rawUnit = values[r][colUnit - 1];
      var cleanCode = cleanUnitCode(rawUnit);
      
      if (!cleanCode) {
        rowsToDelete.push(rowNum);
      } else {
        if (seenUnits[cleanCode]) {
          rowsToDelete.push(rowNum);
        } else {
          seenUnits[cleanCode] = true;
        }
      }
    }
    
    rowsToDelete.sort(function(a, b) { return b - a; });
    for (var i = 0; i < rowsToDelete.length; i++) {
      sheet.deleteRow(rowsToDelete[i]);
    }
  } catch(e) {
    Logger.log("Error cleanAndDeduplicateSheet: " + e.message);
  }
}

function processConMonSheet(nomorUnit, sektor, mecano, conMonJsonStr, timestamp) {
  try {
    var conMonObj = typeof conMonJsonStr === "object" ? conMonJsonStr : JSON.parse(conMonJsonStr);
    var unitType = conMonObj.unitType || "Pompa";
    var items = conMonObj.items || [];
    if (!items || items.length === 0) return;

    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var conMonSheet = ss.getSheetByName(unitType);
    if (!conMonSheet) {
      conMonSheet = ss.insertSheet(unitType);
    }

    if (conMonSheet.getLastRow() === 0) {
      var headerRow = ["Timestamp", "CN Unit", "Sektor", "Mekanik"];
      for (var i = 0; i < items.length; i++) {
        headerRow.push(items[i].name);
      }
      headerRow.push("Notes");
      conMonSheet.appendRow(headerRow);
      conMonSheet.getRange(1, 1, 1, headerRow.length).setFontWeight("bold").setBackground("#E2E8F0").setHorizontalAlignment("center");
    }

    var existingHeaders = conMonSheet.getRange(1, 1, 1, conMonSheet.getLastColumn()).getValues()[0];
    
    var mainLastRow = getMainTableLastRow(conMonSheet, 2);
    var targetRow = -1;
    if (mainLastRow > 1) {
      var unitCols = conMonSheet.getRange(2, 2, mainLastRow - 1, 1).getValues();
      for (var r = 0; r < unitCols.length; r++) {
        if (cleanUnitCode(unitCols[r][0]) === cleanUnitCode(nomorUnit)) {
          targetRow = r + 2;
          break;
        }
      }
    }

    if (targetRow === -1) {
      targetRow = mainLastRow + 1;
    }

    conMonSheet.getRange(targetRow, 1).setValue(timestamp);
    conMonSheet.getRange(targetRow, 2).setValue(nomorUnit);
    conMonSheet.getRange(targetRow, 3).setValue(sektor);
    conMonSheet.getRange(targetRow, 4).setValue(mecano);

    var itemNotesCombined = [];

    for (var i = 0; i < items.length; i++) {
      var itemName = items[i].name;
      var statusVal = (items[i].status || "GOOD").toUpperCase();
      var itemNote = items[i].notes || "";

      if (itemNote) {
        itemNotesCombined.push(itemName + ": " + itemNote);
      }

      var colIdx = -1;
      for (var c = 0; c < existingHeaders.length; c++) {
        if (existingHeaders[c].toString().trim().toLowerCase() === itemName.trim().toLowerCase()) {
          colIdx = c + 1;
          break;
        }
      }

      if (colIdx !== -1) {
        var cell = conMonSheet.getRange(targetRow, colIdx);
        cell.setValue(statusVal);
        if (statusVal === "GOOD") {
          cell.setBackground("#D1E7DD").setFontColor("#0F5132").setFontWeight("bold");
        } else if (statusVal === "BAD") {
          cell.setBackground("#F8D7DA").setFontColor("#842029").setFontWeight("bold");
        } else {
          cell.setBackground(null).setFontColor("#000000").setFontWeight("normal");
        }
      }
    }

    var notesColIdx = existingHeaders.length;
    if (itemNotesCombined.length > 0) {
      conMonSheet.getRange(targetRow, notesColIdx).setValue(itemNotesCombined.join("; "));
    }
  } catch(e) {
    Logger.log("Error processConMonSheet: " + e.message);
  }
}

function getConMonJsonFromSheets(ss, nomorUnit) {
  try {
    var unitTypes = ["Pompa", "Genset", "Tower Lamp", "Compressor", "Welding"];
    var cleanTarget = cleanUnitCode(nomorUnit);
    if (!cleanTarget) return "";
    
    for (var t = 0; t < unitTypes.length; t++) {
      var uType = unitTypes[t];
      var cSheet = ss.getSheetByName(uType);
      if (!cSheet || cSheet.getLastRow() <= 1) continue;
      
      var cLastRow = cSheet.getLastRow();
      var cLastCol = cSheet.getLastColumn();
      var cHeaders = cSheet.getRange(1, 1, 1, cLastCol).getValues()[0];
      var cData = cSheet.getRange(2, 1, cLastRow - 1, cLastCol).getValues();
      
      for (var r = cData.length - 1; r >= 0; r--) {
        var rowUnit = cleanUnitCode(cData[r][1]);
        if (rowUnit === cleanTarget) {
          var itemsArr = [];
          var notesColIdx = cHeaders.length - 1;
          
          for (var c = 4; c < notesColIdx; c++) {
            var itemName = cHeaders[c] ? cHeaders[c].toString().trim() : "";
            if (itemName) {
              var itemStatus = (cData[r][c] || "GOOD").toString().trim().toUpperCase();
              itemsArr.push({
                name: itemName,
                status: itemStatus,
                notes: ""
              });
            }
          }
          
          var combinedNotes = cData[r][notesColIdx] ? cData[r][notesColIdx].toString().trim() : "";
          if (combinedNotes && itemsArr.length > 0) {
            var noteParts = combinedNotes.split(";");
            for (var np = 0; np < noteParts.length; np++) {
              var nPart = noteParts[np].trim();
              if (nPart.indexOf(":") !== -1) {
                var colonIdx = nPart.indexOf(":");
                var k = nPart.substring(0, colonIdx).trim();
                var v = nPart.substring(colonIdx + 1).trim();
                for (var it = 0; it < itemsArr.length; it++) {
                  if (itemsArr[it].name.toLowerCase() === k.toLowerCase()) {
                    itemsArr[it].notes = v;
                  }
                }
              }
            }
          }
          
          return JSON.stringify({
            unitType: uType,
            items: itemsArr
          });
        }
      }
    }
  } catch(e) {
    Logger.log("Error getConMonJsonFromSheets: " + e.message);
  }
  return "";
}

function doGet() {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getActiveSheet();
    
    if (sheet.getRange(1, 10).getValue().toString().trim() === "") {
      sheet.getRange(1, 10).setValue("ConMon Data");
    }
    
    var colUnit = 3;
    var headers = sheet.getRange(1, 1, 1, Math.max(sheet.getLastColumn(), 10)).getValues()[0];
    for (var i = 0; i < headers.length; i++) {
      var h = headers[i].toString().toLowerCase().trim();
      if ((h.includes("unit") || h.includes("nomor") || h.includes("kode") || h.includes("cn")) && !h.includes("status")) {
        colUnit = i + 1;
        break;
      }
    }

    cleanAndDeduplicateSheet(sheet, colUnit);
    updateLateChecksTable(sheet);

    var lastRow = getMainTableLastRow(sheet, colUnit);
    var lastCol = Math.max(sheet.getLastColumn(), 10);
    
    if (lastRow < 2) {
      return ContentService.createTextOutput(JSON.stringify([])).setMimeType(ContentService.MimeType.JSON);
    }
    
    var dataRange = sheet.getRange(2, 1, lastRow - 1, lastCol).getValues();
    
    var result = [];
    for (var r = 0; r < dataRange.length; r++) {
      var row = dataRange[r];
      var obj = {};
      var hasData = false;
      for (var c = 0; c < headers.length; c++) {
        var key = headers[c] || "col_" + c;
        var value = row[c];
        if (value instanceof Date) {
          value = Utilities.formatDate(value, Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm:ss");
        }
        obj[key] = value;
        if (value !== "") hasData = true;
      }

      var existingConMon = obj["ConMon Data"] || obj["conMonData"] || obj["conmon_data"] || obj["conmon"] || "";
      var unitCode = obj[headers[colUnit - 1]] || "";
      if (!existingConMon && unitCode) {
        var fetchedConMon = getConMonJsonFromSheets(ss, unitCode);
        if (fetchedConMon) {
          obj["conMonData"] = fetchedConMon;
          obj["ConMon Data"] = fetchedConMon;
        }
      } else if (existingConMon) {
        obj["conMonData"] = existingConMon;
      }

      if (hasData) {
        result.push(obj);
      }
    }
    
    return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({error: err.message})).setMimeType(ContentService.MimeType.JSON);
  }
}

function updateLateChecksTable(sheet) {
  try {
    var colUnit = 3, colTimestamp = 1, colStatus = 9;
    var headers = sheet.getRange(1, 1, 1, Math.min(Math.max(sheet.getLastColumn(), 9), 9)).getValues()[0];
    
    for (var i = 0; i < headers.length; i++) {
      var h = headers[i].toString().toLowerCase().trim();
      if (h.includes("tanggal") || h.includes("time") || h.includes("stempel") || h.includes("timestamp")) colTimestamp = i + 1;
      else if (h.includes("status")) colStatus = i + 1;
      else if ((h.includes("unit") || h.includes("nomor") || h.includes("kode") || h.includes("cn")) && !h.includes("status")) colUnit = i + 1;
    }

    var mainLastRow = getMainTableLastRow(sheet, colUnit);
    var maxRows = Math.max(sheet.getMaxRows(), 100);
    
    // Clear Kolom L & M (Kolom 12 & 13)
    sheet.getRange(1, 12, maxRows, 2).clearContent().setBackground(null).setFontWeight("normal");
    
    // Set Header di Kolom L (12) dan M (13)
    sheet.getRange(1, 12).setValue("Kode Unit (>7 Hari)");
    sheet.getRange(1, 13).setValue("Hari Aging");
    sheet.getRange(1, 12, 1, 2).setFontWeight("bold").setBackground("#F1F5F9").setHorizontalAlignment("center");
    
    if (mainLastRow < 2) return;
    
    var unitValues = sheet.getRange(2, colUnit, mainLastRow - 1, 1).getValues();
    var tsValues = sheet.getRange(2, colTimestamp, mainLastRow - 1, 1).getValues();
    var statusValues = colStatus <= sheet.getLastColumn() ? sheet.getRange(2, colStatus, mainLastRow - 1, 1).getValues() : [];
    
    var now = new Date();
    var lateUnits = [];
    
    for (var r = 0; r < unitValues.length; r++) {
      var unitCode = unitValues[r][0].toString().trim().toUpperCase();
      var tsVal = tsValues[r][0];
      var stVal = statusValues.length > r ? statusValues[r][0].toString().trim().toUpperCase() : "ON HIRE";
      
      if (!unitCode || stVal === "OFF HIRE") continue;
      
      var tsDate = null;
      if (tsVal instanceof Date) {
        tsDate = tsVal;
      } else if (tsVal) {
        tsDate = new Date(tsVal);
      }
      
      if (tsDate && !isNaN(tsDate.getTime())) {
        var diffTime = now.getTime() - tsDate.getTime();
        var diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));
        
        if (diffDays > 7) {
          lateUnits.push({
            unit: unitCode,
            days: diffDays
          });
        }
      }
    }
    
    lateUnits.sort(function(a, b) {
      return b.days - a.days;
    });
    
    if (lateUnits.length > 0) {
      var outputValues = [];
      for (var i = 0; i < lateUnits.length; i++) {
        outputValues.push([lateUnits[i].unit, lateUnits[i].days]);
      }
      sheet.getRange(2, 12, outputValues.length, 2).setValues(outputValues);
      sheet.getRange(2, 12, outputValues.length, 2).setHorizontalAlignment("center");
    }
  } catch(e) {
    Logger.log("Gagal memperbarui tabel unit terlambat: " + e.message);
  }
}
""".trimIndent()

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

    Box(modifier = Modifier.fillMaxSize()) {
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

    // Version text in top-right corner
    Text(
        text = "v2.0.0",
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 16.dp, end = 16.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
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

    val allUpdates by viewModel.allUpdates.collectAsState()
    val unsyncedCount = remember(allUpdates) { allUpdates.count { !it.isSynced } }

    // Clear sync status silently without showing bottom Toast popup
    LaunchedEffect(syncStatus) {
        syncStatus?.let {
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

                // Google Sheets Sync Status Indicator Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isSyncing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                unsyncedCount > 0 -> Color(0xFFFFF7ED)
                                else -> Color(0xFFECFDF5)
                            }
                        )
                        .clickable { viewModel.syncData() }
                        .border(
                            width = 1.dp,
                            color = when {
                                isSyncing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                unsyncedCount > 0 -> Color(0xFFF97316).copy(alpha = 0.4f)
                                else -> Color(0xFF10B981).copy(alpha = 0.3f)
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Syncing...",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (unsyncedCount > 0) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Data Tertunda",
                                tint = Color(0xFFEA580C),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Tertunda ($unsyncedCount)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC2410C)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Data Tersinkron",
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Tersinkron",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }
            }
        }

        // Unsynced Alert Banner if data is pending sync
        if (unsyncedCount > 0 && !isSyncing) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .clickable { viewModel.syncData() },
                color = Color(0xFFFFF7ED),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFFDBA74))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = Color(0xFFC2410C),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Ada $unsyncedCount data tertunda belum tersinkron ke Google Sheets",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF9A3412)
                        )
                    }
                    Text(
                        text = "SINKRON",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEA580C)
                    )
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
            val tabs = listOf("Daftar", "List Aging", "Rekap", "Settings")
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
                3 -> SettingsTabScreen(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsTabScreen(
    viewModel: MainViewModel,
    onUpdateUnitClick: (UnitEntity) -> Unit,
    onEditUnitClick: (UnitEntity) -> Unit,
    onDeleteUnitClick: (UnitEntity) -> Unit,
    onAddNewUnitClick: () -> Unit
) {
    val allUnits by viewModel.allUnits.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val distinctSektors = remember(allUnits) {
        val sektors = allUnits.map { it.lastSektor }.filter { it.isNotBlank() }.distinct()
        listOf("Semua Sektor") + sektors
    }
    var selectedSektorFilter by remember { mutableStateOf("Semua Sektor") }
    var selectedTypeFilters by remember { mutableStateOf(setOf<String>()) }
    var sortByAgingDaily by remember { mutableStateOf(false) }
    var showSektorDropdown by remember { mutableStateOf(false) }

    val allTypes = remember(allUnits) {
        allUnits.map { detectUnitType(it.nomorUnit) }.distinct().sorted()
    }

    val filteredUnits = remember(allUnits, searchQuery, selectedSektorFilter, selectedTypeFilters, sortByAgingDaily) {
        var list = allUnits.filter {
            it.nomorUnit.contains(searchQuery, ignoreCase = true)
        }
        if (selectedSektorFilter != "Semua Sektor") {
            list = list.filter { it.lastSektor.equals(selectedSektorFilter, ignoreCase = true) }
        }
        if (selectedTypeFilters.isNotEmpty()) {
            list = list.filter { selectedTypeFilters.contains(detectUnitType(it.nomorUnit)) }
        }
        if (sortByAgingDaily) {
            // Sort by oldest lastUpdated first (aging). Never updated (0L) comes first as it is the oldest aging.
            list = list.sortedWith(compareBy { if (it.lastUpdated == 0L) 0L else it.lastUpdated })
        }
        list
    }

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = { viewModel.syncData() },
        modifier = Modifier.fillMaxSize()
    ) {
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
                placeholder = { Text("Cari Nomor Unit (misal: TL565)") },
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
            // Dropdown Sektor Filter Box
            Box {
                Row(
                    modifier = Modifier
                        .background(
                            color = if (selectedSektorFilter != "Semua Sektor") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selectedSektorFilter != "Semua Sektor") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { showSektorDropdown = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = if (selectedSektorFilter != "Semua Sektor") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sektor: ${if (selectedSektorFilter == "Semua Sektor") "Semua" else selectedSektorFilter}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedSektorFilter != "Semua Sektor") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = showSektorDropdown,
                    onDismissRequest = { showSektorDropdown = false }
                ) {
                    distinctSektors.forEach { sektor ->
                        DropdownMenuItem(
                            text = { Text(sektor, fontSize = 13.sp) },
                            onClick = {
                                selectedSektorFilter = sektor
                                showSektorDropdown = false
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

            // Multi-select Tipe Unit Chips
            allTypes.forEach { type ->
                val isSelected = selectedTypeFilters.contains(type)
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectedTypeFilters = if (isSelected) selectedTypeFilters - type else selectedTypeFilters + type
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = type,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                        onUpdateClick = { onUpdateUnitClick(unit) },
                        onEditClick = { onEditUnitClick(unit) },
                        onDeleteClick = { onDeleteUnitClick(unit) },
                        onStatusToggle = { newStatus ->
                            viewModel.updateUnitStatus(unit.nomorUnit, newStatus)
                        }
                    )
                }
            }
        }
    }
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
    onSelectedChange: (Boolean) -> Unit = {},
    onStatusToggle: ((String) -> Unit)? = null,
    cardBackgroundColor: Color? = null,
    cardBorderColor: Color? = null,
    statusBadgeText: String? = null,
    statusBadgeColor: Color? = null
) {
    val isUpdatedToday = unit.lastUpdated >= getStartOfTodayTimestamp()
    val daysDiff = if (unit.lastUpdated == 0L) 999L else (System.currentTimeMillis() - unit.lastUpdated) / (1000 * 60 * 60 * 24L)
    val isCriticalAge = unit.lastUpdated == 0L || daysDiff >= 7L
    val isWarningAge = !isCriticalAge && daysDiff >= 6L
    val isOffHire = unit.statusUnit.equals("OFF HIRE", ignoreCase = true)
    val statusBgColor = if (isOffHire) Color(0xFFEF4444) else Color(0xFF10B981)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUpdateClick)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackgroundColor ?: Color.White
        ),
        border = BorderStroke(1.dp, cardBorderColor ?: Color(0xFFDDE3EA)),
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusBgColor)
                            .clickable {
                                val nextStatus = if (isOffHire) "ON HIRE" else "OFF HIRE"
                                onStatusToggle?.invoke(nextStatus)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = unit.nomorUnit,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = detectUnitType(unit.nomorUnit),
                        fontSize = 12.sp,
                        color = Color(0xFF566066)
                    )
                    if (unit.expiresCommissioning > 0L) {
                        val displayFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }
                        val isExpired = unit.expiresCommissioning < System.currentTimeMillis()
                        val diffDays = (unit.expiresCommissioning - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)
                        val statusText = if (isExpired) {
                            "Commissioning Expired!"
                        } else if (diffDays <= 7) {
                            "Comm. Expiring in $diffDays days"
                        } else {
                            "Comm. Expires: ${displayFormat.format(Date(unit.expiresCommissioning))}"
                        }
                        val statusColor = if (isExpired) {
                            Color(0xFFBA1A1A)
                        } else if (diffDays <= 7) {
                            Color(0xFFE28B00)
                        } else {
                            Color(0xFF006874)
                        }
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Area tag, Status Badge, and Three-dots Options Menu
                val tagText = if (unit.lastArea.isEmpty()) "UMUM" else unit.lastArea.uppercase()

                Row(verticalAlignment = Alignment.CenterVertically) {

                    if (statusBadgeText != null && statusBadgeColor != null) {
                        Box(
                            modifier = Modifier
                                .background(statusBadgeColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .border(1.dp, statusBadgeColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusBadgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusBadgeColor
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE0F3F8), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
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
                        text = if (unit.lastHM == 0) "-" else unit.lastHM.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF191C1E)
                    )
                }

                // Last Update status
                Column(modifier = Modifier.weight(1f)) {
                    val updateTitleColor = when {
                        isCriticalAge -> Color(0xFF93000A)
                        isWarningAge -> Color(0xFFEA580C)
                        else -> Color(0xFF566066)
                    }
                    val updateTextColor = when {
                        isCriticalAge -> Color(0xFF93000A)
                        isWarningAge -> Color(0xFFEA580C)
                        else -> Color(0xFF191C1E)
                    }

                    Text(
                        text = "TERAKHIR UPDATE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = updateTitleColor
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    
                    val timeText = if (unit.lastUpdated == 0L) {
                        "Belum pernah"
                    } else {
                        getFormattedDateString(unit.lastUpdated)
                    }

                    Text(
                        text = timeText,
                        fontSize = 15.sp,
                        fontWeight = if (isCriticalAge || isWarningAge) FontWeight.Bold else FontWeight.Medium,
                        color = updateTextColor
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
                    text = "Edit Unit",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ubah nomor unit ${unit.nomorUnit}.",
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
                                viewModel.updateUnit(unit.nomorUnit, unitNumberInput.trim().uppercase(), unit.expiresCommissioning)
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
    val context = LocalContext.current

    // List Aging: ON HIRE units not updated for 6 or more days (or never updated: lastUpdated == 0L)
    // Sorted strictly starting from the unit not checked for the longest time (lastUpdated == 0L first, then oldest timestamps)
    val overdueUnits = remember(allUnits) {
        allUnits.filter { unit ->
            if (unit.statusUnit.equals("OFF HIRE", ignoreCase = true)) {
                false
            } else if (unit.lastUpdated == 0L) {
                true
            } else {
                val daysDiff = (System.currentTimeMillis() - unit.lastUpdated) / (1000 * 60 * 60 * 24L)
                daysDiff >= 6
            }
        }.sortedBy { it.lastUpdated }
    }

    // Multi-select Tipe Unit Filter
    var selectedTypeFilters by remember { mutableStateOf(setOf<String>()) }

    val allTypes = remember(overdueUnits) {
        overdueUnits.map { detectUnitType(it.nomorUnit) }.distinct().sorted()
    }

    val filteredOverdueUnits = remember(overdueUnits, selectedTypeFilters) {
        if (selectedTypeFilters.isEmpty()) {
            overdueUnits
        } else {
            overdueUnits.filter { unit ->
                selectedTypeFilters.contains(detectUnitType(unit.nomorUnit))
            }
        }
    }

    val criticalCount = remember(filteredOverdueUnits) {
        filteredOverdueUnits.count { unit ->
            if (unit.lastUpdated == 0L) true else {
                val daysDiff = (System.currentTimeMillis() - unit.lastUpdated) / (1000 * 60 * 60 * 24L)
                daysDiff >= 7
            }
        }
    }
    val warningCount = remember(filteredOverdueUnits) { filteredOverdueUnits.size - criticalCount }

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
            border = BorderStroke(1.dp, Color(0xFF93000A).copy(alpha = 0.2f))
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
                        text = "${filteredOverdueUnits.size} Unit Perlu Pengecekan Aging",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF410002)
                    )
                    Text(
                        text = "$criticalCount Unit >7 Hari (Merah) | $warningCount Unit 6-7 Hari (Orange)",
                        fontSize = 12.sp,
                        color = Color(0xFF690005)
                    )
                }
            }
        }

        // Multi-select Filter Bar for Tipe Unit
        if (overdueUnits.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isAllSelected = selectedTypeFilters.isEmpty()
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedTypeFilters = emptySet() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Semua Tipe (${overdueUnits.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAllSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                allTypes.forEach { type ->
                    val isSelected = selectedTypeFilters.contains(type)
                    val typeCount = overdueUnits.count { detectUnitType(it.nomorUnit) == type }
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                selectedTypeFilters = if (isSelected) {
                                    selectedTypeFilters - type
                                } else {
                                    selectedTypeFilters + type
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = "$type ($typeCount)",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        val criticalUnits = remember(filteredOverdueUnits) {
            filteredOverdueUnits.filter { unit ->
                if (unit.lastUpdated == 0L) true else {
                    val daysDiff = (System.currentTimeMillis() - unit.lastUpdated) / (1000 * 60 * 60 * 24L)
                    daysDiff >= 7L
                }
            }
        }

        if (criticalUnits.isNotEmpty()) {
            Button(
                onClick = {
                    val dateStr = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())
                    val sb = StringBuilder()
                    sb.append("*LIST UNIT AGING (>7 HARI)*\n")
                    sb.append("_Tanggal: ${dateStr}_\n\n")
                    sb.append("Total Unit Aging: ${criticalUnits.size} Unit\n\n")

                    criticalUnits.forEachIndexed { index, unit ->
                        val daysDiff = if (unit.lastUpdated == 0L) 999L else (System.currentTimeMillis() - unit.lastUpdated) / (1000 * 60 * 60 * 24L)
                        val timeText = if (unit.lastUpdated == 0L) "Belum Pernah" else "$daysDiff Hari Lalu"
                        val hmText = if (unit.lastHM == 0) "-" else "${unit.lastHM}"
                        val sektorText = if (unit.lastSektor.isBlank()) "Sektor 1" else unit.lastSektor
                        val areaText = if (unit.lastArea.isBlank()) "-" else unit.lastArea

                        sb.append("${index + 1}. *${unit.nomorUnit}* ($timeText)\n")
                        sb.append("   HM: $hmText\n")
                        sb.append("   Sektor: $sektorText - $areaText\n\n")
                    }

                    val message = sb.toString()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                        setPackage("com.whatsapp")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }, "Kirim List Aging via WhatsApp")
                        context.startActivity(chooser)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EKSPOR LIST AGING KE WHATSAPP",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredOverdueUnits.isEmpty()) {
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
                        text = if (overdueUnits.isEmpty()) "Semua unit ON HIRE terupdate dengan baik!" else "Tidak ada unit aging untuk filter ini.",
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
                items(filteredOverdueUnits) { unit ->
                    val daysDiff = if (unit.lastUpdated == 0L) 999L else (System.currentTimeMillis() - unit.lastUpdated) / (1000 * 60 * 60 * 24L)
                    val isCritical = unit.lastUpdated == 0L || daysDiff >= 7L

                    val cardBgColor = if (isCritical) Color(0xFFFEE2E2) else Color(0xFFFFEDD5)
                    val cardBorderColor = if (isCritical) Color(0xFFEF4444) else Color(0xFFF97316)

                    UnitCard(
                        unit = unit,
                        onUpdateClick = { onUpdateUnitClick(unit) },
                        onEditClick = { onEditUnitClick(unit) },
                        onDeleteClick = { onDeleteUnitClick(unit) },
                        onStatusToggle = { newStatus ->
                            viewModel.updateUnitStatus(unit.nomorUnit, newStatus)
                        },
                        cardBackgroundColor = cardBgColor,
                        cardBorderColor = cardBorderColor,
                        statusBadgeText = null,
                        statusBadgeColor = null
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
    val todayUpdates = remember(allUpdates) {
        allUpdates
            .filter { it.timestamp >= todayStart }
            .distinctBy { it.nomorUnit.trim().uppercase() }
    }

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
                    todayUpdates.forEachIndexed { index, update ->
                        sb.append("CN : ${update.nomorUnit}\n")
                        sb.append("HM : ${update.hoursMeter}\n")
                        
                        val rawSektor = update.sektor.trim()
                        val shortSektor = if (rawSektor.lowercase().startsWith("sektor")) {
                            rawSektor.replace(Regex("(?i)^sektor\\s*[- ]*"), "S")
                        } else {
                            rawSektor
                        }
                        
                        val locVal = if (shortSektor.isNotEmpty() && update.area.isNotEmpty()) {
                            "$shortSektor ${update.area}"
                        } else if (shortSektor.isNotEmpty()) {
                            shortSektor
                        } else {
                            update.area
                        }
                        
                        sb.append("Loc : $locVal\n")
                        sb.append("Notes : ${update.notes}")
                        if (index < todayUpdates.size - 1) {
                            sb.append("\n\n")
                        }
                    }
                    sb.append("\n===================================")

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

data class ConMonItemState(
    val name: String,
    val status: String = "GOOD",
    val notes: String = ""
)

fun getConMonItemsForType(type: String): List<String> {
    val normType = type.trim().uppercase()
    return when {
        normType.contains("POMPA") || normType == "WP" -> listOf("Battery", "Engine", "Bareshaft", "Wiring Harness", "Wet End", "Vacum Pump", "Poonton")
        normType.contains("GENSET") || normType == "GS" -> listOf("Fuel Tank", "Battery", "Engine", "Lamp", "Wiring Harness", "Tower")
        normType.contains("TOWER") || normType.contains("LAMP") || normType == "TL" -> listOf("Fuel Tank", "Battery", "Engine", "Lamp", "Wiring Harness", "Cabin", "Tower")
        normType.contains("COMPRESSOR") || normType.contains("KOMPRESOR") || normType == "CM" -> listOf("Fuel Tank", "Battery", "Engine", "Wiring Harness", "Bejana Tekan")
        normType.contains("LAS") || normType.contains("WELDING") || normType == "WS" -> listOf("Fuel Tank", "Battery", "Engine", "Wiring Harness", "Compressor", "Kabel & Stang Welding")
        else -> listOf("Fuel Tank", "Battery", "Engine", "Wiring Harness")
    }
}

fun parseConMonJson(jsonStr: String, unitType: String, fallbackNotes: String = ""): List<ConMonItemState> {
    val defaultItems = getConMonItemsForType(unitType)
    val parsedMap = mutableMapOf<String, Pair<String, String>>()
    var isJsonParsed = false

    if (jsonStr.isNotBlank()) {
        try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("[")) {
                val itemsArray = org.json.JSONArray(trimmed)
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val n = itemObj.optString("name")
                    val s = itemObj.optString("status", "GOOD")
                    val nt = itemObj.optString("notes", "")
                    if (n.isNotBlank()) {
                        parsedMap[n] = Pair(s, nt)
                    }
                }
                isJsonParsed = true
            } else {
                val obj = org.json.JSONObject(trimmed)
                val itemsArray = obj.optJSONArray("items")
                if (itemsArray != null && itemsArray.length() > 0) {
                    for (i in 0 until itemsArray.length()) {
                        val itemObj = itemsArray.getJSONObject(i)
                        val n = itemObj.optString("name")
                        val s = itemObj.optString("status", "GOOD")
                        val nt = itemObj.optString("notes", "")
                        if (n.isNotBlank()) {
                            parsedMap[n] = Pair(s, nt)
                        }
                    }
                    isJsonParsed = true
                }
            }
        } catch (e: Exception) {
            // Not valid JSON
        }
    }

    val textToParse = if (!isJsonParsed && jsonStr.isNotBlank()) {
        if (fallbackNotes.isNotBlank()) "$jsonStr; $fallbackNotes" else jsonStr
    } else {
        fallbackNotes
    }

    val fallbackNoteMap = mutableMapOf<String, Pair<String, String>>()
    if (textToParse.isNotBlank()) {
        val entries = textToParse.split(";|\n".toRegex())
        for (entry in entries) {
            val trimmed = entry.trim()
            if (trimmed.contains(":")) {
                val colonIdx = trimmed.indexOf(":")
                val key = trimmed.substring(0, colonIdx).trim()
                val valRaw = trimmed.substring(colonIdx + 1).trim()
                if (key.isNotBlank() && valRaw.isNotBlank()) {
                    val matchedKey = defaultItems.find { it.equals(key, ignoreCase = true) }
                    if (matchedKey != null) {
                        var status = "GOOD"
                        var note = valRaw
                        
                        val upperVal = valRaw.uppercase()
                        when {
                            upperVal.startsWith("BAD") -> {
                                status = "BAD"
                                note = valRaw.substring(3).trimStart(' ', '-', ':', '(', ')', ',')
                            }
                            upperVal.startsWith("GOOD") -> {
                                status = "GOOD"
                                note = valRaw.substring(4).trimStart(' ', '-', ':', '(', ')', ',')
                            }
                            upperVal.startsWith("NA") -> {
                                status = "NA"
                                note = valRaw.substring(2).trimStart(' ', '-', ':', '(', ')', ',')
                            }
                            upperVal.contains("BAD") -> {
                                status = "BAD"
                            }
                            upperVal.contains("NA") -> {
                                status = "NA"
                            }
                        }
                        fallbackNoteMap[matchedKey] = Pair(status, note)
                    }
                }
            }
        }
    }

    return defaultItems.map { itemName ->
        val jsonItem = parsedMap[itemName]
        val fallbackItem = fallbackNoteMap[itemName]

        val status = jsonItem?.first ?: fallbackItem?.first ?: "GOOD"
        var notes = jsonItem?.second ?: ""
        if (notes.isBlank() && fallbackItem != null) {
            notes = fallbackItem.second
        }

        ConMonItemState(itemName, status, notes)
    }
}

fun encodeConMonJson(unitType: String, items: List<ConMonItemState>): String {
    val obj = org.json.JSONObject()
    obj.put("unitType", unitType)
    val array = org.json.JSONArray()
    for (item in items) {
        val itemObj = org.json.JSONObject()
        itemObj.put("name", item.name)
        itemObj.put("status", item.status)
        itemObj.put("notes", item.notes)
        array.put(itemObj)
    }
    obj.put("items", array)
    return obj.toString()
}

@Composable
fun ConMonDialog(
    nomorUnit: String,
    initialConMonJson: String,
    previousNotes: String = "",
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val unitType = remember(nomorUnit) { detectUnitType(nomorUnit) }
    var itemStates by remember(unitType, initialConMonJson, previousNotes) {
        mutableStateOf(parseConMonJson(initialConMonJson, unitType, previousNotes))
    }
    var isDirty by remember { mutableStateOf(false) }
    var showConfirmExitDialog by remember { mutableStateOf(false) }

    if (showConfirmExitDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmExitDialog = false },
            title = { Text("Konfirmasi Simpan", fontWeight = FontWeight.Bold) },
            text = { Text("Mau disimpan gak data ConMon nya?") },
            confirmButton = {
                Button(onClick = {
                    showConfirmExitDialog = false
                    val json = encodeConMonJson(unitType, itemStates)
                    onSave(json)
                }) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showConfirmExitDialog = false
                    onDismiss()
                }) {
                    Text("Tidak")
                }
            }
        )
    }

    Dialog(onDismissRequest = {
        if (isDirty) {
            showConfirmExitDialog = true
        } else {
            onDismiss()
        }
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF006874), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "ConMon: $nomorUnit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Condition Monitoring - $unitType", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    IconButton(onClick = {
                        if (isDirty) showConfirmExitDialog = true else onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Item Inspection Rows
                itemStates.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)

                            var expandedStatus by remember { mutableStateOf(false) }
                            Box {
                                val (btnColor, btnText) = when (item.status) {
                                    "BAD" -> Pair(Color(0xFFEF4444), "BAD")
                                    "NA" -> Pair(Color(0xFF64748B), "NA")
                                    else -> Pair(Color(0xFF10B981), "GOOD")
                                }

                                Surface(
                                    onClick = { expandedStatus = true },
                                    color = btnColor,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(text = btnText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }

                                DropdownMenu(
                                    expanded = expandedStatus,
                                    onDismissRequest = { expandedStatus = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("GOOD", color = Color(0xFF10B981), fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            expandedStatus = false
                                            itemStates = itemStates.toMutableList().apply {
                                                this[index] = this[index].copy(status = "GOOD")
                                            }
                                            isDirty = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("BAD", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            expandedStatus = false
                                            itemStates = itemStates.toMutableList().apply {
                                                this[index] = this[index].copy(status = "BAD")
                                            }
                                            isDirty = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("NA", color = Color(0xFF64748B), fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            expandedStatus = false
                                            itemStates = itemStates.toMutableList().apply {
                                                this[index] = this[index].copy(status = "NA")
                                            }
                                            isDirty = true
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = item.notes,
                            onValueChange = { newNote ->
                                itemStates = itemStates.toMutableList().apply {
                                    this[index] = this[index].copy(notes = newNote)
                                }
                                isDirty = true
                            },
                            placeholder = {
                                Text(
                                    "Catatan / Notes (${item.name})...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = Color(0xFF006874),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val json = encodeConMonJson(unitType, itemStates)
                        onSave(json)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006874))
                ) {
                    Text("Simpan Data ConMon", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
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

    var hmInput by remember { mutableStateOf(if (unit.lastHM == 0) "" else unit.lastHM.toString()) }
    var selectedSektor by remember(sektorOptions) { 
        mutableStateOf(if (unit.lastSektor.isNotEmpty() && unit.lastSektor in sektorOptions) unit.lastSektor else (sektorOptions.firstOrNull() ?: "Sektor 1")) 
    }
    var areaInput by remember { mutableStateOf(unit.lastArea) }
    var expiresCommissioningTimestamp by remember { mutableStateOf(unit.expiresCommissioning) }
    var notesInput by remember { mutableStateOf(unit.lastNotes) }
    var statusUnitInput by remember { mutableStateOf(if (unit.statusUnit.isBlank()) "ON HIRE" else unit.statusUnit) }
    var conMonJsonInput by remember { mutableStateOf(unit.conMonData) }
    var showConMonDialog by remember { mutableStateOf(false) }

    var hmError by remember { mutableStateOf<String?>(null) }
    var areaError by remember { mutableStateOf<String?>(null) }
    var conMonError by remember { mutableStateOf<String?>(null) }
    var expiresCommError by remember { mutableStateOf<String?>(null) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    var expandedDropdown by remember { mutableStateOf(false) }

    // State for HM Calculator
    var showHmCalculator by remember { mutableStateOf(false) }
    var isAdditionOperator by remember { mutableStateOf(true) }
    var hmLamaInput by remember { mutableStateOf("") }
    var hmBaruInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    var selectedTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var isDateManuallyChanged by remember { mutableStateOf(false) }
    val displayDateFormatter = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")) }

    val performSaveAction: () -> Unit = {
        val parsedDouble = hmInput.replace(",", ".").toDoubleOrNull()
        val hmVal = parsedDouble?.let { Math.round(it).toInt() }
        if (hmVal == null) {
            hmError = "Masukkan nilai HM berupa angka!"
        } else if (hmVal <= 0) {
            hmError = "HM harus lebih besar dari 0!"
        }

        if (areaInput.isBlank()) {
            areaError = "Harap masukkan detail area lokasi!"
        }

        if (conMonJsonInput.isBlank()) {
            conMonError = "Wajib mengisi ConMon Unit terlebih dahulu!"
        }

        if (expiresCommissioningTimestamp <= 0L) {
            expiresCommError = "Wajib mengisi Tanggal Expires Commissioning!"
        }

        if (hmError == null && areaError == null && conMonError == null && expiresCommError == null && hmVal != null) {
            viewModel.addHMUpdate(
                nomorUnit = unit.nomorUnit,
                hoursMeter = hmVal,
                sektor = selectedSektor,
                area = areaInput.trim(),
                customTimestamp = selectedTimestamp,
                isDateManuallyChanged = isDateManuallyChanged,
                notes = notesInput.trim(),
                expiresCommissioning = expiresCommissioningTimestamp,
                statusUnit = statusUnitInput,
                conMonData = conMonJsonInput
            )
            onDismiss()
        }
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Konfirmasi Simpan", fontWeight = FontWeight.Bold) },
            text = { Text("Data mau disave nggak?", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                Button(onClick = {
                    showExitConfirmDialog = false
                    performSaveAction()
                }) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showExitConfirmDialog = false
                    onDismiss()
                }) {
                    Text("Tidak")
                }
            }
        )
    }

    if (showConMonDialog) {
        ConMonDialog(
            nomorUnit = unit.nomorUnit,
            initialConMonJson = conMonJsonInput,
            previousNotes = if (notesInput.isNotBlank()) notesInput else unit.lastNotes,
            onSave = { newJson ->
                conMonJsonInput = newJson
                conMonError = null
                showConMonDialog = false
            },
            onDismiss = { showConMonDialog = false }
        )
    }

    Dialog(onDismissRequest = { showExitConfirmDialog = true }) {
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
                if (unit.lastHM > 0) {
                    Text(
                        text = "HM Sebelumnya: ${unit.lastHM}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                OutlinedTextField(
                    value = hmInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            hmInput = input
                            hmError = null
                        }
                    },
                    placeholder = { Text("Ketik angka HM baru...") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = hmError != null,
                    trailingIcon = {
                        IconButton(onClick = { 
                            showHmCalculator = !showHmCalculator 
                            if (showHmCalculator && hmLamaInput.isBlank() && unit.lastHM > 0) {
                                hmLamaInput = unit.lastHM.toString()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "Kalkulator HM Unit",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
                hmError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }

                AnimatedVisibility(visible = showHmCalculator) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Kalkulator HM Unit",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gunakan kalkulator ini untuk menjumlahkan atau mengurangi hm sesuai yg tertera pada panel, Klik tombol (+) atau (-) untuk beralih mode penjumlahan atau pengurangan.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = hmLamaInput,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() }) {
                                            hmLamaInput = input
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                )
                                OutlinedButton(
                                    onClick = { isAdditionOperator = !isAdditionOperator },
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(38.dp),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = if (isAdditionOperator) "+" else "-",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                OutlinedTextField(
                                    value = hmBaruInput,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() }) {
                                            hmBaruInput = input
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showHmCalculator = false }) {
                                    Text("Batal", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val lama = hmLamaInput.toIntOrNull() ?: 0
                                        val baru = hmBaruInput.toIntOrNull() ?: 0
                                        val result = if (isAdditionOperator) {
                                            lama + baru
                                        } else {
                                            lama - baru
                                        }
                                        hmInput = result.toString()
                                        showHmCalculator = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                                ) {
                                    Text("Terapkan", fontSize = 12.sp)
                                }
                            }
                        }
                    }
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

                // Tanggal Expires Commissioning
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tanggal Expires Commissioning *",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val displayCommissioningDateFormatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")) }

                OutlinedButton(
                    onClick = {
                        val currentCal = Calendar.getInstance().apply { 
                            if (expiresCommissioningTimestamp > 0L) {
                                timeInMillis = expiresCommissioningTimestamp
                            }
                        }
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val chosenCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.HOUR_OF_DAY, 23)
                                    set(Calendar.MINUTE, 59)
                                    set(Calendar.SECOND, 59)
                                    set(Calendar.MILLISECOND, 999)
                                }
                                expiresCommissioningTimestamp = chosenCal.timeInMillis
                                expiresCommError = null
                            },
                            currentCal.get(Calendar.YEAR),
                            currentCal.get(Calendar.MONTH),
                            currentCal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (expiresCommError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (expiresCommissioningTimestamp > 0L) displayCommissioningDateFormatter.format(Date(expiresCommissioningTimestamp)) else "Pilih Tanggal Expires *",
                                color = if (expiresCommissioningTimestamp > 0L) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary,
                                fontSize = 15.sp
                            )
                        }
                        if (expiresCommissioningTimestamp > 0L) {
                            IconButton(
                                onClick = { 
                                    expiresCommissioningTimestamp = 0L
                                    expiresCommError = "Wajib mengisi Tanggal Expires Commissioning!"
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus Tanggal",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                expiresCommError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }

                // Input Notes
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Status Unit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val isOffHireInput = statusUnitInput.equals("OFF HIRE", ignoreCase = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isOffHireInput) Color(0xFFEF4444) else Color(0xFF10B981))
                            .clickable {
                                statusUnitInput = if (isOffHireInput) "ON HIRE" else "OFF HIRE"
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isOffHireInput) "OFF HIRE" else "ON HIRE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = if (isOffHireInput) "Unit tidak aktif (Excluded dari list aging)" else "Unit aktif beroperasi",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // Pengisian ConMon Unit
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showConMonDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (conMonError != null) MaterialTheme.colorScheme.error else Color(0xFF006874))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = if (conMonError != null) MaterialTheme.colorScheme.error else Color(0xFF006874),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (conMonJsonInput.isBlank()) "Pengisian ConMon Unit *" else "Pengisian ConMon Unit (Tersimpan)",
                            fontWeight = FontWeight.Bold,
                            color = if (conMonError != null) MaterialTheme.colorScheme.error else Color(0xFF006874),
                            fontSize = 13.sp
                        )
                    }
                }
                conMonError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                // Input Notes
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Catatan / Notes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    placeholder = { Text("Tambahkan catatan/notes (opsional)...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Input Tanggal Update
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tanggal Update",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val onShowDatePicker = {
                    val currentCal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                    android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val chosenCal = Calendar.getInstance().apply {
                                timeInMillis = selectedTimestamp
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            }
                            selectedTimestamp = chosenCal.timeInMillis
                            isDateManuallyChanged = true
                        },
                        currentCal.get(Calendar.YEAR),
                        currentCal.get(Calendar.MONTH),
                        currentCal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }

                OutlinedButton(
                    onClick = { onShowDatePicker() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = displayDateFormatter.format(Date(selectedTimestamp)),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Ubah Tanggal",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showExitConfirmDialog = true }, modifier = Modifier.height(48.dp)) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { performSaveAction() },
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
                    text = "Sesuaikan URL database Google Sheets dan daftar sektor kerja operasional tambang.",
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
                    placeholder = { Text("Contoh: Sektor 1, Sektor 2, Sektor 4") },
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
                    var showScriptCode by remember { mutableStateOf(false) }
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    val context = androidx.compose.ui.platform.LocalContext.current

                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Cara Sinkronisasi Google Sheets:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. Buka spreadsheet perusahaan di browser.\n2. Klik Ekstensi -> Apps Script.\n3. Salin & tempel kode Apps Script Anti-Duplikat di bawah ini.\n4. Deploy sebagai Web App (akses: Anyone, role: Execute as me).\n5. Copy URL-nya ke input di atas.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showScriptCode = !showScriptCode }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (showScriptCode) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Lihat Kode Apps Script (Anti-Duplikat)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        AnimatedVisibility(visible = showScriptCode) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 150.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                        .verticalScroll(rememberScrollState())
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = GOOGLE_APPS_SCRIPT_CODE,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = Color(0xFFF1F5F9)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(GOOGLE_APPS_SCRIPT_CODE))
                                        Toast.makeText(context, "Kode Apps Script berhasil disalin!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Build,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Salin Kode Apps Script", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
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
    var expiresCommissioningTimestamp by remember { mutableStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var expiresCommError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

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

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tanggal Expires Commissioning *",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val displayCommissioningDateFormatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")) }

                OutlinedButton(
                    onClick = {
                        val currentCal = Calendar.getInstance().apply { 
                            if (expiresCommissioningTimestamp > 0L) {
                                timeInMillis = expiresCommissioningTimestamp
                            }
                        }
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val chosenCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.HOUR_OF_DAY, 23)
                                    set(Calendar.MINUTE, 59)
                                    set(Calendar.SECOND, 59)
                                    set(Calendar.MILLISECOND, 999)
                                }
                                expiresCommissioningTimestamp = chosenCal.timeInMillis
                                expiresCommError = null
                            },
                            currentCal.get(Calendar.YEAR),
                            currentCal.get(Calendar.MONTH),
                            currentCal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (expiresCommError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (expiresCommissioningTimestamp > 0L) displayCommissioningDateFormatter.format(Date(expiresCommissioningTimestamp)) else "Pilih Tanggal Expires *",
                                color = if (expiresCommissioningTimestamp > 0L) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary,
                                fontSize = 15.sp
                            )
                        }
                        if (expiresCommissioningTimestamp > 0L) {
                            IconButton(
                                onClick = { 
                                    expiresCommissioningTimestamp = 0L 
                                    expiresCommError = "Wajib mengisi Tanggal Expires Commissioning!"
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus Tanggal",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                expiresCommError?.let {
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
                            var hasError = false
                            if (unitNumberInput.isBlank()) {
                                errorText = "Nomor unit tidak boleh kosong!"
                                hasError = true
                            }
                            if (expiresCommissioningTimestamp <= 0L) {
                                expiresCommError = "Wajib mengisi Tanggal Expires Commissioning!"
                                hasError = true
                            }
                            if (!hasError) {
                                viewModel.addNewUnit(unitNumberInput, expiresCommissioningTimestamp)
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
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
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
    val context = LocalContext.current
    var selectedTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    val displayDateFormatter = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")) }
    
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
                                    "Tempel pesan di sini...\nContoh:\n- GS794 HM 45100 lok S4 Combat",
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Input Tanggal Laporan
                        Text(
                            text = "Tanggal Laporan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(bottom = 6.dp)
                        )

                        val onShowDatePicker = {
                            val currentCal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val chosenCal = Calendar.getInstance().apply {
                                        timeInMillis = selectedTimestamp
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    selectedTimestamp = chosenCal.timeInMillis
                                },
                                currentCal.get(Calendar.YEAR),
                                currentCal.get(Calendar.MONTH),
                                currentCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }

                        OutlinedButton(
                            onClick = { onShowDatePicker() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.EditCalendar,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = displayDateFormatter.format(Date(selectedTimestamp)),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        
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
                                        viewModel.parseDailyReportWithAI(rawText, "")
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
                                    viewModel.saveParsedAIUpdates(state.parsedUpdates, selectedTimestamp)
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
fun SettingsTabScreen(viewModel: MainViewModel) {
    // States from ViewModel
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState() // This is the NRP
    val sektorOptions by viewModel.sektorOptions.collectAsState()

    // Internal input states
    var nrpInput by remember(userEmail) { mutableStateOf(userEmail ?: "") }
    var nameInput by remember(userName) { mutableStateOf(userName ?: "") }
    var showSektorDialog by remember { mutableStateOf(false) }
    var newSektorInput by remember { mutableStateOf("") }
    var editingSektorOldName by remember { mutableStateOf<String?>(null) }
    var editingSektorNewName by remember { mutableStateOf("") }

    var profileError by remember { mutableStateOf<String?>(null) }
    var profileSuccess by remember { mutableStateOf<String?>(null) }
    var sektorSuccess by remember { mutableStateOf<String?>(null) }

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

        // TOMBOL: KELOLA SEKTOR OPERASIONAL
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSektorDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Kelola Sektor Operasional",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${sektorOptions.size} Sektor Terdaftar",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Kelola Sektor",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // CARD: LOGOUT / KELUAR APLIKASI
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

    // POPUP DIALOG: KELOLA SEKTOR OPERASIONAL
    if (showSektorDialog) {
        Dialog(onDismissRequest = { showSektorDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Daftar Sektor Operasional",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showSektorDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Tambah atau ubah nama sektor yang tersedia pada pilihan form unit.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Input Tambah Sektor Baru
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newSektorInput,
                            onValueChange = { 
                                newSektorInput = it
                                sektorSuccess = null
                            },
                            placeholder = { Text("Contoh: Sektor 9", fontSize = 12.sp) },
                            label = { Text("Tambah Sektor Baru", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                if (newSektorInput.isNotBlank()) {
                                    viewModel.addSektor(newSektorInput.trim())
                                    newSektorInput = ""
                                    sektorSuccess = "Sektor berhasil ditambahkan!"
                                }
                            },
                            enabled = newSektorInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah")
                        }
                    }

                    sektorSuccess?.let {
                        Text(text = it, color = Color(0xFF10B981), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Daftar Sektor Terdaftar (${sektorOptions.size}):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // List of Sektors
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        sektorOptions.forEach { sektor ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = sektor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Edit button
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Sektor",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                editingSektorOldName = sektor
                                                editingSektorNewName = sektor
                                            }
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Delete button
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus Sektor",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                viewModel.deleteSektor(sektor)
                                                sektorSuccess = "Sektor '$sektor' telah dihapus."
                                            }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showSektorDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Selesai")
                    }
                }
            }
        }
    }

    // Dialog Edit Nama Sektor
    editingSektorOldName?.let { oldName ->
        AlertDialog(
            onDismissRequest = { editingSektorOldName = null },
            title = { Text("Ubah Nama Sektor", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text("Ubah nama '$oldName' menjadi:", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingSektorNewName,
                        onValueChange = { editingSektorNewName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editingSektorNewName.isNotBlank()) {
                            viewModel.updateSektor(oldName, editingSektorNewName.trim())
                            editingSektorOldName = null
                        }
                    },
                    enabled = editingSektorNewName.isNotBlank()
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSektorOldName = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
