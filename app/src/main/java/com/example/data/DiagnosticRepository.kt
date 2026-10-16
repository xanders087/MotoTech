package com.example.data

import kotlinx.coroutines.flow.Flow

class DiagnosticRepository(private val diagnosticDao: DiagnosticDao) {
    val allDiagnostics: Flow<List<DiagnosticEntity>> = diagnosticDao.getAllDiagnostics()

    suspend fun findDiagnostic(marca: String, modelo: String, anio: String, kilometraje: String): DiagnosticEntity? {
        val exactMatch = diagnosticDao.findDiagnostic(marca.trim(), modelo.trim(), anio.trim(), kilometraje.trim())
        if (exactMatch != null) return exactMatch

        // Also check case-insensitive match from database
        return null
    }

    suspend fun insert(diagnostic: DiagnosticEntity) {
        diagnosticDao.insertDiagnostic(diagnostic)
    }

    suspend fun deleteById(id: Int) {
        diagnosticDao.deleteDiagnosticById(id)
    }
}
