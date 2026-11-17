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
import com.google.firebase.firestore.FirebaseFirestoreException
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
        /** Cuándo se leyó la ficha de la biblioteca. */
        val consultadoEl: Long,
        val isFromCache: Boolean,
        /** Se llena cuando se intentó actualizar y no se pudo, y se muestra la ficha guardada. */
        val aviso: String? = null
    ) : DiagnosticState()
    /** La moto no se pudo identificar o no se encontró ninguna fuente. */
    /** La biblioteca todavía no tiene la ficha oficial de esa moto y año. */
    data class SinFicha(
        val marca: String,
        val modelo: String,
        val cilindraje: String,
        val anio: String
    ) : DiagnosticState()
    data class Error(
        val message: String,
        /** Mensaje original del servicio, para poder diagnosticar la causa. */
        val detalle: String? = null
    ) : DiagnosticState()
}

/** Estado del botón "Solicitar esta ficha". */
enum class SolicitudEstado { DISPONIBLE, ENVIANDO, ENVIADA, EN_COLA, ERROR }

class MainViewModel(private val repository: DiagnosticRepository) : ViewModel() {
    private val _solicitud = MutableStateFlow(SolicitudEstado.DISPONIBLE)
    val solicitud: StateFlow<SolicitudEstado> = _solicitud.asStateFlow()

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

    // Configuración de Apariencia (Claro, Oscuro, Predeterminado según sistema)
    private val _themeMode = MutableStateFlow(com.example.ui.theme.MotoThemeState.themeMode)
    val themeMode: StateFlow<com.example.ui.theme.AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: com.example.ui.theme.AppThemeMode, context: android.content.Context? = null) {
        _themeMode.value = mode
        com.example.ui.theme.MotoThemeState.themeMode = mode
        if (context != null) {
            ThemePreferences.saveThemeMode(context, mode)
        }
    }

    // Configuración de Notificaciones Locales Preventivas por Kilometraje
    private val _notificacionesActivas = MutableStateFlow(true)
    val notificacionesActivas: StateFlow<Boolean> = _notificacionesActivas.asStateFlow()

    private val _umbralAlertaKm = MutableStateFlow(500)
    val umbralAlertaKm: StateFlow<Int> = _umbralAlertaKm.asStateFlow()

    private val _ultimaNotificacionResumen = MutableStateFlow<String?>(null)
    val ultimaNotificacionResumen: StateFlow<String?> = _ultimaNotificacionResumen.asStateFlow()

    var ultimoEstadoNotificadoKey: String? = null
        private set

    fun setNotificacionesActivas(activas: Boolean) {
        _notificacionesActivas.value = activas
    }

    fun setUmbralAlertaKm(nuevoUmbralKm: Int) {
        _umbralAlertaKm.value = nuevoUmbralKm.coerceAtLeast(100)
    }

    fun registrarNotificacionEnviada(resumen: String, claveEstado: String?) {
        _ultimaNotificacionResumen.value = resumen
        if (claveEstado != null) {
            ultimoEstadoNotificadoKey = claveEstado
        }
    }

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

    fun addMaintenanceRecord(
        titulo: String,
        kilometraje: Int,
        taller: String,
        costo: String,
        categoria: String,
        fechaMillis: Long = System.currentTimeMillis(),
        notas: String = ""
    ) {
        val formattedDate = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("es", "CO"))
            .format(java.util.Date(fechaMillis))
        val newRecord = MaintenanceLogItem(
            id = "LOG-${System.currentTimeMillis() % 10000}",
            fecha = formattedDate,
            kilometraje = kilometraje,
            titulo = titulo,
            taller = taller,
            costo = costo,
            facturaNumero = "FAC-${(1000..9999).random()}",
            categoria = categoria,
            tieneComprobante = true,
            notas = notas.trim()
        )
        _maintenanceLogs.value = listOf(newRecord) + _maintenanceLogs.value

        // También persiste el servicio realizado en la base de datos Room (service_records)
        val key = ensureActiveVehicleKey()
        registerService(
            vehicleKey = key,
            componente = titulo,
            kilometraje = kilometraje,
            fechaMillis = fechaMillis,
            notas = notas,
            taller = taller,
            costo = costo
        )
    }

    /** Pide que se cargue la ficha de la moto consultada. Solo cuenta una vez por teléfono. */
    fun solicitarFicha(moto: DiagnosticState.SinFicha) {
        if (_solicitud.value == SolicitudEstado.ENVIANDO || _solicitud.value == SolicitudEstado.ENVIADA) return
        val anio = moto.anio.toIntOrNull() ?: return
        _solicitud.value = SolicitudEstado.ENVIANDO
        viewModelScope.launch {
            try {
                val confirmada = FichaLibraryClient.solicitar(moto.marca, moto.modelo, moto.cilindraje, anio)
                repository.markFichaRequested(FichaMapper.idSolicitud(moto.marca, moto.modelo, moto.cilindraje, anio))
                _solicitud.value = if (confirmada) SolicitudEstado.ENVIADA else SolicitudEstado.EN_COLA
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Ficha request failed", e)
                _solicitud.value = SolicitudEstado.ERROR
            }
        }
    }

    fun reset() {
        _uiState.value = DiagnosticState.Idle
        _vehicleKey.value = null
    }

    /**
     * Actualiza el kilometraje actual desde la pantalla de resumen o el formulario,
     * recalculando en tiempo real los próximos mantenimientos preventivos.
     */
    fun updateCurrentKilometraje(nuevoKm: Int) {
        val safeKm = nuevoKm.coerceAtLeast(0)
        formKilometraje = safeKm.toString()
        val current = _uiState.value
        if (current is DiagnosticState.Success) {
            _uiState.value = current.copy(kmActual = safeKm)
        }
    }

    /** Devuelve la clave del vehículo activo para asociar registros de servicio. */
    fun ensureActiveVehicleKey(): String {
        val currentKey = _vehicleKey.value
        if (!currentKey.isNullOrBlank()) return currentKey
        val marca = formMarca.ifBlank { "Yamaha" }
        val modelo = formModelo.ifBlank { "MT-07" }
        val cc = formCilindraje.ifBlank { "689cc" }
        val anio = formAnio.ifBlank { "2023" }
        val computed = VehicleKey.of(marca, modelo, cc, anio)
        _vehicleKey.value = computed
        return computed
    }

    /** Servicios registrados por el usuario para la moto que se está viendo. */
    private val _vehicleKey = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val serviceRecords: StateFlow<List<ServiceRecordEntity>> = _vehicleKey
        .flatMapLatest { key -> if (key == null) flowOf(emptyList()) else repository.serviceRecords(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Todos los mantenimientos registrados en Room (con fecha y notas). */
    val allServiceRecords: StateFlow<List<ServiceRecordEntity>> = repository.allServiceRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun registerService(
        vehicleKey: String,
        componente: String,
        kilometraje: Int,
        fechaMillis: Long,
        notas: String = "",
        taller: String = "",
        costo: String = ""
    ) {
        _vehicleKey.value = vehicleKey
        viewModelScope.launch {
            repository.addServiceRecord(
                ServiceRecordEntity(
                    vehicleKey = vehicleKey,
                    componente = MaintenanceCalculator.claveComponente(componente),
                    kilometraje = kilometraje,
                    fecha = fechaMillis,
                    notas = notas.trim(),
                    taller = taller.trim(),
                    costo = costo.trim()
                )
            )
        }
    }

    fun deleteServiceRecord(id: Int) {
        viewModelScope.launch {
            repository.deleteServiceRecord(id)
        }
    }

    /**
     * Busca la ficha de la moto en la biblioteca de manuales oficiales.
     * Nunca genera datos: si la moto no está en la biblioteca, el resultado es "sin ficha".
     */
    fun diagnose(
        marca: String,
        modelo: String,
        cilindraje: String,
        anio: String,
        kilometraje: String
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

                // Siempre se consulta la biblioteca, para que una ficha corregida llegue al usuario.
                // La copia guardada en el teléfono solo se usa si la biblioteca no responde.
                val fetched = try {
                    FichaLibraryClient.buscar(cleanMarca, cleanModelo, cleanCc, cleanAnio.toIntOrNull() ?: 0)
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Ficha library timed out", e)
                    val msg = "La biblioteca de fichas tardó demasiado en responder."
                    if (!showCached("$msg Se muestra la ficha guardada en este teléfono.")) {
                        _uiState.value = DiagnosticState.Error("$msg Revisa tu conexión e intenta de nuevo.")
                    }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Ficha library lookup failed", e)
                    val msg = describeLibraryError(e)
                    if (!showCached("$msg Se muestra la ficha guardada en este teléfono.")) {
                        _uiState.value = DiagnosticState.Error(message = msg, detalle = ErrorDetails.detalle(e))
                    }
                    return@launch
                }

                if (fetched == null) {
                    // Si la ficha se retiró de la biblioteca, tampoco se muestra la copia guardada.
                    cached?.let { repository.deleteById(it.id) }
                    val requestId = FichaMapper.idSolicitud(cleanMarca, cleanModelo, cleanCc, cleanAnio.toIntOrNull() ?: 0)
                    _solicitud.value =
                        if (repository.wasFichaRequested(requestId)) SolicitudEstado.ENVIADA else SolicitudEstado.DISPONIBLE
                    _uiState.value = DiagnosticState.SinFicha(cleanMarca, cleanModelo, cleanCc, cleanAnio)
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
                _uiState.value = DiagnosticState.Error(
                    message = "No se pudo completar la consulta.",
                    detalle = ErrorDetails.detalle(e)
                )
            }
        }
    }

    private fun describeLibraryError(e: Exception): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "La biblioteca de fichas no permite la lectura. Hay que activar Firestore y publicar sus reglas."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "No hay conexión con la biblioteca de fichas."
        e is IOException -> "No hay conexión con la biblioteca de fichas."
        else -> "No se pudo leer la biblioteca de fichas."
    }
}
