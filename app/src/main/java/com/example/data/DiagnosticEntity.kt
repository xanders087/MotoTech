package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostics")
data class DiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val marca: String,
    val modelo: String,
    val cilindraje: String,
    val anio: String,
    val kilometraje: String,
    val jsonResponse: String
)
