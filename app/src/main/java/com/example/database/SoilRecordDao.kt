package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SoilRecordDao {

    @Query("SELECT * FROM soil_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<SoilRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SoilRecord): Long

    @Query("DELETE FROM soil_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM soil_records")
    suspend fun clearAllRecords()
}
