package com.racha.ai.core.knowledge

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.racha.ai.services.location.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

data class TunisiaCity(
    val name: String,
    val aliases: List<String>,
    val lat: Double,
    val lon: Double,
    val region: String,
    val hasTrain: Boolean = false,
    val trainLine: String = ""
)

object TunisiaKnowledge {

    // ==========================================
    // 1. قاعدة بيانات ولايات ومدن الجمهورية التونسية الـ 24
    // ==========================================
    val cities = listOf(
        // الشمال
        TunisiaCity("تونس العاصمة", listOf("تونس", "العاصمة", "لتونس", "tunis"), 36.8065, 10.1815, "الشمال", true, "محطة تونس برشلونة (SNCFT)"),
        TunisiaCity("أريانة", listOf("أريانة", "اريانة", "ariana"), 36.8665, 10.1647, "الشمال"),
        TunisiaCity("بن عروس", listOf("بن عروس", "ben arous"), 36.7531, 10.2189, "الشمال", true, "خط الضاحية الجنوبية"),
        TunisiaCity("منوبة", listOf("منوبة", "manouba"), 36.8080, 10.0972, "الشمال"),
        TunisiaCity("نابل", listOf("نابل", "nabeul"), 36.4561, 10.7376, "الوطن القبلي", true, "خط قطار بئر بورقبة - نابل"),
        TunisiaCity("الحمامات", listOf("الحمامات", "حمامات", "hammamet"), 36.4000, 10.6167, "الوطن القبلي", true, "محطة براكة الساحل"),
        TunisiaCity("بنزرت", listOf("بنزرت", "bizerte"), 37.2744, 9.8739, "الشمال", true, "خط قطار تونس - بنزرت"),
        TunisiaCity("باجة", listOf("باجة", "باجه", "beja"), 36.7256, 9.1817, "الشمال الغربي", true, "خط قطار الشمال الغربي"),
        TunisiaCity("جندوبة", listOf("جندوبة", "jendouba"), 36.5011, 8.7802, "الشمال الغربي", true, "خط قطار غار الدماء"),
        TunisiaCity("طبرقة", listOf("طبرقة", "tabarka"), 36.9544, 8.7581, "الشمال الغربي"),
        TunisiaCity("الكاف", listOf("الكاف", "كاف", "kef"), 36.1742, 8.7049, "الشمال الغربي", true, "خط القلعة الخصبة"),
        TunisiaCity("سليانة", listOf("سليانة", "siliana"), 36.0849, 9.3708, "الشمال الغربي"),
        TunisiaCity("زغوان", listOf("زغوان", "zaghouan"), 36.4029, 10.1429, "الشمال"),

        // الساحل والوسط
        TunisiaCity("سوسة", listOf("سوسة", "سوسه", "sousse"), 35.8256, 10.6369, "الساحل", true, "محطة سوسة باب جديد + مترو الساحل"),
        TunisiaCity("المنستير", listOf("المنستير", "منستير", "monastir"), 35.7779, 10.8262, "الساحل", true, "مترو الساحل الكهربائي"),
        TunisiaCity("المهدية", listOf("المهدية", "مهدية", "mahdia"), 35.5047, 11.0622, "الساحل", true, "مترو الساحل الكهربائي"),
        TunisiaCity("صفاقس", listOf("صفاقس", "سفاكس", "sfax"), 34.7406, 10.7603, "الوسط الشرقي", true, "محطة قطار صفاقس الخطوط البعيدة"),
        TunisiaCity("القيروان", listOf("القيروان", "قيروان", "kairouan"), 35.6781, 10.0963, "الوسط"),
        TunisiaCity("سيدي بوزيد", listOf("سيدي بوزيد", "بوزيد", "sidi bouzid"), 35.0382, 9.4849, "الوسط الغربي"),
        TunisiaCity("القصرين", listOf("القصرين", "قصرين", "kasserine"), 35.1676, 8.8365, "الوسط الغربي", true, "خط قطار تونس - القصرين"),

        // الجنوب
        TunisiaCity("قفصة", listOf("قفصة", "gafsa"), 34.4250, 8.7842, "الجنوب الغربي", true, "قطار الجنوب الغربي"),
        TunisiaCity("توزر", listOf("توزر", "tozeur"), 33.9197, 8.1335, "الجنوب الغربي", true, "خط قطار تونس - توزر"),
        TunisiaCity("قبلي", listOf("قبلي", "kebili"), 33.7044, 8.9690, "الجنوب الغربي"),
        TunisiaCity("دوز", listOf("دوز", "douz"), 33.4663, 9.0203, "الجنوب الغربي"),
        TunisiaCity("قابس", listOf("قابس", "gabes"), 33.8815, 10.0982, "الجنوب الشرقي", true, "محطة قطار قابس المدينة"),
        TunisiaCity("مدنين", listOf("مدنين", "medenine"), 33.3549, 10.5055, "الجنوب الشرقي"),
        TunisiaCity("جربة", listOf("جربة", "djerba"), 33.8750, 10.8575, "الجنوب الشرقي"),
        TunisiaCity("جرجيس", listOf("جرجيس", "zarzis"), 33.5038, 11.1122, "الجنوب الشرقي"),
        TunisiaCity("تطاوين", listOf("تطاوين", "tataouine"), 32.9333, 10.4500, "أقصى الجنوب"),
        TunisiaCity("رمادة", listOf("رمادة", "remada"), 32.3167, 10.3833, "أقصى الجنوب")
    )

    // ==========================================
    // 2. محرك الملاحة التكتيكية الحية والاستباقية
    // ==========================================
    suspend fun tryHandleTransportQuery(query: String, context: Context): String? {
        // تطبيع النص أولاً عبر قاموس الدارجة
        val normalized = normalizeDerja(query)
        val lower = query.lowercase().trim()

        // 1. بروتوكول النجاة والخروج من المسالك الفلاحية والجبال (Rural & Mountain Exit)
        val isOffroadEmergency = lower.contains("مسلك") || lower.contains("فلاحي") || lower.contains("كياس") ||
                                 lower.contains("خرجني") || lower.contains("جبل") || lower.contains("غابة") ||
                                 lower.contains("طريق رئيسي") || lower.contains("طريق معبد") || lower.contains("ضعت") ||
                                 lower.contains("وحلت") || lower.contains("تايه") || lower.contains("تائه")

        if (isOffroadEmergency) {
            val gps = LocationHelper.getCurrentLocation(context)
            val nearest = if (gps != null) findNearestCity(gps.latitude, gps.longitude) else cities.find { it.name == "سيدي بوزيد" }!!

            // إطلاق الملاحة الحية الاستباقية فوراً (Turn-by-Turn في نافذة عائمة PiP)
            launchLiveNavigation(context, nearest.name, nearest.lat, nearest.lon)

            return "يا رمزي، التقطت إحداثياتك الحالية بدقة عبر الـ GPS. أطلقت لك شاشة الملاحة الحية العائمة فوراً (Turn-by-Turn) لترشدك خطوة بخطوة نحو أقرب طريق رئيسي معبد باتجاه ${nearest.name}. اتبع التوجيهات الظاهرة في النافذة العائمة."
        }

        // 2. فحص أسئلة الملاحة والسفر العادية
        val isTransport = lower.contains("أصل") || lower.contains("اصل") || lower.contains("نوصل") ||
                          lower.contains("الوصول") || lower.contains("الذهاب") ||
                          lower.contains("كيفاش") || lower.contains("كفاش") || lower.contains("كيف") ||
                          lower.contains("نمشي") || lower.contains("نروح") || lower.contains("نسافر") ||
                          lower.contains("ثنية") || lower.contains("طريق") || lower.contains("لواج") ||
                          lower.contains("تران") || lower.contains("قطار") || lower.contains("قداش تبعد") ||
                          lower.contains("كم تبعد") || lower.contains("مسافة")

        if (!isTransport) return null

        var originCity: TunisiaCity? = null
        var destCity: TunisiaCity? = null

        // فحص الانطلاق والوصول بالاسم (مثال: من سيدي بوزيد لتطاوين)
        for (city in cities) {
            val matchedAlias = city.aliases.firstOrNull { lower.contains("من $it") || lower.contains("بين $it") }
            if (matchedAlias != null && originCity == null) {
                originCity = city
            }
        }

        for (city in cities) {
            if (city == originCity) continue
            val isDest = city.aliases.any { alias ->
                lower.contains("إلى $alias") || lower.contains("الى $alias") ||
                lower.contains("ل$alias") || lower.contains("في $alias") ||
                lower.contains(alias)
            }
            if (isDest && destCity == null) {
                destCity = city
            }
        }

        // إذا لم يذكر مدينة الانطلاق، نقرأ الـ GPS اللحظي التلقائي
        if (originCity == null) {
            val gpsData = LocationHelper.getCurrentLocation(context)
            if (gpsData != null) {
                if (!gpsData.cityName.isNullOrBlank()) {
                    val gpsName = gpsData.cityName.lowercase()
                    originCity = cities.find { c -> c.aliases.any { gpsName.contains(it) } }
                }
                if (originCity == null) {
                    originCity = findNearestCity(gpsData.latitude, gpsData.longitude)
                }
            }
            // في حال تعطل الـ GPS، سوسة هي النقطة الافتراضية
            if (originCity == null) {
                originCity = cities.find { it.name == "سوسة" }!!
            }
        }

        if (destCity == null || originCity == destCity) return null

        // حساب المسافة الحقيقية والوقت
        val distanceKm = calculateRoadDistance(originCity.lat, originCity.lon, destCity.lat, destCity.lon)
        val durationHours = distanceKm / 75.0
        val h = durationHours.toInt()
        val m = ((durationHours - h) * 60).toInt()
        val durationStr = if (h > 0) "حوالي $h ساعات و $m دقيقة" else "حوالي $m دقيقة"

        val routeAdvice = buildRouteAdvice(originCity, destCity)

        // إطلاق الملاحة التفاعلية الاستباقية فورياً في النافذة العائمة المصغرة PiP
        launchLiveNavigation(context, destCity.name, destCity.lat, destCity.lon)

        val isDistanceOnly = (lower.contains("قداش تبعد") || lower.contains("كم تبعد") || lower.contains("مسافة")) &&
                             !lower.contains("كيف") && !lower.contains("كيفاش") && !lower.contains("لواج")

        return if (isDistanceOnly) {
            "المسافة بين ${originCity.name} و ${destCity.name} حوالي $distanceKm كم برّاً ($durationStr بالسيارة). وأطلقت لك المسار في النافذة العائمة."
        } else {
            buildString {
                append("من عيوني يا رمزي! بدأت لك الملاحة الحية في النافذة العائمة، وها هي تفاصيل ثنية ${destCity.name} انطلاقاً من ${originCity.name}:\n")
                append("• المسافة والوقت: حوالي $distanceKm كم ($durationStr).\n")
                append("• بالسيارة: $routeAdvice\n")
                append("• باللواج: من محطة لواج ${originCity.name}")
                if (distanceKm > 180 && !destCity.region.contains(originCity.region)) {
                    append(" (يُفضل الربط عبر صفاقس أو قابس للمسافات البعيدة).\n")
                } else {
                    append(" (خط مباشر متوفر).\n")
                }
                if (originCity.hasTrain && destCity.hasTrain) {
                    append("• بالقطار: خط قطار SNCFT يربط بين ${originCity.trainLine} ومحطة ${destCity.name}.\n")
                }
            }
        }
    }

    private suspend fun launchLiveNavigation(context: Context, destName: String, lat: Double, lon: Double) = withContext(Dispatchers.Main) {
        try {
            // بروتوكول الملاحة الحية المباشرة Turn-by-Turn ونافذة PiP العائمة
            val navUri = Uri.parse("google.navigation:q=$lat,$lon&mode=d")
            val mapIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            try {
                val genericUri = Uri.parse("geo:$lat,$lon?q=${Uri.encode(destName)}")
                val genericIntent = Intent(Intent.ACTION_VIEW, genericUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericIntent)
            } catch (_: Exception) {}
        }
    }

    private fun buildRouteAdvice(origin: TunisiaCity, dest: TunisiaCity): String {
        return when {
            origin.name.contains("سيدي بوزيد") && dest.name.contains("تطاوين") ->
                "عبر الوطنية RN13 نحو الصخيرة والمحرس ⬅️ ثم الالتحاق بـ A1 أو الوطنية RN1 نحو قابس ⬅️ ثم مدنين ⬅️ ثم الوطنية RN19 إلى تطاوين."

            dest.name == "تونس العاصمة" && origin.lat < 36.0 ->
                "عبر الطريق السيارة A1 مباشرة نحو الشمال (مدخل مرناق/العاصمة)."

            (origin.name in listOf("تونس العاصمة", "سوسة", "المنستير", "المهدية", "صفاقس", "قابس")) &&
            (dest.name in listOf("تونس العاصمة", "سوسة", "صفاقس", "قابس", "مدنين")) ->
                "عبر الطريق السيارة السريعة A1 مباشرة."

            origin.name in listOf("سيدي بوزيد", "القيروان", "القصرين") && dest.name in listOf("سوسة", "المنستير", "صفاقس") ->
                "عبر الطريق الوطنية RN13 (نحو صفاقس) أو الوطنية RN12 عبر مساكن (نحو سوسة والساحل)."

            dest.name in listOf("تطاوين", "مدنين", "جربة", "جرجيس") ->
                "الاتجاه جنوباً عبر قابس ⬅️ مدنين، ثم الطريق الوطنية RN19 نحو تطاوين (أو القنطرة الرومانية/البطاح لجربة)."

            else -> "عبر شبكة الطرقات الوطنية الرابطة بين ولايتي ${origin.name} و ${dest.name}."
        }
    }

    private fun findNearestCity(lat: Double, lon: Double): TunisiaCity {
        return cities.minByOrNull { calculateHaversine(lat, lon, it.lat, it.lon) } ?: cities.first()
    }

    private fun calculateRoadDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val straight = calculateHaversine(lat1, lon1, lat2, lon2)
        return (straight * 1.28).roundToInt()
    }

    private fun calculateHaversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // ==========================================
    // 3. قاموس ومترجم الدارجة التونسية التفاعلي (Derja Lexicon)
    // ==========================================
    fun normalizeDerja(input: String): String {
        var text = input
        val map = listOf(
            // أدوات الاستفهام التونسية
            "\\b(شنوة|شنيا|اشنوة|اشنية|شنهو)\\b" to "ماذا",
            "\\b(وقتاش|وقتاه)\\b" to "متى",
            "\\b(كيفاش|كفاش|كفاه)\\b" to "كيف",
            "\\b(شكون)\\b" to "من",
            "\\b(قداش|قداه)\\b" to "كم",
            "\\b(علاش|لواش)\\b" to "لماذا",
            "\\b(وين|فين|وين جاية)\\b" to "أين",
            "\\b(منين)\\b" to "من أين",

            // أفعال الحركة والطلب
            "\\b(نحب نمشي|نحب نروح|باش نمشي)\\b" to "أريد الذهاب",
            "\\b(نحب|حاشتي بـ|حاشتي)\\b" to "أريد",
            "\\b(نلوج على|نفركس على|لوجلي)\\b" to "ابحث عن",
            "\\b(سكر|سكّر|بلّع)\\b" to "اغلق",
            "\\b(حل|حلّ|افتحلي)\\b" to "افتح",
            "\\b(وريني|عتني|فرّجني|ورّيني)\\b" to "أرني",
            "\\b(خرجني|خرّجني)\\b" to "أخرجني",
            "\\b(عاوني)\\b" to "ساعدني",
            "\\b(ثبتلي)\\b" to "تأكد لي",

            // الزمان والمقادير
            "\\b(برشا|برشة)\\b" to "كثيرا",
            "\\b(شوية|شوي)\\b" to "قليلا",
            "\\b(فيسع|بالزربة|فيسع فيسع)\\b" to "بسرعة",
            "\\b(توا|توة|تو)\\b" to "الآن",
            "\\b(غدوة|غدوى)\\b" to "غدا",
            "\\b(البارح|البارحة)\\b" to "أمس",
            "\\b(مبعد|مباعد)\\b" to "لاحقا",

            // التحيات والتأكيدات
            "\\b(عسلامة|نهارك زين)\\b" to "مرحبا",
            "\\b(يعيشك|يرحم والديك)\\b" to "شكرا",
            "\\b(مريقل|واضح|باهي)\\b" to "حسنا"
        )

        for ((pattern, replacement) in map) {
            text = text.replace(Regex(pattern, RegexOption.IGNORE_CASE), replacement)
        }
        return text
    }
}
