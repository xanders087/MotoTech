package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ficha guardada de una moto. Una sola por moto (vehicleKey), sin importar el kilometraje. */
@Entity(tableName = "diagnostics", indices = [Index(value = ["vehicleKey"], unique = true)])
data class DiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val vehicleKey: String,
    val marca: String,
    val modelo: String,
    val cilindraje: String,
    val anio: String,
    val jsonResponse: String
)
