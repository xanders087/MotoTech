package com.example.data

import kotlinx.coroutines.flow.Flow

class DiagnosticRepository(private val diagnosticDao: DiagnosticDao) {
    val allDiagnostics: Flow<List<DiagnosticEntity>> = diagnosticDao.getAllDiagnostics()

    suspend fun findDiagnostic(vehicleKey: String): DiagnosticEntity? =
        diagnosticDao.findDiagnostic(vehicleKey)

    suspend fun insert(diagnostic: DiagnosticEntity) {
        diagnosticDao.insertDiagnostic(diagnostic)
    }

    suspend fun deleteById(id: Int) {
        diagnosticDao.deleteDiagnosticById(id)
    }

    fun serviceRecords(vehicleKey: String): Flow<List<ServiceRecordEntity>> =
        diagnosticDao.getServiceRecords(vehicleKey)

    suspend fun addServiceRecord(record: ServiceRecordEntity) {
        diagnosticDao.insertServiceRecord(record)
    }
}
