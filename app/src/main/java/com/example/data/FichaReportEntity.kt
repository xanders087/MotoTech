package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Datos que este usuario ya reportó, para no contar dos veces el mismo reporte desde el mismo teléfono. */
@Entity(tableName = "ficha_reports")
data class FichaReportEntity(
    @PrimaryKey val reportId: String,
    val fecha: Long = System.currentTimeMillis()
)
