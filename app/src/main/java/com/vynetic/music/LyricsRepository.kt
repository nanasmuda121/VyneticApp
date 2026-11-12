package com.vynetic.music

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsResult(
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val plainText: String = ""
)

object LyricsRepository {
    private const val TAG = "LyricsRepository"
    private val cache = mutableMapOf<String, LyricsResult?>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getLyrics(title: String, artist: String): LyricsResult? {
        val cacheKey = "$title::$artist"
        if (cache.containsKey(cacheKey)) {
            return cache[cacheKey]
        }

        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)

        Log.d(TAG, "Fetching lyrics for '$cleanedTitle' by '$cleanedArtist' (raw: '$title' / '$artist')")

        // 1. Try exact match on LRCLIB
        val exactResult = fetchLrclibExact(cleanedTitle, cleanedArtist)
        if (exactResult != null) {
            cache[cacheKey] = exactResult
            return exactResult
        }

        // 2. Try search query on LRCLIB
        val searchResult = fetchLrclibSearch("$cleanedTitle $cleanedArtist".trim())
        if (searchResult != null) {
            cache[cacheKey] = searchResult
            return searchResult
        }

        // 3. Try search with title only
        if (cleanedTitle.isNotBlank()) {
            val titleOnlyResult = fetchLrclibSearch(cleanedTitle)
            if (titleOnlyResult != null) {
                cache[cacheKey] = titleOnlyResult
                return titleOnlyResult
            }
        }

        // 4. Try raw query fallback
        if (cleanedTitle != title) {
            val fallbackSearch = fetchLrclibSearch("$title $artist".trim())
            if (fallbackSearch != null) {
                cache[cacheKey] = fallbackSearch
                return fallbackSearch
            }
        }

        cache[cacheKey] = null
        return null
    }

    private fun fetchLrclibExact(title: String, artist: String): LyricsResult? {
        return runCatching {
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$encTitle&artist_name=$encArtist"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "VyneticMusicApp/1.0 (https://github.com/nanasmuda121/VyneticApp)")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching null
                parseLrclibJson(JSONObject(body))
            }
        }.getOrNull()
    }

    private fun fetchLrclibSearch(query: String): LyricsResult? {
        return runCatching {
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encQuery"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "VyneticMusicApp/1.0 (https://github.com/nanasmuda121/VyneticApp)")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching null
                val array = JSONArray(body)
                if (array.length() == 0) return@runCatching null

                var bestJson: JSONObject? = null
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    if (obj.optString("syncedLyrics").isNotBlank()) {
                        bestJson = obj
                        break
                    }
                }
                if (bestJson == null) {
                    bestJson = array.optJSONObject(0)
                }

                bestJson?.let { parseLrclibJson(it) }
            }
        }.getOrNull()
    }

    private fun parseLrclibJson(json: JSONObject): LyricsResult? {
        val synced = json.optString("syncedLyrics")
        val plain = json.optString("plainLyrics")

        if (synced.isNotBlank()) {
            val parsedLines = parseLrc(synced)
            if (parsedLines.isNotEmpty()) {
                return LyricsResult(
                    isSynced = true,
                    lines = parsedLines,
                    plainText = plain.ifBlank { parsedLines.joinToString("\n") { it.text } }
                )
            }
        }

        if (plain.isNotBlank()) {
            val lines = plain.lines().map { LyricLine(-1L, it) }
            return LyricsResult(
                isSynced = false,
                lines = lines,
                plainText = plain
            )
        }

        return null
    }

    fun parseLrc(lrcText: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val lrcPattern = Pattern.compile("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")

        for (rawLine in lrcText.lines()) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val matcher = lrcPattern.matcher(line)
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val fracStr = matcher.group(3) ?: "0"
                val ms = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                    else -> (fracStr.take(3).toLongOrNull() ?: 0L)
                }
                val totalMs = (min * 60 + sec) * 1000 + ms
                val text = matcher.group(4)?.trim().orEmpty()
                result.add(LyricLine(totalMs, text))
            }
        }

        return result.sortedBy { it.timeMs }
    }

    private fun cleanTitle(title: String): String {
        var t = title
        t = t.replace(Regex("""\((?i).*?(official|video|audio|lirik|lyric|clip|mv|remix|hd|4k|feat|ft).*?\)"""), "")
        t = t.replace(Regex("""\[(?i).*?(official|video|audio|lirik|lyric|clip|mv|remix|hd|4k|feat|ft).*?\]"""), "")
        t = t.substringBefore('|')
        if (" - " in t) {
            val parts = t.split(" - ")
            if (parts.size == 2) {
                t = parts[0].trim()
            }
        }
        return t.trim()
    }

    private fun cleanArtist(artist: String): String {
        return artist
            .replace(Regex("""(?i)\s*(feat\.|ft\.|featuring).*"""), "")
            .replace(Regex("""(?i)\s*-\s*topic"""), "")
            .trim()
    }
}
