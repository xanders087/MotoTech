package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class OperationType {
    CREATE, UPDATE, DELETE, LIST, GET, WRITE
}

/**
 * Lee la biblioteca de fichas de manuales oficiales (Firestore, colección "fichas")
 * y registra solicitudes y reportes de datos incorrectos.
 */
object FichaLibraryClient {
    private const val TAG = "FichaLibraryClient"
    private const val COLECCION = "fichas"
    private const val CATALOGO = "catalogo"
    private const val SOLICITUDES = "solicitudes"
    private const val REPORTES = "reportes"
    private const val TIMEOUT_MS = 20_000L
    private const val TIMEOUT_CATALOGO_MS = 8_000L
    private const val TIMEOUT_SOLICITUD_MS = 12_000L
    const val DEFAULT_DATABASE_ID = "ai-studio-android-mototech-364d3daf-8123-4de3-b899-775d66bdcdfb"

    @Volatile
    private var configuredDatabaseId: String = DEFAULT_DATABASE_ID

    fun configure(context: Context) {
        val dbId = context.getString(R.string.firestore_database_id).trim()
        if (dbId.isNotEmpty()) {
            configuredDatabaseId = dbId
        }
    }

    private fun firestore(databaseId: String = configuredDatabaseId): FirebaseFirestore {
        val resolvedId = databaseId.ifBlank { configuredDatabaseId }.ifBlank { DEFAULT_DATABASE_ID }
        return FirebaseFirestore.getInstance(resolvedId)
    }

    private fun logFirestoreError(error: Exception, operationType: OperationType, path: String?) {
        runCatching {
            val errInfo = JSONObject().apply {
                put("error", error.message ?: "Unknown Firestore error")
                put("operationType", operationType.name.lowercase())
                put("path", path ?: "")
            }
            Log.e(TAG, "Firestore Error: $errInfo", error)
        }
    }

    /**
     * Registra que un usuario pide la ficha de una moto. Las solicitudes de la misma moto y año
     * se suman en un solo documento. Devuelve false si quedó en cola por falta de conexión:
     * Firestore la envía sola cuando vuelve la red.
     */
    suspend fun solicitar(
        marca: String,
        modelo: String,
        cilindraje: String,
        anio: Int,
        databaseId: String = configuredDatabaseId
    ): Boolean {
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
        val path = "$SOLICITUDES/$id"
        return try {
            withTimeout(TIMEOUT_SOLICITUD_MS) {
                suspendCancellableCoroutine<Unit> { cont ->
                    firestore(databaseId).collection(SOLICITUDES).document(id)
                        .set(datos, SetOptions.merge())
                        .addOnSuccessListener { cont.resume(Unit) }
                        .addOnFailureListener { error ->
                            logFirestoreError(error, OperationType.WRITE, path)
                            cont.resumeWithException(error)
                        }
                }
            }
            true
        } catch (e: TimeoutCancellationException) {
            false
        }
    }

    /**
     * Reporta un dato técnico o tarea de mantenimiento sospechoso en una ficha publicada.
     * Suma 1 al contador en "reportes/{fichaId__campo}". Devuelve false si quedó en cola sin red.
     */
    suspend fun reportar(
        fichaId: String,
        marca: String,
        modelo: String,
        cilindraje: String,
        campo: String,
        valorReportado: String,
        motivo: String,
        databaseId: String = configuredDatabaseId
    ): Boolean {
        val cleanFichaId = fichaId.trim().take(200)
        val cleanCampo = campo.trim().take(120)
        val datos = mapOf(
            "ficha_id" to cleanFichaId,
            "clave" to FichaMapper.clave(marca, modelo, cilindraje).take(200),
            "marca" to marca.trim().take(60),
            "modelo" to modelo.trim().take(80),
            "cilindraje_cc" to (FichaMapper.cilindrajeCc(cilindraje) ?: 0),
            "campo" to cleanCampo,
            "valor_reportado" to valorReportado.trim().take(200),
            "motivo" to motivo.trim().take(120),
            "total" to FieldValue.increment(1),
            "ultima" to FieldValue.serverTimestamp()
        )
        val id = FichaMapper.idReporte(cleanFichaId, cleanCampo)
        val path = "$REPORTES/$id"
        return try {
            withTimeout(TIMEOUT_SOLICITUD_MS) {
                suspendCancellableCoroutine<Unit> { cont ->
                    firestore(databaseId).collection(REPORTES).document(id)
                        .set(datos, SetOptions.merge())
                        .addOnSuccessListener { cont.resume(Unit) }
                        .addOnFailureListener { error ->
                            logFirestoreError(error, OperationType.WRITE, path)
                            cont.resumeWithException(error)
                        }
                }
            }
            true
        } catch (e: TimeoutCancellationException) {
            false
        }
    }

    suspend fun buscar(
        marca: String,
        modelo: String,
        cilindraje: String,
        anio: Int,
        databaseId: String = configuredDatabaseId
    ): DiagnosticResponse? {
        val clave = FichaMapper.clave(marca, modelo, cilindraje)
        val (documentos, desdeCache) = withTimeout(TIMEOUT_MS) { consultar(clave, databaseId) }

        val seleccion = FichaMapper.seleccionarConCercania(documentos, anio)
        // Sin conexión Firestore responde con lo que tenga guardado; si no tiene nada,
        // no se puede afirmar que la ficha no exista.
        if (seleccion == null && desdeCache) throw IOException("Sin conexión con la biblioteca de fichas.")
        return seleccion?.let {
            FichaMapper.toResponse(
                doc = it.documento,
                aproximada = !it.exacta,
                anioConsultado = anio
            )
        }
    }

    private suspend fun consultar(
        clave: String,
        databaseId: String
    ): Pair<List<Map<String, Any?>>, Boolean> =
        suspendCancellableCoroutine { cont ->
            firestore(databaseId).collection(COLECCION)
                .whereEqualTo("clave", clave)
                .whereEqualTo("estado", FichaMapper.ESTADO_PUBLICADA)
                .get()
                .addOnSuccessListener { snapshot ->
                    val docs = snapshot.documents.mapNotNull { doc ->
                        doc.data?.plus("_docId" to doc.id)
                    }
                    cont.resume(docs to snapshot.metadata.isFromCache)
                }
                .addOnFailureListener { error ->
                    logFirestoreError(error, OperationType.LIST, COLECCION)
                    cont.resumeWithException(error)
                }
        }

    /**
     * Lee el catálogo oficial publicado en Firestore (`catalogo`). Si no hay conexión,
     * expira el tiempo de espera o Firebase aún no está inicializado, devuelve un mapa vacío
     * para usar sin bloqueos la instantánea local `/motorcycle_catalog.json`.
     */
    suspend fun cargarCatalogo(
        databaseId: String = configuredDatabaseId
    ): Map<String, List<MotoModelSpec>> = try {
        withTimeout(TIMEOUT_CATALOGO_MS) {
            val docs = suspendCancellableCoroutine<List<Map<String, Any?>>> { cont ->
                firestore(databaseId).collection(CATALOGO)
                    .whereEqualTo("estado", FichaMapper.ESTADO_PUBLICADA)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val items = snapshot.documents.mapNotNull { it.data }
                        cont.resume(items)
                    }
                    .addOnFailureListener { error ->
                        logFirestoreError(error, OperationType.LIST, CATALOGO)
                        cont.resumeWithException(error)
                    }
            }
            MotorcycleCatalog.parseCatalogDocuments(docs)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        if (e is TimeoutCancellationException) {
            Log.w(TAG, "Tiempo de espera agotado al leer catalogo; usando respaldo local")
            emptyMap()
        } else {
            throw e
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo actualizar catalogo desde Firestore; usando respaldo local: ${e.message}")
        emptyMap()
    }
}
