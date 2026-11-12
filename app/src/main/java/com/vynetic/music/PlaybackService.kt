package com.vynetic.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()

        runCatching {
            val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply {
                    setSmallIcon(R.drawable.ic_notification)
                }
            setMediaNotificationProvider(notificationProvider)
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.youtube/20.01.35 (Linux; U; Android 11; id_ID)")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId > 0) {
                    currentAudioSessionId = audioSessionId
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
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.accept(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build(),
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon().build()
                    )
                }
            })
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.playback_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.playback_channel_description)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        try {
            super.onUpdateNotification(session, startInForegroundRequired)
        } catch (t: Throwable) {
            Log.e("PlaybackService", "Notification update error (ignored to prevent crash)", t)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    companion object {
        const val CHANNEL_ID = "vynetic_playback_channel"

        @Volatile var currentAudioSessionId: Int = 0
            private set
        @Volatile var eqBandCount: Int = 5
            private set
        @Volatile var eqBandRange: ShortArray = shortArrayOf(-1500, 1500)
            private set
        @Volatile var eqAvailable: Boolean = false
            private set
        private var activeEqualizer: Equalizer? = null

        fun setEqualizerEnabled(enabled: Boolean) {
            runCatching {
                if (enabled) {
                    if (activeEqualizer == null && currentAudioSessionId > 0) {
                        val eq = Equalizer(0, currentAudioSessionId)
                        eqBandCount = eq.numberOfBands.toInt()
                        eqBandRange = eq.bandLevelRange ?: shortArrayOf(-1500, 1500)
                        activeEqualizer = eq
                        eqAvailable = true
                    }
                    activeEqualizer?.enabled = true
                } else {
                    activeEqualizer?.enabled = false
                }
            }.onFailure {
                eqAvailable = false
                activeEqualizer = null
            }
        }

        fun setEqualizerBand(band: Int, level: Short) {
            runCatching { activeEqualizer?.setBandLevel(band.toShort(), level) }
        }
    }

    override fun onDestroy() {
        runCatching { activeEqualizer?.release() }
        activeEqualizer = null
        eqAvailable = false
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
