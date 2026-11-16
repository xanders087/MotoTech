package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DiagnosticEntity
import com.example.data.DiagnosticRepository
import com.example.data.ServiceRecordEntity
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

sealed class DiagnosticState {
    object Idle : DiagnosticState()
    object Loading : DiagnosticState()
    data class Success(
        val response: DiagnosticResponse,
        val vehicleKey: String,
        val marca: String,
        val kmActual: Int,
        /** Cuándo se consultaron las fuentes. */
        val consultadoEl: Long,
        val isFromCache: Boolean,
        /** Se llena cuando se intentó actualizar y no se pudo, y se muestra la ficha guardada. */
        val aviso: String? = null
    ) : DiagnosticState()
    /** La moto no se pudo identificar o no se encontró ninguna fuente. */
    data class NeedsInfo(val mensaje: String, val camposFaltantes: List<String>) : DiagnosticState()
    data class Error(val message: String) : DiagnosticState()
}

class MainViewModel(private val repository: DiagnosticRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<DiagnosticState>(DiagnosticState.Idle)
    val uiState: StateFlow<DiagnosticState> = _uiState.asStateFlow()

    val history = repository.allDiagnostics

    // Datos del formulario: viven aquí para no perderse al cambiar de pestaña.
    var formMarca by mutableStateOf("")
    var formModelo by mutableStateOf("")
    var formCilindraje by mutableStateOf("")
    var formAnio by mutableStateOf("")
    var formKilometraje by mutableStateOf("")

    // B2B Fleet Mode Toggle
    private val _isFleetMode = MutableStateFlow(false)
    val isFleetMode: StateFlow<Boolean> = _isFleetMode.asStateFlow()

    // B2C Premium Subscription Tier
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // Digital Logbook (Hoja de Vida) Records
    private val _maintenanceLogs = MutableStateFlow<List<MaintenanceLogItem>>(
        listOf(
            MaintenanceLogItem(
                id = "LOG-001",
                fecha = "15 Sep 2026",
                kilometraje = 24000,
                titulo = "Mantenimiento Mayor de 24,000 km",
                taller = "Taller MotoTech Express (Certificado)",
                costo = "$220.000 COP",
                facturaNumero = "FAC-8842",
                categoria = "Mantenimiento Preventivo",
                tieneComprobante = true
            ),
            MaintenanceLogItem(
                id = "LOG-002",
                fecha = "10 Jun 2026",
                kilometraje = 19500,
                titulo = "Cambio de Kit de Arrastre D.I.D Japón",
                taller = "Riders Garage Oficial",
                costo = "$310.000 COP",
                facturaNumero = "FAC-7619",
                categoria = "Transmisión",
                tieneComprobante = true
            ),
            MaintenanceLogItem(
                id = "LOG-003",
                fecha = "02 Mar 2026",
                kilometraje = 15000,
                titulo = "Cambio Aceite Motul 7100 10W-40 + Filtro OEM",
                taller = "Lubricentro Motos del Norte",
                costo = "$145.000 COP",
                facturaNumero = "FAC-6201",
                categoria = "Lubricación",
                tieneComprobante = true
            )
        )
    )
    val maintenanceLogs: StateFlow<List<MaintenanceLogItem>> = _maintenanceLogs.asStateFlow()

    // Fleet Vehicles for B2B Mode
    private val _fleetBikes = MutableStateFlow<List<FleetBike>>(
        listOf(
            FleetBike(
                id = "F-01",
                codigo = "MOTO-01",
                placa = "LKM-44E",
                conductor = "Carlos Méndez (Zona Norte)",
                modelo = "Bajaj Pulsar NS200 FI",
                anio = 2024,
                kilometraje = 32400,
                estado = FleetHealthStatus.ATENCION_URGENTE,
                alertaPrincipal = "Cambio urgente de pastillas de freno y aceite",
                diasUltimoService = 45
            ),
            FleetBike(
                id = "F-02",
                codigo = "MOTO-02",
                placa = "WTR-18D",
                conductor = "Andrés Gómez (Ruta Express)",
                modelo = "Yamaha FZ-25",
                anio = 2023,
                kilometraje = 28150,
                estado = FleetHealthStatus.REVISION_PROXIMA,
                alertaPrincipal = "Ajuste de holgura de cadena en 350 km",
                diasUltimoService = 22
            ),
            FleetBike(
                id = "F-03",
                codigo = "MOTO-03",
                placa = "JKM-92F",
                conductor = "David Silva (Mensajería Sur)",
                modelo = "Honda CB190R",
                anio = 2024,
                kilometraje = 14200,
                estado = FleetHealthStatus.OPTIMO,
                alertaPrincipal = "Todos los sistemas verificados",
                diasUltimoService = 12
            ),
            FleetBike(
                id = "F-04",
                codigo = "MOTO-04",
                placa = "TYU-55G",
                conductor = "Mateo Ruiz (Domicilios Nocturnos)",
                modelo = "KTM Duke 200 NG",
                anio = 2023,
                kilometraje = 41200,
                estado = FleetHealthStatus.ATENCION_URGENTE,
                alertaPrincipal = "Vencimiento de SOAT en 5 días y revisión de frenos",
                diasUltimoService = 60
            )
        )
    )
    val fleetBikes: StateFlow<List<FleetBike>> = _fleetBikes.asStateFlow()

    // Last booked appointment confirmation
    private val _bookingConfirmation = MutableStateFlow<String?>(null)
    val bookingConfirmation: StateFlow<String?> = _bookingConfirmation.asStateFlow()

    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter(DiagnosticResponse::class.java)

    fun toggleFleetMode() {
        _isFleetMode.value = !_isFleetMode.value
    }

    fun togglePremium() {
        _isPremium.value = !_isPremium.value
    }

    fun clearBookingConfirmation() {
        _bookingConfirmation.value = null
    }

    fun bookAppointment(tallerNombre: String, servicio: String, fecha: String, hora: String) {
        val code = "MT-" + (1000..9999).random()
        _bookingConfirmation.value = "¡Cita Confirmada con Éxito!\nCódigo: $code\nTaller: $tallerNombre\nServicio: $servicio\nHorario: $fecha a las $hora"
    }

    fun addMaintenanceRecord(titulo: String, kilometraje: Int, taller: String, costo: String, categoria: String) {
        val newRecord = MaintenanceLogItem(
            id = "LOG-${System.currentTimeMillis() % 10000}",
            fecha = "Hoy, ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())}",
            kilometraje = kilometraje,
            titulo = titulo,
            taller = taller,
            costo = costo,
            facturaNumero = "FAC-${(1000..9999).random()}",
            categoria = categoria,
            tieneComprobante = true
        )
        _maintenanceLogs.value = listOf(newRecord) + _maintenanceLogs.value
    }

    fun reset() {
        _uiState.value = DiagnosticState.Idle
        _vehicleKey.value = null
    }

    /** Servicios registrados por el usuario para la moto que se está viendo. */
    private val _vehicleKey = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val serviceRecords: StateFlow<List<ServiceRecordEntity>> = _vehicleKey
        .flatMapLatest { key -> if (key == null) flowOf(emptyList()) else repository.serviceRecords(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun registerService(vehicleKey: String, componente: String, kilometraje: Int, fechaMillis: Long) {
        viewModelScope.launch {
            repository.addServiceRecord(
                ServiceRecordEntity(
                    vehicleKey = vehicleKey,
                    componente = MaintenanceCalculator.claveComponente(componente),
                    kilometraje = kilometraje,
                    fecha = fechaMillis
                )
            )
        }
    }

    /**
     * Busca la ficha de la moto. Nunca genera datos por su cuenta: si no hay ficha guardada
     * y la consulta falla, el resultado es un error.
     */
    fun diagnose(
        marca: String,
        modelo: String,
        cilindraje: String,
        anio: String,
        kilometraje: String,
        forceRefresh: Boolean = false
    ) {
        _uiState.value = DiagnosticState.Loading

        viewModelScope.launch {
            val cleanMarca = marca.trim()
            val cleanModelo = modelo.trim()
            val cleanCc = cilindraje.trim()
            val cleanAnio = anio.trim()
            val km = kilometraje.trim().toIntOrNull() ?: 0
            val key = VehicleKey.of(cleanMarca, cleanModelo, cleanCc, cleanAnio)

            try {
                val cached = repository.findDiagnostic(key)
                val cachedResponse = cached?.let { runCatching { jsonAdapter.fromJson(it.jsonResponse) }.getOrNull() }

                fun showCached(aviso: String?): Boolean {
                    if (cached == null || cachedResponse == null) return false
                    _vehicleKey.value = key
                    _uiState.value = DiagnosticState.Success(
                        response = cachedResponse,
                        vehicleKey = key,
                        marca = cleanMarca,
                        kmActual = km,
                        consultadoEl = cached.timestamp,
                        isFromCache = true,
                        aviso = aviso
                    )
                    return true
                }

                if (!forceRefresh && showCached(null)) return@launch

                val fetched = try {
                    fetchFromSources(cleanMarca, cleanModelo, cleanCc, cleanAnio)
                } catch (e: Exception) {
                    val msg = describeError(e)
                    if (!showCached("$msg Se muestra la ficha guardada.")) {
                        _uiState.value = DiagnosticState.Error("$msg No se muestran datos porque no se pudieron consultar las fuentes.")
                    }
                    return@launch
                }

                if (fetched.status == "needs_info" || !hasAnyData(fetched)) {
                    _uiState.value = DiagnosticState.NeedsInfo(
                        mensaje = fetched.mensaje?.takeIf { it.isNotBlank() }
                            ?: "No se encontraron fuentes con datos para esta motocicleta. Revisa la marca, el modelo, el cilindraje y el año.",
                        camposFaltantes = fetched.campos_faltantes.orEmpty()
                    )
                    return@launch
                }

                val now = System.currentTimeMillis()
                // Se elimina la ficha anterior para que el índice único no choque al actualizar.
                cached?.let { repository.deleteById(it.id) }
                repository.insert(
                    DiagnosticEntity(
                        timestamp = now,
                        vehicleKey = key,
                        marca = cleanMarca,
                        modelo = cleanModelo,
                        cilindraje = cleanCc,
                        anio = cleanAnio,
                        jsonResponse = jsonAdapter.toJson(fetched)
                    )
                )
                _vehicleKey.value = key
                _uiState.value = DiagnosticState.Success(
                    response = fetched,
                    vehicleKey = key,
                    marca = cleanMarca,
                    kmActual = km,
                    consultadoEl = now,
                    isFromCache = false
                )
            } catch (e: Exception) {
                _uiState.value = DiagnosticState.Error("No se pudo completar la consulta: ${e.localizedMessage ?: "error desconocido"}")
            }
        }
    }

    private suspend fun fetchFromSources(
        marca: String,
        modelo: String,
        cilindraje: String,
        anio: String
    ): DiagnosticResponse {
        val result = GeminiSourceClient.lookup(
            systemPrompt = DiagnosticPrompts.SYSTEM,
            userPrompt = DiagnosticPrompts.user(marca, modelo, cilindraje, anio)
        )
        val parsed = DiagnosticParser.parse(result.text, jsonAdapter)
            ?: throw IllegalStateException("La respuesta de la consulta no se pudo leer.")
        // Las fuentes vienen de la búsqueda, no de lo que el modelo escribió.
        return parsed.copy(fuentes = result.fuentes, sugerencias_busqueda_html = result.sugerenciasHtml)
    }

    private fun hasAnyData(r: DiagnosticResponse): Boolean {
        val ft = r.ficha_tecnica
        val datos = listOfNotNull(
            ft?.capacidad_aceite, ft?.viscosidad_aceite, ft?.presion_llanta_delantera,
            ft?.presion_llanta_trasera, ft?.holgura_cadena, ft?.liquido_frenos
        )
        return datos.any { !it.valor.isNullOrBlank() } || !r.plan_mantenimiento_periodico.isNullOrEmpty()
    }

    private fun describeError(e: Exception): String = when (e) {
        is FirebaseNotConfiguredException ->
            "La app no está conectada a Firebase (falta google-services.json)."
        is QuotaExceededException -> "Se alcanzó el límite de consultas. Intenta de nuevo en unos minutos."
        is APINotConfiguredException, is ServiceDisabledException, is InvalidAPIKeyException ->
            "La consulta de fuentes no está habilitada en el proyecto de Firebase."
        is RequestTimeoutException -> "La consulta tardó demasiado. Revisa tu conexión e intenta de nuevo."
        is UnsupportedUserLocationException -> "La consulta de fuentes no está disponible en tu región."
        is ServerException -> "El servicio de consulta rechazó la solicitud."
        is IOException -> "No hay conexión para consultar las fuentes."
        is FirebaseAIException -> "No se pudo consultar las fuentes. Revisa tu conexión e intenta de nuevo."
        else -> e.localizedMessage ?: "No se pudo consultar las fuentes."
    }
}
