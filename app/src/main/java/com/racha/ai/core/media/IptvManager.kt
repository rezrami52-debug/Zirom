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
        // 🎬 قنوات الأفلام والسينما (Movies & Cinema)
        TvChannel("mov_rotana_cinema", "روتانا سينما", "أفلام", "العالم العربي", "https://shd-live.akamaized.net/hls/live/2041285/rotanacinema/playlist.m3u8"),
        TvChannel("mov_rotana_classic", "روتانا كلاسيك", "أفلام", "العالم العربي", "https://shd-live.akamaized.net/hls/live/2041285/rotanaclassic/playlist.m3u8"),
        TvChannel("mov_classic_cinema", "Classic Hollywood Movies", "أفلام", "عالمي", "https://classicmovies.akamaized.net/hls/live/2034912/stream/playlist.m3u8"),
        TvChannel("mov_action_movies", "Action Cinema 24/7", "أفلام", "عالمي", "https://action-movies.akamaized.net/hls/live/stream/playlist.m3u8"),

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

        // وثائقي وعام 🌍
        TvChannel("dc_natgeo", "National Geographic Abu Dhabi", "وثائقي", "العالم العربي", "https://dmitv.cdn.mangomolo.com/natgeo/index.m3u8"),
        TvChannel("gn_dubaitv", "تلفزيون دبي", "عام", "العالم العربي", "https://dmi.mangomolo.com/dubaitv/index.m3u8")
    )

    suspend fun getAllChannels(): List<TvChannel> = withContext(Dispatchers.IO) {
        val result = mutableListOf<TvChannel>()
        result.addAll(builtInChannels)

        val customFile = listOf(File(CUSTOM_M3U_PATH), File(CUSTOM_M3U_ALT)).firstOrNull { it.exists() && it.length() > 50 }
        if (customFile != null) {
            try {
                val parsed = parseM3uContent(customFile.readText())
                if (parsed.isNotEmpty()) {
                    result.addAll(0, parsed)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        result
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
                currentName = trimmed.substringAfterLast(",").trim()

                if (trimmed.contains("group-title=\"")) {
                    currentCategory = trimmed.substringAfter("group-title=\"").substringBefore("\"").trim()
                }

                if (trimmed.contains("tvg-logo=\"")) {
                    currentLogo = trimmed.substringAfter("tvg-logo=\"").substringBefore("\"").trim()
                }

                val lowerCat = currentCategory.lowercase()
                val lowerName = currentName.lowercase()

                // كاشف قنوات الأفلام والمسلسلات التلقائي
                val isMovieOrCinema = lowerCat.contains("movie") || lowerCat.contains("film") ||
                                      lowerCat.contains("cinema") || lowerCat.contains("aflam") ||
                                      lowerCat.contains("افلام") || lowerCat.contains("أفلام") ||
                                      lowerCat.contains("سينما") || lowerCat.contains("مسلسل") ||
                                      lowerCat.contains("series") || lowerCat.contains("box office") ||
                                      lowerCat.contains("vod") || lowerCat.contains("action") ||
                                      lowerName.contains("cinema") || lowerName.contains("سينما") ||
                                      lowerName.contains("aflam") || lowerName.contains("افلام")

                if (isMovieOrCinema) {
                    currentCategory = "أفلام"
                }

                currentCountry = when {
                    lowerCat.contains("تونس") || lowerName.contains("tunis") || lowerName.contains("الوطنية") -> "تونس"
                    lowerCat.contains("مصر") || lowerCat.contains("سعودية") || lowerCat.contains("عرب") -> "العالم العربي"
                    lowerCat.contains("france") || lowerName.contains("fr") -> "فرنسا"
                    lowerCat.contains("sport") || lowerCat.contains("رياض") || lowerName.contains("bein") -> "رياضة"
                    isMovieOrCinema -> "أفلام"
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

        var match = sports.find { it.name.lowercase().contains(query) }
        if (match == null) {
            match = sports.firstOrNull() ?: all.firstOrNull()
        }
        return match
    }

    suspend fun findMovieChannel(queryName: String): TvChannel? {
        val all = getAllChannels()
        val q = queryName.lowercase().trim()
        val movies = all.filter { it.category == "أفلام" || it.name.lowercase().contains("سينما") || it.name.lowercase().contains("cinema") }

        return movies.find { it.name.lowercase().contains(q) } ?: movies.firstOrNull()
    }
}
