package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Catálogo en caché de modelos y cilindradas consultados por marca. */
@Entity(tableName = "brand_catalogs")
data class BrandCatalogEntity(
    @PrimaryKey val marcaKey: String,
    val marca: String,
    val jsonModels: String,
    val updatedAt: Long = System.currentTimeMillis()
)
