package com.racha.ai.data.repository

import android.content.Context
import com.racha.ai.data.local.RachaDatabase
import com.racha.ai.data.local.RepeatType
import com.racha.ai.data.local.RoutineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * Repository للروتين اليومي.
 * يدير التخزين والقراءة والإضافة والتعديل.
 */
class RoutineRepository(context: Context) {

    private val dao = RachaDatabase.getDatabase(context).routineDao()

    fun getAll(): Flow<List<RoutineItem>> = dao.getAll()

    fun getEnabled(): Flow<List<RoutineItem>> = dao.getEnabled()

    suspend fun getById(id: String): RoutineItem? = dao.getById(id)

    suspend fun insert(item: RoutineItem) = dao.insert(item)

    suspend fun update(item: RoutineItem) = dao.update(item)

    suspend fun delete(item: RoutineItem) = dao.delete(item)

    suspend fun deleteById(id: String) = dao.deleteById(id)

    suspend fun clearAll() = dao.deleteAll()

    /**
     * يتحقق: هل قاعدة البيانات فارغة؟
     * إذا نعم، يُنشئ الروتين الافتراضي.
     */
    suspend fun initializeDefaultIfEmpty() {
        val count = dao.count()
        if (count == 0) {
            dao.insertAll(defaultRoutine())
        }
    }

    /**
     * الروتين الافتراضي (يُنشأ أول مرة).
     */
    private fun defaultRoutine(): List<RoutineItem> = listOf(
        RoutineItem(
            title = "الاستيقاظ",
            description = "استيقظ، توضأ، ابدأ يومك",
            emoji = "⏰",
            hour = 7, minute = 0,
            repeat = RepeatType.DAILY.name
        ),
        RoutineItem(
            title = "الفطور",
            description = "وجبة الصباح",
            emoji = "🍳",
            hour = 8, minute = 0,
            repeat = RepeatType.DAILY.name
        ),
        RoutineItem(
            title = "شرب الماء",
            description = "اشرب كوباً كاملاً",
            emoji = "💧",
            hour = 10, minute = 0,
            repeat = RepeatType.DAILY.name
        ),
        RoutineItem(
            title = "شرب الماء",
            description = "اشرب كوباً كاملاً",
            emoji = "💧",
            hour = 12, minute = 0,
            repeat = RepeatType.DAILY.name
        ),
        RoutineItem(
            title = "الغداء",
            description = "وجبة منتصف اليوم",
            emoji = "🍽️",
            hour = 13, minute = 0,
            repeat = RepeatType.DAILY.name
        ),
        RoutineItem(
            title = "شرب الماء",
            description = "اشرب كوباً كاملاً",
            emoji = "💧",
            hour = 15, minute = 0,
            repeat = RepeatType.DAILY.name
        ),
        RoutineItem(
            title = "التمارين الرياضية",
            description = "30 دقيقة من النشاط البدني",
            emoji = "🏃",
            hour = 17, minute = 0,
            repeat = RepeatType.DAILY.name
        ),
        RoutineItem(
            title = "الاستحمام",
            description = "استرخِ ونظّف نفسك",
            emoji = "🚿",
            hour = 18, minute = 30,
            repeat = RepeatType.DAILY.name
        ),
        RoutineItem(
            title = "العشاء",
            description = "وجبة المساء",
            emoji = "🍲",
            hour = 20, minute = 0,
            repeat = RepeatType.DAILY.name
        ),
        RoutineItem(
            title = "شرب الماء",
            description = "اشرب كوباً كاملاً",
            emoji = "💧",
            hour = 21, minute = 0,
            repeat = RepeatType.DAILY.name
        ),
        RoutineItem(
            title = "الاستعداد للنوم",
            description = "أنهِ يومك، اقرأ، استرخِ",
            emoji = "🛏️",
            hour = 22, minute = 30,
            repeat = RepeatType.DAILY.name
        )
    )
}
