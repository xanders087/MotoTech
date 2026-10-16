package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticDao {
    @Query("SELECT * FROM diagnostics ORDER BY timestamp DESC")
    fun getAllDiagnostics(): Flow<List<DiagnosticEntity>>

    @Query("SELECT * FROM diagnostics WHERE marca = :marca AND modelo = :modelo AND anio = :anio AND kilometraje = :kilometraje LIMIT 1")
    suspend fun findDiagnostic(marca: String, modelo: String, anio: String, kilometraje: String): DiagnosticEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnostic(diagnostic: DiagnosticEntity)

    @Query("DELETE FROM diagnostics WHERE id = :id")
    suspend fun deleteDiagnosticById(id: Int)
}
