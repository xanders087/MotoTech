package com.example

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Lee la biblioteca de fichas de manuales oficiales (Firestore, colección "fichas").
 * No genera nada: devuelve la ficha publicada o null si esa moto aún no está cargada.
 */
object FichaLibraryClient {
    private const val COLECCION = "fichas"
    private const val TIMEOUT_MS = 20_000L

    suspend fun buscar(marca: String, modelo: String, cilindraje: String, anio: Int): DiagnosticResponse? {
        val clave = FichaMapper.clave(marca, modelo, cilindraje)
        val (documentos, desdeCache) = withTimeout(TIMEOUT_MS) { consultar(clave) }

        val ficha = FichaMapper.seleccionar(documentos, anio)
        // Sin conexión Firestore responde con lo que tenga guardado; si no tiene nada,
        // no se puede afirmar que la ficha no exista.
        if (ficha == null && desdeCache) throw IOException("Sin conexión con la biblioteca de fichas.")
        return ficha?.let { FichaMapper.toResponse(it) }
    }

    private suspend fun consultar(clave: String): Pair<List<Map<String, Any?>>, Boolean> =
        suspendCancellableCoroutine { cont ->
            Firebase.firestore.collection(COLECCION)
                .whereEqualTo("clave", clave)
                .get()
                .addOnSuccessListener { snapshot ->
                    val docs = snapshot.documents.mapNotNull { it.data }
                    cont.resume(docs to snapshot.metadata.isFromCache)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
