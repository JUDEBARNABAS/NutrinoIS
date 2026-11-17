package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farmer_profiles")
data class FarmerProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val fieldName: String,
    val cropType: String,
    val location: String,
    val projectName: String = "",
    val googleUserEmail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    
    // Testing data (added after Collect Test)
    val hasTestData: Boolean = false,
    val testTimestamp: Long = 0L,
    
    // Ground Truth
    val soilN: Int = 0,
    val soilP: Int = 0,
    val soilK: Int = 0,
    val soilPh: Double = 0.0,
    val soilHumidity: Double = 0.0,
    val soilTemp: Double = 0.0,
    val soilEc: Int = 0,
    val groundTruthInsights: String = "",
    
    // Sky Truth
    val localWeather: String = "",
    val alphaEarthData: String = "",
    val skyTruthInsights: String = ""
)
