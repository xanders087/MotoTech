package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.util.Locale

data class ResumenAlertaNotificacion(
    val nombreMoto: String,
    val kmActual: Int,
    val umbralKm: Int,
    val tareasEnAlerta: List<ProximoMantenimientoPreventivo>,
    val vencidasCount: Int,
    val proximasCount: Int,
    val titulo: String,
    val mensajeCorto: String,
    val lineasDetalle: List<String>
)

/**
 * Gestiona las notificaciones locales de Android para avisar cuando una motocicleta
 * está cerca de alcanzar (o ya alcanzó) el kilometraje de su próximo mantenimiento preventivo.
 */
object MaintenanceNotificationHelper {
    const val CHANNEL_ID = "mototech_preventive_maintenance"
    const val NOTIFICATION_ID = 4021
    const val EXTRA_OPEN_ROUTE = "extra_open_route"

    private val LocaleCo = Locale("es", "CO")
    private fun formatKm(km: Int): String = NumberFormat.getIntegerInstance(LocaleCo).format(km)

    /** Crea el canal de notificaciones de alta prioridad en Android 8.0+ (API 26+). */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas de Mantenimiento Preventivo"
            val descriptionText = "Avisos cuando tu motocicleta se acerca al kilometraje de su próximo servicio preventivo"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /** Verifica si la app cuenta con permiso para mostrar notificaciones (Android 13+). */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Evalúa qué mantenimientos preventivos están vencidos o dentro del umbral de aviso en kilómetros
     * (`umbralKm` o margen ≤10% del intervalo) para el kilometraje actual de la motocicleta.
     */
    fun evaluarAlertasPorKilometraje(
        nombreMoto: String,
        kmActual: Int,
        items: List<ProximoMantenimientoPreventivo>,
        umbralKm: Int = 500
    ): ResumenAlertaNotificacion? {
        val umbralSeguro = umbralKm.coerceAtLeast(50)
        val enAlerta = items.filter { item ->
            val rest = item.kmRestantes
            item.estado == EstadoMantenimiento.VENCIDO ||
                item.estado == EstadoMantenimiento.PROXIMO ||
                (rest != null && rest <= umbralSeguro)
        }

        if (enAlerta.isEmpty()) return null

        val vencidas = enAlerta.count { (it.kmRestantes != null && it.kmRestantes <= 0) || it.estado == EstadoMantenimiento.VENCIDO }
        val proximas = enAlerta.size - vencidas
        val motoLimpia = nombreMoto.trim().ifBlank { "Tu motocicleta" }

        val prioritaria = enAlerta.first()
        val titulo = when {
            vencidas > 0 -> "Mantenimiento requerido: $motoLimpia (${formatKm(kmActual)} km)"
            else -> "Próximo mantenimiento cerca: $motoLimpia (${formatKm(kmActual)} km)"
        }

        val mensajeCorto = prioritaria.kmRestantes?.let { rest ->
            when {
                rest < 0 -> "${prioritaria.componente}: vencido hace ${formatKm(-rest)} km (meta ${formatKm(prioritaria.proximoKilometraje ?: kmActual)} km)"
                rest == 0 -> "${prioritaria.componente}: toca exactamente a los ${formatKm(kmActual)} km"
                else -> "${prioritaria.componente}: faltan solo ${formatKm(rest)} km (a los ${formatKm(prioritaria.proximoKilometraje ?: (kmActual + rest))} km)"
            }
        } ?: "${prioritaria.componente}: requiere atención preventiva"

        val lineas = enAlerta.take(5).map { tarea ->
            val estadoKm = tarea.kmRestantes?.let { r ->
                when {
                    r < 0 -> "Vencido por ${formatKm(-r)} km"
                    r == 0 -> "Toca ahora (${formatKm(kmActual)} km)"
                    else -> "Faltan ${formatKm(r)} km (a los ${formatKm(tarea.proximoKilometraje ?: (kmActual + r))} km)"
                }
            } ?: "Revisión pendiente"
            "• ${tarea.componente}: $estadoKm"
        }

        return ResumenAlertaNotificacion(
            nombreMoto = motoLimpia,
            kmActual = kmActual,
            umbralKm = umbralSeguro,
            tareasEnAlerta = enAlerta,
            vencidasCount = vencidas,
            proximasCount = proximas,
            titulo = titulo,
            mensajeCorto = mensajeCorto,
            lineasDetalle = lineas
        )
    }

    /**
     * Publica la notificación local en la barra de estado de Android si hay permiso concedido.
     * Devuelve true si la notificación fue enviada al sistema.
     */
    fun mostrarNotificacionMantenimiento(
        context: Context,
        alerta: ResumenAlertaNotificacion
    ): Boolean {
        createNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, "summary")
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            pendingIntentFlags
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(alerta.titulo)
            .setSummaryText("${alerta.tareasEnAlerta.size} servicio(s) preventivo(s) en alerta")
        alerta.lineasDetalle.forEach { linea ->
            inboxStyle.addLine(linea)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(alerta.titulo)
            .setContentText(alerta.mensajeCorto)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }
}
