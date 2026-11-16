package com.example

import com.squareup.moshi.JsonClass

/**
 * Ficha de una moto tal como la muestra la app. Todos los campos son anulables a propósito:
 * si el manual no trae un dato, queda en null y la app muestra "Sin dato".
 */
@JsonClass(generateAdapter = true)
data class DiagnosticResponse(
    val status: String? = null,
    val moto_info: MotoInfo? = null,
    val ficha_tecnica: FichaTecnica? = null,
    val plan_mantenimiento_periodico: List<PlanMantenimiento>? = null,
    // Manual oficial del que se transcribió la ficha.
    val manual: ManualInfo? = null
)

@JsonClass(generateAdapter = true)
data class ManualInfo(
    val titulo: String? = null,
    val edicion: String? = null,
    val url: String? = null,
    val revisado_por: String? = null,
    val fecha_revision: String? = null,
    val anio_desde: Int? = null,
    val anio_hasta: Int? = null
)

@JsonClass(generateAdapter = true)
data class MotoInfo(
    val marca: String? = null,
    val modelo: String? = null,
    val cilindraje: String? = null,
    val anio: Int? = null
)

/** Un dato técnico con la página del manual de la que salió. */
@JsonClass(generateAdapter = true)
data class DatoTecnico(
    val valor: String? = null,
    val pagina: Int? = null
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
    val pagina: Int? = null
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
