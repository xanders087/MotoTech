package com.example

/** Prepara el detalle técnico de un error para mostrarlo en pantalla. */
object ErrorDetails {
    private const val MAX_DETALLE = 700

    /** Mensaje original del servidor, recortado, para mostrarlo como detalle técnico. */
    fun detalle(e: Throwable): String? {
        val texto = (e.message ?: e.cause?.message)?.trim().orEmpty()
        if (texto.isEmpty()) return null
        val tipo = e::class.simpleName ?: "Error"
        val cuerpo = if (texto.length > MAX_DETALLE) texto.take(MAX_DETALLE) + "…" else texto
        return "$tipo: $cuerpo"
    }
}
