package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val isFleetMode by viewModel.isFleetMode.collectAsStateWithLifecycle()
    val fleetBikes by viewModel.fleetBikes.collectAsStateWithLifecycle()
    var showDispatchDialog by remember { mutableStateOf<FleetBike?>(null) }

    Scaffold(
        containerColor = MotoBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = WarningText, modifier = Modifier.size(20.dp))
                            Text("B2B Flotas & Planes Premium", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                        }
                        Text("Monitoreo empresarial y garaje ilimitado", fontSize = 12.sp, color = MotoTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MotoBg)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Segmented Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MotoSurface,
                contentColor = MotoPrimary,
                divider = { Divider(color = MotoBorder) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Modo Flotas B2B", fontWeight = FontWeight.Bold)
                        }
                    },
                    selectedContentColor = MotoAccentCyan,
                    unselectedContentColor = MotoTextSecondary
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Planes & Upgrade", fontWeight = FontWeight.Bold)
                        }
                    },
                    selectedContentColor = MotoPrimary,
                    unselectedContentColor = MotoTextSecondary
                )
            }

            if (selectedTab == 0) {
                // =========================================
                // 5. MÓDULO B2B (MODO FLOTAS / EMPRESAS)
                // =========================================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Fleet Control Header Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MotoBorder, RoundedCornerShape(18.dp)),
                            colors = CardDefaults.cardColors(containerColor = MotoSurface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("RAPIMOTOS LOGISTICS S.A.S", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MotoAccentCyan, letterSpacing = 1.sp)
                                        Text("Consola Global de Telemetría", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(RoutineBg, RoundedCornerShape(8.dp))
                                            .border(1.dp, RoutineBorder, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("89% OPERATIVO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RoutineText)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FleetKpiCard(title = "Total Flota", value = "${fleetBikes.size} Motos", icon = Icons.Default.TwoWheeler, color = MotoTextPrimary, modifier = Modifier.weight(1f))
                                    FleetKpiCard(title = "Urgencias", value = "2 Requeridas", icon = Icons.Default.Warning, color = UrgentText, modifier = Modifier.weight(1f))
                                    FleetKpiCard(title = "En Ruta", value = "100%", icon = Icons.Default.CheckCircle, color = RoutineText, modifier = Modifier.weight(1f))
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Monitoreo preventivo continuo para evitar detenciones imprevistas y optimizar el consumo de combustible.",
                                    fontSize = 11.sp,
                                    color = MotoTextSecondary
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            "MOTOCICLETAS EN OPERACIÓN (${fleetBikes.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MotoTextSecondary
                        )
                    }

                    // Fleet Bike Cards
                    items(fleetBikes, key = { it.id }) { bike ->
                        FleetBikeCard(
                            bike = bike,
                            onDispatch = { showDispatchDialog = bike }
                        )
                    }
                }
            } else {
                // =========================================
                // 3. MONETIZACIÓN B2C: CAPA PREMIUM
                // =========================================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (isPremium) RoutineBorder else MotoPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = MotoSurface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(if (isPremium) RoutineBg else MotoPrimary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isPremium) Icons.Default.CheckCircle else Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = if (isPremium) RoutineText else MotoPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (isPremium) "Suscripción Premium Activa" else "Desbloquea MotoTech Premium",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MotoTextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (isPremium) "Tienes acceso ilimitado a todas las herramientas profesionales." else "Pasa de 1 moto a Garaje Ilimitado + Hoja de Vida en la Nube y Alertas SOAT/RTM",
                                    fontSize = 13.sp,
                                    color = MotoTextSecondary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(20.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    BenefitRow(text = "Gestor de Múltiples Motocicletas (Garaje Ilimitado)")
                                    BenefitRow(text = "Exportación Oficial de Hoja de Vida en PDF para Reventa")
                                    BenefitRow(text = "Adjunto Ilimitado de Facturas y Fotos de Repuestos")
                                    BenefitRow(text = "Alertas Automatizadas de SOAT, Tecnomecánica y Aceite")
                                    BenefitRow(text = "15% de Descuento Exclusivo en Talleres Certificados Aliados")
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { viewModel.togglePremium() },
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("toggle_premium_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPremium) RoutineIconBg else MotoPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        if (isPremium) "Desactivar / Probar Capa Gratuita" else "Desbloquear con Premium ($4.99/mes)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Plan B2B Enterprise Card
                    item {
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
                                    Column {
                                        Text("PLAN EMPRESAS & FLOTAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MotoAccentCyan)
                                        Text("SaaS para Domicilios y Logística", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                                    }
                                    Text("$29.99/mes", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Incluye gestión hasta 30 motocicletas, auditoría de mecánicos, integración de telemetría y soporte de repuestos al por mayor.",
                                    fontSize = 12.sp,
                                    color = MotoTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal para Despachar a Taller
    showDispatchDialog?.let { bike ->
        Dialog(onDismissRequest = { showDispatchDialog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotoPrimary, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Despacho a Taller Aliado", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Moto: ${bike.codigo} • ${bike.modelo} (${bike.placa})", fontSize = 13.sp, color = MotoAccentCyan)
                    Text("Conductor: ${bike.conductor}", fontSize = 12.sp, color = MotoTextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UrgentBg, RoundedCornerShape(8.dp))
                            .border(1.dp, UrgentBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(bike.alertaPrincipal, fontSize = 12.sp, color = UrgentText, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.bookAppointment("Taller MotoTech Express", "Revisión Flota: " + bike.alertaPrincipal, "Inmediato", "Turno Prioritario")
                            showDispatchDialog = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary)
                    ) {
                        Text("Confirmar Cita Prioritaria Flota")
                    }
                }
            }
        }
    }
}

@Composable
fun FleetKpiCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MotoSurfaceVariant, RoundedCornerShape(12.dp))
            .border(0.5.dp, MotoBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            Text(title, fontSize = 10.sp, color = MotoTextSecondary)
        }
    }
}

@Composable
fun FleetBikeCard(
    bike: FleetBike,
    onDispatch: () -> Unit
) {
    val (statusBg, statusBorder, statusText, statusLabel) = when (bike.estado) {
        FleetHealthStatus.OPTIMO -> Quadruple(RoutineBg, RoutineBorder, RoutineText, "ÓPTIMO")
        FleetHealthStatus.REVISION_PROXIMA -> Quadruple(WarningBg, WarningBorder, WarningText, "REVISIÓN PRÓXIMA")
        FleetHealthStatus.ATENCION_URGENTE -> Quadruple(UrgentBg, UrgentBorder, UrgentText, "ATENCIÓN INMEDIATA")
    }

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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(bike.codigo, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary, fontFamily = FontFamily.Monospace)
                    Text("•", color = MotoBorder)
                    Text(bike.placa, fontSize = 13.sp, color = MotoTextSecondary, fontWeight = FontWeight.SemiBold)
                }

                Box(
                    modifier = Modifier
                        .background(statusBg, RoundedCornerShape(6.dp))
                        .border(0.5.dp, statusBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(statusLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statusText)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("${bike.modelo} (${bike.anio})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MotoTextPrimary)
            Text("Conductor: ${bike.conductor}", fontSize = 12.sp, color = MotoTextSecondary)
            Text("Odómetro: ${bike.kilometraje} km • Último service: hace ${bike.diasUltimoService} días", fontSize = 11.sp, color = MotoTextMuted)

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusBg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = statusText, modifier = Modifier.size(14.dp))
                    Text(bike.alertaPrincipal, fontSize = 11.sp, color = statusText, fontWeight = FontWeight.Medium)
                }
            }

            if (bike.estado != FleetHealthStatus.OPTIMO) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDispatch,
                    colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Despachar a Taller Aliado", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun BenefitRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(RoutineBg, CircleShape)
                .border(1.dp, RoutineBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = RoutineText, modifier = Modifier.size(12.dp))
        }
        Text(text, fontSize = 13.sp, color = MotoTextPrimary)
    }
}
