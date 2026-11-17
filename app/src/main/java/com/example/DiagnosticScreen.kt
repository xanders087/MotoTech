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
    onNavigateToPremium: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val serviceRecords by viewModel.serviceRecords.collectAsState()
    val solicitud by viewModel.solicitud.collectAsState()
    val modelosDeMarca by viewModel.modelosDeMarca.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()
    val catalogStatusLabel by viewModel.catalogStatusLabel.collectAsState()

    var marcaError by remember { mutableStateOf(false) }
    var modeloError by remember { mutableStateOf(false) }
    var anioError by remember { mutableStateOf(false) }
    var kilometrajeError by remember { mutableStateOf(false) }

    var showBrandPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showGarageLimitDialog by remember { mutableStateOf(false) }
    var itemToRegister by remember { mutableStateOf<ItemMantenimiento?>(null) }

    // Si ya había una marca seleccionada al entrar a la pantalla, asegura que su lista esté cargada
    LaunchedEffect(viewModel.formMarca) {
        if (viewModel.formMarca.isNotBlank() && modelosDeMarca.isEmpty()) {
            viewModel.selectMarca(viewModel.formMarca, forceRefreshOnline = false)
        }
    }

    val exampleBikes = remember {
        listOf(
            "Yamaha MT-07" to Quadruple("Yamaha", "MT-07", "689cc", "2023"),
            "Bajaj Pulsar NS200" to Quadruple("Bajaj", "Pulsar NS200", "199cc", "2023"),
            "AKT NKD 125" to Quadruple("AKT", "NKD 125", "124cc", "2024"),
            "Suzuki Gixxer 150" to Quadruple("Suzuki", "Gixxer 150 FI ABS", "155cc", "2024"),
            "KTM Duke 390" to Quadruple("KTM", "Duke 390", "373cc", "2024"),
            "Honda CB190R" to Quadruple("Honda", "CB190R", "184cc", "2024"),
            "BMW G 310 GS" to Quadruple("BMW", "G 310 GS", "313cc", "2022")
        )
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

    // Lo que toca se calcula con el plan citado y el historial que registró el usuario.
    val success = uiState as? DiagnosticState.Success
    val maintenanceItems = remember(success, serviceRecords) {
        if (success == null) emptyList() else {
            val ultimos = serviceRecords
                .groupBy { it.componente }
                .mapValues { (_, list) ->
                    val last = list.maxWith(compareBy({ it.kilometraje }, { it.fecha }))
                    UltimoServicio(last.kilometraje, last.fecha)
                }
            MaintenanceCalculator.calcular(
                plan = success.response.plan_mantenimiento_periodico.orEmpty(),
                kmActual = success.kmActual,
                ultimosServicios = ultimos,
                ahoraMillis = System.currentTimeMillis()
            )
        }
    }

    Scaffold(
        containerColor = MotoBg,
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
                            Text("Datos del manual oficial de tu moto", fontSize = 11.sp, color = MotoTextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MotoBg),
                actions = {
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
                    items = maintenanceItems
                )
            }

            // =========================================
            // EJEMPLOS
            // =========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "EJEMPLOS RÁPIDOS (SELECCIONAN MARCA, MODELO Y CILINDRADA)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MotoTextSecondary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(exampleBikes) { (label, data) ->
                            val isCurrent = viewModel.formMarca.equals(data.first, ignoreCase = true) &&
                                viewModel.formModelo.equals(data.second, ignoreCase = true)
                            ElevatedFilterChip(
                                selected = isCurrent,
                                onClick = {
                                    viewModel.selectExampleBike(
                                        marca = data.first,
                                        modelo = data.second,
                                        cilindraje = data.third,
                                        anio = data.fourth
                                    )
                                    marcaError = false
                                    modeloError = false
                                    anioError = false
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MotoPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MotoSurfaceVariant,
                                    labelColor = MotoTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isCurrent,
                                    borderColor = if (isCurrent) MotoPrimary else MotoBorder
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
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
                                isLoadingModels -> "SINCRONIZANDO…"
                                viewModel.formModelo.isNotBlank() -> "VERIFICADO"
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

                        // Barra de estado de sincronización en tiempo real de modelos de la marca
                        if (viewModel.formMarca.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isLoadingModels) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp,
                                            color = MotoAccentBlue
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = MotoAccentBlue,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = catalogStatusLabel ?: "${modelosDeMarca.size} modelos disponibles",
                                        fontSize = 11.sp,
                                        color = MotoTextSecondary
                                    )
                                }
                                Text(
                                    text = "Actualizar lista en línea",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MotoAccentBlue,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier
                                        .clickable(enabled = !isLoadingModels) {
                                            viewModel.selectMarca(viewModel.formMarca, forceRefreshOnline = true)
                                        }
                                        .padding(vertical = 2.dp)
                                )
                            }
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

                    item { ManualBanner(manual = manual, aviso = state.aviso) }

                    item { TechnicalSpecsCard(resp.ficha_tecnica, manual) }

                    item {
                        MaintenanceSection(
                            items = maintenanceItems,
                            manual = manual,
                            kmActual = state.kmActual,
                            onRegister = { itemToRegister = it },
                            onFindWorkshop = onNavigateToWorkshops
                        )
                    }

                    item {
                        Text(
                            "Los datos se transcribieron del manual indicado y se muestran tal como aparecen en él. " +
                                "Ante cualquier duda, prevalece el manual de tu moto.",
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
            brands = viewModel.marcasDisponibles,
            selectedBrand = viewModel.formMarca,
            onDismiss = { showBrandPicker = false },
            onSelectBrand = { chosenBrand ->
                viewModel.selectMarca(chosenBrand, forceRefreshOnline = false)
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
            isLoadingOnline = isLoadingModels,
            onRefreshOnline = {
                viewModel.selectMarca(viewModel.formMarca, forceRefreshOnline = true)
            },
            onDismiss = { showModelPicker = false },
            onSelectModel = { spec ->
                viewModel.selectModelo(spec)
                modeloError = false
                showModelPicker = false
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
            onConfirm = { km, fecha ->
                viewModel.registerService(success.vehicleKey, registering.componente, km, fecha)
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
                val count = MotorcycleCatalog.baseModelsForBrand(brand).size
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
    isLoadingOnline: Boolean,
    onRefreshOnline: () -> Unit,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
                    Text(
                        text = "${filteredModels.size} modelos de $marca",
                        fontSize = 11.sp,
                        color = MotoTextSecondary
                    )
                    TextButton(
                        onClick = onRefreshOnline,
                        enabled = !isLoadingOnline,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        if (isLoadingOnline) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MotoAccentBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Consultando web…", fontSize = 11.sp, color = MotoAccentBlue)
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MotoAccentBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sincronizar en línea", fontSize = 11.sp, color = MotoAccentBlue)
                        }
                    }
                }

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
                                if (!spec.categoria.isNullOrBlank()) {
                                    Text(
                                        text = spec.categoria,
                                        fontSize = 11.sp,
                                        color = MotoTextSecondary
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
    items: List<ItemMantenimiento>
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
                val vencidos = items.count { it.estado == EstadoMantenimiento.VENCIDO }
                val conHistorial = items.filter { it.kmRestantes != null && it.estado != EstadoMantenimiento.VENCIDO }
                val (proximoTexto, proximoColor) = when {
                    vencidos > 0 -> "$vencidos vencido${if (vencidos > 1) "s" else ""}" to UrgentText
                    conHistorial.isNotEmpty() -> "En ${formatKm(conHistorial.minOf { it.kmRestantes!! })} km" to WarningText
                    else -> "Sin historial" to MotoTextSecondary
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CockpitHudItem("Odómetro", "${formatKm(success.kmActual)} km", Icons.Default.Speed, MotoTextPrimary, Modifier.weight(1f))
                    CockpitHudItem("Próximo servicio", proximoTexto, Icons.Default.Build, proximoColor, Modifier.weight(1f))
                    CockpitHudItem("Fuente", "Manual", Icons.Default.MenuBook, MotoAccentBlue, Modifier.weight(1f))
                }
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

// Banner: de qué manual salen los datos y quién lo revisó
@Composable
private fun ManualBanner(manual: ManualInfo?, aviso: String?) {
    val uriHandler = LocalUriHandler.current

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
            Icon(Icons.Default.MenuBook, contentDescription = null, tint = InfoText, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    manual?.titulo ?: "Manual del fabricante",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InfoText
                )
                val detalle = listOfNotNull(
                    manual?.edicion?.let { "Edición $it" },
                    if (manual?.anio_desde != null && manual.anio_hasta != null) {
                        if (manual.anio_desde == manual.anio_hasta) "Modelo ${manual.anio_desde}"
                        else "Modelos ${manual.anio_desde}-${manual.anio_hasta}"
                    } else null,
                    manual?.fecha_revision?.let { fecha ->
                        "Revisado el $fecha" + (manual.revisado_por?.let { " por $it" } ?: "")
                    }
                ).joinToString(" · ")
                if (detalle.isNotEmpty()) {
                    Text(detalle, fontSize = 11.sp, color = InfoText.copy(alpha = 0.8f))
                }
                manual?.url?.let { url ->
                    Text(
                        "Abrir el manual oficial",
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

@Composable
private fun FuenteBadge(nivel: NivelFuente) {
    val (texto, bg, fg) = when (nivel) {
        NivelFuente.MANUAL -> Triple("Manual oficial", RoutineIconBg, Color.White)
        NivelFuente.SIN_FUENTE -> Triple("Sin página del manual", WarningIconBg, Color.White)
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
fun TechnicalSpecsCard(ficha: FichaTecnica?, manual: ManualInfo?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MotoBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("FICHA TÉCNICA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))
            val filas = listOf(
                "Capacidad de aceite" to ficha?.capacidad_aceite,
                "Viscosidad de aceite" to ficha?.viscosidad_aceite,
                "Presión llanta delantera" to ficha?.presion_llanta_delantera,
                "Presión llanta trasera" to ficha?.presion_llanta_trasera,
                "Holgura de cadena" to ficha?.holgura_cadena,
                "Líquido de frenos" to ficha?.liquido_frenos
            )
            filas.forEachIndexed { index, (label, dato) ->
                SpecItem(label, SourceValidator.verificarConManual(dato?.valor, dato?.pagina, manual))
                if (index < filas.size - 1) {
                    HorizontalDivider(color = MotoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun SpecItem(label: String, dato: DatoVerificado, modifier: Modifier = Modifier) {
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
                "El manual no trae este dato.",
                fontSize = 13.sp,
                color = MotoTextMuted
            )
        } else {
            Text(dato.valor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MotoTextPrimary)
            if (dato.nivel == NivelFuente.SIN_FUENTE) {
                Text(
                    "La ficha no indica en qué página del manual está este valor. Confírmalo antes de usarlo.",
                    fontSize = 11.sp,
                    color = WarningText
                )
            }
            FuenteLink(dato)
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

// Qué le toca a la moto: intervalo citado + historial registrado por el usuario
@Composable
private fun MaintenanceSection(
    items: List<ItemMantenimiento>,
    manual: ManualInfo?,
    kmActual: Int,
    onRegister: (ItemMantenimiento) -> Unit,
    onFindWorkshop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MANTENIMIENTO A ${formatKm(kmActual)} KM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary, letterSpacing = 1.sp)

        if (items.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MotoBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Text(
                    "La ficha de esta moto no incluye un plan de mantenimiento con intervalos. " +
                        "Consulta la tabla de mantenimiento de tu manual.",
                    fontSize = 13.sp,
                    color = MotoTextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@Column
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
                        }
                        FuenteLink(fuente)

                        OutlinedButton(
                            onClick = { onRegister(item) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = style.text, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Registrar servicio hecho", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = style.text)
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
    }
}

@Composable
private fun RegisterServiceDialog(
    componente: String,
    kmActual: Int,
    onDismiss: () -> Unit,
    onConfirm: (km: Int, fechaMillis: Long) -> Unit
) {
    var kmText by remember { mutableStateOf(kmActual.toString()) }
    var fechaText by remember { mutableStateOf(formatFecha(System.currentTimeMillis())) }
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
                    label = "Fecha (dd/mm/aaaa)",
                    error = fechaError,
                    tag = "servicio_fecha_input"
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
                                onConfirm(km, fecha)
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
