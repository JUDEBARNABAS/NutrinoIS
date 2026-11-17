package com.example.database

import kotlinx.coroutines.flow.Flow

class SoilRepository(
    private val recordDao: SoilRecordDao,
    private val farmerDao: FarmerProfileDao
) {

    val allRecords: Flow<List<SoilRecord>> = recordDao.getAllRecords()
    val allFarmerProfiles: Flow<List<FarmerProfile>> = farmerDao.getAllProfiles()

    suspend fun insert(record: SoilRecord): Long {
        return recordDao.insertRecord(record)
    }

    suspend fun deleteById(id: Long) {
        recordDao.deleteRecordById(id)
    }

    suspend fun clearAll() {
        recordDao.clearAllRecords()
    }

    // Farmer Profile Functions
    suspend fun insertProfile(profile: FarmerProfile): Long {
        return farmerDao.insertProfile(profile)
    }

    suspend fun updateProfile(profile: FarmerProfile) {
        farmerDao.updateProfile(profile)
    }

    suspend fun getProfileById(id: Long): FarmerProfile? {
        return farmerDao.getProfileById(id)
    }

    suspend fun deleteProfileById(id: Long) {
        farmerDao.deleteProfileById(id)
    }

    suspend fun clearAllProfiles() {
        farmerDao.clearAllProfiles()
    }
}
