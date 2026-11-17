package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SoilRecord::class, FarmerProfile::class], version = 2, exportSchema = false)
abstract class SoilDatabase : RoomDatabase() {
    
    abstract fun soilRecordDao(): SoilRecordDao
    abstract fun farmerProfileDao(): FarmerProfileDao

    companion object {
        @Volatile
        private var INSTANCE: SoilDatabase? = null

        fun getDatabase(context: Context): SoilDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SoilDatabase::class.java,
                    "soil_testing_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
