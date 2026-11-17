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

    @Query("SELECT * FROM diagnostics WHERE vehicleKey = :vehicleKey LIMIT 1")
    suspend fun findDiagnostic(vehicleKey: String): DiagnosticEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnostic(diagnostic: DiagnosticEntity)

    @Query("DELETE FROM diagnostics WHERE id = :id")
    suspend fun deleteDiagnosticById(id: Int)

    @Query("SELECT * FROM service_records WHERE vehicleKey = :vehicleKey ORDER BY fecha DESC, kilometraje DESC")
    fun getServiceRecords(vehicleKey: String): Flow<List<ServiceRecordEntity>>

    @Query("SELECT * FROM service_records ORDER BY fecha DESC, kilometraje DESC")
    fun getAllServiceRecords(): Flow<List<ServiceRecordEntity>>

    @Insert
    suspend fun insertServiceRecord(record: ServiceRecordEntity)

    @Query("DELETE FROM service_records WHERE id = :id")
    suspend fun deleteServiceRecordById(id: Int)

    @Query("SELECT * FROM brand_catalogs WHERE marcaKey = :marcaKey LIMIT 1")
    suspend fun findBrandCatalog(marcaKey: String): BrandCatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrandCatalog(entity: BrandCatalogEntity)

    @Query("SELECT COUNT(*) FROM ficha_requests WHERE requestId = :requestId")
    suspend fun countFichaRequests(requestId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFichaRequest(entity: FichaRequestEntity)

    @Query("SELECT reportId FROM ficha_reports")
    fun observeAllReportedIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM ficha_reports WHERE reportId = :reportId")
    suspend fun countFichaReports(reportId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFichaReport(entity: FichaReportEntity)
}
