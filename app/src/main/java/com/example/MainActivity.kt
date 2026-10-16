package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.AppDatabase
import com.example.data.DiagnosticRepository
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val database = AppDatabase.getDatabase(context)
                val repository = DiagnosticRepository(database.diagnosticDao())
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(repository)
                )

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

                Scaffold(
                    containerColor = MotoBg,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MotoSurface,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = currentRoute == "dashboard",
                                onClick = { navController.navigate("dashboard") },
                                icon = {
                                    Icon(
                                        Icons.Default.Dashboard,
                                        contentDescription = "Cockpit",
                                        tint = if (currentRoute == "dashboard") MotoPrimary else MotoTextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        "Cockpit",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentRoute == "dashboard") FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentRoute == "dashboard") MotoPrimary else MotoTextSecondary
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "history",
                                onClick = { navController.navigate("history") },
                                icon = {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        contentDescription = "Hoja de Vida",
                                        tint = if (currentRoute == "history") MotoPrimary else MotoTextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        "Hoja Vida",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentRoute == "history") FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentRoute == "history") MotoPrimary else MotoTextSecondary
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "workshops",
                                onClick = { navController.navigate("workshops") },
                                icon = {
                                    Icon(
                                        Icons.Default.Storefront,
                                        contentDescription = "Talleres",
                                        tint = if (currentRoute == "workshops") MotoPrimary else MotoTextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        "Talleres",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentRoute == "workshops") FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentRoute == "workshops") MotoPrimary else MotoTextSecondary
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "premium",
                                onClick = { navController.navigate("premium") },
                                icon = {
                                    Icon(
                                        Icons.Default.LocalShipping,
                                        contentDescription = "Flotas & Planes",
                                        tint = if (currentRoute == "premium") MotoPrimary else MotoTextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        "Flotas/SaaS",
                                        fontSize = 11.sp,
                                        fontWeight = if (currentRoute == "premium") FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentRoute == "premium") MotoPrimary else MotoTextSecondary
                                    )
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") {
                            MotoTechCockpit(viewModel, onNavigateToWorkshops = {
                                navController.navigate("workshops")
                            }, onNavigateToPremium = {
                                navController.navigate("premium")
                            })
                        }
                        composable("history") {
                            HistoryScreen(viewModel)
                        }
                        composable("workshops") {
                            WorkshopsScreen(viewModel)
                        }
                        composable("premium") {
                            PremiumScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotoTechCockpit(
    viewModel: MainViewModel,
    onNavigateToWorkshops: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()

    var marca by remember { mutableStateOf("Yamaha") }
    var modelo by remember { mutableStateOf("MT-07") }
    var cilindraje by remember { mutableStateOf("689cc") }
    var anio by remember { mutableStateOf("2023") }
    var kilometraje by remember { mutableStateOf("24500") }

    var marcaError by remember { mutableStateOf(false) }
    var modeloError by remember { mutableStateOf(false) }
    var cilindrajeError by remember { mutableStateOf(false) }
    var anioError by remember { mutableStateOf(false) }
    var kilometrajeError by remember { mutableStateOf(false) }

    var affiliateDealToBuy by remember { mutableStateOf<RepuestoAfiliado?>(null) }
    var showGarageLimitDialog by remember { mutableStateOf(false) }

    val presetBikes = remember {
        listOf(
            "Yamaha MT-07 (689cc)" to Quadruple("Yamaha", "MT-07", "689cc", "2023"),
            "KTM Duke 390" to Quadruple("KTM", "Duke 390", "373cc", "2024"),
            "Bajaj Pulsar NS200" to Quadruple("Bajaj", "Pulsar NS200", "199cc", "2023"),
            "Honda CB190R" to Quadruple("Honda", "CB190R", "184cc", "2024"),
            "BMW G310 GS" to Quadruple("BMW", "G310 GS", "313cc", "2022")
        )
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("MotoTech Cockpit", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                                Box(
                                    modifier = Modifier
                                        .background(RoutineBg, RoundedCornerShape(4.dp))
                                        .border(0.5.dp, RoutineBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("ECU ONLINE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = RoutineText)
                                }
                            }
                            Text("Telemetría y Diagnóstico Inteligente", fontSize = 11.sp, color = MotoTextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MotoBg),
                actions = {
                    // Garage Selector Chip (Free vs Premium limit)
                    FilledTonalButton(
                        onClick = {
                            if (!isPremium) {
                                showGarageLimitDialog = true
                            } else {
                                onNavigateToPremium()
                            }
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
            // HERO TELEMETRY COCKPIT DISPLAY
            // =========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MotoBorder, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MotoSurface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("VEHÍCULO ACTIVO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MotoAccentCyan, letterSpacing = 1.sp)
                                Text("$marca $modelo", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                                Text("Cilindraje: $cilindraje • Modelo $anio", fontSize = 12.sp, color = MotoTextSecondary)
                            }

                            // Health Score Gauge
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MotoSurfaceVariant, CircleShape)
                                    .border(2.dp, Brush.sweepGradient(listOf(RoutineText, WarningText, RoutineText)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("88%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RoutineText)
                                    Text("SALUD", fontSize = 8.sp, color = MotoTextSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = MotoBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Cockpit Telemetry HUD Chips
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CockpitHudItem(
                                title = "Odómetro Actual",
                                value = "$kilometraje KM",
                                icon = Icons.Default.Speed,
                                accentColor = MotoTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            CockpitHudItem(
                                title = "Próximo Servicio",
                                value = "En 500 KM",
                                icon = Icons.Default.Build,
                                accentColor = WarningText,
                                modifier = Modifier.weight(1f)
                            )
                            CockpitHudItem(
                                title = "Presión Neumáticos",
                                value = "33 / 36 PSI",
                                icon = Icons.Default.Tune,
                                accentColor = MotoAccentCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // =========================================
            // PRESET QUICK CHIPS
            // =========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PRESELECCIÓN RÁPIDA DE MOTOCICLETAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MotoTextSecondary
                        )
                        Text("1-Tap Test", fontSize = 11.sp, color = MotoAccentCyan, fontWeight = FontWeight.SemiBold)
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presetBikes) { (label, data) ->
                            val isCurrent = marca.equals(data.first, ignoreCase = true) && modelo.equals(data.second, ignoreCase = true)
                            ElevatedFilterChip(
                                selected = isCurrent,
                                onClick = {
                                    marca = data.first
                                    modelo = data.second
                                    cilindraje = data.third
                                    anio = data.fourth
                                    kilometraje = "24500"
                                    marcaError = false
                                    modeloError = false
                                    cilindrajeError = false
                                    anioError = false
                                    kilometrajeError = false
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
            // 2. FORMULARIO DE INGRESO DE VEHÍCULO
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
                            Text("Ficha de Entrada del Vehículo", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                            Box(
                                modifier = Modifier
                                    .background(InfoBg, RoundedCornerShape(6.dp))
                                    .border(0.5.dp, InfoBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("CACHE-FIRST READY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = InfoText)
                            }
                        }

                        OutlinedTextField(
                            value = marca,
                            onValueChange = { marca = it; marcaError = false },
                            label = { Text("Marca (ej. Yamaha, KTM, Honda)") },
                            modifier = Modifier.fillMaxWidth().testTag("marca_input"),
                            singleLine = true,
                            isError = marcaError,
                            supportingText = if (marcaError) { { Text("Ingresa la marca de la motocicleta") } } else null,
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

                        OutlinedTextField(
                            value = modelo,
                            onValueChange = { modelo = it; modeloError = false },
                            label = { Text("Modelo (ej. MT-07, Duke 390, Pulsar NS200)") },
                            modifier = Modifier.fillMaxWidth().testTag("modelo_input"),
                            singleLine = true,
                            isError = modeloError,
                            supportingText = if (modeloError) { { Text("Ingresa el modelo") } } else null,
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

                        OutlinedTextField(
                            value = cilindraje,
                            onValueChange = { cilindraje = it; cilindrajeError = false },
                            label = { Text("Cilindraje (ej. 689cc o 200cc)") },
                            modifier = Modifier.fillMaxWidth().testTag("cilindraje_input"),
                            singleLine = true,
                            isError = cilindrajeError,
                            supportingText = if (cilindrajeError) { { Text("Ingresa el cilindraje") } } else null,
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

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = anio,
                                onValueChange = { anio = it; anioError = false },
                                label = { Text("Año") },
                                modifier = Modifier.weight(1f).testTag("anio_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = anioError,
                                supportingText = if (anioError) { { Text("Año válido") } } else null,
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
                            OutlinedTextField(
                                value = kilometraje,
                                onValueChange = { kilometraje = it; kilometrajeError = false },
                                label = { Text("Kilometraje") },
                                modifier = Modifier.weight(1f).testTag("kilometraje_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = kilometrajeError,
                                supportingText = if (kilometrajeError) { { Text("Km válido") } } else null,
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

                        Spacer(modifier = Modifier.height(4.dp))

                        // Action Button
                        Button(
                            onClick = {
                                val isMValid = marca.isNotBlank()
                                val isModValid = modelo.isNotBlank()
                                val isCcValid = cilindraje.isNotBlank()
                                val isAnValid = anio.isNotBlank() && anio.toIntOrNull() != null
                                val isKmValid = kilometraje.isNotBlank() && kilometraje.toIntOrNull() != null

                                marcaError = !isMValid
                                modeloError = !isModValid
                                cilindrajeError = !isCcValid
                                anioError = !isAnValid
                                kilometrajeError = !isKmValid

                                if (isMValid && isModValid && isCcValid && isAnValid && isKmValid) {
                                    viewModel.diagnose(marca, modelo, cilindraje, anio, kilometraje)
                                }
                            },
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
                                Text("Consultando Caché / IA...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Diagnosticar Mantenimiento Inteligente", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // =========================================
            // RESULTADOS DEL DIAGNÓSTICO
            // =========================================
            when (val state = uiState) {
                is DiagnosticState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                            }
                        }
                    }
                }

                is DiagnosticState.Success -> {
                    val resp = state.response

                    // Status and Cache Source Banner
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (state.isFromCache) RoutineBorder else InfoBorder, RoundedCornerShape(14.dp)),
                            colors = CardDefaults.cardColors(containerColor = if (state.isFromCache) RoutineBg else InfoBg)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        if (state.isFromCache) Icons.Default.Memory else Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = if (state.isFromCache) RoutineText else InfoText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(state.sourceLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (state.isFromCache) RoutineText else InfoText)
                                        Text(
                                            if (state.isFromCache) "Consumo a costo $0 sin llamar a la API externa" else "Generado y almacenado en SQLite Room",
                                            fontSize = 11.sp,
                                            color = if (state.isFromCache) RoutineText.copy(alpha = 0.8f) else InfoText.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .background(if (state.isFromCache) RoutineIconBg else InfoIconBg, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(if (state.isFromCache) "$0 USD" else "API OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    // 1. Ficha Técnica Completa
                    resp.ficha_tecnica?.let { ft ->
                        item {
                            TechnicalSpecsCard(ft)
                        }
                    }

                    // 2. Tarjeta Roja: Atención Inmediata / Urgente (con Smart CTA Afiliado)
                    val urgentes = resp.diagnostico_kilometraje?.atencion_inmediata
                    if (!urgentes.isNullOrEmpty()) {
                        item {
                            UrgentDiagnosticsCard(
                                items = urgentes,
                                onBuyRepuesto = { deal ->
                                    affiliateDealToBuy = deal
                                }
                            )
                        }
                    }

                    // 3. Tarjeta Amarilla: Próxima Revisión / Próximos km (con CTA Taller Aliado)
                    val proximos = resp.diagnostico_kilometraje?.proxima_revision
                    if (!proximos.isNullOrEmpty()) {
                        item {
                            WarningDiagnosticsCard(
                                items = proximos,
                                onBookWorkshop = onNavigateToWorkshops
                            )
                        }
                    }

                    // 4. Tarjeta Verde: Rutina habitualmente realizada
                    val rutinas = resp.diagnostico_kilometraje?.rutina_habitual
                    if (!rutinas.isNullOrEmpty()) {
                        item {
                            RoutineDiagnosticsCard(items = rutinas)
                        }
                    }

                    // 5. Plan Periódico
                    resp.plan_mantenimiento_periodico?.let { plan ->
                        if (plan.isNotEmpty()) {
                            item {
                                PeriodicPlanCard(plan)
                            }
                        }
                    }

                    // 6. Recomendaciones Clave
                    resp.recomendaciones_clave?.let { recs ->
                        if (recs.isNotEmpty()) {
                            item {
                                KeyRecommendationsCard(recs)
                            }
                        }
                    }
                }

                else -> {}
            }
        }
    }

    // Modal: Smart CTA Marketplace / Afiliado
    affiliateDealToBuy?.let { deal ->
        Dialog(onDismissRequest = { affiliateDealToBuy = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UrgentBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MotoSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Marketplace Aliado MotoTech", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                        IconButton(onClick = { affiliateDealToBuy = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MotoTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MotoSurfaceVariant, RoundedCornerShape(12.dp))
                            .border(1.dp, MotoBorder, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = UrgentText, modifier = Modifier.size(18.dp))
                                Text(deal.nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Marca: ${deal.marcaRecomendada} • Distribuidor Oficial: ${deal.tiendaAliada}", fontSize = 12.sp, color = MotoTextSecondary)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Precio con descuento:", fontSize = 12.sp, color = MotoTextSecondary)
                                Text(deal.precioEstimado, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RoutineText)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Cupón exclusivo: ${deal.cupon} (${deal.descuentoPct} OFF)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UrgentText)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Al comprar a través de enlaces de afiliados certificados de MotoTech, apoyas la plataforma y obtienes garantía oficial contra repuestos falsificados.",
                        fontSize = 11.sp,
                        color = MotoTextSecondary
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { affiliateDealToBuy = null },
                        colors = ButtonDefaults.buttonColors(containerColor = UrgentIconBg),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Canjear Cupón y Comprar Ahora", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Modal: Garage Limit Alert (Free to Premium Banner)
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

@Composable
fun TechnicalSpecsCard(ficha: com.example.FichaTecnica) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, InfoBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = InfoBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(32.dp).background(InfoIconBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text("FICHA TÉCNICA OFICIAL", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InfoText, letterSpacing = 1.sp)
                }
                Text("OEM SPECS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = InfoText.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecItem("Aceite Motor", "${ficha.capacidad_aceite_litros} (${ficha.viscosidad_aceite_recomendada})", modifier = Modifier.weight(1f))
                SpecItem("Cadena Transmisión", ficha.holgura_cadena_mm, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecItem("Presión Neumáticos", "Solo: ${ficha.presion_llantas_psi.delantera_solo}D/${ficha.presion_llantas_psi.trasera_solo}T PSI\nCarga: ${ficha.presion_llantas_psi.delantera_acompanante}D/${ficha.presion_llantas_psi.trasera_acompanante}T PSI", modifier = Modifier.weight(1f))
                SpecItem("Líquido Frenos", ficha.tipo_liquido_frenos, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SpecItem(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MotoSurface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .border(0.5.dp, InfoBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = InfoText.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MotoTextPrimary)
        }
    }
}

// 🔴 Tarjeta Roja: Atención Inmediata / Urgente
@Composable
fun UrgentDiagnosticsCard(
    items: List<com.example.AtencionInmediata>,
    onBuyRepuesto: (RepuestoAfiliado) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, UrgentBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = UrgentBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(32.dp).background(UrgentIconBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Dangerous, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text("ATENCIÓN INMEDIATA (URGENTE)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = UrgentText, letterSpacing = 1.sp)
                }
                Box(
                    modifier = Modifier
                        .background(UrgentIconBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("CRÍTICO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            items.forEachIndexed { index, item ->
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(item.componente, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = UrgentText)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(item.motivo, fontSize = 12.sp, color = UrgentText.copy(alpha = 0.85f), lineHeight = 16.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Contextual Smart CTA (Affiliate link button)
                    Button(
                        onClick = {
                            val repuesto = if (item.componente.contains("Aceite", ignoreCase = true)) {
                                RepuestoAfiliado(
                                    nombre = "Aceite Motul 7100 4T 10W-40 (1 Litro)",
                                    categoria = "Lubricantes",
                                    marcaRecomendada = "Motul 100% Sintético Ester",
                                    descuentoPct = "10%",
                                    precioEstimado = "$48.500 COP",
                                    cupon = "MOTOTECH10",
                                    tiendaAliada = "Distribuidor Autorizado Motul"
                                )
                            } else {
                                RepuestoAfiliado(
                                    nombre = "Pastillas de Freno Sinterizadas Brembo / EBC",
                                    categoria = "Frenos",
                                    marcaRecomendada = "Brembo Racing",
                                    descuentoPct = "15%",
                                    precioEstimado = "$125.000 COP",
                                    cupon = "MOTOFRENOS15",
                                    tiendaAliada = "Riders Moto Parts Colombia"
                                )
                            }
                            onBuyRepuesto(repuesto)
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = UrgentIconBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (item.componente.contains("Aceite", ignoreCase = true))
                                "Comprar Aceite Recomendado con 10% Off"
                            else
                                "Comprar Repuesto con Descuento Afiliado",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                if (index < items.size - 1) {
                    Divider(color = UrgentBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

// 🟡 Tarjeta Amarilla: Próxima Revisión / Próximos km
@Composable
fun WarningDiagnosticsCard(
    items: List<com.example.ProximaRevision>,
    onBookWorkshop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WarningBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = WarningBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(32.dp).background(WarningIconBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                    Text("PRÓXIMA REVISIÓN (PREVENTIVA)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarningText, letterSpacing = 1.sp)
                }
                Box(
                    modifier = Modifier
                        .background(WarningIconBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("PRÓXIMOS KM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.componente, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WarningText)
                    }
                    Box(
                        modifier = Modifier
                            .background(MotoSurface, RoundedCornerShape(6.dp))
                            .border(0.5.dp, WarningBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("En ${item.kilometros_restantes} km", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WarningText, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onBookWorkshop,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarningIconBg),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Agendar en Taller Aliado (15% OFF)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

// 🟢 Tarjeta Verde: Rutina habitualmente realizada
@Composable
fun RoutineDiagnosticsCard(items: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RoutineBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = RoutineBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(32.dp).background(RoutineIconBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text("RUTINA HABITUAL (VERIFICADO)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RoutineText, letterSpacing = 1.sp)
                }
                Box(
                    modifier = Modifier
                        .background(RoutineIconBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("ÓPTIMO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { routine ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = RoutineText, modifier = Modifier.size(16.dp))
                    Text(routine, fontSize = 12.sp, color = RoutineText, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun PeriodicPlanCard(plan: List<com.example.PlanMantenimiento>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MotoBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PLAN DE MANTENIMIENTO PERIÓDICO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MotoTextSecondary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))
            plan.forEach { p ->
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(p.componente, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                        Text("Cada ${p.intervalo_km} km", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MotoAccentCyan, fontFamily = FontFamily.Monospace)
                    }
                    Text(p.accion, fontSize = 12.sp, color = MotoTextSecondary)
                }
            }
        }
    }
}

@Composable
fun KeyRecommendationsCard(recs: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MotoBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("RECOMENDACIONES CLAVE DE INGENIERÍA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MotoTextSecondary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))
            recs.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", color = MotoPrimary, fontWeight = FontWeight.Bold)
                    Text(r, fontSize = 12.sp, color = MotoTextPrimary, lineHeight = 16.sp)
                }
            }
        }
    }
}
