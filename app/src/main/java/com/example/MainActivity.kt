package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    containerColor = MotoBg,
                    bottomBar = {
                        NavigationBar(containerColor = MotoBg) {
                            NavigationBarItem(
                                selected = currentRoute == "dashboard",
                                onClick = { navController.navigate("dashboard") },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard", tint = if (currentRoute == "dashboard") MotoPrimary else MotoTextSecondary) },
                                label = { Text("Moto", color = if (currentRoute == "dashboard") MotoPrimary else MotoTextSecondary) }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "history",
                                onClick = { navController.navigate("history") },
                                icon = { Icon(Icons.Default.History, contentDescription = "History", tint = if (currentRoute == "history") MotoPrimary else MotoTextSecondary) },
                                label = { Text("Hoja Vida", color = if (currentRoute == "history") MotoPrimary else MotoTextSecondary) }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "workshops",
                                onClick = { navController.navigate("workshops") },
                                icon = { Icon(Icons.Default.Build, contentDescription = "Workshops", tint = if (currentRoute == "workshops") MotoPrimary else MotoTextSecondary) },
                                label = { Text("Talleres", color = if (currentRoute == "workshops") MotoPrimary else MotoTextSecondary) }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "premium",
                                onClick = { navController.navigate("premium") },
                                icon = { Icon(Icons.Default.Star, contentDescription = "Premium", tint = if (currentRoute == "premium") MotoPrimary else MotoTextSecondary) },
                                label = { Text("Premium", color = if (currentRoute == "premium") MotoPrimary else MotoTextSecondary) }
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
                            MotoTechApp(viewModel)
                        }
                        composable("history") {
                            HistoryScreen(viewModel)
                        }
                        composable("workshops") {
                            WorkshopsScreen()
                        }
                        composable("premium") {
                            PremiumScreen()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotoTechApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    var marca by remember { mutableStateOf("") }
    var modelo by remember { mutableStateOf("") }
    var cilindraje by remember { mutableStateOf("") }
    var anio by remember { mutableStateOf("") }
    var kilometraje by remember { mutableStateOf("") }
    
    var marcaError by remember { mutableStateOf(false) }
    var modeloError by remember { mutableStateOf(false) }
    var cilindrajeError by remember { mutableStateOf(false) }
    var anioError by remember { mutableStateOf(false) }
    var kilometrajeError by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MotoBg,
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MotoPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TwoWheeler, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Text("MotoTech AI", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MotoTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MotoBg
                ),
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Modo Flotas", fontSize = 12.sp, color = MotoTextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(checked = false, onCheckedChange = { /* Navigate to Premium or activate */ })
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Button(
                    onClick = { /* Unlock Premium */ },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MotoBorder.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mi Moto (1/1)", color = MotoTextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MotoPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Premium para +", color = MotoPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, MotoBorder, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = MotoSurface),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("Datos de la Motocicleta", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MotoPrimary)
                                Text("Ingresa los datos para el análisis", fontSize = 14.sp, color = MotoTextSecondary)
                            }
                        }
                        
                        OutlinedTextField(
                            value = marca,
                            onValueChange = { marca = it; marcaError = false },
                            label = { Text("Marca") },
                            modifier = Modifier.fillMaxWidth().testTag("marca_input"),
                            singleLine = true,
                            isError = marcaError,
                            supportingText = if (marcaError) { { Text("Campo requerido") } } else null,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MotoTextPrimary,
                                unfocusedTextColor = MotoTextPrimary,
                                errorTextColor = MotoTextPrimary,
                                focusedBorderColor = MotoPrimary,
                                unfocusedBorderColor = MotoBorder,
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                focusedLabelColor = MotoPrimary,
                                unfocusedLabelColor = MotoTextSecondary,
                                errorLabelColor = MaterialTheme.colorScheme.error,
                                cursorColor = MotoPrimary,
                                errorCursorColor = MaterialTheme.colorScheme.error
                            )
                        )
                        OutlinedTextField(
                            value = modelo,
                            onValueChange = { modelo = it; modeloError = false },
                            label = { Text("Modelo") },
                            modifier = Modifier.fillMaxWidth().testTag("modelo_input"),
                            singleLine = true,
                            isError = modeloError,
                            supportingText = if (modeloError) { { Text("Campo requerido") } } else null,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MotoTextPrimary,
                                unfocusedTextColor = MotoTextPrimary,
                                errorTextColor = MotoTextPrimary,
                                focusedBorderColor = MotoPrimary,
                                unfocusedBorderColor = MotoBorder,
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                focusedLabelColor = MotoPrimary,
                                unfocusedLabelColor = MotoTextSecondary,
                                errorLabelColor = MaterialTheme.colorScheme.error,
                                cursorColor = MotoPrimary,
                                errorCursorColor = MaterialTheme.colorScheme.error
                            )
                        )
                        OutlinedTextField(
                            value = cilindraje,
                            onValueChange = { cilindraje = it; cilindrajeError = false },
                            label = { Text("Cilindraje (ej. 200cc)") },
                            modifier = Modifier.fillMaxWidth().testTag("cilindraje_input"),
                            singleLine = true,
                            isError = cilindrajeError,
                            supportingText = if (cilindrajeError) { { Text("Campo requerido") } } else null,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MotoTextPrimary,
                                unfocusedTextColor = MotoTextPrimary,
                                errorTextColor = MotoTextPrimary,
                                focusedBorderColor = MotoPrimary,
                                unfocusedBorderColor = MotoBorder,
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                focusedLabelColor = MotoPrimary,
                                unfocusedLabelColor = MotoTextSecondary,
                                errorLabelColor = MaterialTheme.colorScheme.error,
                                cursorColor = MotoPrimary,
                                errorCursorColor = MaterialTheme.colorScheme.error
                            )
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    errorTextColor = MotoTextPrimary,
                                    focusedBorderColor = MotoPrimary,
                                    unfocusedBorderColor = MotoBorder,
                                    errorBorderColor = MaterialTheme.colorScheme.error,
                                    focusedLabelColor = MotoPrimary,
                                    unfocusedLabelColor = MotoTextSecondary,
                                    errorLabelColor = MaterialTheme.colorScheme.error,
                                    cursorColor = MotoPrimary,
                                    errorCursorColor = MaterialTheme.colorScheme.error
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
                                    errorTextColor = MotoTextPrimary,
                                    focusedBorderColor = MotoPrimary,
                                    unfocusedBorderColor = MotoBorder,
                                    errorBorderColor = MaterialTheme.colorScheme.error,
                                    focusedLabelColor = MotoPrimary,
                                    unfocusedLabelColor = MotoTextSecondary,
                                    errorLabelColor = MaterialTheme.colorScheme.error,
                                    cursorColor = MotoPrimary,
                                    errorCursorColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val isMarcaValid = marca.isNotBlank()
                        val isModeloValid = modelo.isNotBlank()
                        val isCilindrajeValid = cilindraje.isNotBlank()
                        val isAnioValid = anio.isNotBlank() && anio.toIntOrNull() != null
                        val isKilometrajeValid = kilometraje.isNotBlank() && kilometraje.toIntOrNull() != null

                        marcaError = !isMarcaValid
                        modeloError = !isModeloValid
                        cilindrajeError = !isCilindrajeValid
                        anioError = !isAnioValid
                        kilometrajeError = !isKilometrajeValid

                        if (isMarcaValid && isModeloValid && isCilindrajeValid && isAnioValid && isKilometrajeValid) {
                            viewModel.diagnose(marca, modelo, cilindraje, anio, kilometraje)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("diagnosticar_button"),
                    enabled = uiState !is DiagnosticState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    if (uiState is DiagnosticState.Loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Procesando...", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Diagnosticar Mantenimiento", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.5.sp)
                    }
                }
            }

            when (val state = uiState) {
                is DiagnosticState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                is DiagnosticState.Success -> {
                    val response = state.response
                    
                    if (response.status == "error") {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = response.mensaje ?: "Error en los datos",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    response.campos_faltantes?.let { faltantes ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Faltan: ${faltantes.joinToString()}", color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }
                    } else {
                        // Results
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RESULTADOS DEL DIAGNÓSTICO",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp,
                                    color = MotoTextSecondary
                                )
                                Box(
                                    modifier = Modifier
                                        .background(MotoPrimary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Gemini AI v1.5", fontSize = 10.sp, color = MotoPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Ficha Tecnica
                        response.ficha_tecnica?.let { ficha ->
                            item {
                                FichaTecnicaCard(ficha)
                            }
                        }

                        // Atencion Inmediata
                        val inmediatos = response.diagnostico_kilometraje?.atencion_inmediata
                        if (!inmediatos.isNullOrEmpty()) {
                            item {
                                UrgentCard(inmediatos)
                            }
                        }

                        // Proxima Revision
                        val proxima = response.diagnostico_kilometraje?.proxima_revision
                        if (!proxima.isNullOrEmpty()) {
                            item {
                                NextRevisionCard(proxima)
                            }
                        }

                        // Rutina
                        val rutina = response.diagnostico_kilometraje?.rutina_habitual
                        if (!rutina.isNullOrEmpty()) {
                            item {
                                RoutineCard(rutina)
                            }
                        }

                        // Plan Mantenimiento
                        val plan = response.plan_mantenimiento_periodico
                        if (!plan.isNullOrEmpty()) {
                            item {
                                PlanMantenimientoCard(plan)
                            }
                        }
                        
                        // Recomendaciones
                        val recs = response.recomendaciones_clave
                        if (!recs.isNullOrEmpty()) {
                            item {
                                RecomendacionesCard(recs)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun ResultCard(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    iconBgColor: Color,
    borderColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBgColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(2.dp))
                content()
            }
        }
    }
}

@Composable
fun FichaTecnicaCard(ficha: FichaTecnica) {
    ResultCard(
        title = "Ficha Técnica",
        icon = Icons.Default.ListAlt,
        containerColor = InfoBg,
        contentColor = InfoText,
        iconBgColor = InfoIconBg,
        borderColor = InfoBorder
    ) {
        Text("Aceite: ${ficha.capacidad_aceite_litros} (${ficha.viscosidad_aceite_recomendada})", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = InfoText)
        Text("Presión: ${ficha.presion_llantas_psi.delantera_solo} psi D / ${ficha.presion_llantas_psi.trasera_solo} psi T", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = InfoText)
        Text("Cadena: ${ficha.holgura_cadena_mm} | Frenos: ${ficha.tipo_liquido_frenos}", fontSize = 12.sp, color = InfoText.copy(alpha = 0.8f))
    }
}

@Composable
fun UrgentCard(items: List<AtencionInmediata>) {
    ResultCard(
        title = "Urgente (Reemplazo)",
        icon = Icons.Default.Warning,
        containerColor = UrgentBg,
        contentColor = UrgentText,
        iconBgColor = UrgentIconBg,
        borderColor = UrgentBorder
    ) {
        items.forEachIndexed { index, item ->
            Text(item.componente, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = UrgentText)
            Text(item.motivo, fontSize = 12.sp, color = UrgentText.copy(alpha = 0.8f), lineHeight = 16.sp)
            if (index < items.size - 1) Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { /* Smart CTA: Comprar repuesto */ },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UrgentIconBg),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("Comprar Repuesto con 10% Off", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun NextRevisionCard(items: List<ProximaRevision>) {
    ResultCard(
        title = "Próxima Revisión",
        icon = Icons.Default.Schedule,
        containerColor = WarningBg,
        contentColor = WarningText,
        iconBgColor = WarningIconBg,
        borderColor = WarningBorder
    ) {
        items.forEachIndexed { index, item ->
            Text(item.componente, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WarningText)
            Text("En ${item.kilometros_restantes} km", fontSize = 12.sp, color = WarningText.copy(alpha = 0.8f), lineHeight = 16.sp)
            if (index < items.size - 1) Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { /* Agendar Taller */ },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WarningIconBg),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("Agendar en Taller Aliado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun RoutineCard(items: List<String>) {
    ResultCard(
        title = "Rutina (OK)",
        icon = Icons.Default.CheckCircle,
        containerColor = RoutineBg,
        contentColor = RoutineText,
        iconBgColor = RoutineIconBg,
        borderColor = RoutineBorder
    ) {
        items.forEachIndexed { index, item ->
            Text(item, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RoutineText)
            if (index < items.size - 1) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun PlanMantenimientoCard(items: List<PlanMantenimiento>) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, MotoBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PLAN DE MANTENIMIENTO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MotoTextSecondary.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { item ->
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(item.componente, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MotoTextPrimary)
                    Text("Cada ${item.intervalo_km} km - ${item.accion}", fontSize = 12.sp, color = MotoTextSecondary)
                }
            }
        }
    }
}

@Composable
fun RecomendacionesCard(items: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, MotoBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("RECOMENDACIONES CLAVE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MotoTextSecondary.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { item ->
                Text("• $item", fontSize = 14.sp, color = MotoTextPrimary, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}
