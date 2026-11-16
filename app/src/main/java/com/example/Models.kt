package com.example

import com.squareup.moshi.JsonClass

/**
 * Respuesta del diagnóstico. Todos los campos son anulables a propósito:
 * si no hay una fuente para un dato, el dato queda en null y la app muestra "Sin dato".
 */
@JsonClass(generateAdapter = true)
data class DiagnosticResponse(
    val status: String? = null,
    val moto_info: MotoInfo? = null,
    val ficha_tecnica: FichaTecnica? = null,
    val plan_mantenimiento_periodico: List<PlanMantenimiento>? = null,
    val mensaje: String? = null,
    val campos_faltantes: List<String>? = null,
    // Lo llena la app con los resultados reales de la búsqueda, nunca el modelo.
    val fuentes: List<FuenteConsultada>? = null,
    // HTML de sugerencias de búsqueda que Google exige mostrar junto a un resultado con búsqueda.
    val sugerencias_busqueda_html: String? = null
)

@JsonClass(generateAdapter = true)
data class MotoInfo(
    val marca: String? = null,
    val modelo: String? = null,
    val cilindraje: String? = null,
    val anio: Int? = null
)

/** Un dato técnico con la fuente de la que salió. */
@JsonClass(generateAdapter = true)
data class DatoTecnico(
    val valor: String? = null,
    val fuente_titulo: String? = null,
    val fuente_dominio: String? = null,
    // manual_fabricante | sitio_oficial | otro
    val tipo_fuente: String? = null
)

@JsonClass(generateAdapter = true)
data class FichaTecnica(
    val capacidad_aceite: DatoTecnico? = null,
    val viscosidad_aceite: DatoTecnico? = null,
    val presion_llanta_delantera: DatoTecnico? = null,
    val presion_llanta_trasera: DatoTecnico? = null,
    val holgura_cadena: DatoTecnico? = null,
    val liquido_frenos: DatoTecnico? = null
)

@JsonClass(generateAdapter = true)
data class PlanMantenimiento(
    val componente: String? = null,
    val intervalo_km: Int? = null,
    val intervalo_meses: Int? = null,
    val accion: String? = null,
    val fuente_titulo: String? = null,
    val fuente_dominio: String? = null,
    val tipo_fuente: String? = null
)

/** Página que la búsqueda realmente devolvió. */
@JsonClass(generateAdapter = true)
data class FuenteConsultada(
    val titulo: String? = null,
    val url: String? = null
)

// UI and Business Models
data class MaintenanceLogItem(
    val id: String,
    val fecha: String,
    val kilometraje: Int,
    val titulo: String,
    val taller: String,
    val costo: String,
    val facturaNumero: String,
    val categoria: String,
    val tieneComprobante: Boolean
)

data class WorkshopItem(
    val id: String,
    val nombre: String,
    val direccion: String,
    val distancia: String,
    val calificacion: Double,
    val totalResenas: Int,
    val especialidades: List<String>,
    val descuentoConvenio: String,
    val telefono: String,
    val horario: String,
    val latitud: Double = 4.6097,
    val longitud: Double = -74.0817
)

data class FleetBike(
    val id: String,
    val codigo: String,
    val placa: String,
    val conductor: String,
    val modelo: String,
    val anio: Int,
    val kilometraje: Int,
    val estado: FleetHealthStatus,
    val alertaPrincipal: String,
    val diasUltimoService: Int
)

enum class FleetHealthStatus {
    OPTIMO,
    REVISION_PROXIMA,
    ATENCION_URGENTE
}
