package com.racha.ai.data.repository

import android.content.Context
import android.util.Log
import com.racha.ai.data.local.KnowledgeEntity
import com.racha.ai.data.local.RachaDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository لقاعدة المعرفة.
 * يدير: البحث، الحفظ، التعديل.
 */
class KnowledgeRepository(context: Context) {

    companion object {
        private const val TAG = "RachaKnowledge"
    }

    private val dao = RachaDatabase.getDatabase(context).knowledgeDao()

    fun getAll(): Flow<List<KnowledgeEntity>> = dao.getAll()

    /**
     * البحث في قاعدة المعرفة.
     */
    suspend fun search(query: String, limit: Int = 5): List<KnowledgeEntity> {
        if (query.isBlank()) return emptyList()
        return try {
            dao.search(query.trim(), limit)
        } catch (e: Exception) {
            Log.e(TAG, "فشل البحث", e)
            emptyList()
        }
    }

    /**
     * إيجاد أفضل تطابق (إن وُجد).
     */
    suspend fun findBestMatch(query: String): KnowledgeEntity? {
        val results = search(query, limit = 1)
        return results.firstOrNull()
    }

    /**
     * حفظ معلومة جديدة.
     */
    suspend fun save(entry: KnowledgeEntity) {
        try {
            dao.insert(entry)
            Log.d(TAG, "✅ حفظ: ${entry.question}")
        } catch (e: Exception) {
            Log.e(TAG, "فشل الحفظ", e)
        }
    }

    /**
     * حفظ سريع (من query → answer).
     */
    suspend fun quickSave(
        question: String,
        answer: String,
        topic: String = "",
        keywords: String = "",
        source: String = "user"
    ) {
        save(
            KnowledgeEntity(
                id = UUID.randomUUID().toString(),
                question = question,
                answer = answer,
                topic = topic,
                keywords = keywords,
                source = source
            )
        )
    }

    suspend fun update(entry: KnowledgeEntity) = dao.update(entry)

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun count(): Int = dao.count()

    /**
     * تسجيل استخدام معلومة.
     */
    suspend fun markUsed(id: String) = dao.incrementHit(id)

    /**
     * بناء سياق للـ LLM من المطابقات.
     */
    suspend fun buildContextFor(query: String, limit: Int = 3): String {
        val matches = search(query, limit)
        if (matches.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("[معلومات ذات صلة من معرفتي]\n")
        for (entry in matches) {
            sb.append("- س: ${entry.question}\n")
            sb.append("  ج: ${entry.answer}\n")
            // سجّل الاستخدام
            markUsed(entry.id)
        }
        return sb.toString()
    }
}
