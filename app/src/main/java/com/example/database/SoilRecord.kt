package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "soil_records")
data class SoilRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val humidityPercent: Double,
    val temperatureCelsius: Double,
    val electricalConductivity: Int,
    val pH: Double,
    val nitrogen: Int,
    val phosphorus: Int,
    val potassium: Int,
    val salinityTds: Int,
    val locationLabel: String,
    val projectName: String = "",
    val farmerProfileName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
