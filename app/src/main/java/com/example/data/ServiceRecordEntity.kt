package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Servicio que el usuario registró como hecho en su moto. */
@Entity(tableName = "service_records", indices = [Index(value = ["vehicleKey"])])
data class ServiceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleKey: String,
    val componente: String,
    val kilometraje: Int,
    val fecha: Long = System.currentTimeMillis()
)
