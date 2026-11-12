package com.example.controlherbal.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.controlherbal.database.entity.PlantTask
import com.example.controlherbal.database.dao.PlantTaskDao
import com.example.controlherbal.database.dao.PlantCatalogDao
import com.example.controlherbal.database.dao.UserDao
import com.example.controlherbal.database.entity.PlantCatalogEntity
import com.example.controlherbal.database.entity.UserEntity
import com.example.controlherbal.database.entity.UserPlantEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SensorReading::class,
        Plant::class,
        UserEntity::class,
        PlantCatalogEntity::class,
        UserPlantEntity::class,
        PlantTask::class
    ],
    version = 10,
    exportSchema = false
)
abstract class SensorDatabase : RoomDatabase() {

    abstract fun sensorDao(): SensorDao
    abstract fun plantDao(): PlantDao
    abstract fun userDao(): UserDao
    abstract fun plantCatalogDao(): PlantCatalogDao
    abstract fun plantTaskDao(): PlantTaskDao

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
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.let { PlantDatabaseSeeder.seedCatalogIfEmpty(it) }
                        }
                    }
                })
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
