package com.racha.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RoutineItem::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class RachaDatabase : RoomDatabase() {

    abstract fun routineDao(): RoutineDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: RachaDatabase? = null

        fun getDatabase(context: Context): RachaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RachaDatabase::class.java,
                    "racha_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
