package com.example

import com.squareup.moshi.JsonAdapter

object DiagnosticParser {
    /** Extrae el objeto JSON de la respuesta aunque venga rodeado de texto o de ``` . */
    fun parse(text: String, adapter: JsonAdapter<DiagnosticResponse>): DiagnosticResponse? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { adapter.fromJson(text.substring(start, end + 1)) }.getOrNull()
    }
}
