package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LocaleCo = Locale("es", "CO")

private fun formatKm(km: Int): String = NumberFormat.getIntegerInstance(LocaleCo).format(km)

private fun formatFecha(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", LocaleCo).format(Date(millis))

private fun parseFecha(text: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy", LocaleCo).apply { isLenient = false }.parse(text.trim())?.time
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotoTechCockpit(
    viewModel: MainViewModel,
    onNavigateToWorkshops: () -> Unit,
    onNavigateToPremium: () -> Unit,
    onNavigateToSummary: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val serviceRecords by viewModel.serviceRecords.collectAsState()
    val solicitud by viewModel.solicitud.collectAsState()
    val catalogo by viewModel.catalogo.collectAsState()
    val modelosDeMarca by viewModel.modelosDeMarca.collectAsState()
    val reportedIds by viewModel.reportedIds.collectAsState()
    val reportandoCampo by viewModel.reportandoCampo.collectAsState()
    val reporteMensaje by viewModel.reporteMensaje.collectAsState()
    val currentThemeMode = MotoThemeState.themeMode
    val snackbarHostState = remember { SnackbarHostState() }

    var marcaError by remember { mutableStateOf(false) }
    var modeloError by remember { mutableStateOf(false) }
    var anioError by remember { mutableStateOf(false) }
    var kilometrajeError by remember { mutableStateOf(false) }

    var showBrandPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showGarageLimitDialog by remember { mutableStateOf(false) }
    var itemToRegister by remember { mutableStateOf<ItemMantenimiento?>(null) }
    var reportTarget by remember { mutableStateOf<ReportTarget?>(null) }

    LaunchedEffect(reporteMensaje) {
        val msg = reporteMensaje ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearReporteMensaje()
    }

    // Si ya había una marca seleccionada al entrar a la pantalla, asegura que su lista esté cargada
    LaunchedEffect(viewModel.formMarca) {
        if (viewModel.formMarca.isNotBlank() && modelosDeMarca.isEmpty()) {
            viewModel.selectMarca(viewModel.formMarca)
        }
    }

    fun submit() {
        val anioNum = viewModel.formAnio.trim().toIntOrNull()
        val kmNum = viewModel.formKilometraje.trim().toIntOrNull()
        marcaError = viewModel.formMarca.isBlank()
        modeloError = viewModel.formModelo.isBlank() || viewModel.formCilindraje.isBlank()
        anioError = anioNum == null || anioNum < 1950 || anioNum > 2100
        kilometrajeError = kmNum == null || kmNum < 0
        if (!marcaError && !modeloError && !anioError && !kilometrajeError) {
            viewModel.diagnose(
                viewModel.formMarca,
                viewModel.formModelo,
                viewModel.formCilindraje,
                viewModel.formAnio,
                viewModel.formKilometraje
            )
        }
    }

    // Lo que toca se calcula con el plan oficial citado y el historial que registró el usuario.
    val success = uiState as? DiagnosticState.Success
    var selectedResultTab by remember(success?.vehicleKey) { mutableStateOf(0) }
    val maintenanceItems = remember(success, serviceRecords) {
        if (success == null) emptyList() else {
            val ultimos = serviceRecords
                .groupBy { it.componente }
                .mapValues { (_, list) ->
                    val last = list.maxWith(compareBy({ it.kilometraje }, { it.fecha }))
                    UltimoServicio(last.kilometraje, last.fecha, last.notas)
                }
            MaintenanceCalculator.calcular(
                plan = success.response.plan_mantenimiento_periodico.orEmpty(),
                kmActual = success.kmActual,
                ultimosServicios = ultimos,
                ahoraMillis = System.currentTimeMillis()
            )
        }
    }
    val proximosResumen = remember(success, serviceRecords) {
        if (success == null) emptyList() else {
            val ultimos = serviceRecords
                .groupBy { it.componente }
                .mapValues { (_, list) ->
                    val last = list.maxWith(compareBy({ it.kilometraje }, { it.fecha }))
                    UltimoServicio(last.kilometraje, last.fecha, last.notas)
                }
            MaintenanceCalculator.calcularResumenPorKilometraje(
                plan = success.response.plan_mantenimiento_periodico.orEmpty(),
                kmActual = success.kmActual,
                ultimosServicios = ultimos,
                ahoraMillis = System.currentTimeMillis()
            )
        }
    }

    Scaffold(
        containerColor = MotoBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MotoPrimary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TwoWheeler, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text("MotoTech", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                            Text("Ficha técnica y mantenimiento oficial", fontSize = 11.sp, color = MotoTextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MotoBg),
                actions = {
                    ThemeModeTopBarButton(
                        currentMode = currentThemeMode,
                        onSelectMode = { chosen -> viewModel.setThemeMode(chosen, context) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    FilledTonalButton(
                        onClick = {
                            if (!isPremium) showGarageLimitDialog = true else onNavigateToPremium()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MotoSurfaceVariant,
                            contentColor = if (isPremium) RoutineText else WarningText
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("garage_selector_button")
                    ) {
                        Icon(
                            if (isPremium) Icons.Default.Garage else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPremium) "Garaje (Ilimitado)" else "Moto (1/1)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================
            // RESUMEN DE LA MOTO (solo datos reales)
            // =========================================
            item {
                VehicleSummaryCard(
                    marca = viewModel.formMarca,
                    modelo = viewModel.formModelo,
                    cilindraje = viewModel.formCilindraje,
                    anio = viewModel.formAnio,
                    success = success,
                    items = maintenanceItems,
                    onOpenSummary = onNavigateToSummary
                )
            }

            // =========================================
            // FORMULARIO: SELECCIÓN GUIADA + AÑO Y KM LIBRES
            // =========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MotoBorder, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MotoSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Datos de tu moto", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                            Box(
                                modifier = Modifier
                                    .background(InfoBg, RoundedCornerShape(6.dp))
                                    .border(0.5.dp, InfoBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("CATÁLOGO OFICIAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = InfoText)
                            }
                        }

                        Text(
                            "Selecciona la marca y el modelo de la lista oficial: la cilindrada se asigna automáticamente. Solo escribe el año y el kilometraje.",
                            fontSize = 12.sp,
                            color = MotoTextSecondary
                        )

                        // 1. Selector de Marca (Desplegable con buscador)
                        CatalogSelectorField(
                            label = "1. Marca de la motocicleta",
                            selectedValue = viewModel.formMarca,
                            placeholder = "Seleccionar marca (Yamaha, Bajaj, AKT, Suzuki…)",
                            enabled = true,
                            error = if (marcaError) "Selecciona la marca de la motocicleta" else null,
                            leadingIcon = Icons.Default.TwoWheeler,
                            badgeText = if (viewModel.formMarca.isNotBlank()) "SELECCIONADA" else "LISTADO",
                            tag = "marca_input",
                            onClick = { showBrandPicker = true }
                        )

                        // 2. Selector de Modelo exclusivo de la Marca seleccionada
                        CatalogSelectorField(
                            label = if (viewModel.formMarca.isBlank()) {
                                "2. Modelo (selecciona primero una marca)"
                            } else {
                                "2. Modelo exclusivo de ${viewModel.formMarca}"
                            },
                            selectedValue = viewModel.formModelo,
                            placeholder = if (viewModel.formMarca.isBlank()) {
                                "Primero elige una marca arriba"
                            } else {
                                "Seleccionar modelo de ${viewModel.formMarca} (${modelosDeMarca.size} disponibles)"
                            },
                            enabled = viewModel.formMarca.isNotBlank(),
                            error = if (modeloError) "Selecciona un modelo de ${viewModel.formMarca.ifBlank { "la marca" }}" else null,
                            leadingIcon = Icons.Default.FormatListBulleted,
                            badgeText = when {
                                viewModel.formModelo.isNotBlank() -> "SELECCIONADO"
                                viewModel.formMarca.isNotBlank() -> "${modelosDeMarca.size} MODELOS"
                                else -> null
                            },
                            tag = "modelo_input",
                            onClick = {
                                if (viewModel.formMarca.isNotBlank()) {
                                    showModelPicker = true
                                } else {
                                    marcaError = true
                                }
                            }
                        )

                        if (viewModel.formMarca.isNotBlank()) {
                            Text(
                                text = "${modelosDeMarca.size} modelos disponibles de ${viewModel.formMarca}",
                                fontSize = 11.sp,
                                color = MotoTextSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        // 3. Cilindrada Fija Automática (Sin caja de texto editable)
                        FixedDisplacementDisplay(
                            marca = viewModel.formMarca,
                            modelo = viewModel.formModelo,
                            cilindraje = viewModel.formCilindraje,
                            tag = "cilindraje_input"
                        )

                        // 4. Únicos campos libres: Año y Kilometraje actual
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FormField(
                                value = viewModel.formAnio,
                                onValueChange = { viewModel.formAnio = it; anioError = false },
                                label = "Año (libre)",
                                error = if (anioError) "Año válido" else null,
                                tag = "anio_input",
                                numeric = true,
                                modifier = Modifier.weight(1f)
                            )
                            FormField(
                                value = viewModel.formKilometraje,
                                onValueChange = { viewModel.formKilometraje = it; kilometrajeError = false },
                                label = "Kilometraje actual (libre)",
                                error = if (kilometrajeError) "Km válido" else null,
                                tag = "kilometraje_input",
                                numeric = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { submit() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("diagnosticar_button"),
                            enabled = uiState !is DiagnosticState.Loading,
                            colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (uiState is DiagnosticState.Loading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Buscando la ficha…", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Consultar ficha y mantenimiento", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // =========================================
            // RESULTADOS
            // =========================================
            when (val state = uiState) {
                is DiagnosticState.Error -> item {
                    MessageCard(
                        icon = Icons.Default.Warning,
                        title = "No se pudo consultar",
                        message = state.message,
                        bg = UrgentBg, border = UrgentBorder, textColor = UrgentText,
                        actionLabel = "Reintentar",
                        onAction = { submit() },
                        detalle = state.detalle
                    )
                }

                is DiagnosticState.SinFicha -> item {
                    SinFichaCard(
                        moto = state,
                        solicitud = solicitud,
                        onSolicitar = { viewModel.solicitarFicha(state) }
                    )
                }

                is DiagnosticState.Success -> {
                    val resp = state.response
                    val manual = resp.manual
                    val marcaActual = resp.moto_info?.marca ?: state.marca
                    val modeloActual = resp.moto_info?.modelo ?: viewModel.formModelo
                    val cilindrajeActual = resp.moto_info?.cilindraje ?: viewModel.formCilindraje
                    val fichaId = manual?.ficha_id ?: FichaMapper.idFichaFallback(
                        marca = marcaActual,
                        modelo = modeloActual,
                        cilindraje = cilindrajeActual,
                        anioDesde = manual?.anio_desde,
                        anioHasta = manual?.anio_hasta
                    )
                    val solicitarAnioAction = {
                        val anioSolicitado = manual?.anio_consultado ?: resp.moto_info?.anio
                        if (anioSolicitado != null && anioSolicitado > 0) {
                            viewModel.solicitarAnio(
                                marca = marcaActual,
                                modelo = modeloActual,
                                cilindraje = cilindrajeActual,
                                anio = anioSolicitado
                            )
                        }
                    }

                    item {
                        ManualBanner(
                            marca = marcaActual,
                            manual = manual,
                            especificaciones = resp.especificaciones,
                            aviso = state.aviso,
                            solicitud = solicitud,
                            onSolicitarAnio = solicitarAnioAction
                        )
                    }

                    item {
                        MotoResultTabBar(
                            selectedTab = selectedResultTab,
                            maintenanceCount = maintenanceItems.size,
                            onSelectTab = { selectedResultTab = it }
                        )
                    }

                    if (selectedResultTab == 0) {
                        item {
                            TechnicalSpecsCard(
                                marca = marcaActual,
                                especificaciones = resp.especificaciones,
                                ficha = resp.ficha_tecnica,
                                manual = manual,
                                fichaId = fichaId,
                                reportedIds = reportedIds,
                                reportandoCampo = reportandoCampo,
                                onReportar = { campo, etiqueta, valor ->
                                    reportTarget = ReportTarget(
                                        fichaId = fichaId,
                                        marca = marcaActual,
                                        modelo = modeloActual,
                                        cilindraje = cilindrajeActual,
                                        campo = campo,
                                        etiqueta = etiqueta,
                                        valorReportado = valor
                                    )
                                }
                            )
                        }
                    } else {
                        item {
                            MaintenanceSection(
                                marca = marcaActual,
                                items = maintenanceItems,
                                proximosResumen = proximosResumen,
                                manual = manual,
                                kmActual = state.kmActual,
                                fichaId = fichaId,
                                reportedIds = reportedIds,
                                reportandoCampo = reportandoCampo,
                                solicitud = solicitud,
                                onSolicitarPlan = solicitarAnioAction,
                                onOpenSummary = onNavigateToSummary,
                                onRegister = { itemToRegister = it },
                                onFindWorkshop = onNavigateToWorkshops,
                                onReportar = { campo, etiqueta, valor ->
                                    reportTarget = ReportTarget(
                                        fichaId = fichaId,
                                        marca = marcaActual,
                                        modelo = modeloActual,
                                        cilindraje = cilindrajeActual,
                                        campo = campo,
                                        etiqueta = etiqueta,
                                        valorReportado = valor
                                    )
                                }
                            )
                        }
                    }

                    item {
                        Text(
                            "Cada dato proviene exclusivamente del sitio oficial del fabricante o de su manual en PDF. " +
                                "MotoTech no estima ni rellena valores faltantes.",
                            fontSize = 11.sp,
                            color = MotoTextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                    }
                }

                else -> {}
            }
        }
    }

    // Modal desplegable con buscador para seleccionar la Marca
    if (showBrandPicker) {
        BrandPickerDialog(
            brands = catalogo.keys.toList(),
            selectedBrand = viewModel.formMarca,
            modelCountForBrand = { brand -> MotorcycleCatalog.modelsForBrand(brand, catalogo).size },
            onDismiss = { showBrandPicker = false },
            onSelectBrand = { chosenBrand ->
                viewModel.selectMarca(chosenBrand)
                marcaError = false
                modeloError = false
                showBrandPicker = false
            }
        )
    }

    // Modal desplegable con buscador para seleccionar el Modelo de la Marca elegida
    if (showModelPicker && viewModel.formMarca.isNotBlank()) {
        ModelPickerDialog(
            marca = viewModel.formMarca,
            models = modelosDeMarca,
            selectedModel = viewModel.formModelo,
            onDismiss = { showModelPicker = false },
            onSelectModel = { spec ->
                viewModel.selectModelo(spec)
                modeloError = false
                showModelPicker = false
            }
        )
    }

    val pendingReport = reportTarget
    if (pendingReport != null) {
        ReportDatoDialog(
            target = pendingReport,
            onDismiss = { reportTarget = null },
            onConfirm = { motivo ->
                viewModel.reportarDato(
                    fichaId = pendingReport.fichaId,
                    marca = pendingReport.marca,
                    modelo = pendingReport.modelo,
                    cilindraje = pendingReport.cilindraje,
                    campo = pendingReport.campo,
                    valorReportado = pendingReport.valorReportado,
                    motivo = motivo
                )
                reportTarget = null
            }
        )
    }

    // Modal: registrar un servicio hecho
    val registering = itemToRegister
    if (registering != null && success != null) {
        RegisterServiceDialog(
            componente = registering.componente,
            kmActual = success.kmActual,
            onDismiss = { itemToRegister = null },
            onConfirm = { km, fecha, notas ->
                viewModel.registerService(success.vehicleKey, registering.componente, km, fecha, notas)
                itemToRegister = null
            }
        )
    }

    // Modal: límite de garaje
    if (showGarageLimitDialog) {
        Dialog(onDismissRequest = { showGarageLimitDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotoPrimary, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MotoPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MotoPrimary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Límite de Garaje Gratuito", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "La capa gratuita está limitada a 1 motocicleta. Desbloquea MotoTech Premium para registrar todas tus motos, acceder a la exportación en PDF y activar alertas automáticas de SOAT y RTM.",
                        fontSize = 13.sp,
                        color = MotoTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showGarageLimitDialog = false
                            onNavigateToPremium()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Ver Planes Premium", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSelectorField(
    label: String,
    selectedValue: String,
    placeholder: String,
    enabled: Boolean,
    error: String?,
    leadingIcon: ImageVector,
    badgeText: String?,
    tag: String,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (error != null) MaterialTheme.colorScheme.error else MotoTextSecondary
            )
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .background(
                            if (selectedValue.isNotBlank()) RoutineBg else MotoSurfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            0.5.dp,
                            if (selectedValue.isNotBlank()) RoutineBorder else MotoBorder,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedValue.isNotBlank()) RoutineText else MotoTextSecondary
                    )
                }
            }
        }

        val borderColor = when {
            error != null -> MaterialTheme.colorScheme.error
            selectedValue.isNotBlank() -> MotoPrimary.copy(alpha = 0.85f)
            else -> MotoBorder
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .background(
                    if (enabled) MotoSurfaceVariant else MotoSurfaceVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag(tag),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (selectedValue.isNotBlank()) MotoPrimary else MotoTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = selectedValue.ifBlank { placeholder },
                        fontSize = 14.sp,
                        fontWeight = if (selectedValue.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            !enabled -> MotoTextMuted
                            selectedValue.isNotBlank() -> MotoTextPrimary
                            else -> MotoTextSecondary
                        }
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Desplegar opciones",
                    tint = if (enabled) MotoTextPrimary else MotoTextMuted
                )
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun FixedDisplacementDisplay(
    marca: String,
    modelo: String,
    cilindraje: String,
    tag: String
) {
    val hasDisplacement = cilindraje.isNotBlank()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (hasDisplacement) InfoBg.copy(alpha = 0.65f) else MotoSurfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                if (hasDisplacement) InfoBorder else MotoBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (hasDisplacement) InfoIconBg else MotoSurface,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (hasDisplacement) Color.White else MotoTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "3. Cilindrada oficial del modelo",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasDisplacement) InfoText else MotoTextSecondary
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (hasDisplacement) RoutineIconBg else MotoSurface,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "FIJO AUTOMÁTICO",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasDisplacement) Color.White else MotoTextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasDisplacement) {
                            "Asignada automáticamente para $marca $modelo"
                        } else {
                            "Se calcula automáticamente al elegir el modelo"
                        },
                        fontSize = 11.sp,
                        color = if (hasDisplacement) InfoText.copy(alpha = 0.85f) else MotoTextMuted
                    )
                }
            }

            Text(
                text = if (hasDisplacement) cilindraje else "--- cc",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = if (hasDisplacement) MotoAccentBlue else MotoTextMuted
            )
        }
    }
}

@Composable
private fun CatalogPickerDialog(
    title: String,
    subtitle: String,
    subtitleFontSize: TextUnit = 12.sp,
    maxHeight: Dp = 540.dp,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    searchTestTag: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .border(1.dp, MotoBorder, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = MotoSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MotoTextPrimary
                        )
                        Text(
                            text = subtitle,
                            fontSize = subtitleFontSize,
                            color = MotoTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MotoTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(searchPlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MotoTextSecondary) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda", tint = MotoTextSecondary)
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(searchTestTag),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MotoTextPrimary,
                        unfocusedTextColor = MotoTextPrimary,
                        focusedBorderColor = MotoPrimary,
                        unfocusedBorderColor = MotoBorder
                    )
                )

                content()
            }
        }
    }
}

@Composable
private fun BrandPickerDialog(
    brands: List<String>,
    selectedBrand: String,
    modelCountForBrand: (String) -> Int = { MotorcycleCatalog.baseModelsForBrand(it).size },
    onDismiss: () -> Unit,
    onSelectBrand: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredBrands = remember(brands, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) brands
        else brands.filter { it.contains(q, ignoreCase = true) }
    }

    CatalogPickerDialog(
        title = "Selecciona la Marca",
        subtitle = "Catálogo oficial de motocicletas",
        subtitleFontSize = 12.sp,
        maxHeight = 540.dp,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        searchPlaceholder = "Buscar marca (ej. Yamaha, Bajaj, AKT…)",
        searchTestTag = "marca_search_input",
        onDismiss = onDismiss
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredBrands) { brand ->
                val isSelected = brand.equals(selectedBrand, ignoreCase = true)
                val count = modelCountForBrand(brand)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MotoPrimary.copy(alpha = 0.18f) else MotoSurfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) MotoPrimary else MotoBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectBrand(brand) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.TwoWheeler,
                            contentDescription = null,
                            tint = if (isSelected) MotoPrimary else MotoTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = brand,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MotoTextPrimary
                        )
                    }
                    Text(
                        text = "$count modelos",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MotoAccentBlue
                    )
                }
            }

            if (filteredBrands.isEmpty()) {
                item {
                    Text(
                        text = "No se encontraron marcas que coincidan con \"$searchQuery\".",
                        fontSize = 13.sp,
                        color = MotoTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelPickerDialog(
    marca: String,
    models: List<MotoModelSpec>,
    selectedModel: String,
    onDismiss: () -> Unit,
    onSelectModel: (MotoModelSpec) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredModels = remember(models, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) models
        else models.filter {
            it.modelo.contains(q, ignoreCase = true) ||
                it.cilindraje.contains(q, ignoreCase = true) ||
                (it.categoria?.contains(q, ignoreCase = true) == true)
        }
    }

    CatalogPickerDialog(
        title = "Modelos de $marca",
        subtitle = "Al elegir el modelo se fija automáticamente su cilindrada",
        subtitleFontSize = 11.sp,
        maxHeight = 580.dp,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        searchPlaceholder = "Buscar modelo o cilindrada de $marca…",
        searchTestTag = "modelo_search_input",
        onDismiss = onDismiss
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${filteredModels.size} modelos de $marca",
            fontSize = 11.sp,
            color = MotoTextSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredModels) { spec ->
                        val isSelected = spec.modelo.equals(selectedModel, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) MotoPrimary.copy(alpha = 0.18f) else MotoSurfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MotoPrimary else MotoBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectModel(spec) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = spec.modelo,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MotoTextPrimary
                                )
                                val subtitulo = buildList {
                                    spec.categoria?.takeIf { it.isNotBlank() }?.let { add(it) }
                                    if (!spec.vigente) add("Descontinuado")
                                    if (spec.tiene_ficha) add("Con ficha oficial")
                                }.joinToString(" · ")
                                if (subtitulo.isNotEmpty()) {
                                    Text(
                                        text = subtitulo,
                                        fontSize = 11.sp,
                                        color = if (spec.tiene_ficha) RoutineText else MotoTextSecondary
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .background(InfoBg, RoundedCornerShape(8.dp))
                                    .border(0.5.dp, InfoBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = spec.cilindraje,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = InfoText
                                )
                            }
                        }
                    }

                    if (filteredModels.isEmpty()) {
                        item {
                            Text(
                                text = "No se encontraron modelos de $marca con \"$searchQuery\".",
                                fontSize = 13.sp,
                                color = MotoTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp)
                            )
                        }
                    }
                }
            }
        }

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    tag: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MotoTextPrimary,
            unfocusedTextColor = MotoTextPrimary,
            focusedBorderColor = MotoPrimary,
            unfocusedBorderColor = MotoBorder,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = MotoPrimary,
            unfocusedLabelColor = MotoTextSecondary
        )
    )
}

@Composable
private fun VehicleSummaryCard(
    marca: String,
    modelo: String,
    cilindraje: String,
    anio: String,
    success: DiagnosticState.Success?,
    items: List<ItemMantenimiento>,
    onOpenSummary: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MotoBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            val nombre = "$marca $modelo".trim()
            if (nombre.isEmpty()) {
                Text("Selecciona tu moto", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                Text(
                    "Elige la marca y el modelo oficial para obtener su cilindrada exacta, su ficha técnica y su plan de mantenimiento.",
                    fontSize = 12.sp,
                    color = MotoTextSecondary
                )
            } else {
                Text("TU MOTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MotoAccentBlue, letterSpacing = 1.sp)
                Text(nombre, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                val detalle = listOf(cilindraje.trim(), anio.trim().takeIf { it.isNotEmpty() }?.let { "Año $it" }.orEmpty())
                    .filter { it.isNotEmpty() }.joinToString(" • ")
                if (detalle.isNotEmpty()) Text(detalle, fontSize = 12.sp, color = MotoTextSecondary)
            }

            if (success != null) {
                val tienePlanOficial = success.response.plan_mantenimiento_periodico.orEmpty().isNotEmpty()
                val vencidos = items.count { it.estado == EstadoMantenimiento.VENCIDO }
                val conHistorial = items.filter { it.kmRestantes != null && it.estado != EstadoMantenimiento.VENCIDO }
                val (proximoTexto, proximoColor) = when {
                    !tienePlanOficial -> "Sin plan oficial" to MotoTextSecondary
                    vencidos > 0 -> "$vencidos vencido${if (vencidos > 1) "s" else ""}" to UrgentText
                    conHistorial.isNotEmpty() -> "En ${formatKm(conHistorial.minOf { it.kmRestantes!! })} km" to WarningText
                    else -> "Sin historial" to MotoTextSecondary
                }
                val fuenteLabel = when {
                    success.response.especificaciones != null && success.response.manual?.fuente_plan != null -> "Web + PDF"
                    success.response.especificaciones != null -> "Sitio oficial"
                    else -> "Manual"
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CockpitHudItem("Odómetro", "${formatKm(success.kmActual)} km", Icons.Default.Speed, MotoTextPrimary, Modifier.weight(1f))
                    CockpitHudItem("Próximo servicio", proximoTexto, Icons.Default.Build, proximoColor, Modifier.weight(1f))
                    CockpitHudItem("Fuente", fuenteLabel, Icons.Default.MenuBook, MotoAccentBlue, Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onOpenSummary,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MotoSurfaceVariant,
                    contentColor = MotoAccentBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ver_resumen_mantenimiento_button")
            ) {
                Icon(Icons.Default.BuildCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Ver Resumen de Próximos Mantenimientos por KM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CockpitHudItem(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MotoSurfaceVariant, RoundedCornerShape(12.dp))
            .border(0.5.dp, MotoBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor, fontFamily = FontFamily.Monospace)
            Text(title, fontSize = 9.sp, color = MotoTextSecondary)
        }
    }
}

// La moto no está en la biblioteca: se dice claramente y se ofrece pedirla.
@Composable
private fun SinFichaCard(
    moto: DiagnosticState.SinFicha,
    solicitud: SolicitudEstado,
    onSolicitar: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarningBg),
        modifier = Modifier.fillMaxWidth().border(1.dp, WarningBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = WarningText)
                Text(
                    "Aún no tenemos la ficha oficial de esta moto",
                    color = WarningText, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "${moto.marca} ${moto.modelo} ${moto.anio} todavía no está en la biblioteca de manuales. " +
                    "No mostramos datos que no vengan del manual del fabricante.",
                color = WarningText.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (solicitud) {
                SolicitudEstado.ENVIADA, SolicitudEstado.EN_COLA -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RoutineText, modifier = Modifier.size(18.dp))
                        Text(
                            if (solicitud == SolicitudEstado.ENVIADA) "Ya solicitaste esta ficha. Gracias: nos ayuda a saber cuáles cargar primero."
                            else "Solicitud guardada. Se enviará cuando tengas conexión.",
                            color = WarningText, fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    Button(
                        onClick = onSolicitar,
                        enabled = solicitud != SolicitudEstado.ENVIANDO,
                        colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("solicitar_ficha_button")
                    ) {
                        if (solicitud == SolicitudEstado.ENVIANDO) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Enviando…", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Text("Solicitar esta ficha", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    if (solicitud == SolicitudEstado.ERROR) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No se pudo enviar la solicitud. Intenta de nuevo.", color = UrgentText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    icon: ImageVector,
    title: String,
    message: String,
    bg: Color,
    border: Color,
    textColor: Color,
    actionLabel: String?,
    onAction: () -> Unit,
    detalle: String? = null
) {
    var showDetalle by remember(detalle) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth().border(1.dp, border, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = textColor)
                Text(title, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(message, color = textColor.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionLabel, color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            if (detalle != null) {
                Text(
                    if (showDetalle) "Ocultar detalle técnico" else "Ver detalle técnico",
                    color = textColor,
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { showDetalle = !showDetalle }
                        .padding(vertical = 8.dp)
                        .testTag("error_detalle_toggle")
                )
                if (showDetalle) {
                    // Seleccionable para poder copiarlo y compartirlo.
                    SelectionContainer {
                        Text(
                            detalle,
                            color = textColor.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

private data class ReportTarget(
    val fichaId: String,
    val marca: String,
    val modelo: String,
    val cilindraje: String,
    val campo: String,
    val etiqueta: String,
    val valorReportado: String
)

// Banner: de qué fuentes oficiales salen los datos y aviso si corresponde a otro año
@Composable
private fun ManualBanner(
    marca: String,
    manual: ManualInfo?,
    especificaciones: EspecificacionesTecnicas?,
    aviso: String?,
    solicitud: SolicitudEstado = SolicitudEstado.DISPONIBLE,
    onSolicitarAnio: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val rangoTexto = if (manual?.anio_desde != null && manual.anio_hasta != null) {
        if (manual.anio_desde == manual.anio_hasta) "${manual.anio_desde}"
        else "${manual.anio_desde}–${manual.anio_hasta}"
    } else null
    val hasWebSpecs = especificaciones != null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (manual?.aproximada == true) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WarningBorder, RoundedCornerShape(14.dp))
                    .testTag("manual_otro_anio_banner"),
                colors = CardDefaults.cardColors(containerColor = WarningBg)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningText, modifier = Modifier.size(20.dp))
                        Text(
                            "Ficha de otro año como referencia",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningText
                        )
                    }
                    val modeloManual = rangoTexto ?: "otro año"
                    val anioConsultado = manual.anio_consultado?.toString() ?: "tu modelo"
                    Text(
                        "Estos datos corresponden a los modelos $modeloManual. Aún no tenemos la ficha exacta de tu modelo ($anioConsultado); algunos valores pueden variar.",
                        fontSize = 12.sp,
                        color = WarningText.copy(alpha = 0.92f),
                        lineHeight = 17.sp
                    )
                    when (solicitud) {
                        SolicitudEstado.ENVIADA -> Text(
                            "Registramos la solicitud para tu año ($anioConsultado). Gracias.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RoutineText
                        )
                        SolicitudEstado.EN_COLA -> Text(
                            "Solicitud para tu año ($anioConsultado) guardada. Se enviará al recuperar la conexión.",
                            fontSize = 11.sp,
                            color = WarningText
                        )
                        else -> OutlinedButton(
                            onClick = onSolicitarAnio,
                            enabled = solicitud != SolicitudEstado.ENVIANDO,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("solicitar_anio_aproximado_button")
                        ) {
                            Text(
                                if (solicitud == SolicitudEstado.ENVIANDO) "Enviando solicitud…" else "Solicitar ficha de mi año",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarningText
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, InfoBorder, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = InfoBg)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    if (hasWebSpecs) Icons.Default.Verified else Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = InfoText,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    val tituloBanner = manual?.titulo?.takeIf { it.isNotBlank() && !it.equals("untitled", ignoreCase = true) }
                        ?: if (hasWebSpecs) "Ficha oficial · $marca" else "Manual del fabricante"
                    Text(
                        tituloBanner,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = InfoText
                    )
                    val fechaMostrada = SourceValidator.formatearFechaIso(manual?.verificacion_fecha ?: manual?.fecha_revision)
                    val detalle = listOfNotNull(
                        manual?.edicion?.let { "Edición $it" },
                        rangoTexto?.let { "Modelos $it" },
                        fechaMostrada?.let { fecha ->
                            if (manual?.verificacion_metodo != null) "Verificado el $fecha"
                            else "Revisado el $fecha" + (manual?.revisado_por?.let { " por $it" } ?: "")
                        }
                    ).joinToString(" · ")
                    if (detalle.isNotEmpty()) {
                        Text(detalle, fontSize = 11.sp, color = InfoText.copy(alpha = 0.8f))
                    }
                    manual?.url?.let { url ->
                        Text(
                            "Ver manual oficial (PDF)",
                            fontSize = 12.sp,
                            color = MotoAccentBlue,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable { runCatching { uriHandler.openUri(url) } }
                                .padding(vertical = 4.dp)
                        )
                    }
                    if (aviso != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(aviso, fontSize = 12.sp, color = WarningText)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MotoResultTabBar(
    selectedTab: Int,
    maintenanceCount: Int,
    onSelectTab: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MotoSurfaceVariant, RoundedCornerShape(14.dp))
            .border(1.dp, MotoBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val mantenimientoLabel = if (maintenanceCount > 0) "Mantenimiento ($maintenanceCount)" else "Mantenimiento"
        val tabs = listOf(
            Triple(0, "Ficha técnica", "result_tab_ficha" to Icons.Default.Description),
            Triple(1, mantenimientoLabel, "result_tab_mantenimiento" to Icons.Default.Build)
        )
        tabs.forEach { (index, label, tagAndIcon) ->
            val selected = selectedTab == index
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) MotoPrimary else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectTab(index) }
                    .testTag(tagAndIcon.first)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tagAndIcon.second,
                        contentDescription = null,
                        tint = if (selected) Color.White else MotoTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) Color.White else MotoTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun FuenteBadge(nivel: NivelFuente) {
    val (texto, bg, fg) = when (nivel) {
        NivelFuente.MANUAL -> Triple("Manual oficial", RoutineIconBg, Color.White)
        NivelFuente.MANUAL_OTRO_ANIO -> Triple("Manual otro año", WarningIconBg, Color.White)
        NivelFuente.SITIO_OFICIAL -> Triple("Sitio oficial", RoutineIconBg, Color.White)
        NivelFuente.SITIO_OFICIAL_OTRO_ANIO -> Triple("Sitio oficial (otro año)", WarningIconBg, Color.White)
        NivelFuente.SIN_FUENTE -> Triple("Sin fuente oficial", WarningIconBg, Color.White)
        NivelFuente.SIN_DATO -> Triple("Sin dato", MotoSurfaceVariant, MotoTextSecondary)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(texto, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun FuenteLink(dato: DatoVerificado) {
    val uriHandler = LocalUriHandler.current
    val url = dato.fuenteUrl
    if (dato.fuenteTitulo != null && url == null) {
        // Manual sin enlace público: se muestra la referencia para buscarla en el manual impreso.
        Text(dato.fuenteTitulo, fontSize = 11.sp, color = MotoTextSecondary, modifier = Modifier.padding(vertical = 4.dp))
    } else if (dato.fuenteTitulo != null && url != null) {
        Text(
            "Ver fuente: ${dato.fuenteTitulo}",
            fontSize = 11.sp,
            color = MotoAccentBlue,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable { runCatching { uriHandler.openUri(url) } }
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
fun TechnicalSpecsCard(
    marca: String = "",
    especificaciones: EspecificacionesTecnicas? = null,
    ficha: FichaTecnica?,
    manual: ManualInfo?,
    fichaId: String = "",
    reportedIds: Set<String> = emptySet(),
    reportandoCampo: String? = null,
    onReportar: ((campo: String, etiqueta: String, valor: String) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val gruposWeb = remember(especificaciones) {
        if (especificaciones == null) emptyList()
        else listOf(
            "MOTOR" to listOf(
                Triple("ficha_tecnica.cilindraje", "Cilindraje", especificaciones.cilindraje),
                Triple("ficha_tecnica.tipo_motor", "Tipo de motor", especificaciones.tipo_motor),
                Triple("ficha_tecnica.potencia_maxima", "Potencia máxima", especificaciones.potencia_maxima),
                Triple("ficha_tecnica.torque_maximo", "Torque máximo", especificaciones.torque_maximo),
                Triple("ficha_tecnica.alimentacion", "Alimentación", especificaciones.alimentacion),
                Triple("ficha_tecnica.refrigeracion", "Refrigeración", especificaciones.refrigeracion),
                Triple("ficha_tecnica.arranque", "Arranque", especificaciones.arranque),
                Triple("ficha_tecnica.relacion_compresion", "Relación de compresión", especificaciones.relacion_compresion)
            ).filter { it.third != null },
            "TRANSMISIÓN" to listOf(
                Triple("ficha_tecnica.tipo_transmision", "Tipo de transmisión", especificaciones.tipo_transmision),
                Triple("ficha_tecnica.numero_velocidades", "Número de velocidades", especificaciones.numero_velocidades),
                Triple("ficha_tecnica.transmision_final", "Transmisión final", especificaciones.transmision_final)
            ).filter { it.third != null },
            "CHASIS Y FRENOS" to listOf(
                Triple("ficha_tecnica.freno_delantero", "Freno delantero", especificaciones.freno_delantero),
                Triple("ficha_tecnica.freno_trasero", "Freno trasero", especificaciones.freno_trasero),
                Triple("ficha_tecnica.abs", "ABS", especificaciones.abs),
                Triple("ficha_tecnica.suspension_delantera", "Suspensión delantera", especificaciones.suspension_delantera),
                Triple("ficha_tecnica.suspension_trasera", "Suspensión trasera", especificaciones.suspension_trasera),
                Triple("ficha_tecnica.llanta_delantera", "Llanta delantera", especificaciones.llanta_delantera),
                Triple("ficha_tecnica.llanta_trasera", "Llanta trasera", especificaciones.llanta_trasera)
            ).filter { it.third != null },
            "DIMENSIONES Y CAPACIDADES" to listOf(
                Triple("ficha_tecnica.capacidad_tanque", "Capacidad del tanque", especificaciones.capacidad_tanque),
                Triple("ficha_tecnica.peso", "Peso", especificaciones.peso),
                Triple("ficha_tecnica.altura_asiento", "Altura del asiento", especificaciones.altura_asiento),
                Triple("ficha_tecnica.distancia_ejes", "Distancia entre ejes", especificaciones.distancia_ejes)
            ).filter { it.third != null }
        ).filter { it.second.isNotEmpty() }
    }

    val prefijoServicio = if (especificaciones != null) "datos_servicio" else "ficha_tecnica"
    val filasServicio = listOf(
        Triple("$prefijoServicio.capacidad_aceite", "Capacidad de aceite", ficha?.capacidad_aceite),
        Triple("$prefijoServicio.viscosidad_aceite", "Viscosidad de aceite", ficha?.viscosidad_aceite),
        Triple("$prefijoServicio.presion_llanta_delantera", "Presión llanta delantera", ficha?.presion_llanta_delantera),
        Triple("$prefijoServicio.presion_llanta_trasera", "Presión llanta trasera", ficha?.presion_llanta_trasera),
        Triple("$prefijoServicio.holgura_cadena", "Holgura de cadena", ficha?.holgura_cadena),
        Triple("$prefijoServicio.liquido_frenos", "Líquido de frenos", ficha?.liquido_frenos)
    )
    val mostrarServicio = especificaciones == null || filasServicio.any { it.third != null }

    // Fuente principal web para el pie de la pestaña Ficha técnica.
    val primerDatoWeb = remember(gruposWeb) {
        gruposWeb.firstOrNull()?.second?.firstOrNull()?.third
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        gruposWeb.forEach { (tituloGrupo, itemsGrupo) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotoBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        tituloGrupo,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MotoAccentBlue,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    itemsGrupo.forEachIndexed { index, (campo, label, dato) ->
                        val verificado = SourceValidator.verificarConSitioOficial(dato, manual, marca)
                        val reportId = if (fichaId.isNotBlank()) FichaMapper.idReporte(fichaId, campo) else ""
                        val yaReportado = reportId.isNotEmpty() && reportId in reportedIds
                        SpecItem(
                            label = label,
                            dato = verificado,
                            yaReportado = yaReportado,
                            reportando = reportandoCampo == campo,
                            onReportar = if (onReportar != null && verificado.valor != null && fichaId.isNotBlank()) {
                                { onReportar(campo, label, verificado.valor) }
                            } else null
                        )
                        if (index < itemsGrupo.size - 1) {
                            HorizontalDivider(color = MotoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }

        if (mostrarServicio) {
            val filasVisibles = if (especificaciones != null) filasServicio.filter { it.third != null } else filasServicio
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotoBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (especificaciones != null) "DATOS DE SERVICIO (MANUAL OFICIAL)" else "FICHA TÉCNICA",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MotoTextPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    filasVisibles.forEachIndexed { index, (campo, label, dato) ->
                        val verificado = SourceValidator.verificarConManual(dato?.valor, dato?.pagina, manual)
                        val reportId = if (fichaId.isNotBlank()) FichaMapper.idReporte(fichaId, campo) else ""
                        val yaReportado = reportId.isNotEmpty() && reportId in reportedIds
                        SpecItem(
                            label = label,
                            dato = verificado,
                            yaReportado = yaReportado,
                            reportando = reportandoCampo == campo,
                            onReportar = if (onReportar != null && verificado.valor != null && fichaId.isNotBlank()) {
                                { onReportar(campo, label, verificado.valor) }
                            } else null
                        )
                        if (index < filasVisibles.size - 1) {
                            HorizontalDivider(color = MotoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }

        // Pie obligatorio de fuente en la pestaña Ficha técnica.
        if (primerDatoWeb != null) {
            val urlSitio = primerDatoWeb.fuente
            val fechaConsulta = SourceValidator.formatearFechaIso(primerDatoWeb.capturado ?: manual?.verificacion_fecha)
            val textoFuente = buildString {
                append("Fuente: sitio oficial")
                if (marca.isNotBlank()) append(" de $marca")
                if (fechaConsulta != null) append(" · consultado el $fechaConsulta")
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotoBorder, RoundedCornerShape(14.dp))
                    .testTag("ficha_tecnica_footer"),
                colors = CardDefaults.cardColors(containerColor = MotoSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = textoFuente,
                        fontSize = 12.sp,
                        color = MotoTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    if (!urlSitio.isNullOrBlank()) {
                        Text(
                            text = urlSitio,
                            fontSize = 11.sp,
                            color = MotoAccentBlue,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { runCatching { uriHandler.openUri(urlSitio) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpecItem(
    label: String,
    dato: DatoVerificado,
    modifier: Modifier = Modifier,
    yaReportado: Boolean = false,
    reportando: Boolean = false,
    onReportar: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MotoTextSecondary)
            FuenteBadge(dato.nivel)
        }
        Spacer(modifier = Modifier.height(2.dp))
        if (dato.valor == null) {
            Text(
                "La fuente oficial no trae este dato.",
                fontSize = 13.sp,
                color = MotoTextMuted
            )
        } else {
            Text(dato.valor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MotoTextPrimary)
            dato.detalleOriginal?.let { original ->
                Text(original, fontSize = 11.sp, color = MotoTextSecondary)
            }
            if (dato.nivel == NivelFuente.SIN_FUENTE) {
                Text(
                    "La ficha no indica la fuente oficial de este valor. Confírmalo antes de usarlo.",
                    fontSize = 11.sp,
                    color = WarningText
                )
            }
            FuenteLink(dato)
            if (onReportar != null) {
                ReportActionLink(
                    yaReportado = yaReportado,
                    reportando = reportando,
                    onReportar = onReportar
                )
            }
        }
    }
}

@Composable
private fun ReportActionLink(
    yaReportado: Boolean,
    reportando: Boolean,
    onReportar: () -> Unit,
    tint: Color = MotoTextSecondary
) {
    if (yaReportado) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RoutineText, modifier = Modifier.size(13.dp))
            Text("Dato reportado", fontSize = 11.sp, color = RoutineText, fontWeight = FontWeight.Medium)
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clickable(enabled = !reportando) { onReportar() }
                .padding(vertical = 4.dp)
        ) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Text(
                if (reportando) "Enviando reporte…" else "Reportar dato incorrecto",
                fontSize = 11.sp,
                color = tint,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

private data class EstadoStyle(
    val titulo: String,
    val bg: Color,
    val border: Color,
    val text: Color,
    val icon: ImageVector
)

private fun styleFor(estado: EstadoMantenimiento): EstadoStyle = when (estado) {
    EstadoMantenimiento.VENCIDO -> EstadoStyle("VENCIDO SEGÚN EL INTERVALO", UrgentBg, UrgentBorder, UrgentText, Icons.Default.Warning)
    EstadoMantenimiento.PROXIMO -> EstadoStyle("PRÓXIMO", WarningBg, WarningBorder, WarningText, Icons.Default.Schedule)
    EstadoMantenimiento.AL_DIA -> EstadoStyle("AL DÍA", RoutineBg, RoutineBorder, RoutineText, Icons.Default.CheckCircle)
    EstadoMantenimiento.SIN_HISTORIAL -> EstadoStyle("SIN HISTORIAL", MotoSurface, MotoBorder, MotoTextPrimary, Icons.Default.History)
}

private fun intervaloTexto(plan: PlanMantenimiento): String {
    val partes = listOfNotNull(
        plan.intervalo_km?.takeIf { it > 0 }?.let { "${formatKm(it)} km" },
        plan.intervalo_meses?.takeIf { it > 0 }?.let { if (it == 1) "1 mes" else "$it meses" }
    )
    return "Cada " + partes.joinToString(" o ")
}

private fun estadoTexto(item: ItemMantenimiento): String {
    val km = item.kmRestantes
    val dias = item.diasRestantes
    return when (item.estado) {
        EstadoMantenimiento.SIN_HISTORIAL ->
            "Registra cuándo lo hiciste por última vez para saber si ya toca."
        EstadoMantenimiento.VENCIDO -> listOfNotNull(
            km?.takeIf { it <= 0 }?.let { if (it == 0) "Toca ahora por kilometraje" else "Pasado por ${formatKm(-it)} km" },
            dias?.takeIf { it <= 0 }?.let { if (it == 0) "Toca hoy por tiempo" else "Pasado por ${-it} días" }
        ).joinToString(" · ")
        else -> "Faltan " + listOfNotNull(
            km?.let { "${formatKm(it)} km" },
            dias?.let { "$it días" }
        ).joinToString(" o ")
    }
}

// Qué le toca a la moto: intervalo oficial citado + próximo servicio + historial registrado por el usuario
@Composable
internal fun MaintenanceSection(
    marca: String,
    items: List<ItemMantenimiento>,
    proximosResumen: List<ProximoMantenimientoPreventivo>,
    manual: ManualInfo?,
    kmActual: Int,
    fichaId: String,
    reportedIds: Set<String>,
    reportandoCampo: String?,
    solicitud: SolicitudEstado,
    onSolicitarPlan: () -> Unit,
    onOpenSummary: () -> Unit,
    onRegister: (ItemMantenimiento) -> Unit,
    onFindWorkshop: () -> Unit,
    onReportar: (campo: String, etiqueta: String, valor: String) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MANTENIMIENTO A ${formatKm(kmActual)} KM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary, letterSpacing = 1.sp)

        if (items.isEmpty()) {
            val marcaVisible = marca.trim().ifBlank { "El fabricante" }
            val urlManual = manual?.url?.takeIf { it.isNotBlank() }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WarningBorder, RoundedCornerShape(18.dp))
                    .testTag("sin_plan_oficial_card"),
                colors = CardDefaults.cardColors(containerColor = WarningBg)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = WarningText)
                        Text(
                            "Plan del fabricante aún no disponible",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningText
                        )
                    }
                    Text(
                        "$marcaVisible aún no publica el plan de mantenimiento de este modelo en su sitio.",
                        fontSize = 13.sp,
                        color = WarningText.copy(alpha = 0.95f),
                        lineHeight = 18.sp
                    )
                    Text(
                        "MotoTech solo muestra intervalos oficiales verificados del fabricante y no utiliza planes genéricos.",
                        fontSize = 12.sp,
                        color = WarningText.copy(alpha = 0.8f)
                    )

                    if (urlManual != null) {
                        OutlinedButton(
                            onClick = { runCatching { uriHandler.openUri(urlManual) } },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ver_manual_oficial_button")
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = WarningText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ver manual oficial", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WarningText)
                        }
                    }

                    when (solicitud) {
                        SolicitudEstado.ENVIADA, SolicitudEstado.EN_COLA -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RoutineText, modifier = Modifier.size(18.dp))
                                Text(
                                    if (solicitud == SolicitudEstado.ENVIADA) "Solicitud registrada. Nos ayuda a priorizar este modelo."
                                    else "Solicitud guardada. Se enviará cuando recuperes la conexión.",
                                    color = WarningText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        else -> {
                            Button(
                                onClick = onSolicitarPlan,
                                enabled = solicitud != SolicitudEstado.ENVIANDO,
                                colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("solicitar_plan_button")
                            ) {
                                if (solicitud == SolicitudEstado.ENVIANDO) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Enviando…", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                } else {
                                    Text("Solicitar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            if (solicitud == SolicitudEstado.ERROR) {
                                Text("No se pudo enviar la solicitud. Intenta de nuevo.", color = UrgentText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            return@Column
        }

        // Tarjeta "Próximo servicio" calculado con el odómetro y el historial del usuario.
        val proximoItem = proximosResumen.firstOrNull()
        if (proximoItem != null) {
            val esUrgente = proximoItem.estado == EstadoMantenimiento.VENCIDO
            val bgNext = if (esUrgente) UrgentBg else InfoBg
            val borderNext = if (esUrgente) UrgentBorder else InfoBorder
            val fgNext = if (esUrgente) UrgentText else InfoText
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderNext, RoundedCornerShape(16.dp))
                    .testTag("proximo_servicio_tab_card"),
                colors = CardDefaults.cardColors(containerColor = bgNext)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (esUrgente) "PRÓXIMO SERVICIO (VENCIDO / TOCA AHORA)" else "PRÓXIMO SERVICIO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = fgNext,
                            letterSpacing = 0.8.sp
                        )
                        proximoItem.proximoKilometraje?.let { hito ->
                            Text(
                                "Meta: ${formatKm(hito)} km",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = fgNext
                            )
                        }
                    }
                    Text(proximoItem.componente, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fgNext)
                    val detalleProx = proximoItem.kmRestantes?.let { rest ->
                        when {
                            rest < 0 -> "Vencido por ${formatKm(-rest)} km"
                            rest == 0 -> "Corresponde exactamente a los ${formatKm(kmActual)} km"
                            else -> "Faltan ${formatKm(rest)} km (${if (proximoItem.basadoEnRegistro) "según tu último servicio" else "ciclo por odómetro"})"
                        }
                    } ?: "Según intervalo calendario del fabricante"
                    Text(detalleProx, fontSize = 12.sp, color = fgNext.copy(alpha = 0.9f))
                    TextButton(
                        onClick = onOpenSummary,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "Ver cronograma completo y alertas en Resumen KM →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MotoAccentBlue
                        )
                    }
                }
            }
        }

        EstadoMantenimiento.values().forEach { estado ->
            val grupo = items.filter { it.estado == estado }
            if (grupo.isEmpty()) return@forEach
            val style = styleFor(estado)
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, style.border, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = style.bg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(style.icon, contentDescription = null, tint = style.text, modifier = Modifier.size(20.dp))
                        Text("${style.titulo} (${grupo.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = style.text, letterSpacing = 1.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    grupo.forEachIndexed { index, item ->
                        val fuente = SourceValidator.verificarConManual(
                            valor = intervaloTexto(item.plan),
                            pagina = item.plan.pagina,
                            manual = manual
                        )
                        val campoPlan = "plan_mantenimiento.${MaintenanceCalculator.claveComponente(item.componente)}"
                        val reportId = if (fichaId.isNotBlank()) FichaMapper.idReporte(fichaId, campoPlan) else ""
                        val yaReportado = reportId.isNotEmpty() && reportId in reportedIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(item.componente, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = style.text, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            FuenteBadge(fuente.nivel)
                        }
                        item.plan.accion?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 12.sp, color = style.text.copy(alpha = 0.85f), lineHeight = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(intervaloTexto(item.plan), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = style.text, fontFamily = FontFamily.Monospace)
                        Text(estadoTexto(item), fontSize = 12.sp, color = style.text.copy(alpha = 0.9f))
                        item.ultimo?.let {
                            Text(
                                "Último registro: ${formatKm(it.kilometraje)} km · ${formatFecha(it.fechaMillis)}",
                                fontSize = 11.sp,
                                color = style.text.copy(alpha = 0.7f)
                            )
                            if (it.notas.isNotBlank()) {
                                Text(
                                    "Nota: ${it.notas}",
                                    fontSize = 11.sp,
                                    color = style.text.copy(alpha = 0.85f)
                                )
                            }
                        }
                        FuenteLink(fuente)

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onRegister(item) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = style.text, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Registrar servicio hecho", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = style.text)
                            }
                            if (fichaId.isNotBlank()) {
                                ReportActionLink(
                                    yaReportado = yaReportado,
                                    reportando = reportandoCampo == campoPlan,
                                    onReportar = {
                                        onReportar(campoPlan, item.componente, intervaloTexto(item.plan))
                                    },
                                    tint = style.text.copy(alpha = 0.85f)
                                )
                            }
                        }

                        if (index < grupo.size - 1) {
                            HorizontalDivider(color = style.border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))
                        }
                    }

                    if (estado == EstadoMantenimiento.VENCIDO) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onFindWorkshop,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = UrgentIconBg),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buscar taller", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Pie obligatorio de fuente en la pestaña Mantenimiento.
        val paginaPlan = manual?.fuente_plan?.pagina ?: items.firstNotNullOfOrNull { it.plan.pagina }
        val urlPlan = manual?.fuente_plan?.url ?: manual?.url
        val shaPlan = manual?.fuente_plan?.sha256
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MotoBorder, RoundedCornerShape(14.dp))
                .testTag("mantenimiento_footer"),
            colors = CardDefaults.cardColors(containerColor = MotoSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val textoPie = if (paginaPlan != null && paginaPlan > 0) {
                    "Según el manual oficial, pág. $paginaPlan"
                } else {
                    "Según el manual oficial del fabricante"
                }
                Text(
                    text = textoPie,
                    fontSize = 12.sp,
                    color = MotoTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                if (!shaPlan.isNullOrBlank()) {
                    Text(
                        text = "PDF verificado (SHA-256: ${shaPlan.take(12)}…)",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MotoTextSecondary
                    )
                }
                if (!urlPlan.isNullOrBlank()) {
                    Text(
                        text = "Ver manual oficial (PDF)",
                        fontSize = 11.sp,
                        color = MotoAccentBlue,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { runCatching { uriHandler.openUri(urlPlan) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportDatoDialog(
    target: ReportTarget,
    onDismiss: () -> Unit,
    onConfirm: (motivo: String) -> Unit
) {
    val motivos = remember {
        listOf(
            "El valor no coincide con mi manual",
            "La página citada no corresponde",
            "No aplica a esta motocicleta"
        )
    }
    var motivoSeleccionado by remember { mutableStateOf(motivos.first()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, MotoBorder, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = MotoSurface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Reportar dato incorrecto", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                Text(
                    "${target.etiqueta}: ${target.valorReportado}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MotoAccentBlue
                )
                Text(
                    "Si varios usuarios reportan este dato, se retirará automáticamente para volver a verificarlo contra el manual.",
                    fontSize = 12.sp,
                    color = MotoTextSecondary,
                    lineHeight = 16.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    motivos.forEach { opcion ->
                        val selected = opcion == motivoSeleccionado
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) MotoPrimary.copy(alpha = 0.14f) else MotoSurfaceVariant,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selected) MotoPrimary else MotoBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { motivoSeleccionado = opcion }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(selected = selected, onClick = { motivoSeleccionado = opcion })
                            Text(opcion, fontSize = 13.sp, color = MotoTextPrimary)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = MotoTextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(motivoSeleccionado) },
                        colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("reporte_enviar_button")
                    ) {
                        Text("Enviar reporte", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisterServiceDialog(
    componente: String,
    kmActual: Int,
    onDismiss: () -> Unit,
    onConfirm: (km: Int, fechaMillis: Long, notas: String) -> Unit
) {
    var kmText by remember { mutableStateOf(kmActual.toString()) }
    var fechaText by remember { mutableStateOf(formatFecha(System.currentTimeMillis())) }
    var notasText by remember { mutableStateOf("") }
    var kmError by remember { mutableStateOf<String?>(null) }
    var fechaError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, MotoBorder, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = MotoSurface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Registrar servicio hecho", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                Text(componente, fontSize = 13.sp, color = MotoTextSecondary)

                FormField(
                    value = kmText,
                    onValueChange = { kmText = it; kmError = null },
                    label = "Kilometraje cuando se hizo",
                    error = kmError,
                    tag = "servicio_km_input",
                    numeric = true
                )
                FormField(
                    value = fechaText,
                    onValueChange = { fechaText = it; fechaError = null },
                    label = "Fecha de servicio (dd/mm/aaaa)",
                    error = fechaError,
                    tag = "servicio_fecha_input"
                )
                FormField(
                    value = notasText,
                    onValueChange = { notasText = it },
                    label = "Notas del servicio (opcional: marca de aceite, repuestos, etc.)",
                    error = null,
                    tag = "servicio_notas_input"
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = MotoTextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val km = kmText.trim().toIntOrNull()
                            val fecha = parseFecha(fechaText)
                            kmError = when {
                                km == null || km < 0 -> "Ingresa un kilometraje válido"
                                km > kmActual -> "No puede ser mayor al odómetro actual (${formatKm(kmActual)} km)"
                                else -> null
                            }
                            fechaError = when {
                                fecha == null -> "Usa el formato dd/mm/aaaa"
                                fecha > System.currentTimeMillis() -> "La fecha no puede ser futura"
                                else -> null
                            }
                            if (kmError == null && fechaError == null && km != null && fecha != null) {
                                onConfirm(km, fecha, notasText.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("servicio_guardar_button")
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
