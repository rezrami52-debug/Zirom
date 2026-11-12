package com.racha.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        ChatMessageEntity::class,
        UserProfile::class,
        RoutineItem::class,
        KnowledgeEntity::class,
        LockAttemptEntity::class
    ],
    version = 2,
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
        private const val DATABASE_NAME = "racha_database"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(
                database: SupportSQLiteDatabase
            ) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conversations (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id TEXT NOT NULL,
                        conversationId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        isFromUser INTEGER NOT NULL,
                        isError INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_profile (
                        id INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        city TEXT NOT NULL,
                        occupation TEXT NOT NULL,
                        workDays TEXT NOT NULL,
                        workStartHour INTEGER NOT NULL,
                        workStartMinute INTEGER NOT NULL,
                        workEndHour INTEGER NOT NULL,
                        workEndMinute INTEGER NOT NULL,
                        wakeUpHour INTEGER NOT NULL,
                        wakeUpMinute INTEGER NOT NULL,
                        sleepHour INTEGER NOT NULL,
                        sleepMinute INTEGER NOT NULL,
                        breakfastHour INTEGER NOT NULL,
                        breakfastMinute INTEGER NOT NULL,
                        lunchHour INTEGER NOT NULL,
                        lunchMinute INTEGER NOT NULL,
                        dinnerHour INTEGER NOT NULL,
                        dinnerMinute INTEGER NOT NULL,
                        sportType TEXT NOT NULL,
                        sportDays TEXT NOT NULL,
                        hobbies TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_entries (
                        id TEXT NOT NULL,
                        question TEXT NOT NULL,
                        answer TEXT NOT NULL,
                        topic TEXT NOT NULL,
                        keywords TEXT NOT NULL,
                        source TEXT NOT NULL,
                        hitCount INTEGER NOT NULL,
                        lastUsed INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lock_attempts (
                        id TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        success INTEGER NOT NULL,
                        photoPath TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: RachaDatabase? = null

        fun getDatabase(context: Context): RachaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RachaDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
