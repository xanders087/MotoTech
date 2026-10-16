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
import androidx.compose.ui.graphics.Brush
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
fun HistoryScreen(viewModel: MainViewModel) {
    val logs by viewModel.maintenanceLogs.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showPdfDialog by remember { mutableStateOf(false) }
    var viewingInvoice by remember { mutableStateOf<MaintenanceLogItem?>(null) }

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
                        Text("Certificación y registro cronológico", fontSize = 12.sp, color = MotoTextSecondary)
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
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MotoPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_service_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Registrar Servicio")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
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
                                Text("Yamaha MT-07 ABS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
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
                            Text("Placa: MT-070 • 24,500 KM • 689cc Bi-Cilíndrico", fontSize = 12.sp, color = MotoTextSecondary)
                        }
                    }
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
                        "HISTORIAL CRONOLÓGICO DE MANTENIMIENTO",
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

                    // Simulated Receipt Photo/Document
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
                            Text("Vehículo: Yamaha MT-07 (2023) - 24,500 KM", fontSize = 12.sp, color = MotoTextPrimary)
                            Text("Índice de Salud Mecánica: 94 / 100", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RoutineText)
                            Text("Mantenimientos Validados: 3 registros certificados", fontSize = 11.sp, color = MotoTextSecondary)
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

    // Modal: Registrar Nuevo Servicio
    if (showAddDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newKm by remember { mutableStateOf("") }
        var newWorkshop by remember { mutableStateOf("") }
        var newCost by remember { mutableStateOf("") }
        var hasReceipt by remember { mutableStateOf(true) }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Registrar Mantenimiento", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Servicio (ej. Cambio de Aceite)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MotoTextPrimary,
                            unfocusedTextColor = MotoTextPrimary,
                            focusedBorderColor = MotoPrimary,
                            unfocusedBorderColor = MotoBorder
                        )
                    )

                    OutlinedTextField(
                        value = newKm,
                        onValueChange = { newKm = it },
                        label = { Text("Kilometraje Actual") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MotoTextPrimary,
                            unfocusedTextColor = MotoTextPrimary,
                            focusedBorderColor = MotoPrimary,
                            unfocusedBorderColor = MotoBorder
                        )
                    )

                    OutlinedTextField(
                        value = newWorkshop,
                        onValueChange = { newWorkshop = it },
                        label = { Text("Taller / Mecánico") },
                        modifier = Modifier.fillMaxWidth(),
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
                        label = { Text("Costo (ej. $120.000 COP)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MotoTextPrimary,
                            unfocusedTextColor = MotoTextPrimary,
                            focusedBorderColor = MotoPrimary,
                            unfocusedBorderColor = MotoBorder
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MotoSurfaceVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MotoAccentCyan)
                            Text("Adjuntar Factura/Foto", fontSize = 13.sp, color = MotoTextPrimary)
                        }
                        Switch(
                            checked = hasReceipt,
                            onCheckedChange = { hasReceipt = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MotoPrimary, checkedTrackColor = MotoPrimary.copy(alpha = 0.5f))
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
                                if (newTitle.isNotBlank()) {
                                    viewModel.addMaintenanceRecord(
                                        titulo = newTitle,
                                        kilometraje = newKm.toIntOrNull() ?: 24500,
                                        taller = if (newWorkshop.isNotBlank()) newWorkshop else "Taller Particular",
                                        costo = if (newCost.isNotBlank()) newCost else "$0 COP",
                                        categoria = "Mantenimiento General"
                                    )
                                    showAddDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary)
                        ) {
                            Text("Guardar")
                        }
                    }
                }
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

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MotoBorder, thickness = 0.5.dp)
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
