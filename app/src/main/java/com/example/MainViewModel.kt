package com.example

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BrandCatalogEntity
import com.example.data.DiagnosticEntity
import com.example.data.DiagnosticRepository
import com.example.data.ServiceRecordEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException

private const val TAG = "MainViewModel"

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

    // Catálogo real de Marcas y Modelos (con cilindrada oficial automática)
    val marcasDisponibles: List<String> = MotorcycleCatalog.MARCAS

    private val _modelosDeMarca = MutableStateFlow<List<MotoModelSpec>>(emptyList())
    val modelosDeMarca: StateFlow<List<MotoModelSpec>> = _modelosDeMarca.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _catalogStatusLabel = MutableStateFlow<String?>(null)
    val catalogStatusLabel: StateFlow<String?> = _catalogStatusLabel.asStateFlow()

    private var catalogLoadJob: Job? = null

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
    private val brandCatalogAdapter = moshi.adapter(BrandCatalogResponse::class.java)

    private val datoTecnicoSchema by lazy {
        com.google.firebase.ai.type.Schema.obj(
            properties = mapOf(
                "valor" to com.google.firebase.ai.type.Schema.string(nullable = true),
                "fuente_titulo" to com.google.firebase.ai.type.Schema.string(nullable = true),
                "fuente_dominio" to com.google.firebase.ai.type.Schema.string(nullable = true),
                "tipo_fuente" to com.google.firebase.ai.type.Schema.string(nullable = true)
            ),
            optionalProperties = listOf("valor", "fuente_titulo", "fuente_dominio", "tipo_fuente"),
            nullable = true
        )
    }

    private val diagnosticSchema by lazy {
        com.google.firebase.ai.type.Schema.obj(
            properties = mapOf(
                "status" to com.google.firebase.ai.type.Schema.enumeration(values = listOf("success", "needs_info")),
                "mensaje" to com.google.firebase.ai.type.Schema.string(nullable = true),
                "campos_faltantes" to com.google.firebase.ai.type.Schema.array(
                    items = com.google.firebase.ai.type.Schema.string(),
                    nullable = true
                ),
                "moto_info" to com.google.firebase.ai.type.Schema.obj(
                    properties = mapOf(
                        "marca" to com.google.firebase.ai.type.Schema.string(nullable = true),
                        "modelo" to com.google.firebase.ai.type.Schema.string(nullable = true),
                        "cilindraje" to com.google.firebase.ai.type.Schema.string(nullable = true),
                        "anio" to com.google.firebase.ai.type.Schema.integer(nullable = true)
                    ),
                    optionalProperties = listOf("marca", "modelo", "cilindraje", "anio"),
                    nullable = true
                ),
                "ficha_tecnica" to com.google.firebase.ai.type.Schema.obj(
                    properties = mapOf(
                        "capacidad_aceite" to datoTecnicoSchema,
                        "viscosidad_aceite" to datoTecnicoSchema,
                        "presion_llanta_delantera" to datoTecnicoSchema,
                        "presion_llanta_trasera" to datoTecnicoSchema,
                        "holgura_cadena" to datoTecnicoSchema,
                        "liquido_frenos" to datoTecnicoSchema
                    ),
                    optionalProperties = listOf(
                        "capacidad_aceite",
                        "viscosidad_aceite",
                        "presion_llanta_delantera",
                        "presion_llanta_trasera",
                        "holgura_cadena",
                        "liquido_frenos"
                    ),
                    nullable = true
                ),
                "plan_mantenimiento_periodico" to com.google.firebase.ai.type.Schema.array(
                    items = com.google.firebase.ai.type.Schema.obj(
                        properties = mapOf(
                            "componente" to com.google.firebase.ai.type.Schema.string(nullable = true),
                            "intervalo_km" to com.google.firebase.ai.type.Schema.integer(nullable = true),
                            "intervalo_meses" to com.google.firebase.ai.type.Schema.integer(nullable = true),
                            "accion" to com.google.firebase.ai.type.Schema.string(nullable = true),
                            "fuente_titulo" to com.google.firebase.ai.type.Schema.string(nullable = true),
                            "fuente_dominio" to com.google.firebase.ai.type.Schema.string(nullable = true),
                            "tipo_fuente" to com.google.firebase.ai.type.Schema.string(nullable = true)
                        ),
                        optionalProperties = listOf(
                            "componente",
                            "intervalo_km",
                            "intervalo_meses",
                            "accion",
                            "fuente_titulo",
                            "fuente_dominio",
                            "tipo_fuente"
                        )
                    ),
                    nullable = true
                )
            ),
            optionalProperties = listOf(
                "mensaje",
                "campos_faltantes",
                "moto_info",
                "ficha_tecnica",
                "plan_mantenimiento_periodico"
            )
        )
    }

    /** Un intento de consulta: qué modelo y si se fuerza la salida JSON con esquema. */
    private data class Intento(val modelo: String, val salidaJson: Boolean)

    // Todos los intentos usan la búsqueda de Google: sin búsqueda el modelo respondería de memoria.
    // El segundo existe por si el modelo no admite salida JSON forzada junto con la búsqueda.
    private val intentos = listOf(
        Intento("gemini-3.8-flash", salidaJson = true),
        Intento("gemini-3.8-flash", salidaJson = false),
        Intento("gemini-3.5-flash-lite", salidaJson = false)
    )

    private fun modelFor(intento: Intento) = Firebase.ai.generativeModel(
        modelName = intento.modelo,
        generationConfig = if (intento.salidaJson) {
            generationConfig {
                responseMimeType = "application/json"
                responseSchema = diagnosticSchema
            }
        } else null,
        tools = listOf(Tool.googleSearch()),
        systemInstruction = content { text(DiagnosticPrompts.SYSTEM) }
    )

    /**
     * Selecciona una marca del listado oficial, limpia el modelo y cilindrada previos,
     * carga los modelos verificados de esa marca y sincroniza en tiempo real nuevos modelos.
     */
    fun selectMarca(nuevaMarca: String, forceRefreshOnline: Boolean = false) {
        val cleanMarca = nuevaMarca.trim()
        val changed = !formMarca.equals(cleanMarca, ignoreCase = true)
        formMarca = cleanMarca
        if (changed) {
            formModelo = ""
            formCilindraje = ""
        }
        loadModelsForBrand(cleanMarca, forceRefreshOnline)
    }

    /**
     * Selecciona un modelo perteneciente a la marca actual y asigna automáticamente su cilindrada oficial.
     */
    fun selectModelo(spec: MotoModelSpec) {
        formModelo = spec.modelo
        formCilindraje = MotorcycleCatalog.normalizeCilindraje(spec.cilindraje)
    }

    /**
     * Selecciona una moto de los botones de ejemplo rápido, cargando los modelos de su marca
     * y asignando automáticamente la cilindrada oficial.
     */
    fun selectExampleBike(marca: String, modelo: String, cilindraje: String, anio: String) {
        formMarca = marca
        formModelo = modelo
        formCilindraje = MotorcycleCatalog.normalizeCilindraje(cilindraje)
        formAnio = anio
        loadModelsForBrand(marca, forceRefreshOnline = false)
    }

    private fun loadModelsForBrand(marca: String, forceRefreshOnline: Boolean) {
        val cleanMarca = marca.trim()
        if (cleanMarca.isEmpty()) {
            _modelosDeMarca.value = emptyList()
            _catalogStatusLabel.value = null
            return
        }

        val baseModels = MotorcycleCatalog.baseModelsForBrand(cleanMarca)
        _modelosDeMarca.value = baseModels
        _catalogStatusLabel.value = "${baseModels.size} modelos base de $cleanMarca • Sincronizando API…"

        catalogLoadJob?.cancel()
        catalogLoadJob = viewModelScope.launch {
            val marcaKey = cleanMarca.lowercase()
            try {
                val cached = repository.findBrandCatalog(marcaKey)
                val cachedList = cached?.let {
                    MotorcycleCatalog.parseBrandCatalogJson(it.jsonModels, brandCatalogAdapter)
                }.orEmpty()

                if (cachedList.isNotEmpty()) {
                    val merged = MotorcycleCatalog.mergeModels(baseModels, cachedList)
                    _modelosDeMarca.value = merged
                    _catalogStatusLabel.value = "${merged.size} modelos de $cleanMarca (Caché Room + API)"
                    if (formModelo.isNotBlank() && formCilindraje.isBlank()) {
                        MotorcycleCatalog.findDisplacement(cleanMarca, formModelo, merged)?.let {
                            formCilindraje = it
                        }
                    }
                    if (!forceRefreshOnline) return@launch
                }

                _isLoadingModels.value = true
                _catalogStatusLabel.value = "Consultando API y Web Scraping de $cleanMarca con Retrofit…"

                // Consulta en tiempo real exclusivamente vía Retrofit (NHTSA vPIC API + MediaWiki API + Web Scraping)
                // sin consumir cuota de Gemini al seleccionar la marca.
                val retrofitResult = MotorcycleCatalogClient.fetchBrandCatalogRealTime(cleanMarca)

                if (retrofitResult.modelos.isNotEmpty()) {
                    val merged = MotorcycleCatalog.mergeModels(baseModels, retrofitResult.modelos)
                    _modelosDeMarca.value = merged
                    val responseJson = brandCatalogAdapter.toJson(
                        BrandCatalogResponse(marca = cleanMarca, modelos = merged)
                    )
                    repository.saveBrandCatalog(
                        BrandCatalogEntity(
                            marcaKey = marcaKey,
                            marca = cleanMarca,
                            jsonModels = responseJson
                        )
                    )
                    val fuenteDesc = if (retrofitResult.fuentesExitosas.isNotEmpty()) {
                        retrofitResult.fuentesExitosas.joinToString(" + ")
                    } else {
                        "API Retrofit"
                    }
                    _catalogStatusLabel.value = "${merged.size} modelos de $cleanMarca ($fuenteDesc)"
                    if (formModelo.isNotBlank()) {
                        MotorcycleCatalog.findDisplacement(cleanMarca, formModelo, merged)?.let {
                            formCilindraje = it
                        }
                    }
                } else {
                    _catalogStatusLabel.value = "${_modelosDeMarca.value.size} modelos oficiales de $cleanMarca"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _catalogStatusLabel.value = "${_modelosDeMarca.value.size} modelos oficiales de $cleanMarca"
            } finally {
                _isLoadingModels.value = false
            }
        }
    }

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
     * Busca la ficha de la moto mediante Firebase AI Logic (Firebase.ai).
     * Nunca genera datos por su cuenta: si no hay ficha guardada y la consulta falla, el resultado es un error.
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
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Diagnostic generation timed out", e)
                    val msg = "La consulta tardó demasiado. Intenta nuevamente."
                    if (!showCached("$msg Se muestra la ficha guardada.")) {
                        _uiState.value = DiagnosticState.Error(msg)
                    }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase AI diagnostic failed", e)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error completing diagnostic query", e)
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
        val userPrompt = DiagnosticPrompts.user(marca, modelo, cilindraje, anio)

        var response: GenerateContentResponse? = null
        var lastError: Exception? = null
        for (intento in intentos) {
            try {
                val r = withTimeout(60_000L) { modelFor(intento).generateContent(userPrompt) }
                if (r.text.isNullOrBlank()) throw IllegalStateException("El modelo devolvió una respuesta vacía.")
                response = r
                break
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "${intento.modelo} timed out", e)
                lastError = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: QuotaExceededException) {
                // Reintentar no ayuda y gasta más cuota.
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${intento.modelo} (json=${intento.salidaJson}) failed", e)
                lastError = e
            }
        }
        val ok = response ?: throw (lastError ?: IllegalStateException("No se pudo consultar las fuentes."))

        val parsed = DiagnosticParser.parse(ok.text.orEmpty(), jsonAdapter)
            ?: throw IllegalStateException("La respuesta de la consulta no se pudo leer.")

        // Las fuentes son las páginas que la búsqueda devolvió, nunca las que el modelo dice haber usado.
        val grounding = ok.candidates.firstOrNull()?.groundingMetadata
        val fuentes = grounding?.groundingChunks.orEmpty()
            .mapNotNull { it.web }
            .filter { !it.uri.isNullOrBlank() }
            .map { FuenteConsultada(titulo = it.title ?: it.domain, url = it.uri) }
            .distinctBy { it.url }

        // Sin páginas de respaldo la respuesta salió de la memoria del modelo: no se muestra.
        if (fuentes.isEmpty() && parsed.status != "needs_info") throw SinFuentesException()

        return parsed.copy(
            fuentes = fuentes,
            sugerencias_busqueda_html = grounding?.searchEntryPoint?.renderedContent
        )
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
        is SinFuentesException -> "La búsqueda no devolvió ninguna página de respaldo para esta moto."
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

/** El modelo respondió sin apoyarse en ninguna página de la búsqueda. */
class SinFuentesException : Exception()
