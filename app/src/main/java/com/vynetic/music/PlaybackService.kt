package com.vynetic.music

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var equalizer: Equalizer? = null

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.youtube/20.01.35 (Linux; U; Android 11; id_ID)")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId > 0) {
                    runCatching {
                        equalizer?.release()
                        val eq = Equalizer(0, audioSessionId).apply { enabled = false }
                        equalizer = eq
                        eqBandCount = eq.numberOfBands.toInt()
                        eqBandRange = eq.bandLevelRange ?: shortArrayOf(-1500, 1500)
                        activeEqualizer = eq
                        eqAvailable = true
                    }
                }
            }
        })

        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    companion object {
        @Volatile var eqBandCount: Int = 5
            private set
        @Volatile var eqBandRange: ShortArray = shortArrayOf(-1500, 1500)
            private set
        @Volatile var eqAvailable: Boolean = false
            private set
        private var activeEqualizer: Equalizer? = null
        fun setEqualizerEnabled(enabled: Boolean) { runCatching { activeEqualizer?.enabled = enabled } }
        fun setEqualizerBand(band: Int, level: Short) {
            runCatching { activeEqualizer?.setBandLevel(band.toShort(), level) }
        }
    }

    override fun onDestroy() {
        runCatching { equalizer?.release() }
        equalizer = null
        activeEqualizer = null
        eqAvailable = false
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
