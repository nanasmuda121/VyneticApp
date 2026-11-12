package com.vynetic.music

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Unofficial YouTube Music InnerTube client.
 * Uses the public WEB_REMIX client context; no developer API key is required.
 * This implementation intentionally accepts only direct audio URLs returned by
 * InnerTube. Cipher-only/SABR formats need a dedicated extraction transport and
 * are reported clearly rather than attempting to bypass access restrictions.
 */
object InnerTubeRepository {
    private const val MUSIC_ORIGIN = "https://music.youtube.com"
    private const val MUSIC_CLIENT_VERSION = "1.20260707.12.00"
    private const val ANDROID_CLIENT_VERSION = "20.01.35"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    fun initialize() = Unit

    fun searchMusic(query: String): List<Track> {
        val payload = JSONObject()
            .put("context", context())
            .put("query", query)
            .put("params", "")
        val response = post("search", payload, musicClient = true)
        val renderers = ArrayList<JSONObject>()
        collectObjects(response, "musicResponsiveListItemRenderer", renderers)

        return renderers.mapNotNull { renderer ->
            runCatching {
                val videoId = findString(renderer, "videoId") ?: return@runCatching null
                val title = columnText(renderer, 0).ifBlank { "Unknown title" }
                val metadata = columnText(renderer, 1)
                val artist = metadata
                    .split(" • ", " · ", " - ")
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "Unknown artist" }
                val durationText = columnText(renderer, 2).ifBlank { columnText(renderer, 1).substringAfterLast(" • ", "") }
                val thumbnail = findThumbnail(renderer).orEmpty()
                Track(title, artist, "https://www.youtube.com/watch?v=$videoId", thumbnail, parseDuration(durationText))
            }.getOrNull()
        }.distinctBy { it.url }.take(30)
    }

    fun resolveAudioUrl(videoUrl: String, quality: String = "Auto (terbaik tersedia)"): String {
        val videoId = extractVideoId(videoUrl)
            ?: throw IOException("ID video tidak valid.")
        val payload = JSONObject()
            .put("context", context(musicClient = false))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("playbackContext", JSONObject().put("contentPlaybackContext", JSONObject().put("html5Preference", "HTML5_PREF_WANTS")))

        val response = post("player", payload, musicClient = false)
        val playability = response.optJSONObject("playabilityStatus")
        if (playability?.optString("status") != "OK") {
            val reason = playability?.optString("reason")?.takeIf { it.isNotBlank() }
            throw IOException(reason ?: "InnerTube tidak menyediakan stream untuk lagu ini.")
        }

        val streaming = response.optJSONObject("streamingData")
            ?: throw IOException("Data stream tidak tersedia dari InnerTube.")
        val formats = JSONArray()
        listOf("adaptiveFormats", "formats").forEach { key ->
            val array = streaming.optJSONArray(key) ?: return@forEach
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optString("mimeType").startsWith("audio/")) formats.put(item)
            }
        }

        val direct = (0 until formats.length()).mapNotNull { formats.optJSONObject(it) }
            .filter { it.optString("url").startsWith("https://") }
        if (direct.isEmpty()) {
            throw IOException("InnerTube hanya mengembalikan format terenkripsi/segmented untuk lagu ini. Format tersebut belum didukung oleh pemutar VYNETIC saat ini.")
        }

        val mp4Audio = direct.filter {
            it.optString("mimeType").startsWith("audio/mp4") || it.optInt("itag") == 140
        }
        val pool = if (mp4Audio.isNotEmpty()) mp4Audio else direct
        val chosen = when (quality) {
            "Rendah · 64 kbps" -> pool.find { it.optInt("itag") == 139 } ?: pool.minByOrNull { it.optInt("bitrate") }
            "Sedang · 128 kbps" -> pool.find { it.optInt("itag") == 140 } ?: pool.minByOrNull { kotlin.math.abs(it.optInt("bitrate") - 128_000) }
            "Tinggi · 192 kbps" -> pool.maxByOrNull { it.optInt("bitrate") }
            else -> pool.find { it.optInt("itag") == 140 } ?: pool.maxByOrNull { it.optInt("bitrate") }
        } ?: pool.firstOrNull()

        return chosen?.optString("url")?.takeIf { it.startsWith("https://") }
            ?: throw IOException("URL audio langsung tidak ditemukan.")
    }

    private fun post(endpoint: String, body: JSONObject, musicClient: Boolean): JSONObject {
        val origin = if (musicClient) MUSIC_ORIGIN else "https://www.youtube.com"
        val apiBase = "$origin/youtubei/v1"
        val clientName = if (musicClient) "67" else "3"
        val clientVersion = if (musicClient) MUSIC_CLIENT_VERSION else ANDROID_CLIENT_VERSION
        val userAgent = if (musicClient) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        } else {
            "com.google.android.youtube/$ANDROID_CLIENT_VERSION (Linux; U; Android 11; id_ID)"
        }
        val request = Request.Builder()
            .url("$apiBase/$endpoint?prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("Origin", origin)
            .header("Referer", "$origin/")
            .header("User-Agent", userAgent)
            .header("X-YouTube-Client-Name", clientName)
            .header("X-YouTube-Client-Version", clientVersion)
            .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) throw IOException("Respons InnerTube kosong.")
            return JSONObject(text)
        }
    }

    private fun context(musicClient: Boolean = true): JSONObject {
        val client = JSONObject()
            .put("clientName", if (musicClient) "WEB_REMIX" else "ANDROID")
            .put("clientVersion", if (musicClient) MUSIC_CLIENT_VERSION else ANDROID_CLIENT_VERSION)
            .put("hl", "id")
            .put("gl", "ID")
        if (!musicClient) {
            client.put("androidSdkVersion", 30)
            client.put("platform", "MOBILE")
        } else {
            client.put("platform", "DESKTOP")
        }
        return JSONObject().put("client", client)
    }

    private fun collectObjects(value: Any?, key: String, output: MutableList<JSONObject>) {
        when (value) {
            is JSONObject -> {
                val child = value.optJSONObject(key)
                if (child != null) output += child
                val keys = value.keys()
                while (keys.hasNext()) collectObjects(value.opt(keys.next()), key, output)
            }
            is JSONArray -> for (i in 0 until value.length()) collectObjects(value.opt(i), key, output)
        }
    }

    private fun findString(value: Any?, key: String): String? {
        when (value) {
            is JSONObject -> {
                value.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { return it }
                val keys = value.keys()
                while (keys.hasNext()) findString(value.opt(keys.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until value.length()) findString(value.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun columnText(renderer: JSONObject, index: Int): String {
        val columns = renderer.optJSONArray("flexColumns") ?: return ""
        val column = columns.optJSONObject(index) ?: return ""
        val flex = column.optJSONObject("musicResponsiveListItemFlexColumnRenderer") ?: return ""
        val text = flex.optJSONObject("text") ?: return ""
        text.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = text.optJSONArray("runs") ?: return ""
        return (0 until runs.length()).mapNotNull { runs.optJSONObject(it)?.optString("text") }
            .joinToString("")
    }

    private fun findThumbnail(renderer: JSONObject): String? {
        val candidates = ArrayList<JSONObject>()
        collectObjects(renderer, "thumbnail", candidates)
        return candidates.asSequence()
            .mapNotNull { it.optJSONArray("thumbnails") }
            .flatMap { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("url") } }
            .lastOrNull { it.startsWith("https://") }
    }

    private fun parseDuration(value: String): Long {
        val parts = value.trim().split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return -1L
        return parts.fold(0L) { total, part -> total * 60 + part }
    }

    private fun extractVideoId(url: String): String? = when {
        "v=" in url -> url.substringAfter("v=").substringBefore('&').takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore('?').takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        url.matches(Regex("[A-Za-z0-9_-]{11}")) -> url
        else -> null
    }
}
