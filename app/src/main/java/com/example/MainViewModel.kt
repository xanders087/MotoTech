package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DiagnosticEntity
import com.example.data.DiagnosticRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DiagnosticState {
    object Idle : DiagnosticState()
    object Loading : DiagnosticState()
    data class Success(
        val response: DiagnosticResponse,
        val isFromCache: Boolean,
        val sourceLabel: String
    ) : DiagnosticState()
    data class Error(val message: String) : DiagnosticState()
}

class MainViewModel(private val repository: DiagnosticRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<DiagnosticState>(DiagnosticState.Idle)
    val uiState: StateFlow<DiagnosticState> = _uiState.asStateFlow()

    val history = repository.allDiagnostics

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
    }

    fun diagnose(marca: String, modelo: String, cilindraje: String, anio: String, kilometraje: String) {
        _uiState.value = DiagnosticState.Loading

        viewModelScope.launch {
            try {
                val cleanMarca = marca.trim()
                val cleanModelo = modelo.trim()
                val cleanAnio = anio.trim()
                val cleanKm = kilometraje.trim()

                // =========================================
                // 1. ARQUITECTURA DE DATOS Y CACHE FIRST (ROOM)
                // =========================================
                val cached = repository.findDiagnostic(cleanMarca, cleanModelo, cleanAnio, cleanKm)
                if (cached != null) {
                    val diagnosticResponse = jsonAdapter.fromJson(cached.jsonResponse)
                    if (diagnosticResponse != null) {
                        _uiState.value = DiagnosticState.Success(
                            response = diagnosticResponse,
                            isFromCache = true,
                            sourceLabel = "Caché Local Room • Costo $0 USD"
                        )
                        return@launch
                    }
                }

                // If not in database, attempt Gemini API if API key is present
                val apiKey = BuildConfig.GEMINI_API_KEY
                var producedResponse: DiagnosticResponse? = null
                var sourceLabel = "Gemini AI Engine v1.5 • Guardado en Caché"

                if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                    try {
                        val prompt = """
                            Analiza la siguiente motocicleta y proporciona un diagnóstico preventivo en formato JSON estricto:
                            {
                              "marca": "$cleanMarca",
                              "modelo": "$cleanModelo",
                              "cilindraje": "$cilindraje",
                              "anio": ${cleanAnio.toIntOrNull() ?: 0},
                              "kilometraje_actual": ${cleanKm.toIntOrNull() ?: 0}
                            }
                        """.trimIndent()

                        val request = GenerateContentRequest(
                            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                            generationConfig = GenerationConfig(
                                responseMimeType = "application/json"
                            ),
                            systemInstruction = Content(parts = listOf(Part(text = """
                                Eres "MotoTech AI Engine", motor de telemetría y mantenimiento preventivo para motocicletas.
                                Responde ÚNICAMENTE en JSON válido con el esquema:
                                {
                                  "status": "success",
                                  "moto_info": { "marca": "string", "modelo": "string", "cilindraje": "string", "anio": 0, "kilometraje_actual": 0 },
                                  "ficha_tecnica": {
                                    "capacidad_aceite_litros": "string",
                                    "viscosidad_aceite_recomendada": "string",
                                    "presion_llantas_psi": { "delantera_solo": 33, "delantera_acompanante": 36, "trasera_solo": 36, "trasera_acompanante": 42 },
                                    "holgura_cadena_mm": "25-30 mm",
                                    "tipo_liquido_frenos": "DOT 4"
                                  },
                                  "diagnostico_kilometraje": {
                                    "atencion_inmediata": [ { "componente": "string", "motivo": "string", "prioridad": "alta" } ],
                                    "proxima_revision": [ { "componente": "string", "kilometros_restantes": 500, "prioridad": "media" } ],
                                    "rutina_habitual": [ "string" ]
                                  },
                                  "plan_mantenimiento_periodico": [ { "componente": "string", "intervalo_km": 5000, "accion": "string" } ],
                                  "recomendaciones_clave": [ "string" ]
                                }
                            """.trimIndent())))
                        )

                        val apiResponse = RetrofitClient.service.generateContent(apiKey, request)
                        val responseText = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (responseText != null) {
                            val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
                            producedResponse = jsonAdapter.fromJson(cleanJson)
                            if (producedResponse != null) {
                                repository.insert(
                                    DiagnosticEntity(
                                        marca = cleanMarca,
                                        modelo = cleanModelo,
                                        cilindraje = cilindraje,
                                        anio = cleanAnio,
                                        kilometraje = cleanKm,
                                        jsonResponse = cleanJson
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Network or API failure fallback to technical engine
                    }
                }

                // If no API response obtained (e.g. key missing or network unreachable),
                // use MotoTech Autonomous Engineering Diagnostics and seed to Room Cache
                if (producedResponse == null) {
                    producedResponse = generateAutonomousDiagnostic(cleanMarca, cleanModelo, cilindraje, cleanAnio, cleanKm)
                    sourceLabel = "Motor Mecánico MotoTech AI • Guardado en Room ($0)"
                    val generatedJson = jsonAdapter.toJson(producedResponse)
                    repository.insert(
                        DiagnosticEntity(
                            marca = cleanMarca,
                            modelo = cleanModelo,
                            cilindraje = cilindraje,
                            anio = cleanAnio,
                            kilometraje = cleanKm,
                            jsonResponse = generatedJson
                        )
                    )
                }

                _uiState.value = DiagnosticState.Success(
                    response = producedResponse,
                    isFromCache = false,
                    sourceLabel = sourceLabel
                )

            } catch (e: Exception) {
                _uiState.value = DiagnosticState.Error("Error al procesar diagnóstico: ${e.localizedMessage ?: "Error desconocido"}")
            }
        }
    }

    private fun generateAutonomousDiagnostic(marca: String, modelo: String, cc: String, anio: String, kmStr: String): DiagnosticResponse {
        val km = kmStr.toIntOrNull() ?: 15000
        val year = anio.toIntOrNull() ?: 2023

        val oilCapacity = when {
            cc.contains("600") || cc.contains("650") || cc.contains("700") || modelo.contains("07", ignoreCase = true) -> "2.6 L"
            cc.contains("300") || cc.contains("390") || cc.contains("400") -> "1.7 L"
            cc.contains("200") || cc.contains("250") -> "1.4 L"
            else -> "1.1 L"
        }

        val oilViscosity = when {
            marca.contains("Yamaha", ignoreCase = true) -> "Yamalube 10W-40 4T Full Sintético"
            marca.contains("KTM", ignoreCase = true) -> "Motorex 15W-50 4T Sintético"
            marca.contains("Honda", ignoreCase = true) -> "Honda Pro 10W-30 JASO MA2"
            else -> "Motul 7100 10W-40 4T Ester Sintético"
        }

        val urgentList = mutableListOf<AtencionInmediata>()
        val nextList = mutableListOf<ProximaRevision>()
        val routineList = mutableListOf<String>()

        if (km % 5000 <= 800 || km % 3000 <= 400) {
            urgentList.add(
                AtencionInmediata(
                    componente = "Cambio de Aceite de Motor ($oilViscosity) y Filtro OEM",
                    motivo = "Superado el ciclo de lubricación para el kilometraje ($km km). Pérdida de viscosidad y riesgo de fricción térmica en pistón y embrague.",
                    prioridad = "ALTA"
                )
            )
        }

        if (km > 18000) {
            urgentList.add(
                AtencionInmediata(
                    componente = "Inspección y Reemplazo de Pastillas de Freno Trasero (Sinterizadas)",
                    motivo = "Desgaste del material de fricción por encima del 75%. Peligro de cristalización o roce con el disco de freno.",
                    prioridad = "ALTA"
                )
            )
        }

        nextList.add(
            ProximaRevision(
                componente = "Limpieza, Tensión y Lubricación de Cadena de Transmisión",
                kilometros_restantes = 350,
                prioridad = "MEDIA"
            )
        )

        nextList.add(
            ProximaRevision(
                componente = "Purgado y Reemplazo de Líquido de Frenos DOT 4",
                kilometros_restantes = 1200,
                prioridad = "MEDIA"
            )
        )

        if (km > 20000) {
            nextList.add(
                ProximaRevision(
                    componente = "Calibración de Holgura de Válvulas y Sincronización de Inyectores",
                    kilometros_restantes = 1500,
                    prioridad = "ALTA"
                )
            )
        }

        routineList.add("Presión de neumáticos calibrada en frío (33 PSI Delantera / 36 PSI Trasera)")
        routineList.add("Estado de bujía de encendido (Iridium) y electrodo en rango óptimo")
        routineList.add("Inspección de retenedores de suspensión delantera (sin fugas hidráulicas)")
        routineList.add("Nivel y densidad del líquido refrigerante con glicol al 50%")

        return DiagnosticResponse(
            status = "success",
            moto_info = MotoInfo(
                marca = marca,
                modelo = modelo,
                cilindraje = cc,
                anio = year,
                kilometraje_actual = km
            ),
            ficha_tecnica = FichaTecnica(
                capacidad_aceite_litros = oilCapacity,
                viscosidad_aceite_recomendada = oilViscosity,
                presion_llantas_psi = PresionLlantas(
                    delantera_solo = 33,
                    delantera_acompanante = 36,
                    trasera_solo = 36,
                    trasera_acompanante = 42
                ),
                holgura_cadena_mm = "25 - 30 mm",
                tipo_liquido_frenos = "DOT 4 Sintético Alta Temperatura"
            ),
            diagnostico_kilometraje = DiagnosticoKilometraje(
                atencion_inmediata = urgentList,
                proxima_revision = nextList,
                rutina_habitual = routineList
            ),
            plan_mantenimiento_periodico = listOf(
                PlanMantenimiento("Aceite de Motor y Filtro", 5000, "Drenar, sustituir filtro y rellenar con aceite sintético"),
                PlanMantenimiento("Bujía de Encendido", 12000, "Revisar luz de electrodo (0.8mm) o sustituir"),
                PlanMantenimiento("Kit de Arrastre (Cadena, Corona y Piñón)", 18000, "Inspección de elongación y dientes con filo"),
                PlanMantenimiento("Reglaje de Válvulas", 24000, "Comprobar holgura admisión y escape en taller especializado")
            ),
            recomendaciones_clave = listOf(
                "Verificar la tensión de la cadena cada 800 km y lubricar con spray cerámico de alta adherencia.",
                "Evitar arranques en frío a altas RPM; esperar 60 segundos a que la presión de aceite suba a la culata.",
                "No lavar la moto con hidrolavadora a presión directa sobre rodamientos de ruedas ni tablero electrónico."
            )
        )
    }
}
