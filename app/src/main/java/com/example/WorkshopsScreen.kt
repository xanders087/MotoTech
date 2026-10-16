package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import com.google.android.gms.location.LocationServices
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkshopsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("Todos") }
    var bookingWorkshop by remember { mutableStateOf<WorkshopItem?>(null) }
    val bookingConfirmation by viewModel.bookingConfirmation.collectAsState()

    // GPS & Location State
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var locationStatusMessage by remember { mutableStateOf("Consultando telemetría satelital...") }

    // Check location permission state
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Function to retrieve real device location
    val fetchCurrentLocation = {
        isLocating = true
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    isLocating = false
                    if (loc != null) {
                        userLat = loc.latitude
                        userLon = loc.longitude
                        locationStatusMessage = "GPS Conectado: ${"%.4f".format(loc.latitude)}, ${"%.4f".format(loc.longitude)}"
                    } else {
                        // Fallback default coordinates (Bogotá D.C. Centro) if emulator has null lastLocation
                        userLat = 4.6097
                        userLon = -74.0817
                        locationStatusMessage = "GPS Activo: Bogotá D.C. (Ubicación de Referencia)"
                    }
                }.addOnFailureListener {
                    isLocating = false
                    userLat = 4.6097
                    userLon = -74.0817
                    locationStatusMessage = "Modo Estimado: Bogotá D.C."
                }
            } else {
                isLocating = false
                userLat = 4.6097
                userLon = -74.0817
                locationStatusMessage = "Permiso GPS no concedido (Ubicación aproximada)"
            }
        } catch (e: Exception) {
            isLocating = false
            userLat = 4.6097
            userLon = -74.0817
            locationStatusMessage = "Sensor GPS: Bogotá D.C."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = fineGranted || coarseGranted
        if (hasLocationPermission) {
            fetchCurrentLocation()
        } else {
            userLat = 4.6097
            userLon = -74.0817
            locationStatusMessage = "Permiso denegado: Mostrando talleres Bogotá"
        }
    }

    // Auto-fetch on screen mount
    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            fetchCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val categories = listOf("Todos", "Ajuste Válvulas", "Sincronización", "Frenos & Suspensión", "Flotas B2B", "Urgencias 24h")

    val baseWorkshops = remember {
        listOf(
            WorkshopItem(
                id = "W-01",
                nombre = "Taller MotoTech Express (Centro)",
                direccion = "Av. Circunvalar # 45-21",
                distancia = "2.1 km • 6 min en moto",
                calificacion = 4.9,
                totalResenas = 184,
                especialidades = listOf("Ajuste Válvulas", "Sincronización", "Scanner OBD-II"),
                descuentoConvenio = "15% OFF en mano de obra",
                telefono = "+57 312 450 8921",
                horario = "Abierto Hoy: 8:00 AM - 6:30 PM",
                latitud = 4.6025,
                longitud = -74.0720
            ),
            WorkshopItem(
                id = "W-02",
                nombre = "Riders Garage Certificado",
                direccion = "Calle 72 # 14-30",
                distancia = "3.8 km • 11 min en moto",
                calificacion = 4.8,
                totalResenas = 96,
                especialidades = listOf("Frenos & Suspensión", "Inyección Electrónica", "Flotas B2B"),
                descuentoConvenio = "10% OFF en repuestos de alta gama",
                telefono = "+57 320 891 3344",
                horario = "Abierto Hoy: 7:30 AM - 6:00 PM",
                latitud = 4.6580,
                longitud = -74.0590
            ),
            WorkshopItem(
                id = "W-03",
                nombre = "Centro Especializado Flotas & Motos B2B",
                direccion = "Zona Industrial Norte Calle 129",
                distancia = "5.4 km • 15 min en moto",
                calificacion = 4.9,
                totalResenas = 230,
                especialidades = listOf("Flotas B2B", "Ajuste Válvulas", "Mantenimiento Preventivo"),
                descuentoConvenio = "Tarifa preferencial SaaS Empresas",
                telefono = "+57 310 777 9000",
                horario = "Abierto Hoy: 7:00 AM - 7:00 PM",
                latitud = 4.7080,
                longitud = -74.0450
            ),
            WorkshopItem(
                id = "W-04",
                nombre = "SOS Moto Auxilio & Mecánica 24 Horas",
                direccion = "Carrera 30 con Calle 53 (Estación Movilidad)",
                distancia = "1.5 km • 4 min en moto",
                calificacion = 4.7,
                totalResenas = 312,
                especialidades = listOf("Urgencias 24h", "Frenos & Suspensión", "Scanner OBD-II"),
                descuentoConvenio = "Asistencia express MotoTech 24/7",
                telefono = "+57 301 999 1234",
                horario = "Abierto 24 Horas • Servicio Inmediato",
                latitud = 4.6420,
                longitud = -74.0830
            )
        )
    }

    // Dynamic distance calculation relative to current user Lat/Lon
    val workshopsWithLiveDistance = remember(userLat, userLon, baseWorkshops) {
        val currentLat = userLat ?: 4.6097
        val currentLon = userLon ?: -74.0817
        baseWorkshops.map { ws ->
            val distKm = calculateDistanceKm(currentLat, currentLon, ws.latitud, ws.longitud)
            val distFormatted = String.format(java.util.Locale.US, "%.1f", distKm)
            val etaMinutes = (distKm * 2.6).roundToInt().coerceAtLeast(2)
            ws.copy(distancia = "$distFormatted km • ~$etaMinutes min en moto")
        }.sortedBy { ws ->
            calculateDistanceKm(currentLat, currentLon, ws.latitud, ws.longitud)
        }
    }

    val filteredWorkshops = if (selectedCategory == "Todos") {
        workshopsWithLiveDistance
    } else {
        workshopsWithLiveDistance.filter { it.especialidades.contains(selectedCategory) }
    }

    Scaffold(
        containerColor = MotoBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(RoutineText, CircleShape))
                            Text("Talleres & Google Maps", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                        }
                        Text("Geolocalización en tiempo real y navegación GPS", fontSize = 12.sp, color = MotoTextSecondary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { fetchCurrentLocation() },
                        modifier = Modifier.testTag("refresh_gps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar GPS",
                            tint = MotoAccentCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MotoBg)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Geolocation Radar Card with Google Maps Direct Search
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MotoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MotoSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MotoSurfaceVariant, CircleShape)
                                    .border(1.dp, if (hasLocationPermission) RoutineText.copy(alpha = 0.7f) else WarningText, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isLocating) Icons.Default.Sync else Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = if (hasLocationPermission) MotoAccentCyan else WarningText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (hasLocationPermission) RoutineText else WarningText, CircleShape)
                                    )
                                    Text(
                                        if (hasLocationPermission) "RADAR GPS CONECTADO" else "GPS EN MODO ESTIMADO",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (hasLocationPermission) RoutineText else WarningText
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    locationStatusMessage,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MotoTextPrimary
                                )
                            }
                        }

                        if (!hasLocationPermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MotoAccentCyan)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Conceder Permiso GPS para Mayor Precisión", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MotoBorder.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Master Google Maps Search Button: "Buscar talleres mecánicos en Google Maps"
                        Button(
                            onClick = {
                                val lat = userLat ?: 4.6097
                                val lon = userLon ?: -74.0817
                                openGoogleMapsSearch(context, lat, lon)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("buscar_en_google_maps_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MotoAccentCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = MotoBg,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Buscar Todos los Talleres en Google Maps",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MotoBg
                                )
                                Text(
                                    "Ver cualquier taller mecánico reportado cerca de ti",
                                    fontSize = 10.sp,
                                    color = MotoBg.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Categories Filter Bar
            item {
                Column {
                    Text(
                        "Red Certificada MotoTech (Convenio & Garantía)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MotoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { category ->
                            val isSelected = category == selectedCategory
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = { Text(category, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MotoPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MotoSurfaceVariant,
                                    labelColor = MotoTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) MotoPrimary else MotoBorder
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }
            }

            // Workshop Cards
            items(filteredWorkshops, key = { it.id }) { workshop ->
                WorkshopCard(
                    workshop = workshop,
                    onNavigateMaps = {
                        openGoogleMapsNavigation(context, workshop.latitud, workshop.longitud, workshop.nombre)
                    },
                    onBook = { bookingWorkshop = workshop }
                )
            }
        }
    }

    // Modal: Agendar Cita en Taller
    bookingWorkshop?.let { ws ->
        var selectedService by remember { mutableStateOf("Ajuste de Válvulas y Sincronización") }
        var selectedDate by remember { mutableStateOf("Mañana (09:00 AM)") }

        Dialog(onDismissRequest = { bookingWorkshop = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MotoPrimary, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Agendar Cita en Taller", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                        IconButton(onClick = { bookingWorkshop = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MotoTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(ws.nombre, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MotoAccentCyan)
                    Text(ws.direccion, fontSize = 12.sp, color = MotoTextSecondary)

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Selecciona el Servicio Requerido:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MotoTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    val services = listOf(
                        "Ajuste de Válvulas y Sincronización",
                        "Cambio de Aceite & Filtros",
                        "Inspección de Frenos y Pastillas",
                        "Mantenimiento Periódico General"
                    )

                    services.forEach { s ->
                        val isSel = s == selectedService
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MotoPrimary.copy(alpha = 0.15f) else MotoSurfaceVariant)
                                .border(1.dp, if (isSel) MotoPrimary else MotoBorder, RoundedCornerShape(8.dp))
                                .clickable { selectedService = s }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSel,
                                onClick = { selectedService = s },
                                colors = RadioButtonDefaults.colors(selectedColor = MotoPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s, fontSize = 12.sp, color = if (isSel) MotoTextPrimary else MotoTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Horario Preferente:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MotoTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    val times = listOf("Mañana (09:00 AM)", "Mañana (02:30 PM)", "En 2 días (10:00 AM)")
                    times.forEach { t ->
                        val isSel = t == selectedDate
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MotoAccentCyan.copy(alpha = 0.15f) else MotoSurfaceVariant)
                                .border(1.dp, if (isSel) MotoAccentCyan else MotoBorder, RoundedCornerShape(8.dp))
                                .clickable { selectedDate = t }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSel,
                                onClick = { selectedDate = t },
                                colors = RadioButtonDefaults.colors(selectedColor = MotoAccentCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(t, fontSize = 12.sp, color = if (isSel) MotoTextPrimary else MotoTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val targetTaller = ws.nombre
                            val parts = selectedDate.split(" ")
                            val f = parts[0]
                            val h = parts.drop(1).joinToString(" ")
                            viewModel.bookAppointment(targetTaller, selectedService, f, h)
                            bookingWorkshop = null
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirmar Cita con 15% Descuento", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Modal de Confirmación
    bookingConfirmation?.let { conf ->
        Dialog(onDismissRequest = { viewModel.clearBookingConfirmation() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RoutineBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(RoutineBg, CircleShape)
                            .border(1.dp, RoutineBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RoutineText, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        conf,
                        fontSize = 14.sp,
                        color = MotoTextPrimary,
                        lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.clearBookingConfirmation() },
                        colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Entendido")
                    }
                }
            }
        }
    }
}

@Composable
fun WorkshopCard(
    workshop: WorkshopItem,
    onNavigateMaps: () -> Unit,
    onBook: () -> Unit
) {
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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(workshop.nombre, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                        Icon(Icons.Default.Verified, contentDescription = "Certificado", tint = MotoAccentCyan, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(workshop.direccion, fontSize = 12.sp, color = MotoTextSecondary)
                }

                Box(
                    modifier = Modifier
                        .background(MotoSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = WarningText, modifier = Modifier.size(14.dp))
                        Text("${workshop.calificacion}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Navigation, contentDescription = null, tint = MotoAccentCyan, modifier = Modifier.size(14.dp))
                Text(workshop.distancia, fontSize = 12.sp, color = MotoAccentCyan, fontWeight = FontWeight.Medium)
                Text("•", color = MotoBorder)
                Text(workshop.horario, fontSize = 11.sp, color = MotoTextSecondary)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                workshop.especialidades.forEach { esp ->
                    Box(
                        modifier = Modifier
                            .background(MotoSurfaceVariant, RoundedCornerShape(6.dp))
                            .border(0.5.dp, MotoBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(esp, fontSize = 10.sp, color = MotoTextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RoutineBg, RoundedCornerShape(8.dp))
                    .border(1.dp, RoutineBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocalOffer, contentDescription = null, tint = RoutineText, modifier = Modifier.size(14.dp))
                    Text(workshop.descuentoConvenio, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RoutineText)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Cómo Llegar (Google Maps Navigation) & Agendar Cita
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateMaps,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("como_llegar_maps_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MotoAccentCyan),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MotoAccentCyan.copy(alpha = 0.7f)))
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cómo llegar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onBook,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(46.dp)
                        .testTag("agendar_taller_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.BuildCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Agendar Cita", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Calculates spherical distance in kilometers using the Haversine formula
 */
fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c
}

/**
 * Launches Google Maps to search for motorcycle repair shops near user's position
 */
fun openGoogleMapsSearch(context: Context, lat: Double, lon: Double) {
    try {
        // Query for motorcycle workshops near the specified coordinates
        val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=taller+mecanico+motos")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            // Web fallback if Google Maps app is not installed
            val webSearchUri = Uri.parse("https://www.google.com/maps/search/taller+mecanico+motos/@$lat,$lon,14z")
            val webIntent = Intent(Intent.ACTION_VIEW, webSearchUri)
            context.startActivity(webIntent)
        }
    } catch (e: Exception) {
        val fallbackIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/taller+mecanico+motos")
        )
        context.startActivity(fallbackIntent)
    }
}

/**
 * Launches Google Maps turn-by-turn navigation directly to the target workshop
 */
fun openGoogleMapsNavigation(context: Context, lat: Double, lon: Double, label: String) {
    try {
        val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lon&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            // Web browser directions fallback
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon&travelmode=driving")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri)
            context.startActivity(webIntent)
        }
    } catch (e: Exception) {
        val fallbackUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon")
        context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
    }
}
