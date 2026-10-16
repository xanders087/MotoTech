package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.data.DiagnosticRepository
import com.example.data.DiagnosticEntity

sealed class DiagnosticState {
    object Idle : DiagnosticState()
    object Loading : DiagnosticState()
    data class Success(val response: DiagnosticResponse) : DiagnosticState()
    data class Error(val message: String) : DiagnosticState()
}

class MainViewModel(private val repository: DiagnosticRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<DiagnosticState>(DiagnosticState.Idle)
    val uiState: StateFlow<DiagnosticState> = _uiState.asStateFlow()

    val history = repository.allDiagnostics

    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter(DiagnosticResponse::class.java)

    fun reset() {
        _uiState.value = DiagnosticState.Idle
    }

    fun diagnose(marca: String, modelo: String, cilindraje: String, anio: String, kilometraje: String) {
        _uiState.value = DiagnosticState.Loading

        viewModelScope.launch {
            try {
                // Check cache first
                val cached = repository.findDiagnostic(marca, modelo, anio, kilometraje)
                if (cached != null) {
                    val diagnosticResponse = jsonAdapter.fromJson(cached.jsonResponse)
                    if (diagnosticResponse != null) {
                        _uiState.value = DiagnosticState.Success(diagnosticResponse)
                        return@launch
                    }
                }

                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _uiState.value = DiagnosticState.Error("API Key no configurada. Añádela en la configuración.")
                    return@launch
                }

                val prompt = """
                    Analiza la siguiente motocicleta y proporciona un diagnóstico preventivo en formato JSON estricto:
                    {
                      "marca": "$marca",
                      "modelo": "$modelo",
                      "cilindraje": "$cilindraje",
                      "anio": ${anio.toIntOrNull() ?: 0},
                      "kilometraje_actual": ${kilometraje.toIntOrNull() ?: 0}
                    }
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json"
                    ),
                    systemInstruction = Content(parts = listOf(Part(text = """
                        Eres "MotoTech AI Engine", un motor API especializado en ingeniería mecánica de motocicletas y mantenimiento preventivo. Tu única función es procesar la información de una motocicleta (Marca, Modelo, Cilindraje, Año y Kilometraje actual) y responder ÚNICAMENTE con un JSON válido y bien estructurado.

                        REGLAS DE SALIDA:
                        1. Responde SIEMPRE en formato JSON estricto.
                        2. NO incluyas textos introductorios, ni saludos, ni explicaciones fuera del objeto JSON.
                        3. Si faltan datos clave (Marca, Modelo o Kilometraje), devuelve un JSON de estado "incompleto" especificando los campos faltantes.

                        ESQUEMA JSON DE SALIDA REQUERIDO:
                        {
                          "status": "success",
                          "moto_info": {
                            "marca": "string",
                            "modelo": "string",
                            "cilindraje": "string",
                            "anio": 0,
                            "kilometraje_actual": 0
                          },
                          "ficha_tecnica": {
                            "capacidad_aceite_litros": "string",
                            "viscosidad_aceite_recomendada": "string",
                            "presion_llantas_psi": {
                              "delantera_solo": 0,
                              "delantera_acompanante": 0,
                              "trasera_solo": 0,
                              "trasera_acompanante": 0
                            },
                            "holgura_cadena_mm": "string",
                            "tipo_liquido_frenos": "string"
                          },
                          "diagnostico_kilometraje": {
                            "atencion_inmediata": [
                              {
                                "componente": "string",
                                "motivo": "string",
                                "prioridad": "alta"
                              }
                            ],
                            "proxima_revision": [
                              {
                                "componente": "string",
                                "kilometros_restantes": 0,
                                "prioridad": "media"
                              }
                            ],
                            "rutina_habitual": [
                              "string"
                            ]
                          },
                          "plan_mantenimiento_periodico": [
                            {
                              "componente": "string",
                              "intervalo_km": 0,
                              "accion": "string"
                            }
                          ],
                          "recomendaciones_clave": [
                            "string"
                          ]
                        }

                        ESQUEMA DE ERROR (Si faltan datos indispensables):
                        {
                          "status": "error",
                          "mensaje": "Faltan parámetros para realizar el diagnóstico.",
                          "campos_faltantes": ["marca", "modelo", "kilometraje_actual"]
                        }
                    """.trimIndent())))
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (responseText != null) {
                    try {
                        val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
                        val diagnosticResponse = jsonAdapter.fromJson(cleanJson)
                        if (diagnosticResponse != null) {
                            if (diagnosticResponse.status == "success" || diagnosticResponse.status == "error") {
                                if (diagnosticResponse.status == "success") {
                                    repository.insert(
                                        com.example.data.DiagnosticEntity(
                                            marca = marca,
                                            modelo = modelo,
                                            cilindraje = cilindraje,
                                            anio = anio,
                                            kilometraje = kilometraje,
                                            jsonResponse = cleanJson
                                        )
                                    )
                                }
                                _uiState.value = DiagnosticState.Success(diagnosticResponse)
                            } else {
                                _uiState.value = DiagnosticState.Error("Respuesta inesperada: ${diagnosticResponse.status}")
                            }
                        } else {
                            _uiState.value = DiagnosticState.Error("No se pudo parsear el JSON.")
                        }
                    } catch (e: Exception) {
                        _uiState.value = DiagnosticState.Error("Error al procesar JSON: ${e.message}")
                    }
                } else {
                    _uiState.value = DiagnosticState.Error("Respuesta vacía del servidor.")
                }

            } catch (e: Exception) {
                _uiState.value = DiagnosticState.Error("Error de red: ${e.message}")
            }
        }
    }
}
