package com.jonny.healthtrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    suspend fun getAllLogsSnapshot(): List<LogEntity>

    @Query("SELECT * FROM logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getLogsInRange(startTime: Long, endTime: Long): List<LogEntity>

    @Query("SELECT * FROM logs WHERE id = :id")
    suspend fun getLogById(id: String): LogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<LogEntity>)

    @Delete
    suspend fun deleteLog(log: LogEntity)
    
    @Query("DELETE FROM logs")
    suspend fun clearAll()
}
