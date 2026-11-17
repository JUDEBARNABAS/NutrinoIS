package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmerProfileDao {

    @Query("SELECT * FROM farmer_profiles ORDER BY timestamp DESC")
    fun getAllProfiles(): Flow<List<FarmerProfile>>

    @Query("SELECT * FROM farmer_profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): FarmerProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: FarmerProfile): Long

    @Update
    suspend fun updateProfile(profile: FarmerProfile)

    @Query("DELETE FROM farmer_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("DELETE FROM farmer_profiles")
    suspend fun clearAllProfiles()
}
