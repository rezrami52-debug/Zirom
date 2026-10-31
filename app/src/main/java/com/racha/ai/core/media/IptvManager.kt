package com.racha.ai.core.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class TvChannel(
    val id: String,
    val name: String,
    val category: String,
    val country: String,
    val streamUrl: String,
    val logoUrl: String = ""
)

class IptvManager(private val context: Context) {

    companion object {
        private const val CUSTOM_M3U_PATH = "/storage/emulated/0/Racha/iptv/channels.m3u"
        private const val CUSTOM_M3U_ALT = "/sdcard/Racha/iptv/channels.m3u"
    }

    private val builtInChannels = listOf(
        // تونس 🇹🇳
        TvChannel("tn_wat1", "الوطنية 1", "تونس", "تونس", "https://stream.wataniya1.tn/hls/live.m3u8"),
        TvChannel("tn_wat2", "الوطنية 2", "تونس", "تونس", "https://stream.wataniya2.tn/hls/live.m3u8"),
        TvChannel("tn_hannibal", "قناة حنبعل", "تونس", "تونس", "https://stream.hannibaltv.com.tn/live/live.m3u8"),
        TvChannel("tn_zitouna", "قناة الزيتونة", "تونس", "تونس", "https://live.zitounatv.com/hls/stream.m3u8"),

        // رياضة ⚽
        TvChannel("sp_bein_news", "beIN Sports الإخبارية", "رياضة", "عالمي", "https://live.beinsports.com/hls/news.m3u8"),
        TvChannel("sp_alkass", "قناة الكأس المفتوحة", "رياضة", "العالم العربي", "https://alkass.shoof.com.qa/live/alkass1.m3u8"),
        TvChannel("sp_dubai", "دبي الرياضية", "رياضة", "العالم العربي", "https://dmi.mangomolo.com/dubaisports/index.m3u8"),
        TvChannel("sp_ontime", "On Time Sports المفتوحة", "رياضة", "العالم العربي", "https://live.ontimesports.com/live/hls.m3u8"),

        // أخبار 📰
        TvChannel("nw_jazeera", "الجزيرة الإخبارية", "أخبار", "العالم العربي", "https://live-hls-web-aje.getaj.net/AJE/03.m3u8"),
        TvChannel("nw_arabia", "قناة العربية", "أخبار", "العالم العربي", "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8"),
        TvChannel("nw_france24_ar", "فرانس 24 (عربي)", "أخبار", "فرنسا", "https://static.france24.com/live/F24_AR_LO_HLS/live_tv.m3u8"),
        TvChannel("nw_france24_fr", "France 24 (Français)", "أخبار", "فرنسا", "https://static.france24.com/live/F24_FR_LO_HLS/live_tv.m3u8"),
        TvChannel("nw_skynews", "سكاي نيوز عربية", "أخبار", "العالم العربي", "https://stream.skynewsarabia.com/hls/sna.m3u8"),
        TvChannel("nw_bbc_ar", "BBC Arabic", "أخبار", "بريطانيا", "https://vs-hls-push-ww-live.akamaized.net/x=3/i=urn:bbc:pips:service:bbc_arabic_tv/pc_hd_abr_v2.m3u8"),
        TvChannel("nw_rt_ar", "RT العربية", "أخبار", "عالمي", "https://rt-arabic.secure.footprint.net/live/rtarab.m3u8"),

        // وثائقي وعام 🌍
        TvChannel("dc_natgeo", "National Geographic Abu Dhabi", "وثائقي", "العالم العربي", "https://dmitv.cdn.mangomolo.com/natgeo/index.m3u8"),
        TvChannel("gn_dubaitv", "تلفزيون دبي", "عام", "العالم العربي", "https://dmi.mangomolo.com/dubaitv/index.m3u8")
    )

    suspend fun getAllChannels(): List<TvChannel> = withContext(Dispatchers.IO) {
        val result = mutableListOf<TvChannel>()
        result.addAll(builtInChannels)

        // فحص وجود قائمة M3U خارجية للمستخدم (تحتوي على قنواته المشفرة والرياضية)
        val customFile = listOf(File(CUSTOM_M3U_PATH), File(CUSTOM_M3U_ALT)).firstOrNull { it.exists() && it.length() > 50 }
        if (customFile != null) {
            try {
                val parsed = parseM3uContent(customFile.readText())
                if (parsed.isNotEmpty()) {
                    result.addAll(0, parsed) // تقديم قنوات المستخدم في البداية
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        result
    }

    suspend fun loadCustomM3uFromUrl(urlStr: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val content = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val parsed = parseM3uContent(content)
                if (parsed.isNotEmpty()) {
                    val destDir = File("/storage/emulated/0/Racha/iptv")
                    if (!destDir.exists()) destDir.mkdirs()
                    File(destDir, "channels.m3u").writeText(content)
                    return@withContext Result.success(parsed.size)
                }
            }
            Result.failure(Exception("قائمة القنوات غير صالحة"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseM3uContent(content: String): List<TvChannel> {
        val channels = mutableListOf<TvChannel>()
        val lines = content.lines()
        var currentName = ""
        var currentCategory = "عام"
        var currentCountry = "مخصص"
        var currentLogo = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                // استخراج اسم القناة
                currentName = trimmed.substringAfterLast(",").trim()

                // استخراج المجموعة / الفئة (group-title)
                if (trimmed.contains("group-title=\"")) {
                    currentCategory = trimmed.substringAfter("group-title=\"").substringBefore("\"").trim()
                }

                // استخراج الشعار (tvg-logo)
                if (trimmed.contains("tvg-logo=\"")) {
                    currentLogo = trimmed.substringAfter("tvg-logo=\"").substringBefore("\"").trim()
                }

                // تصنيف الدولة التقريبي
                currentCountry = when {
                    currentCategory.contains("تونس") || currentName.contains("Tunis") || currentName.contains("الوطنية") -> "تونس"
                    currentCategory.contains("مصر") || currentCategory.contains("سعودية") || currentCategory.contains("عرب") -> "العالم العربي"
                    currentCategory.contains("France") || currentName.contains("FR") -> "فرنسا"
                    currentCategory.contains("Sport") || currentCategory.contains("رياض") || currentName.contains("beIN") -> "رياضة"
                    else -> "عالمي"
                }
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                if (currentName.isNotBlank()) {
                    channels.add(
                        TvChannel(
                            id = "custom_${channels.size}",
                            name = currentName,
                            category = currentCategory,
                            country = currentCountry,
                            streamUrl = trimmed,
                            logoUrl = currentLogo
                        )
                    )
                }
                currentName = ""
                currentCategory = "عام"
                currentCountry = "مخصص"
                currentLogo = ""
            }
        }
        return channels
    }

    suspend fun findMatchStream(teamOrMatchName: String): TvChannel? {
        val all = getAllChannels()
        val query = teamOrMatchName.lowercase().trim()
        val sports = all.filter { it.category.contains("رياض") || it.category.contains("Sport") || it.country == "رياضة" }

        // 1. البحث المباشر
        var match = sports.find { it.name.lowercase().contains(query) }
        // 2. إذا لم يعثر عليها، يعود لقناة beIN أو الكأس المفتوحة
        if (match == null) {
            match = sports.firstOrNull() ?: all.firstOrNull()
        }
        return match
    }
}
