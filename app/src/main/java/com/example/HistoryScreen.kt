package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ServiceRecordEntity
import com.example.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryLocaleCo = Locale("es", "CO")
private const val HISTORY_DAY_MS = 24L * 60 * 60 * 1000

private fun formatHistoryKm(km: Int): String = NumberFormat.getIntegerInstance(HistoryLocaleCo).format(km)

private fun formatHistoryDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", HistoryLocaleCo).format(Date(millis))

private fun parseHistoryDate(text: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy", HistoryLocaleCo).apply { isLenient = false }.parse(text.trim())?.time
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val logs by viewModel.maintenanceLogs.collectAsStateWithLifecycle()
    val savedRecords by viewModel.allServiceRecords.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showPdfDialog by remember { mutableStateOf(false) }
    var viewingInvoice by remember { mutableStateOf<MaintenanceLogItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.ensureActiveVehicleKey()
    }

    val activeBikeTitle = remember(viewModel.formMarca, viewModel.formModelo) {
        val custom = "${viewModel.formMarca} ${viewModel.formModelo}".trim()
        custom.ifBlank { "Yamaha MT-07 ABS" }
    }
    val activeBikeSubtitle = remember(viewModel.formCilindraje, viewModel.formAnio, viewModel.formKilometraje) {
        val cc = viewModel.formCilindraje.ifBlank { "689cc" }
        val anio = viewModel.formAnio.ifBlank { "2023" }
        val km = viewModel.formKilometraje.toIntOrNull()?.let { "${formatHistoryKm(it)} KM" } ?: "24,500 KM"
        "Modelo $anio • $km • $cc"
    }

    Scaffold(
        containerColor = MotoBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(MotoAccentCyan, CircleShape)
                            )
                            Text("Hoja de Vida Digital", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                        }
                        Text("Historial de mantenimientos con fecha y notas", fontSize = 12.sp, color = MotoTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MotoBg),
                actions = {
                    FilledTonalButton(
                        onClick = { showPdfDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MotoSurfaceVariant,
                            contentColor = MotoAccentCyan
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MotoPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_service_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Registrar Servicio")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registrar Servicio", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vehicle Header Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MotoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MotoSurface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(MotoSurfaceVariant, RoundedCornerShape(12.dp))
                                .border(1.dp, MotoBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TwoWheeler, contentDescription = null, tint = MotoPrimary, modifier = Modifier.size(28.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(activeBikeTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                                Box(
                                    modifier = Modifier
                                        .background(RoutineBg, RoundedCornerShape(4.dp))
                                        .border(1.dp, RoutineBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("VERIFICADO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = RoutineText)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(activeBikeSubtitle, fontSize = 12.sp, color = MotoTextSecondary)
                        }
                    }
                }
            }

            // =========================================================================
            // SECCIÓN: MANTENIMIENTOS REGISTRADOS EN BASE DE DATOS LOCAL (CON FECHA Y NOTAS)
            // =========================================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SERVICIOS GUARDADOS (CON FECHA Y NOTAS)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MotoTextSecondary
                    )
                    Text(
                        "${savedRecords.size} guardados",
                        fontSize = 12.sp,
                        color = RoutineText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (savedRecords.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MotoBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = MotoSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.NoteAdd,
                                contentDescription = null,
                                tint = MotoAccentBlue,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                "Registra tus mantenimientos realizados",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MotoTextPrimary
                            )
                            Text(
                                "Pulsa \"Registrar Servicio\" para guardar cualquier mantenimiento con su fecha exacta, kilometraje y notas personalizadas.",
                                fontSize = 12.sp,
                                color = MotoTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(savedRecords, key = { "saved_${it.id}" }) { record ->
                    PersistedServiceHistoryCard(
                        record = record,
                        onDelete = { viewModel.deleteServiceRecord(record.id) }
                    )
                }
            }

            // Alertas Automatizadas Legales y Preventivas
            item {
                Text(
                    "SISTEMA DE ALERTAS AUTOMATIZADAS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MotoTextSecondary
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertItem(
                        icon = Icons.Default.VerifiedUser,
                        title = "Seguro Obligatorio (SOAT)",
                        subtitle = "Vence en 24 días (10 Nov 2026)",
                        status = "Por Vencer",
                        containerColor = WarningBg,
                        textColor = WarningText,
                        borderColor = WarningBorder
                    )
                    AlertItem(
                        icon = Icons.Default.Speed,
                        title = "Revisión Técnico-Mecánica (RTM)",
                        subtitle = "Vence en 65 días (21 Dic 2026)",
                        status = "Vigente",
                        containerColor = RoutineBg,
                        textColor = RoutineText,
                        borderColor = RoutineBorder
                    )
                    AlertItem(
                        icon = Icons.Default.OilBarrel,
                        title = "Próximo Cambio de Aceite",
                        subtitle = "Restan 500 km (a los 25,000 km)",
                        status = "Atención",
                        containerColor = UrgentBg,
                        textColor = UrgentText,
                        borderColor = UrgentBorder
                    )
                }
            }

            // Timeline Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "BITÁCORA CRONOLÓGICA DE MANTENIMIENTO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MotoTextSecondary
                    )
                    Text(
                        "${logs.size} Registros",
                        fontSize = 12.sp,
                        color = MotoAccentCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Log Items
            items(logs, key = { it.id }) { log ->
                MaintenanceTimelineCard(
                    item = log,
                    onViewInvoice = { viewingInvoice = log }
                )
            }
        }
    }

    // Modal: Ver Factura / Comprobante
    viewingInvoice?.let { item ->
        Dialog(onDismissRequest = { viewingInvoice = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Comprobante Digital", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                        IconButton(onClick = { viewingInvoice = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MotoTextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(MotoSurfaceVariant, RoundedCornerShape(12.dp))
                            .border(1.dp, MotoBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MotoAccentCyan, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(item.facturaNumero, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary, fontFamily = FontFamily.Monospace)
                            Text(item.taller, fontSize = 12.sp, color = MotoTextSecondary)
                            Text("Total Pagado: ${item.costo}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RoutineText)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Foto de factura y sellos mecánicos almacenados de forma segura con cifrado digital.",
                        fontSize = 12.sp,
                        color = MotoTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewingInvoice = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }

    // Modal: PDF Export Sheet
    if (showPdfDialog) {
        Dialog(onDismissRequest = { showPdfDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotoAccentCyan, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = MotoAccentCyan)
                        Text("Certificado Hoja de Vida MotoTech", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MotoSurfaceVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("MOTOTECH CERTIFIED REPORT #MT-90214", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MotoAccentCyan, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Vehículo: $activeBikeTitle", fontSize = 12.sp, color = MotoTextPrimary)
                            Text("Índice de Salud Mecánica: 94 / 100", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RoutineText)
                            Text("Mantenimientos Validados: ${logs.size + savedRecords.size} registros certificados", fontSize = 11.sp, color = MotoTextSecondary)
                            Text("Garantía de Reventa: Certificada por Red de Talleres Aliados", fontSize = 11.sp, color = MotoTextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Ideal para presentar al comprador en reventa y certificar el kilometraje real y el cuidado del motor.",
                        fontSize = 12.sp,
                        color = MotoTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showPdfDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MotoTextSecondary)
                        ) {
                            Text("Volver")
                        }
                        Button(
                            onClick = { showPdfDialog = false },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir PDF")
                        }
                    }
                }
            }
        }
    }

    // Modal: Registrar Nuevo Servicio con Fecha y Notas
    if (showAddDialog) {
        val now = remember { System.currentTimeMillis() }
        val defaultKm = viewModel.formKilometraje.trim().ifBlank { "24500" }

        var newTitle by remember { mutableStateOf("") }
        var newKm by remember { mutableStateOf(defaultKm) }
        var newFecha by remember { mutableStateOf(formatHistoryDate(now)) }
        var newNotes by remember { mutableStateOf("") }
        var newWorkshop by remember { mutableStateOf("") }
        var newCost by remember { mutableStateOf("") }

        var titleError by remember { mutableStateOf<String?>(null) }
        var kmError by remember { mutableStateOf<String?>(null) }
        var fechaError by remember { mutableStateOf<String?>(null) }

        val sugerenciasComponente = remember {
            listOf(
                "Aceite de motor y filtro de aceite",
                "Lubricación y ajuste de holgura de cadena",
                "Filtro de aire",
                "Pastillas y sistema de frenos",
                "Bujía de encendido",
                "Líquido de frenos DOT"
            )
        }

        val quickDates = remember(now) {
            listOf(
                "Hoy" to formatHistoryDate(now),
                "Ayer" to formatHistoryDate(now - HISTORY_DAY_MS),
                "Hace 15 días" to formatHistoryDate(now - 15 * HISTORY_DAY_MS),
                "Hace 1 mes" to formatHistoryDate(now - 30 * HISTORY_DAY_MS)
            )
        }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotoBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Registrar Mantenimiento Realizado",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MotoTextPrimary
                    )

                    Text(
                        "Sugerencias rápidas de componente:",
                        fontSize = 11.sp,
                        color = MotoTextSecondary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sugerenciasComponente) { sugerencia ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (newTitle == sugerencia) MotoPrimary.copy(alpha = 0.25f) else MotoSurfaceVariant,
                                modifier = Modifier
                                    .border(
                                        0.5.dp,
                                        if (newTitle == sugerencia) MotoPrimary else MotoBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        newTitle = sugerencia
                                        titleError = null
                                    }
                            ) {
                                Text(
                                    text = sugerencia,
                                    fontSize = 11.sp,
                                    color = if (newTitle == sugerencia) MotoTextPrimary else MotoTextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it; titleError = null },
                        label = { Text("Servicio / Componente realizado") },
                        isError = titleError != null,
                        supportingText = titleError?.let { err -> { Text(err) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("historial_servicio_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MotoTextPrimary,
                            unfocusedTextColor = MotoTextPrimary,
                            focusedBorderColor = MotoPrimary,
                            unfocusedBorderColor = MotoBorder
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newKm,
                            onValueChange = { newKm = it; kmError = null },
                            label = { Text("Kilometraje (KM)") },
                            isError = kmError != null,
                            supportingText = kmError?.let { err -> { Text(err) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("historial_km_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MotoTextPrimary,
                                unfocusedTextColor = MotoTextPrimary,
                                focusedBorderColor = MotoPrimary,
                                unfocusedBorderColor = MotoBorder
                            )
                        )

                        OutlinedTextField(
                            value = newFecha,
                            onValueChange = { newFecha = it; fechaError = null },
                            label = { Text("Fecha (dd/mm/aaaa)") },
                            isError = fechaError != null,
                            supportingText = fechaError?.let { err -> { Text(err) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("historial_fecha_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MotoTextPrimary,
                                unfocusedTextColor = MotoTextPrimary,
                                focusedBorderColor = MotoPrimary,
                                unfocusedBorderColor = MotoBorder
                            )
                        )
                    }

                    // Chips rápidos de fecha
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickDates) { (etiqueta, valorFecha) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (newFecha == valorFecha) MotoPrimary.copy(alpha = 0.25f) else MotoSurfaceVariant,
                                modifier = Modifier
                                    .border(
                                        0.5.dp,
                                        if (newFecha == valorFecha) MotoPrimary else MotoBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        newFecha = valorFecha
                                        fechaError = null
                                    }
                            ) {
                                Text(
                                    text = etiqueta,
                                    fontSize = 11.sp,
                                    color = if (newFecha == valorFecha) MotoTextPrimary else MotoTextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        label = { Text("Notas del servicio (repuestos, marca de aceite, observaciones)") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("historial_notas_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MotoTextPrimary,
                            unfocusedTextColor = MotoTextPrimary,
                            focusedBorderColor = MotoPrimary,
                            unfocusedBorderColor = MotoBorder
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newWorkshop,
                            onValueChange = { newWorkshop = it },
                            label = { Text("Taller (opcional)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MotoTextPrimary,
                                unfocusedTextColor = MotoTextPrimary,
                                focusedBorderColor = MotoPrimary,
                                unfocusedBorderColor = MotoBorder
                            )
                        )

                        OutlinedTextField(
                            value = newCost,
                            onValueChange = { newCost = it },
                            label = { Text("Costo (opcional)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MotoTextPrimary,
                                unfocusedTextColor = MotoTextPrimary,
                                focusedBorderColor = MotoPrimary,
                                unfocusedBorderColor = MotoBorder
                            )
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showAddDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MotoTextSecondary)
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                val cleanTitle = newTitle.trim()
                                val parsedKm = newKm.trim().toIntOrNull()
                                val parsedDate = parseHistoryDate(newFecha)

                                titleError = if (cleanTitle.isEmpty()) "Indica el servicio realizado" else null
                                kmError = if (parsedKm == null || parsedKm < 0) "Kilometraje inválido" else null
                                fechaError = when {
                                    parsedDate == null -> "Formato dd/mm/aaaa"
                                    parsedDate > System.currentTimeMillis() -> "No puede ser futura"
                                    else -> null
                                }

                                if (titleError == null && kmError == null && fechaError == null && parsedKm != null && parsedDate != null) {
                                    viewModel.addMaintenanceRecord(
                                        titulo = cleanTitle,
                                        kilometraje = parsedKm,
                                        taller = if (newWorkshop.isNotBlank()) newWorkshop.trim() else "Registro del propietario",
                                        costo = if (newCost.isNotBlank()) newCost.trim() else "$0 COP",
                                        categoria = "Mantenimiento Preventivo",
                                        fechaMillis = parsedDate,
                                        notas = newNotes.trim()
                                    )
                                    showAddDialog = false
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("historial_guardar_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary)
                        ) {
                            Text("Guardar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersistedServiceHistoryCard(
    record: ServiceRecordEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RoutineBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.EventAvailable,
                        contentDescription = null,
                        tint = RoutineText,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Fecha de servicio: ${formatHistoryDate(record.fecha)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoutineText
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(MotoSurfaceVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${formatHistoryKm(record.kilometraje)} KM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MotoTextPrimary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Eliminar entrada",
                            tint = MotoTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = record.componente.replaceFirstChar { it.uppercase() },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MotoTextPrimary
            )

            if (record.taller.isNotBlank() || record.costo.isNotBlank()) {
                val extra = listOfNotNull(
                    record.taller.takeIf { it.isNotBlank() },
                    record.costo.takeIf { it.isNotBlank() && it != "$0 COP" }
                ).joinToString(" • ")
                if (extra.isNotBlank()) {
                    Text(
                        text = extra,
                        fontSize = 12.sp,
                        color = MotoTextSecondary
                    )
                }
            }

            if (record.notas.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MotoSurfaceVariant, RoundedCornerShape(10.dp))
                        .border(0.5.dp, MotoBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "NOTAS DEL SERVICIO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MotoAccentBlue,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = record.notas,
                            fontSize = 12.sp,
                            color = MotoTextPrimary
                        )
                    }
                }
            } else {
                Text(
                    text = "Sin notas adicionales registradas.",
                    fontSize = 11.sp,
                    color = MotoTextMuted
                )
            }
        }
    }
}

@Composable
fun AlertItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    status: String,
    containerColor: Color,
    textColor: Color,
    borderColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(borderColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text(subtitle, fontSize = 12.sp, color = textColor.copy(alpha = 0.8f))
            }
            Box(
                modifier = Modifier
                    .background(borderColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
            }
        }
    }
}

@Composable
fun MaintenanceTimelineCard(
    item: MaintenanceLogItem,
    onViewInvoice: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MotoBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MotoAccentCyan, CircleShape)
                    )
                    Text(item.fecha, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MotoAccentCyan)
                }
                Box(
                    modifier = Modifier
                        .background(MotoSurfaceVariant, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${item.kilometraje} KM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(item.titulo, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = MotoTextSecondary, modifier = Modifier.size(14.dp))
                Text(item.taller, fontSize = 12.sp, color = MotoTextSecondary)
            }

            if (item.notas.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MotoSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Notas: ${item.notas}",
                        fontSize = 12.sp,
                        color = MotoTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MotoBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Inversión realizada", fontSize = 10.sp, color = MotoTextSecondary)
                    Text(item.costo, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RoutineText)
                }

                if (item.tieneComprobante) {
                    FilledTonalButton(
                        onClick = onViewInvoice,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MotoSurfaceVariant,
                            contentColor = MotoAccentCyan
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver Factura", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
