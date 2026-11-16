package com.example

enum class EstadoMantenimiento { VENCIDO, PROXIMO, AL_DIA, SIN_HISTORIAL }

data class UltimoServicio(val kilometraje: Int, val fechaMillis: Long)

data class ItemMantenimiento(
    val plan: PlanMantenimiento,
    val componente: String,
    val estado: EstadoMantenimiento,
    val ultimo: UltimoServicio?,
    /** Negativo si ya se pasó. Null si el plan no tiene intervalo en km o no hay historial. */
    val kmRestantes: Int?,
    /** Negativo si ya se pasó. Null si el plan no tiene intervalo en meses o no hay historial. */
    val diasRestantes: Int?
)

/**
 * Calcula qué mantenimiento toca a partir del intervalo citado y del último
 * servicio que el usuario registró. No supone desgaste: sin historial no hay veredicto.
 */
object MaintenanceCalculator {
    private const val MILLIS_DIA = 24L * 60 * 60 * 1000
    private const val DIAS_MES = 30.44
    private const val MARGEN_PROXIMO = 0.10

    fun claveComponente(componente: String): String =
        componente.trim().lowercase().replace(Regex("\\s+"), " ")

    fun calcular(
        plan: List<PlanMantenimiento>,
        kmActual: Int,
        ultimosServicios: Map<String, UltimoServicio>,
        ahoraMillis: Long
    ): List<ItemMantenimiento> = plan.mapNotNull { p ->
        val componente = p.componente?.trim().orEmpty()
        val intervaloKm = p.intervalo_km?.takeIf { it > 0 }
        val intervaloMeses = p.intervalo_meses?.takeIf { it > 0 }
        // Sin componente o sin ningún intervalo no hay nada que calcular.
        if (componente.isEmpty() || (intervaloKm == null && intervaloMeses == null)) return@mapNotNull null

        val ultimo = ultimosServicios[claveComponente(componente)]
            ?: return@mapNotNull ItemMantenimiento(p, componente, EstadoMantenimiento.SIN_HISTORIAL, null, null, null)

        val kmRestantes = intervaloKm?.let { ultimo.kilometraje + it - kmActual }
        val diasRestantes = intervaloMeses?.let {
            val vence = ultimo.fechaMillis + (it * DIAS_MES * MILLIS_DIA).toLong()
            Math.floorDiv(vence - ahoraMillis, MILLIS_DIA).toInt()
        }

        val vencido = (kmRestantes != null && kmRestantes <= 0) || (diasRestantes != null && diasRestantes <= 0)
        val proximo = (kmRestantes != null && kmRestantes <= intervaloKm!! * MARGEN_PROXIMO) ||
            (diasRestantes != null && diasRestantes <= intervaloMeses!! * DIAS_MES * MARGEN_PROXIMO)
        val estado = when {
            vencido -> EstadoMantenimiento.VENCIDO
            proximo -> EstadoMantenimiento.PROXIMO
            else -> EstadoMantenimiento.AL_DIA
        }
        ItemMantenimiento(p, componente, estado, ultimo, kmRestantes, diasRestantes)
    }.sortedBy { it.estado.ordinal }
}
