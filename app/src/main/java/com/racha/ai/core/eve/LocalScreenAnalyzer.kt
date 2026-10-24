package com.racha.ai.core.eve

object LocalScreenAnalyzer {

    // كلمات شائعة في الواجهات يجب استبعادها للتركيز على المحتوى الحقيقي
    private val UI_NOISE = setOf(
        "like", "comment", "share", "reply", "follow", "subscribe", "search", "home",
        "اعجبني", "إعجاب", "تعليق", "مشاركة", "رد", "متابعة", "اشتراك", "بحث", "الرئيسية",
        "notifications", "إشعارات", "profile", "ملف شخصي", "more", "المزيد", "views", "مشاهدة"
    )

    fun analyze(snapshot: ScreenSnapshot): String {
        val pkg = snapshot.packageName.lowercase()
        val textLines = snapshot.visibleText.lines()
            .map { it.trim() }
            .filter { line ->
                line.length > 3 && !UI_NOISE.contains(line.lowercase())
            }

        return when {
            // 1. يوتيوب وتطبيقات الفيديو
            pkg.contains("youtube") || pkg.contains("video") -> {
                analyzeYouTube(textLines)
            }

            // 2. تطبيقات التواصل الاجتماعي (فيسبوك، تويتر، إنستغرام)
            pkg.contains("facebook") || pkg.contains("katana") || pkg.contains("instagram") || pkg.contains("twitter") || pkg.contains("x.android") -> {
                analyzeSocialMedia(snapshot.appName, textLines)
            }

            // 3. المتصفحات وقراءة الأخبار
            pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("firefox") -> {
                analyzeBrowser(textLines)
            }

            // 4. تطبيقات المراسلة
            pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messenger") -> {
                analyzeMessaging(snapshot.appName, textLines)
            }

            // 5. التطبيقات العامة
            else -> {
                val prominentText = textLines.firstOrNull { it.length > 15 } ?: snapshot.appName
                "سيدي، أرى أنك تركز في $prominentText، جولة موفقة وأنا جاهزة لأي أمر."
            }
        }
    }

    private fun analyzeSocialMedia(appName: String, lines: List<String>): String {
        val potentialAuthor = lines.firstOrNull { it.length in 4..25 && !it.contains("http") }
        val potentialPost = lines.firstOrNull { it.length > 25 && it != potentialAuthor }

        return if (potentialAuthor != null && potentialPost != null) {
            val snippet = potentialPost.take(45)
            "منشور مميز من $potentialAuthor يتحدث فيه عن: '$snippet...'، ما رأيك أن أكتب له تعليقاً لطيفاً؟"
        } else if (potentialAuthor != null) {
            "تطالع منشوراً جديداً لـ $potentialAuthor على $appName، يبدو محتوى جميلاً سيدي."
        } else {
            "جولة تصفح ممتعة على $appName سيدي، أنا معك إن أردت صياغة أي تعليق."
        }
    }

    private fun analyzeYouTube(lines: List<String>): String {
        val videoTitle = lines.firstOrNull { it.length > 10 }
        return if (videoTitle != null) {
            "تشاهد فيديو: '${videoTitle.take(50)}'، مشاهدة ممتعة سيدي، هل أضبط لك مؤقت راحة بعده؟"
        } else {
            "أراك تستمتع بمشاهدة مقاطع الفيديو على يوتيوب، أنا في الخدمة متى احتجتني."
        }
    }

    private fun analyzeBrowser(lines: List<String>): String {
        val articleTitle = lines.firstOrNull { it.length in 15..80 }
        return if (articleTitle != null) {
            "مقال مفيد بعنوان: '${articleTitle.take(60)}'، يبدو موضوعاً ثرياً بالمعلومات سيدي."
        } else {
            "إبحار ممتع في صفحات الإنترنت، أنا هنا إن أردت البحث عن أي معلومة."
        }
    }

    private fun analyzeMessaging(appName: String, lines: List<String>): String {
        val contactName = lines.firstOrNull { it.length in 3..25 }
        return if (contactName != null) {
            "أنت في محادثة مع $contactName على $appName، هل تود أن أساعدك في صياغة رد سريع؟"
        } else {
            "محادثة شيقة على $appName، أخبرني إن كنت بحاجة لتدقيق أو صياغة رسالة."
        }
    }
}
