package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DiagnosticEntity::class,
        ServiceRecordEntity::class,
        BrandCatalogEntity::class,
        FichaRequestEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        // Las fichas guardadas con la versión 3 se generaron sin búsqueda y con fuentes
        // no comprobadas: se borran. El historial de servicios y los catálogos se conservan.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM diagnostics")
            }
        }

        // Desde la versión 5 las fichas salen de la biblioteca de manuales oficiales:
        // se borran las que se habían guardado a partir de consultas con IA.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM diagnostics")
            }
        }

        // Versión 6: tabla de fichas solicitadas. El resto de los datos no cambia.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ficha_requests` " +
                        "(`requestId` TEXT NOT NULL, `fecha` INTEGER NOT NULL, PRIMARY KEY(`requestId`))"
                )
            }
        }

        // Versión 7: notas, taller y costo en cada registro de mantenimiento realizado.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `service_records` ADD COLUMN `notas` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `service_records` ADD COLUMN `taller` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `service_records` ADD COLUMN `costo` TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mototech_database"
                )
                    // La versión 1 solo guardaba fichas en caché, y podían contener datos
                    // generados sin fuente: se descartan en vez de migrarlas.
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
