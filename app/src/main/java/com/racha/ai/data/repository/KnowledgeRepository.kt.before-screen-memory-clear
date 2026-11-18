package com.racha.ai.data.repository

import android.content.Context
import android.util.Log
import com.racha.ai.data.local.KnowledgeEntity
import com.racha.ai.data.local.RachaDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class KnowledgeRepository(context: Context) {

    companion object {
        private const val TAG = "RachaKnowledge"
    }

    private val dao = RachaDatabase.getDatabase(context).knowledgeDao()

    fun getAll(): Flow<List<KnowledgeEntity>> = dao.getAll()

    suspend fun search(query: String, limit: Int = 5): List<KnowledgeEntity> {
        if (query.isBlank()) return emptyList()

        return try {
            dao.search(query.trim(), limit)
        } catch (e: Exception) {
            Log.e(TAG, "فشل البحث", e)
            emptyList()
        }
    }

    suspend fun findBestMatch(query: String): KnowledgeEntity? {
        return search(query, limit = 1).firstOrNull()
    }

    suspend fun save(entry: KnowledgeEntity) {
        try {
            dao.insert(entry)
            Log.d(TAG, "تم الحفظ: ${entry.question}")
        } catch (e: Exception) {
            Log.e(TAG, "فشل الحفظ", e)
        }
    }

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

    suspend fun saveOrUpdate(
        question: String,
        answer: String,
        topic: String = "",
        keywords: String = "",
        source: String = "user"
    ) {
        val cleanQuestion = question.trim()
        val cleanAnswer = answer.trim()

        if (cleanQuestion.isBlank() || cleanAnswer.isBlank()) return

        val existing = try {
            dao.findByQuestion(cleanQuestion)
        } catch (e: Exception) {
            Log.e(TAG, "فشل البحث عن الذاكرة السابقة", e)
            null
        }

        if (existing == null) {
            save(
                KnowledgeEntity(
                    question = cleanQuestion,
                    answer = cleanAnswer,
                    topic = topic,
                    keywords = keywords,
                    source = source
                )
            )
        } else {
            try {
                dao.update(
                    existing.copy(
                        answer = cleanAnswer,
                        topic = topic.ifBlank { existing.topic },
                        keywords = keywords.ifBlank { existing.keywords },
                        source = source,
                        lastUsed = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "فشل تحديث الذاكرة", e)
            }
        }
    }

    suspend fun update(entry: KnowledgeEntity) {
        dao.update(entry)
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun count(): Int {
        return dao.count()
    }

    suspend fun markUsed(id: String) {
        dao.incrementHit(id)
    }

    suspend fun buildContextFor(query: String, limit: Int = 3): String {
        val matches = search(query, limit)

        if (matches.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("[معلومات ذات صلة من معرفتي]\n")

        for (entry in matches) {
            sb.append("- س: ${entry.question}\n")
            sb.append("  ج: ${entry.answer}\n")
            markUsed(entry.id)
        }

        return sb.toString()
    }
}
