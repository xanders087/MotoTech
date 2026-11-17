package com.example.data

import kotlinx.coroutines.flow.Flow

class DiagnosticRepository(private val diagnosticDao: DiagnosticDao) {
    val allDiagnostics: Flow<List<DiagnosticEntity>> = diagnosticDao.getAllDiagnostics()
    val allServiceRecords: Flow<List<ServiceRecordEntity>> = diagnosticDao.getAllServiceRecords()

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

    suspend fun deleteServiceRecord(id: Int) {
        diagnosticDao.deleteServiceRecordById(id)
    }

    suspend fun findBrandCatalog(marcaKey: String): BrandCatalogEntity? =
        diagnosticDao.findBrandCatalog(marcaKey)

    suspend fun saveBrandCatalog(entity: BrandCatalogEntity) {
        diagnosticDao.insertBrandCatalog(entity)
    }

    suspend fun wasFichaRequested(requestId: String): Boolean =
        diagnosticDao.countFichaRequests(requestId) > 0

    suspend fun markFichaRequested(requestId: String) {
        diagnosticDao.insertFichaRequest(FichaRequestEntity(requestId = requestId))
    }

    val allReportedIds: Flow<List<String>> = diagnosticDao.observeAllReportedIds()

    suspend fun wasFieldReported(reportId: String): Boolean =
        diagnosticDao.countFichaReports(reportId) > 0

    suspend fun markFieldReported(reportId: String) {
        diagnosticDao.insertFichaReport(FichaReportEntity(reportId = reportId))
    }
}
