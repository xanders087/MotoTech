package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Fichas que este usuario ya solicitó, para no contar dos veces la misma solicitud. */
@Entity(tableName = "ficha_requests")
data class FichaRequestEntity(
    @PrimaryKey val requestId: String,
    val fecha: Long = System.currentTimeMillis()
)
