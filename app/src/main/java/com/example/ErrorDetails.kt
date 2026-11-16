package com.example

/** Interpreta los mensajes de error del servicio de consulta. */
object ErrorDetails {
    private const val MAX_DETALLE = 700

    /**
     * El SDK reporta como "cuota" cualquier respuesta que mencione la palabra. Aquí se distingue
     * el caso en que el proyecto no tiene cuota asignada (límite 0 o solo plan de pago), donde
     * esperar y reintentar no sirve.
     */
    fun esCuotaCero(mensaje: String?): Boolean {
        val m = mensaje?.lowercase() ?: return false
        return Regex("limit:\\s*0(?![0-9.])").containsMatchIn(m) ||
            m.contains("free_tier") || m.contains("free tier") ||
            m.contains("billing")
    }

    /** Mensaje original del servidor, recortado, para mostrarlo como detalle técnico. */
    fun detalle(e: Throwable): String? {
        val texto = (e.message ?: e.cause?.message)?.trim().orEmpty()
        if (texto.isEmpty()) return null
        val tipo = e::class.simpleName ?: "Error"
        val cuerpo = if (texto.length > MAX_DETALLE) texto.take(MAX_DETALLE) + "…" else texto
        return "$tipo: $cuerpo"
    }
}
