package com.vynetic.music

import android.util.Log
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import java.io.IOException

object StreamResolver {
    private const val TAG = "StreamResolver"

    @Volatile
    private var isNewPipeInitialized = false

    @Synchronized
    fun ensureInitialized() {
        if (!isNewPipeInitialized) {
            runCatching {
                NewPipe.init(
                    NewPipeDownloader(),
                    Localization("id", "ID"),
                    ContentCountry("ID")
                )
                isNewPipeInitialized = true
                Log.d(TAG, "NewPipeExtractor initialized successfully")
            }.onFailure {
                Log.e(TAG, "Failed to initialize NewPipeExtractor", it)
            }
        }
    }

    data class ResolvedStream(
        val url: String,
        val mimeType: String,
        val bitrate: Int = 128000
    )

    fun resolve(videoUrl: String, quality: String = "Auto (terbaik tersedia)"): ResolvedStream {
        // Strategy 1: NewPipeExtractor (primary - deciphers signatureCipher & n-param)
        val newPipeResult = runCatching {
            ensureInitialized()
            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            val audioStreams = extractor.audioStreams
            if (!audioStreams.isNullOrEmpty()) {
                val chosen = selectAudioStream(audioStreams, quality)
                if (chosen != null && chosen.content.isNotBlank()) {
                    val mime = chosen.format?.mimeType ?: "audio/mp4"
                    Log.d(TAG, "Resolved via NewPipeExtractor: format=${chosen.format}, itag=${chosen.itag}, mime=$mime")
                    return@runCatching ResolvedStream(chosen.content, mime, chosen.averageBitrate)
                }
            }
            null
        }

        newPipeResult.getOrNull()?.let { return it }

        if (newPipeResult.isFailure) {
            Log.w(TAG, "NewPipe extraction failed, trying InnerTube fallback: ${newPipeResult.exceptionOrNull()?.message}")
        }

        // Strategy 2: InnerTube direct resolver fallback
        return runCatching {
            val directUrl = InnerTubeRepository.resolveAudioUrl(videoUrl, quality)
            val mime = if (directUrl.contains("audio%2Fwebm") || directUrl.contains("audio/webm")) {
                "audio/webm"
            } else {
                "audio/mp4"
            }
            Log.d(TAG, "Resolved via InnerTube fallback: ${directUrl.take(60)}")
            ResolvedStream(directUrl, mime, 128000)
        }.getOrElse { fallbackError ->
            val npError = newPipeResult.exceptionOrNull()?.message
            val errDetail = npError ?: fallbackError.message ?: "sumber tidak merespons"
            Log.e(TAG, "All stream resolution strategies failed: $errDetail", fallbackError)
            throw IOException("Tidak dapat memuat stream audio: $errDetail")
        }
    }

    private fun selectAudioStream(streams: List<AudioStream>, quality: String): AudioStream? {
        // Prioritize native AAC-LC (m4a / itag 140 / 139) for best Android compatibility
        val m4aStreams = streams.filter { it.format == MediaFormat.M4A || it.itag == 140 || it.itag == 139 }
        val pool = if (m4aStreams.isNotEmpty()) m4aStreams else streams

        return when (quality) {
            "Rendah · 64 kbps" -> pool.find { it.itag == 139 }
                ?: pool.minByOrNull { if (it.averageBitrate > 0) it.averageBitrate else 64000 }
            "Sedang · 128 kbps" -> pool.find { it.itag == 140 }
                ?: pool.minByOrNull { kotlin.math.abs((if (it.averageBitrate > 0) it.averageBitrate else 128000) - 128000) }
            "Tinggi · 192 kbps" -> pool.maxByOrNull { it.averageBitrate }
            else -> pool.find { it.itag == 140 } ?: pool.maxByOrNull { it.averageBitrate }
        } ?: pool.firstOrNull()
    }
}
