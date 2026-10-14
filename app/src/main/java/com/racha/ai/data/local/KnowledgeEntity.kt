package com.racha.ai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * معلومة يعرفها Racha.
 * إما تعلّمها من المستخدم، أو بحث عنها تلقائياً.
 */
@Entity(tableName = "knowledge_entries")
data class KnowledgeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    /** السؤال الأصلي (للبحث) */
    val question: String,

    /** الجواب المختصر */
    val answer: String,

    /** الموضوع (جغرافيا، تاريخ، رياضة...) */
    val topic: String = "",

    /** كلمات مفتاحية للبحث (مفصولة بفواصل) */
    val keywords: String = "",

    /** من أين جاءت المعلومة: "user" أو "gemini" أو "wikipedia" */
    val source: String = "user",

    /** عدد المرات التي استُخدمت فيها */
    val hitCount: Int = 0,

    /** آخر مرة استُخدمت */
    val lastUsed: Long = System.currentTimeMillis(),

    /** وقت الإنشاء */
    val createdAt: Long = System.currentTimeMillis()
)
