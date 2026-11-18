package com.example

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import kotlin.math.roundToInt

private val SummaryLocaleCo = Locale("es", "CO")
private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

private fun formatSummaryKm(km: Int): String = NumberFormat.getIntegerInstance(SummaryLocaleCo).format(km)

private fun formatSummaryFecha(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", SummaryLocaleCo).format(Date(millis))

private fun formatSummaryHora(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", SummaryLocaleCo).format(Date(millis))

private fun parseSummaryFecha(text: String): Long? = runCatching {
    SimpleDateFormat("dd/MM/yyyy", SummaryLocaleCo).apply { isLenient = false }.parse(text.trim())?.time
}.getOrNull()

enum class FiltroResumenPreventivo(val etiqueta: String) {
    TODOS("Todos"),
    URGENTES("Toca ahora / Vencidos"),
    PROXIMOS("Próximos (≤10%)"),
    EN_CICLO("En ciclo / Al día")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceSummaryScreen(
    viewModel: MainViewModel,
    onNavigateToCockpit: () -> Unit,
    onNavigateToWorkshops: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serviceRecords by viewModel.serviceRecords.collectAsStateWithLifecycle()
    val notificacionesActivas by viewModel.notificacionesActivas.collectAsStateWithLifecycle()
    val umbralAlertaKm by viewModel.umbralAlertaKm.collectAsStateWithLifecycle()
    val ultimaNotificacionResumen by viewModel.ultimaNotificacionResumen.collectAsStateWithLifecycle()
    val solicitud by viewModel.solicitud.collectAsStateWithLifecycle()

    val successState = uiState as? DiagnosticState.Success

    // Asegura una clave de vehículo activa para leer/guardar servicios en Room
    LaunchedEffect(viewModel.formMarca, viewModel.formModelo, viewModel.formCilindraje, viewModel.formAnio) {
        viewModel.ensureActiveVehicleKey()
    }

    val initialKm = remember(successState?.kmActual, viewModel.formKilometraje) {
        successState?.kmActual
            ?: viewModel.formKilometraje.trim().toIntOrNull()
            ?: 14500
    }

    var kmInputText by remember(initialKm) { mutableStateOf(initialKm.toString()) }
    val kmActual = kmInputText.trim().toIntOrNull()?.coerceAtLeast(0) ?: initialKm

    var filtroSeleccionado by remember { mutableStateOf(FiltroResumenPreventivo.TODOS) }
    var itemParaRegistrar by remember { mutableStateOf<ProximoMantenimientoPreventivo?>(null) }

    val planActivo = remember(successState) {
        successState?.response?.plan_mantenimiento_periodico.orEmpty()
    }
    val usaPlanManualOficial = planActivo.isNotEmpty()
    val paginaPlanOficial = remember(successState) {
        successState?.response?.manual?.fuente_plan?.pagina
            ?: successState?.response?.plan_mantenimiento_periodico?.firstOrNull { (it.pagina ?: 0) > 0 }?.pagina
    }

    val itemsPreventivos = remember(planActivo, kmActual, serviceRecords) {
        if (planActivo.isEmpty()) emptyList()
        else {
            val ultimos = serviceRecords
                .groupBy { it.componente }
                .mapValues { (_, list) ->
                    val last = list.maxWith(compareBy({ it.kilometraje }, { it.fecha }))
                    UltimoServicio(last.kilometraje, last.fecha, last.notas)
                }
            MaintenanceCalculator.calcularResumenPorKilometraje(
                plan = planActivo,
                kmActual = kmActual,
                ultimosServicios = ultimos,
                ahoraMillis = System.currentTimeMillis()
            )
        }
    }

    val nombreMoto = remember(viewModel.formMarca, viewModel.formModelo, successState) {
        val desdeSuccess = successState?.response?.moto_info?.let {
            "${it.marca.orEmpty()} ${it.modelo.orEmpty()}".trim()
        }.orEmpty()
        val desdeForm = "${viewModel.formMarca} ${viewModel.formModelo}".trim()
        when {
            desdeSuccess.isNotBlank() -> desdeSuccess
            desdeForm.isNotBlank() -> desdeForm
            else -> "Motocicleta Activa"
        }
    }

    val subtituloMoto = remember(viewModel.formCilindraje, viewModel.formAnio, successState) {
        val cc = viewModel.formCilindraje.ifBlank { successState?.response?.moto_info?.cilindraje.orEmpty() }
        val anio = viewModel.formAnio.ifBlank { successState?.response?.moto_info?.anio?.toString().orEmpty() }
        listOfNotNull(
            cc.takeIf { it.isNotBlank() },
            anio.takeIf { it.isNotBlank() }?.let { "Modelo $it" }
        ).joinToString(" • ").ifBlank { "Cálculo dinámico por odómetro" }
    }

    // Evalúa alertas locales solo cuando existe un plan oficial del fabricante (sin intervalos inventados)
    val alertaNotificacion = remember(nombreMoto, kmActual, itemsPreventivos, umbralAlertaKm, usaPlanManualOficial) {
        if (!usaPlanManualOficial) null
        else MaintenanceNotificationHelper.evaluarAlertasPorKilometraje(
            nombreMoto = nombreMoto,
            kmActual = kmActual,
            items = itemsPreventivos,
            umbralKm = umbralAlertaKm
        )
    }

    var permisoNotificacionConcedido by remember {
        mutableStateOf(MaintenanceNotificationHelper.hasNotificationPermission(context))
    }
    var permisoDenegadoAviso by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permisoNotificacionConcedido = isGranted
        permisoDenegadoAviso = !isGranted
        if (usaPlanManualOficial && isGranted && alertaNotificacion != null) {
            val enviada = MaintenanceNotificationHelper.mostrarNotificacionMantenimiento(context, alertaNotificacion)
            if (enviada) {
                val hora = formatSummaryHora(System.currentTimeMillis())
                viewModel.registrarNotificacionEnviada(
                    resumen = "Notificación enviada ($hora): ${alertaNotificacion.mensajeCorto}",
                    claveEstado = "${nombreMoto}_${kmActual}_${umbralAlertaKm}_${alertaNotificacion.tareasEnAlerta.size}"
                )
            }
        }
    }

    fun dispararNotificacionAhora(manual: Boolean) {
        if (!usaPlanManualOficial) return
        val alerta = alertaNotificacion ?: return
        if (!MaintenanceNotificationHelper.hasNotificationPermission(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && manual) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }
        val claveEstado = "${nombreMoto}_${kmActual}_${umbralAlertaKm}_${alerta.tareasEnAlerta.size}"
        if (!manual && viewModel.ultimoEstadoNotificadoKey == claveEstado) return

        val enviada = MaintenanceNotificationHelper.mostrarNotificacionMantenimiento(context, alerta)
        if (enviada) {
            val hora = formatSummaryHora(System.currentTimeMillis())
            viewModel.registrarNotificacionEnviada(
                resumen = "Alerta local enviada a las $hora • ${alerta.mensajeCorto}",
                claveEstado = claveEstado
            )
        }
    }

    // Disparo automático solo con plan oficial y cuando las notificaciones están activas
    LaunchedEffect(alertaNotificacion, notificacionesActivas, permisoNotificacionConcedido, usaPlanManualOficial) {
        if (usaPlanManualOficial && notificacionesActivas && alertaNotificacion != null && permisoNotificacionConcedido) {
            dispararNotificacionAhora(manual = false)
        }
    }

    val totalVencidos = itemsPreventivos.count { it.estado == EstadoMantenimiento.VENCIDO }
    val totalProximos = itemsPreventivos.count { it.estado == EstadoMantenimiento.PROXIMO }
    val totalAlDia = itemsPreventivos.count { it.estado == EstadoMantenimiento.AL_DIA }

    val itemsFiltrados = remember(itemsPreventivos, filtroSeleccionado) {
        when (filtroSeleccionado) {
            FiltroResumenPreventivo.TODOS -> itemsPreventivos
            FiltroResumenPreventivo.URGENTES -> itemsPreventivos.filter { it.estado == EstadoMantenimiento.VENCIDO }
            FiltroResumenPreventivo.PROXIMOS -> itemsPreventivos.filter { it.estado == EstadoMantenimiento.PROXIMO }
            FiltroResumenPreventivo.EN_CICLO -> itemsPreventivos.filter { it.estado == EstadoMantenimiento.AL_DIA }
        }
    }

    val proximoPrioritario = itemsPreventivos.firstOrNull()

    Scaffold(
        containerColor = MotoBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(MotoPrimary, CircleShape)
                            )
                            Text(
                                "Resumen Preventivo por KM",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MotoTextPrimary
                            )
                        }
                        Text(
                            "Próximos mantenimientos según odómetro actual",
                            fontSize = 12.sp,
                            color = MotoTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MotoBg),
                actions = {
                    ThemeModeTopBarButton(
                        currentMode = MotoThemeState.themeMode,
                        onSelectMode = { chosen -> viewModel.setThemeMode(chosen, context) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    FilledTonalButton(
                        onClick = onNavigateToCockpit,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MotoSurfaceVariant,
                            contentColor = MotoAccentBlue
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("cambiar_moto_resumen_button")
                    ) {
                        Icon(
                            Icons.Default.TwoWheeler,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ficha / Moto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("maintenance_summary_list"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (successState == null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MotoBorder, RoundedCornerShape(18.dp))
                            .testTag("sin_moto_resumen_card"),
                        colors = CardDefaults.cardColors(containerColor = MotoSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.TwoWheeler,
                                contentDescription = null,
                                tint = MotoAccentBlue,
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                "Consulta tu motocicleta primero",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MotoTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Para calcular tu próximo mantenimiento y activar alertas locales sin inventar intervalos, primero busca tu moto en el Cockpit y carga el plan oficial de su fabricante.",
                                fontSize = 13.sp,
                                color = MotoTextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )
                            Button(
                                onClick = onNavigateToCockpit,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ir al Cockpit", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                // 1. Panel de Odómetro Actual + Moto Activa
                item {
                    OdometerControlCard(
                        nombreMoto = nombreMoto,
                        subtituloMoto = subtituloMoto,
                        usaPlanManualOficial = usaPlanManualOficial,
                        manualTitulo = successState.response.manual?.titulo,
                        paginaPlan = paginaPlanOficial,
                        kmInputText = kmInputText,
                        kmActual = kmActual,
                        onKmTextChange = { nuevoTexto ->
                            val limpio = nuevoTexto.filter { it.isDigit() }.take(7)
                            kmInputText = limpio
                            limpio.toIntOrNull()?.let { viewModel.updateCurrentKilometraje(it) }
                        },
                        onAdjustKm = { delta ->
                            val nuevo = (kmActual + delta).coerceAtLeast(0)
                            kmInputText = nuevo.toString()
                            viewModel.updateCurrentKilometraje(nuevo)
                        }
                    )
                }

                if (!usaPlanManualOficial) {
                    item {
                        val marcaVisible = successState.marca.ifBlank {
                            successState.response.moto_info?.marca.orEmpty().ifBlank { "El fabricante" }
                        }
                        val manualUrl = successState.response.manual?.url
                        val modeloNombre = successState.response.moto_info?.modelo.orEmpty()
                        val cilindrajeTxt = successState.response.moto_info?.cilindraje.orEmpty()
                        val anioNum = successState.response.moto_info?.anio ?: 0
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, InfoBorder, RoundedCornerShape(18.dp))
                                .testTag("resumen_sin_plan_oficial_card"),
                            colors = CardDefaults.cardColors(containerColor = MotoSurface)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = InfoText,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        "Plan del fabricante aún no disponible",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MotoTextPrimary
                                    )
                                }
                                Text(
                                    "$marcaVisible aún no publica el plan de mantenimiento de este modelo en su sitio. Las alertas automáticas de kilometraje solo se activan cuando existe un plan oficial del fabricante.",
                                    fontSize = 13.sp,
                                    color = MotoTextSecondary,
                                    lineHeight = 19.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!manualUrl.isNullOrBlank()) {
                                        OutlinedButton(
                                            onClick = { runCatching { uriHandler.openUri(manualUrl) } },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("resumen_ver_manual_button")
                                        ) {
                                            Text("Ver manual oficial", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            if (anioNum > 0) {
                                                viewModel.solicitarAnio(marcaVisible, modeloNombre, cilindrajeTxt, anioNum)
                                            }
                                        },
                                        enabled = anioNum > 0 &&
                                            solicitud != SolicitudEstado.ENVIANDO &&
                                            solicitud != SolicitudEstado.ENVIADA &&
                                            solicitud != SolicitudEstado.EN_COLA,
                                        colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("resumen_solicitar_plan_button")
                                    ) {
                                        val label = when (solicitud) {
                                            SolicitudEstado.ENVIANDO -> "Enviando…"
                                            SolicitudEstado.ENVIADA -> "Solicitado"
                                            SolicitudEstado.EN_COLA -> "En cola"
                                            else -> "Solicitar"
                                        }
                                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 2. Indicadores KPI de Estado Preventivo
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PreventiveKpiCard(
                                titulo = "Toca Ahora",
                                valor = totalVencidos.toString(),
                                subtitulo = if (totalVencidos == 1) "1 pendiente" else "$totalVencidos pendientes",
                                icono = Icons.Default.Warning,
                                bgColor = if (totalVencidos > 0) UrgentBg else MotoSurface,
                                borderColor = if (totalVencidos > 0) UrgentBorder else MotoBorder,
                                textColor = if (totalVencidos > 0) UrgentText else MotoTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            PreventiveKpiCard(
                                titulo = "Próximos",
                                valor = totalProximos.toString(),
                                subtitulo = "En margen ≤10%",
                                icono = Icons.Default.Schedule,
                                bgColor = if (totalProximos > 0) WarningBg else MotoSurface,
                                borderColor = if (totalProximos > 0) WarningBorder else MotoBorder,
                                textColor = if (totalProximos > 0) WarningText else MotoTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            PreventiveKpiCard(
                                titulo = "En Ciclo",
                                valor = totalAlDia.toString(),
                                subtitulo = "Al día por km",
                                icono = Icons.Default.CheckCircle,
                                bgColor = RoutineBg.copy(alpha = 0.65f),
                                borderColor = RoutineBorder,
                                textColor = RoutineText,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 3. Sistema de Notificaciones Locales de Proximidad de Mantenimiento
                    item {
                        PreventiveNotificationControlCard(
                            notificacionesActivas = notificacionesActivas,
                            umbralAlertaKm = umbralAlertaKm,
                            alertaNotificacion = alertaNotificacion,
                            ultimaNotificacionResumen = ultimaNotificacionResumen,
                            permisoConcedido = permisoNotificacionConcedido,
                            permisoDenegadoAviso = permisoDenegadoAviso,
                            onToggleNotificaciones = { activas ->
                                viewModel.setNotificacionesActivas(activas)
                                if (activas && !permisoNotificacionConcedido && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onSelectUmbralKm = { nuevoUmbral ->
                                viewModel.setUmbralAlertaKm(nuevoUmbral)
                            },
                            onSendNotificationNow = {
                                dispararNotificacionAhora(manual = true)
                            }
                        )
                    }

                    // 4. Tarjeta destacada: Próxima Parada Preventiva más cercana
                    if (proximoPrioritario != null) {
                        item {
                            NextPreventiveHighlightCard(
                                item = proximoPrioritario,
                                kmActual = kmActual,
                                onRegister = { itemParaRegistrar = proximoPrioritario },
                                onScheduleWorkshop = onNavigateToWorkshops
                            )
                        }
                    }

                    // 5. Filtros por estado preventivo
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "CRONOGRAMA PREVENTIVO A ${formatSummaryKm(kmActual)} KM",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MotoTextSecondary
                                )
                                Text(
                                    "${itemsFiltrados.size} de ${itemsPreventivos.size} tareas",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MotoAccentBlue
                                )
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.testTag("filtro_mantenimiento_chips")
                            ) {
                                items(FiltroResumenPreventivo.values().toList()) { filtro ->
                                    val selected = filtroSeleccionado == filtro
                                    ElevatedFilterChip(
                                        selected = selected,
                                        onClick = { filtroSeleccionado = filtro },
                                        label = {
                                            Text(
                                                filtro.etiqueta,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MotoPrimary,
                                            selectedLabelColor = Color.White,
                                            containerColor = MotoSurfaceVariant,
                                            labelColor = MotoTextSecondary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selected,
                                            borderColor = if (selected) MotoPrimary else MotoBorder
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 6. Listado de tarjetas de próximos mantenimientos preventivos
                    if (itemsFiltrados.isEmpty()) {
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
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = RoutineText,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        "No hay mantenimientos en esta categoría",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MotoTextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "Prueba seleccionando el filtro \"Todos\" o ajusta el kilometraje actual arriba para simular próximos hitos.",
                                        fontSize = 12.sp,
                                        color = MotoTextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(itemsFiltrados, key = { it.componente }) { item ->
                            PreventiveMaintenanceItemCard(
                                item = item,
                                kmActual = kmActual,
                                onRegisterService = { itemParaRegistrar = item },
                                onFindWorkshop = onNavigateToWorkshops
                            )
                        }
                    }
                }
            }

            // 7. Historial de servicios realizados en esta motocicleta (con fecha y notas)
            if (serviceRecords.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "HISTORIAL GUARDADO EN ESTA MOTO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MotoTextSecondary
                        )
                        Text(
                            "${serviceRecords.size} registros",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RoutineText
                        )
                    }
                }

                items(serviceRecords, key = { "rec_${it.id}" }) { record ->
                    SavedServiceRecordCard(
                        record = record,
                        onDelete = { viewModel.deleteServiceRecord(record.id) }
                    )
                }
            }
        }
    }

    // Modal para registrar un servicio realizado con fecha y notas
    itemParaRegistrar?.let { item ->
        SummaryRegisterServiceDialog(
            componente = item.componente,
            kmActual = kmActual,
            onDismiss = { itemParaRegistrar = null },
            onConfirm = { kmRealizado, fechaMillis, notas ->
                val key = viewModel.ensureActiveVehicleKey()
                viewModel.registerService(
                    vehicleKey = key,
                    componente = item.componente,
                    kilometraje = kmRealizado,
                    fechaMillis = fechaMillis,
                    notas = notas
                )
                itemParaRegistrar = null
            }
        )
    }
}

@Composable
private fun PreventiveNotificationControlCard(
    notificacionesActivas: Boolean,
    umbralAlertaKm: Int,
    alertaNotificacion: ResumenAlertaNotificacion?,
    ultimaNotificacionResumen: String?,
    permisoConcedido: Boolean,
    permisoDenegadoAviso: Boolean,
    onToggleNotificaciones: (Boolean) -> Unit,
    onSelectUmbralKm: (Int) -> Unit,
    onSendNotificationNow: () -> Unit
) {
    val umbralesDisponibles = listOf(250, 500, 1000)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MotoBorder, RoundedCornerShape(18.dp))
            .testTag("notificaciones_preventivas_card"),
        colors = CardDefaults.cardColors(containerColor = MotoSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            .size(38.dp)
                            .background(
                                if (notificacionesActivas) MotoPrimary.copy(alpha = 0.18f) else MotoSurfaceVariant,
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (notificacionesActivas) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = if (notificacionesActivas) MotoPrimary else MotoTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Notificaciones Locales de Mantenimiento",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MotoTextPrimary
                        )
                        Text(
                            text = if (notificacionesActivas) {
                                "Aviso automático cuando falten ≤ ${formatSummaryKm(umbralAlertaKm)} km"
                            } else {
                                "Alertas automáticas desactivadas"
                            },
                            fontSize = 11.sp,
                            color = MotoTextSecondary
                        )
                    }
                }

                Switch(
                    checked = notificacionesActivas,
                    onCheckedChange = onToggleNotificaciones,
                    modifier = Modifier.testTag("toggle_notificaciones_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MotoPrimary
                    )
                )
            }

            // Selector de umbral de kilometraje para alerta anticipada
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Avisar antes de:",
                    fontSize = 11.sp,
                    color = MotoTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    umbralesDisponibles.forEach { umbral ->
                        val isSelected = umbral == umbralAlertaKm
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MotoPrimary else MotoSurfaceVariant,
                            modifier = Modifier
                                .border(
                                    0.5.dp,
                                    if (isSelected) MotoPrimary else MotoBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectUmbralKm(umbral) }
                        ) {
                            Text(
                                text = "${formatSummaryKm(umbral)} km",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) Color.White else MotoTextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Vista previa del estado de alerta actual
            if (alertaNotificacion != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarningBg.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, WarningBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${alertaNotificacion.tareasEnAlerta.size} mantenimiento(s) dentro del umbral de alerta:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningText
                        )
                        alertaNotificacion.lineasDetalle.take(3).forEach { linea ->
                            Text(
                                text = linea,
                                fontSize = 11.sp,
                                color = WarningText.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                Button(
                    onClick = onSendNotificationNow,
                    colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("enviar_notificacion_button")
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (permisoConcedido) {
                            "Enviar Notificación Local al Teléfono"
                        } else {
                            "Activar Permiso y Enviar Notificación"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "Ningún componente está a menos de ${formatSummaryKm(umbralAlertaKm)} km de su próximo servicio.",
                    fontSize = 11.sp,
                    color = RoutineText
                )
            }

            if (permisoDenegadoAviso) {
                Text(
                    text = "Permiso de notificaciones denegado en Android. Actívalo en los ajustes del sistema para recibir avisos en la barra de estado.",
                    fontSize = 11.sp,
                    color = UrgentText
                )
            }

            if (ultimaNotificacionResumen != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("notificacion_confirmacion_text")
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = RoutineText,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = ultimaNotificacionResumen,
                        fontSize = 11.sp,
                        color = RoutineText
                    )
                }
            }
        }
    }
}

@Composable
private fun OdometerControlCard(
    nombreMoto: String,
    subtituloMoto: String,
    usaPlanManualOficial: Boolean,
    manualTitulo: String?,
    paginaPlan: Int?,
    kmInputText: String,
    kmActual: Int,
    onKmTextChange: (String) -> Unit,
    onAdjustKm: (Int) -> Unit
) {
    val quickDeltas = listOf(-500, 250, 500, 1000, 3000)

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
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MotoPrimary.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .border(1.dp, MotoPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MotoPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        Text(
                            text = nombreMoto,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MotoTextPrimary
                        )
                        Text(
                            text = subtituloMoto,
                            fontSize = 12.sp,
                            color = MotoTextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (usaPlanManualOficial) RoutineBg else WarningBg,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            0.5.dp,
                            if (usaPlanManualOficial) RoutineBorder else WarningBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (usaPlanManualOficial) "PLAN OFICIAL" else "SIN PLAN OFICIAL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (usaPlanManualOficial) RoutineText else WarningText
                    )
                }
            }

            if (usaPlanManualOficial) {
                val tituloLimpio = manualTitulo?.takeIf { it.isNotBlank() && !it.equals("untitled", ignoreCase = true) }
                val textoFuente = when {
                    tituloLimpio != null && paginaPlan != null -> "Según $tituloLimpio · pág. $paginaPlan"
                    paginaPlan != null -> "Según el manual oficial, pág. $paginaPlan"
                    tituloLimpio != null -> "Fuente activa: $tituloLimpio"
                    else -> "Plan oficial del fabricante activo"
                }
                Text(
                    text = textoFuente,
                    fontSize = 11.sp,
                    color = RoutineText.copy(alpha = 0.9f)
                )
            } else {
                Text(
                    text = "Sin plan de mantenimiento oficial publicado para este modelo.",
                    fontSize = 11.sp,
                    color = MotoTextSecondary
                )
            }

            // Campo de Kilometraje Actual
            OutlinedTextField(
                value = kmInputText,
                onValueChange = onKmTextChange,
                label = { Text("Kilometraje actual de la motocicleta (KM)") },
                leadingIcon = {
                    Icon(Icons.Default.EditRoad, contentDescription = null, tint = MotoAccentBlue)
                },
                trailingIcon = {
                    Text(
                        text = "${formatSummaryKm(kmActual)} km",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MotoAccentBlue,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resumen_km_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MotoTextPrimary,
                    unfocusedTextColor = MotoTextPrimary,
                    focusedBorderColor = MotoPrimary,
                    unfocusedBorderColor = MotoBorder,
                    focusedLabelColor = MotoPrimary,
                    unfocusedLabelColor = MotoTextSecondary
                )
            )

            // Botones rápidos para simular/ajustar kilometraje
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Ajuste rápido de odómetro:",
                    fontSize = 11.sp,
                    color = MotoTextSecondary
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickDeltas) { delta ->
                        val sign = if (delta > 0) "+" else ""
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MotoSurfaceVariant,
                            modifier = Modifier
                                .border(0.5.dp, MotoBorder, RoundedCornerShape(8.dp))
                                .clickable { onAdjustKm(delta) }
                        ) {
                            Text(
                                text = "$sign${formatSummaryKm(delta)} km",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (delta > 0) MotoAccentBlue else MotoTextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreventiveKpiCard(
    titulo: String,
    valor: String,
    subtitulo: String,
    icono: ImageVector,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icono, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                Text(titulo, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
            }
            Text(
                text = valor,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = textColor
            )
            Text(
                text = subtitulo,
                fontSize = 10.sp,
                color = textColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun NextPreventiveHighlightCard(
    item: ProximoMantenimientoPreventivo,
    kmActual: Int,
    onRegister: () -> Unit,
    onScheduleWorkshop: () -> Unit
) {
    val esUrgente = item.estado == EstadoMantenimiento.VENCIDO
    val esProximo = item.estado == EstadoMantenimiento.PROXIMO

    val containerBg = when {
        esUrgente -> UrgentBg
        esProximo -> WarningBg
        else -> InfoBg
    }
    val borderCol = when {
        esUrgente -> UrgentBorder
        esProximo -> WarningBorder
        else -> InfoBorder
    }
    val accentCol = when {
        esUrgente -> UrgentText
        esProximo -> WarningText
        else -> InfoText
    }

    val distanciaTexto = item.kmRestantes?.let { kmRest ->
        when {
            kmRest < 0 -> "Vencido hace ${formatSummaryKm(-kmRest)} km"
            kmRest == 0 -> "¡Corresponde exactamente a los ${formatSummaryKm(kmActual)} km!"
            else -> "Faltan ${formatSummaryKm(kmRest)} km"
        }
    } ?: "Revisión periódica pendiente"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderCol, RoundedCornerShape(18.dp))
            .testTag("proxima_parada_card"),
        colors = CardDefaults.cardColors(containerColor = containerBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        if (esUrgente) Icons.Default.PriorityHigh else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = accentCol,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (esUrgente) "ATENCIÓN PREVENTIVA INMEDIATA" else "PRÓXIMA PARADA PREVENTIVA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = accentCol
                    )
                }

                item.proximoKilometraje?.let { hitoKm ->
                    Box(
                        modifier = Modifier
                            .background(borderCol, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "META: ${formatSummaryKm(hitoKm)} KM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = accentCol
                        )
                    }
                }
            }

            Text(
                text = item.componente,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentCol
            )

            item.plan.accion?.takeIf { it.isNotBlank() }?.let { accion ->
                Text(
                    text = accion,
                    fontSize = 12.sp,
                    color = accentCol.copy(alpha = 0.9f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = distanciaTexto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accentCol
                )
                Text(
                    text = "${(item.progresoCiclo * 100).roundToInt()}% del intervalo",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentCol.copy(alpha = 0.85f)
                )
            }

            LinearProgressIndicator(
                progress = { item.progresoCiclo.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accentCol,
                trackColor = Color.Black.copy(alpha = 0.35f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRegister,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentCol)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ya lo realicé", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onScheduleWorkshop,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary)
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agendar Taller", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PreventiveMaintenanceItemCard(
    item: ProximoMantenimientoPreventivo,
    kmActual: Int,
    onRegisterService: () -> Unit,
    onFindWorkshop: () -> Unit
) {
    val (badgeLabel, badgeBg, badgeTextCol, borderCol) = when (item.estado) {
        EstadoMantenimiento.VENCIDO -> QuadrupleStatus("TOCA AHORA / VENCIDO", UrgentBg, UrgentText, UrgentBorder)
        EstadoMantenimiento.PROXIMO -> QuadrupleStatus("PRÓXIMO (≤10%)", WarningBg, WarningText, WarningBorder)
        EstadoMantenimiento.AL_DIA -> QuadrupleStatus("EN CICLO PREVENTIVO", RoutineBg, RoutineText, RoutineBorder)
        EstadoMantenimiento.SIN_HISTORIAL -> QuadrupleStatus("SEGÚN INTERVALO", InfoBg, InfoText, InfoBorder)
    }

    val intervaloDesc = buildList {
        item.plan.intervalo_km?.takeIf { it > 0 }?.let { add("Cada ${formatSummaryKm(it)} km") }
        item.plan.intervalo_meses?.takeIf { it > 0 }?.let { add(if (it == 1) "1 mes" else "$it meses") }
    }.joinToString(" o ")

    val restanteDesc = item.kmRestantes?.let { kmRest ->
        when {
            kmRest < 0 -> "Pasado por ${formatSummaryKm(-kmRest)} km"
            kmRest == 0 -> "Toca hoy (${formatSummaryKm(kmActual)} km)"
            else -> "Faltan ${formatSummaryKm(kmRest)} km"
        }
    } ?: "Por tiempo"

    val proximoHitoDesc = item.proximoKilometraje?.let {
        "A los ${formatSummaryKm(it)} km"
    } ?: "Según calendario"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderCol, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Fila superior: Estado + Origen del cálculo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(6.dp))
                        .border(0.5.dp, borderCol, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextCol
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.plan.pagina?.takeIf { it > 0 }?.let { pag ->
                        Box(
                            modifier = Modifier
                                .background(InfoBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Pág. $pag",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = InfoText
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(MotoSurfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (item.basadoEnRegistro) "Según tu registro" else "Ciclo por odómetro",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MotoTextSecondary
                        )
                    }
                }
            }

            // Nombre del componente y acción
            Text(
                text = item.componente,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MotoTextPrimary
            )

            item.plan.accion?.takeIf { it.isNotBlank() }?.let { accion ->
                Text(
                    text = accion,
                    fontSize = 12.sp,
                    color = MotoTextSecondary,
                    lineHeight = 16.sp
                )
            }

            // Tres píldoras de telemetría: Intervalo, Próximo Hito, Restante
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMetricPill(
                    label = "Intervalo",
                    value = intervaloDesc,
                    valueColor = MotoTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricPill(
                    label = "Próximo servicio",
                    value = proximoHitoDesc,
                    valueColor = MotoAccentBlue,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricPill(
                    label = "Estado en KM",
                    value = restanteDesc,
                    valueColor = badgeTextCol,
                    modifier = Modifier.weight(1f)
                )
            }

            // Barra de progreso del ciclo
            if (item.plan.intervalo_km != null && item.plan.intervalo_km > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val recorridos = item.kmRecorridosEnCiclo ?: 0
                        Text(
                            text = "Avance del ciclo: ${formatSummaryKm(recorridos)} / ${formatSummaryKm(item.plan.intervalo_km)} km",
                            fontSize = 11.sp,
                            color = MotoTextSecondary
                        )
                        Text(
                            text = "${(item.progresoCiclo * 100).roundToInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = badgeTextCol
                        )
                    }
                    LinearProgressIndicator(
                        progress = { item.progresoCiclo.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = badgeTextCol,
                        trackColor = MotoSurfaceVariant
                    )
                }
            }

            // Si tiene último servicio registrado y notas, mostrarlo
            item.ultimo?.let { ultimo ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MotoSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Último servicio: a los ${formatSummaryKm(ultimo.kilometraje)} km • Fecha: ${formatSummaryFecha(ultimo.fechaMillis)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoutineText
                    )
                    if (ultimo.notas.isNotBlank()) {
                        Text(
                            text = "Notas: ${ultimo.notas}",
                            fontSize = 11.sp,
                            color = MotoTextSecondary
                        )
                    }
                }
            }

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRegisterService,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = MotoAccentBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Registrar servicio hecho",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MotoAccentBlue
                    )
                }

                if (item.estado == EstadoMantenimiento.VENCIDO || item.estado == EstadoMantenimiento.PROXIMO) {
                    Button(
                        onClick = onFindWorkshop,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.estado == EstadoMantenimiento.VENCIDO) UrgentIconBg else MotoPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Taller", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedServiceRecordCard(
    record: ServiceRecordEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MotoBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.EventAvailable,
                        contentDescription = null,
                        tint = RoutineText,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = record.componente.replaceFirstChar { it.uppercase() },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MotoTextPrimary
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Eliminar registro",
                        tint = MotoTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fecha: ${formatSummaryFecha(record.fecha)}",
                    fontSize = 12.sp,
                    color = MotoAccentBlue,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "•",
                    fontSize = 12.sp,
                    color = MotoTextMuted
                )
                Text(
                    text = "${formatSummaryKm(record.kilometraje)} KM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = RoutineText
                )
            }

            if (record.notas.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MotoSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Notas: ${record.notas}",
                        fontSize = 12.sp,
                        color = MotoTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricPill(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MotoSurfaceVariant, RoundedCornerShape(10.dp))
            .border(0.5.dp, MotoBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = MotoTextSecondary
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}

@Composable
private fun SummaryRegisterServiceDialog(
    componente: String,
    kmActual: Int,
    onDismiss: () -> Unit,
    onConfirm: (km: Int, fechaMillis: Long, notas: String) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    var kmText by remember(kmActual) { mutableStateOf(kmActual.toString()) }
    var fechaText by remember { mutableStateOf(formatSummaryFecha(now)) }
    var notasText by remember { mutableStateOf("") }
    var kmError by remember { mutableStateOf<String?>(null) }
    var fechaError by remember { mutableStateOf<String?>(null) }

    val quickDateOptions = remember(now) {
        listOf(
            "Hoy" to formatSummaryFecha(now),
            "Ayer" to formatSummaryFecha(now - ONE_DAY_MS),
            "Hace 15 días" to formatSummaryFecha(now - 15 * ONE_DAY_MS),
            "Hace 1 mes" to formatSummaryFecha(now - 30 * ONE_DAY_MS)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MotoBorder, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = MotoSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Registrar mantenimiento realizado",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MotoTextPrimary
                )
                Text(
                    componente,
                    fontSize = 13.sp,
                    color = MotoAccentBlue,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = kmText,
                    onValueChange = { kmText = it; kmError = null },
                    label = { Text("Kilometraje cuando se realizó") },
                    singleLine = true,
                    isError = kmError != null,
                    supportingText = kmError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resumen_servicio_km_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MotoTextPrimary,
                        unfocusedTextColor = MotoTextPrimary,
                        focusedBorderColor = MotoPrimary,
                        unfocusedBorderColor = MotoBorder
                    )
                )

                OutlinedTextField(
                    value = fechaText,
                    onValueChange = { fechaText = it; fechaError = null },
                    label = { Text("Fecha de servicio (dd/mm/aaaa)") },
                    singleLine = true,
                    isError = fechaError != null,
                    supportingText = fechaError?.let { err -> { Text(err) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resumen_servicio_fecha_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MotoTextPrimary,
                        unfocusedTextColor = MotoTextPrimary,
                        focusedBorderColor = MotoPrimary,
                        unfocusedBorderColor = MotoBorder
                    )
                )

                // Chips de selección rápida de fecha
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickDateOptions) { (label, dateStr) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (fechaText == dateStr) MotoPrimary.copy(alpha = 0.25f) else MotoSurfaceVariant,
                            modifier = Modifier
                                .border(
                                    0.5.dp,
                                    if (fechaText == dateStr) MotoPrimary else MotoBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    fechaText = dateStr
                                    fechaError = null
                                }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = if (fechaText == dateStr) MotoTextPrimary else MotoTextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notasText,
                    onValueChange = { notasText = it },
                    label = { Text("Notas del servicio (marca de aceite, repuestos, observaciones)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resumen_servicio_notas_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MotoTextPrimary,
                        unfocusedTextColor = MotoTextPrimary,
                        focusedBorderColor = MotoPrimary,
                        unfocusedBorderColor = MotoBorder
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MotoTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val km = kmText.trim().toIntOrNull()
                            val fecha = parseSummaryFecha(fechaText)
                            kmError = when {
                                km == null || km < 0 -> "Ingresa un kilometraje válido"
                                km > kmActual -> "No puede superar el kilometraje actual (${formatSummaryKm(kmActual)} km)"
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
                        modifier = Modifier.testTag("resumen_servicio_guardar_button")
                    ) {
                        Text("Guardar y recalcular", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class QuadrupleStatus(
    val label: String,
    val bg: Color,
    val text: Color,
    val border: Color
)
