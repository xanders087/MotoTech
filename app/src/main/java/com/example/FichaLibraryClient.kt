package com.example

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.TimeoutCancellationException
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
    private const val SOLICITUDES = "solicitudes"
    private const val TIMEOUT_MS = 20_000L
    private const val TIMEOUT_SOLICITUD_MS = 12_000L

    /**
     * Registra que un usuario pide la ficha de una moto. Las solicitudes de la misma moto y año
     * se suman en un solo documento. Devuelve false si quedó en cola por falta de conexión:
     * Firestore la envía sola cuando vuelve la red.
     */
    suspend fun solicitar(marca: String, modelo: String, cilindraje: String, anio: Int): Boolean {
        val datos = mapOf(
            "marca" to marca.trim().take(60),
            "modelo" to modelo.trim().take(80),
            "cilindraje_cc" to (FichaMapper.cilindrajeCc(cilindraje) ?: 0),
            "anio" to anio,
            "clave" to FichaMapper.clave(marca, modelo, cilindraje).take(200),
            "total" to FieldValue.increment(1),
            "ultima" to FieldValue.serverTimestamp()
        )
        val id = FichaMapper.idSolicitud(marca, modelo, cilindraje, anio)
        return try {
            withTimeout(TIMEOUT_SOLICITUD_MS) {
                suspendCancellableCoroutine<Unit> { cont ->
                    Firebase.firestore.collection(SOLICITUDES).document(id)
                        .set(datos, SetOptions.merge())
                        .addOnSuccessListener { cont.resume(Unit) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
            }
            true
        } catch (e: TimeoutCancellationException) {
            false
        }
    }

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
