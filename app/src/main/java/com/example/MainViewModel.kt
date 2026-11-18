package com.example

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DiagnosticEntity
import com.example.data.DiagnosticRepository
import com.example.data.ServiceRecordEntity
import com.google.firebase.firestore.FirebaseFirestoreException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    // Catálogo de Marcas y Modelos (en línea desde Firestore con respaldo local)
    private val _catalogo = MutableStateFlow(MotorcycleCatalog.snapshot())
    val catalogo: StateFlow<Map<String, List<MotoModelSpec>>> = _catalogo.asStateFlow()

    val marcasDisponibles: List<String>
        get() = _catalogo.value.keys.toList()

    private val _modelosDeMarca = MutableStateFlow<List<MotoModelSpec>>(emptyList())
    val modelosDeMarca: StateFlow<List<MotoModelSpec>> = _modelosDeMarca.asStateFlow()

    init {
        refrescarCatalogoRemoto()
    }

    val reportedIds: StateFlow<Set<String>> = repository.allReportedIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _reportandoCampo = MutableStateFlow<String?>(null)
    val reportandoCampo: StateFlow<String?> = _reportandoCampo.asStateFlow()

    private val _reporteMensaje = MutableStateFlow<String?>(null)
    val reporteMensaje: StateFlow<String?> = _reporteMensaje.asStateFlow()

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

    // Digital Logbook (Hoja de Vida) Records (solo datos reales registrados por el usuario)
    private val _maintenanceLogs = MutableStateFlow<List<MaintenanceLogItem>>(emptyList())
    val maintenanceLogs: StateFlow<List<MaintenanceLogItem>> = _maintenanceLogs.asStateFlow()

    // Fleet Vehicles for B2B Mode (sin vehículos de ejemplo)
    private val _fleetBikes = MutableStateFlow<List<FleetBike>>(emptyList())
    val fleetBikes: StateFlow<List<FleetBike>> = _fleetBikes.asStateFlow()

    // Last booked appointment confirmation
    private val _bookingConfirmation = MutableStateFlow<String?>(null)
    val bookingConfirmation: StateFlow<String?> = _bookingConfirmation.asStateFlow()

    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter(DiagnosticResponse::class.java)

    /**
     * Selecciona una marca del catálogo, limpia el modelo y cilindrada previos si cambió la marca
     * y carga los modelos disponibles.
     */
    fun selectMarca(nuevaMarca: String) {
        val cleanMarca = nuevaMarca.trim()
        val changed = !formMarca.equals(cleanMarca, ignoreCase = true)
        formMarca = cleanMarca
        if (changed) {
            formModelo = ""
            formCilindraje = ""
        }
        loadModelsForBrand(cleanMarca)
    }

    /**
     * Selecciona un modelo perteneciente a la marca actual y asigna su cilindrada fija.
     */
    fun selectModelo(spec: MotoModelSpec) {
        formModelo = spec.modelo
        formCilindraje = MotorcycleCatalog.normalizeCilindraje(spec.cilindraje)
    }

    private fun refrescarCatalogoRemoto() {
        viewModelScope.launch {
            val remoto = FichaLibraryClient.cargarCatalogo()
            if (remoto.isNotEmpty()) {
                _catalogo.value = MotorcycleCatalog.mergeWithOfflineCatalog(remoto)
                if (formMarca.isNotBlank()) {
                    loadModelsForBrand(formMarca)
                }
            }
        }
    }

    private fun loadModelsForBrand(marca: String) {
        val cleanMarca = marca.trim()
        if (cleanMarca.isEmpty()) {
            _modelosDeMarca.value = emptyList()
            return
        }

        val models = MotorcycleCatalog.modelsForBrand(cleanMarca, _catalogo.value)
        _modelosDeMarca.value = models
        if (formModelo.isNotBlank() && formCilindraje.isBlank()) {
            MotorcycleCatalog.findDisplacement(cleanMarca, formModelo, models)?.let {
                formCilindraje = it
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
        fechaMillis: Long = System.currentTimeMillis(),
        notas: String = ""
    ) {
        // Persiste el servicio realizado en la base de datos Room (service_records)
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
        val anio = moto.anio.toIntOrNull() ?: return
        solicitarAnio(moto.marca, moto.modelo, moto.cilindraje, anio)
    }

    /** Pide que se cargue la ficha de un año específico (p. ej. cuando se mostró un año aproximado). */
    fun solicitarAnio(marca: String, modelo: String, cilindraje: String, anio: Int) {
        if (anio <= 0) return
        if (_solicitud.value == SolicitudEstado.ENVIANDO || _solicitud.value == SolicitudEstado.ENVIADA) return
        _solicitud.value = SolicitudEstado.ENVIANDO
        viewModelScope.launch {
            try {
                val requestId = FichaMapper.idSolicitud(marca, modelo, cilindraje, anio)
                if (repository.wasFichaRequested(requestId)) {
                    _solicitud.value = SolicitudEstado.ENVIADA
                    return@launch
                }
                val confirmada = FichaLibraryClient.solicitar(marca, modelo, cilindraje, anio)
                repository.markFichaRequested(requestId)
                _solicitud.value = if (confirmada) SolicitudEstado.ENVIADA else SolicitudEstado.EN_COLA
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Ficha request failed", e)
                _solicitud.value = SolicitudEstado.ERROR
            }
        }
    }

    fun clearReporteMensaje() {
        _reporteMensaje.value = null
    }

    /** Reporta un dato técnico o tarea de mantenimiento sospechoso. Cuenta una vez por teléfono. */
    fun reportarDato(
        fichaId: String,
        marca: String,
        modelo: String,
        cilindraje: String,
        campo: String,
        valorReportado: String,
        motivo: String
    ) {
        val reportId = FichaMapper.idReporte(fichaId, campo)
        if (_reportandoCampo.value != null) return
        _reportandoCampo.value = campo
        viewModelScope.launch {
            try {
                if (repository.wasFieldReported(reportId)) {
                    _reporteMensaje.value = "Ya habías reportado este dato desde este teléfono."
                    return@launch
                }
                val confirmada = FichaLibraryClient.reportar(
                    fichaId = fichaId,
                    marca = marca,
                    modelo = modelo,
                    cilindraje = cilindraje,
                    campo = campo,
                    valorReportado = valorReportado,
                    motivo = motivo
                )
                repository.markFieldReported(reportId)
                _reporteMensaje.value = if (confirmada) {
                    "Reporte enviado. Gracias por ayudarnos a mantener los datos fieles."
                } else {
                    "Reporte guardado. Se enviará automáticamente cuando recuperes la conexión."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Dato report failed", e)
                _reporteMensaje.value = "No se pudo enviar el reporte. Intenta de nuevo."
            } finally {
                _reportandoCampo.value = null
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

    /** Actualiza la clave de vehículo activo solo si hay una motocicleta seleccionada. */
    fun syncActiveVehicleKey() {
        if (formMarca.isNotBlank() && formModelo.isNotBlank()) {
            _vehicleKey.value = VehicleKey.of(formMarca, formModelo, formCilindraje, formAnio)
        }
    }

    /** Devuelve la clave del vehículo activo para asociar registros de servicio. */
    fun ensureActiveVehicleKey(): String {
        val currentKey = _vehicleKey.value
        if (!currentKey.isNullOrBlank()) return currentKey
        if (formMarca.isNotBlank() || formModelo.isNotBlank()) {
            val computed = VehicleKey.of(formMarca, formModelo, formCilindraje, formAnio)
            _vehicleKey.value = computed
            return computed
        }
        return "general"
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
            val anioNum = cleanAnio.toIntOrNull() ?: 0
            val km = kilometraje.trim().toIntOrNull() ?: 0
            val key = VehicleKey.of(cleanMarca, cleanModelo, cleanCc, cleanAnio)

            try {
                val cached = repository.findDiagnostic(key)
                val cachedResponse = cached?.let { runCatching { jsonAdapter.fromJson(it.jsonResponse) }.getOrNull() }

                suspend fun syncSolicitudEstadoSiAproximada(resp: DiagnosticResponse, registrarSiFalta: Boolean) {
                    if (anioNum <= 0) {
                        _solicitud.value = SolicitudEstado.DISPONIBLE
                        return
                    }
                    val requestId = FichaMapper.idSolicitud(cleanMarca, cleanModelo, cleanCc, anioNum)
                    val yaSolicitada = repository.wasFichaRequested(requestId)
                    if (yaSolicitada) {
                        _solicitud.value = SolicitudEstado.ENVIADA
                    } else if (resp.manual?.aproximada == true && registrarSiFalta) {
                        _solicitud.value = SolicitudEstado.ENVIANDO
                        runCatching {
                            val confirmada = FichaLibraryClient.solicitar(cleanMarca, cleanModelo, cleanCc, anioNum)
                            repository.markFichaRequested(requestId)
                            _solicitud.value = if (confirmada) SolicitudEstado.ENVIADA else SolicitudEstado.EN_COLA
                        }.onFailure {
                            _solicitud.value = SolicitudEstado.DISPONIBLE
                        }
                    } else {
                        _solicitud.value = SolicitudEstado.DISPONIBLE
                    }
                }

                suspend fun showCached(aviso: String?): Boolean {
                    if (cached == null || cachedResponse == null) return false
                    syncSolicitudEstadoSiAproximada(cachedResponse, registrarSiFalta = false)
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
                    FichaLibraryClient.buscar(cleanMarca, cleanModelo, cleanCc, anioNum)
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
                    val requestId = FichaMapper.idSolicitud(cleanMarca, cleanModelo, cleanCc, anioNum)
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
                // Sincroniza estado de solicitud (y si es aproximada, registra automáticamente el año pedido).
                syncSolicitudEstadoSiAproximada(fetched, registrarSiFalta = true)
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
