package com.racha.ai.core.command

import com.racha.ai.data.local.KnowledgeEntity

/** Resolves user-taught terms before routing; it does not execute commands. */
object LearnedLexiconResolver {
    const val LEXICON_TOPIC = "معجم رمزي"
    private const val MAX_TERM_LENGTH = 50
    private const val MAX_MEANING_LENGTH = 120

    fun expand(text: String, entries: List<KnowledgeEntity>): String {
        val mappings = entries.asSequence()
            .filter { it.source == "user" && it.topic == LEXICON_TOPIC }
            .mapNotNull { entry ->
                val term = normalize(entry.question)
                val meaning = normalize(entry.answer)
                if (term.length !in 2..MAX_TERM_LENGTH ||
                    meaning.isBlank() || meaning.length > MAX_MEANING_LENGTH ||
                    term == meaning || term.none { it.isLetter() }
                ) null else term to meaning
            }
            // Repository order is newest first; keep the newest definition for duplicate terms.
            .distinctBy { it.first }
            .sortedByDescending { it.first.length }
            .toList()
        if (mappings.isEmpty()) return text

        val meaningByTerm = mappings.toMap()
        val alternatives = mappings.joinToString("|") { (term, _) ->
            term.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
        }
        val matcher = Regex("(?<![\\p{L}\\p{N}])(?:$alternatives)(?![\\p{L}\\p{N}])")
        var result = normalize(text)
        if (!matcher.containsMatchIn(result)) return text
        val seen = mutableSetOf(result)
        // Permit short chains (e.g. alias -> synonym -> supported wording), but bound them and stop cycles.
        repeat(mappings.size.coerceAtMost(8)) {
            val next = matcher.replace(result) { match ->
                meaningByTerm[match.value.replace(Regex("\\s+"), " ")] ?: match.value
            }.replace(Regex("\\s+"), " ").trim()
            if (next == result) return result
            if (!seen.add(next)) return next
            result = next
        }
        return result
    }

    /** Heuristic only for the honest fallback when an expanded imperative has no local handler. */
    fun looksLikeAction(text: String): Boolean {
        val normalized = normalize(text)
        val actionStarts = listOf(
            "افتح", "شغل", "اعرض", "استعرض", "اطفئ", "اطفي", "طفي", "اغلق", "اقفل",
            "وقف", "اوقف", "توقف", "ارسل", "ابعث", "اتصل", "اتصلي", "شوف", "شوفي",
            "اقرأ", "اقرئي", "حلل", "حللي", "افحص", "افحصي", "ابحث", "هات", "وريني",
            "خذني", "خذيني", "وجهني", "وجهيني", "دلني", "دليني", "اضبط", "نبهني",
            "نفذ", "نفذي", "احذف", "امسح", "بدل", "غير", "فعل", "عطل",
            "قم", "قومي", "افعل", "افعلي", "اضغط", "اضغطي", "اسكتي", "اسكت",
            "ارسلي", "افتحلي", "شغللي", "نزّل", "نزل", "حمّل", "حمل", "اقفللي",
            "open", "show", "play", "turn", "stop", "pause", "send", "call", "navigate", "find", "search", "delete", "set", "enable", "disable"
        )
        val questions = listOf("ما معنى", "ماذا تعني", "ما هو", "ما هي", "كيف", "هل", "why", "what", "how")
        if (questions.any { normalized.startsWith(it) }) return false
        if (actionStarts.any { normalized.startsWith(it) }) return true
        val requestPrefixes = listOf("اريد", "ابغى", "نحب", "ممكن", "لو سمحت", "من فضلك", "please", "i want to", "can you")
        return requestPrefixes.any { prefix ->
            normalized.startsWith(prefix) && actionStarts.any { action -> normalized.drop(prefix.length).contains(action) }
        }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        .replace(Regex("[أإآٱ]"), "ا")
        .replace('ى', 'ي')
        .replace(Regex("\\s+"), " ")
        .trim()
}
