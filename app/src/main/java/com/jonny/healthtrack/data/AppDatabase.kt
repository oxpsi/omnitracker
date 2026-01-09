package com.jonny.healthtrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LogEntity::class], version = 3, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE logs ADD COLUMN isOriginalImage INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE logs ADD COLUMN isPrivate INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val queries = listOf(
                    "ALTER TABLE logs ADD COLUMN analysisData TEXT",
                    "ALTER TABLE logs ADD COLUMN analysisModel TEXT",
                    "ALTER TABLE logs ADD COLUMN analysisUpdatedAt INTEGER",
                    "ALTER TABLE logs ADD COLUMN analysisStatus TEXT",
                    "ALTER TABLE logs ADD COLUMN analysisError TEXT"
                )
                
                queries.forEach { query ->
                    try {
                        db.execSQL(query)
                    } catch (e: Exception) {
                        // Ignore if column already exists (idempotent migration)
                    }
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "healthtrack_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
