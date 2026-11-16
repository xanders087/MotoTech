package com.example

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.app

/** Lo que devuelve una consulta: el texto del modelo y las páginas que la búsqueda encontró. */
data class SourceLookupResult(
    val text: String,
    val fuentes: List<FuenteConsultada>,
    /** HTML de "sugerencias de búsqueda" que Google exige mostrar junto al resultado. */
    val sugerenciasHtml: String?
)

/** La app se compiló sin google-services.json, así que Firebase no arrancó. */
class FirebaseNotConfiguredException : Exception()

/**
 * Consulta a Gemini con búsqueda de Google a través de Firebase AI Logic.
 * La clave de Gemini vive en Firebase, no en la app, y App Check solo deja pasar a la app legítima.
 */
object GeminiSourceClient {
    private const val MODEL = "gemini-3.5-flash"

    suspend fun lookup(systemPrompt: String, userPrompt: String): SourceLookupResult {
        try {
            Firebase.app
        } catch (e: IllegalStateException) {
            throw FirebaseNotConfiguredException()
        }

        val response = try {
            model(systemPrompt, jsonMode = true).generateContent(userPrompt)
        } catch (e: ServerException) {
            // Algunos modelos no aceptan salida JSON forzada junto con la búsqueda:
            // se reintenta pidiendo el JSON solo por instrucciones.
            model(systemPrompt, jsonMode = false).generateContent(userPrompt)
        }

        val grounding = response.candidates.firstOrNull()?.groundingMetadata
        val fuentes = grounding?.groundingChunks.orEmpty()
            .mapNotNull { it.web }
            .filter { !it.uri.isNullOrBlank() }
            .map { FuenteConsultada(titulo = it.title ?: it.domain, url = it.uri) }
            .distinctBy { it.url }

        return SourceLookupResult(
            text = response.text.orEmpty(),
            fuentes = fuentes,
            sugerenciasHtml = grounding?.searchEntryPoint?.renderedContent
        )
    }

    private fun model(systemPrompt: String, jsonMode: Boolean) =
        // AI Logic solo acepta tokens de App Check de uso limitado.
        Firebase.ai(backend = GenerativeBackend.googleAI(), useLimitedUseAppCheckTokens = true)
            .generativeModel(
                modelName = MODEL,
                generationConfig = if (jsonMode) generationConfig { responseMimeType = "application/json" } else null,
                tools = listOf(Tool.googleSearch()),
                systemInstruction = content(role = "system") { text(systemPrompt) }
            )
}
