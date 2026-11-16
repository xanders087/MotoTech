package com.example

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null,
    val tools: List<Tool>? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

/** Herramienta de búsqueda en Google: se envía como {"google_search": {}}. */
@JsonClass(generateAdapter = true)
data class Tool(
    @param:Json(name = "google_search") val googleSearch: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null,
    val groundingMetadata: GroundingMetadata? = null
)

@JsonClass(generateAdapter = true)
data class GroundingMetadata(
    val groundingChunks: List<GroundingChunk>? = null
)

@JsonClass(generateAdapter = true)
data class GroundingChunk(
    val web: WebSource? = null
)

@JsonClass(generateAdapter = true)
data class WebSource(
    val uri: String? = null,
    val title: String? = null
)

@JsonClass(generateAdapter = true)
data class ModelsListResponse(
    val models: List<GeminiModelInfo>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiModelInfo(
    val name: String? = null,
    val supportedGenerationMethods: List<String>? = null
)

interface GeminiApiService {
    @GET("v1beta/models")
    suspend fun listModels(
        @Header("x-goog-api-key") apiKey: String
    ): ModelsListResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GenerateContentRequest,
        @Path(value = "model", encoded = true) model: String = RetrofitClient.DEFAULT_MODEL
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    const val DEFAULT_MODEL = "gemini-3.8-flash"

    /**
     * Lista de modelos Gemini 3.x soportados en orden de prioridad.
     */
    val FALLBACK_MODELS: List<String> = listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash-lite"
    )

    @Volatile
    private var cachedAvailableModels: List<String>? = null

    /**
     * Descubre dinámicamente qué modelos Gemini con soporte para generateContent están activos,
     * priorizando gemini-3.8-flash y gemini-3.5-flash-lite.
     */
    suspend fun resolveCandidateModels(apiKey: String): List<String> {
        cachedAvailableModels?.let { if (it.isNotEmpty()) return it }

        val discovered = runCatching {
            val response = service.listModels(apiKey)
            filterAndPrioritizeModels(response.models.orEmpty())
        }.getOrDefault(emptyList())

        if (discovered.isNotEmpty()) {
            cachedAvailableModels = discovered
            return discovered
        }
        return FALLBACK_MODELS
    }

    internal fun filterAndPrioritizeModels(models: List<GeminiModelInfo>): List<String> {
        val excludedKeywords = listOf(
            "embedding", "imagen", "veo", "aqa", "tts", "audio",
            "vision", "learnlm", "Gemma", "gemma", "robotics", "computer-use", "thinking"
        )
        val validNames = models.mapNotNull { info ->
            val methods = info.supportedGenerationMethods.orEmpty()
            if (!methods.contains("generateContent")) return@mapNotNull null
            val rawName = info.name?.removePrefix("models/")?.trim().orEmpty()
            if (!rawName.startsWith("gemini-3", ignoreCase = true) && !rawName.startsWith("gemini-4", ignoreCase = true)) {
                return@mapNotNull null
            }
            if (excludedKeywords.any { rawName.contains(it, ignoreCase = true) }) return@mapNotNull null
            rawName
        }.distinct()

        if (validNames.isEmpty()) return emptyList()

        return validNames.sortedWith(
            compareBy<String> { name ->
                val idx = FALLBACK_MODELS.indexOfFirst { it.equals(name, ignoreCase = true) }
                if (idx >= 0) idx else 100
            }.thenBy { name ->
                when {
                    name.contains("3.8-flash", ignoreCase = true) -> 0
                    name.contains("3.5-flash-lite", ignoreCase = true) -> 1
                    name.contains("flash", ignoreCase = true) -> 2
                    else -> 3
                }
            }
        )
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}
