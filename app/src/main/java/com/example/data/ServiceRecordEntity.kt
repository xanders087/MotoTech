package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Servicio de mantenimiento realizado que el usuario registró en su moto, con fecha y notas. */
@Entity(tableName = "service_records", indices = [Index(value = ["vehicleKey"])])
data class ServiceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleKey: String,
    val componente: String,
    val kilometraje: Int,
    val fecha: Long = System.currentTimeMillis(),
    val notas: String = "",
    val taller: String = "",
    val costo: String = ""
)
