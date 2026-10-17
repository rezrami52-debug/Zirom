package com.racha.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, ChatMessageEntity::class, UserProfile::class, RoutineItem::class, KnowledgeEntity::class, LockAttemptEntity::class],
    version = 2, // ✅ رفعنا الإصدار لتطبيق التغييرات
    exportSchema = false
)
abstract class RachaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun routineDao(): RoutineDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun lockAttemptDao(): LockAttemptDao

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
                .fallbackToDestructiveMigration() // ✅ يسمح بإعادة البناء بأمان عند تغيير الجداول
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
