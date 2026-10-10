package com.example.controlherbal.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.controlherbal.data.database.dao.PlantCatalogDao
import com.example.controlherbal.data.database.dao.UserDao
import com.example.controlherbal.data.database.entity.PlantCatalogEntity
import com.example.controlherbal.data.database.entity.UserEntity
import com.example.controlherbal.data.database.entity.UserPlantEntity

@Database(
    entities = [
        SensorReading::class,
        Plant::class,
        UserEntity::class,
        PlantCatalogEntity::class,
        UserPlantEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class SensorDatabase : RoomDatabase() {

    abstract fun sensorDao(): SensorDao
    abstract fun plantDao(): PlantDao
    abstract fun userDao(): UserDao
    abstract fun plantCatalogDao(): PlantCatalogDao

    companion object {
        @Volatile
        private var INSTANCE: SensorDatabase? = null

        fun getInstance(context: Context): SensorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SensorDatabase::class.java,
                    "sensor_database"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
