package com.flux.musicplugin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var logCallback: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = LocalBinder()

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    private fun log(message: String) {
        logCallback?.invoke(message)
    }

    override fun onCreate() {
    super.onCreate()
    player = ExoPlayer.Builder(this).build()

    player?.addListener(object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            log("ERROR: ${error.errorCodeName} - ${error.message}")
        }

        override fun onPlaybackStateChanged(state: Int) {
            val stateName = when (state) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            log("State: $stateName")
        }
    })

    mediaSession = MediaSession.Builder(this, player!!).build()
    createNotificationChannel()
    startForegroundNotification()
}

private fun startForegroundNotification() {
    val notification = androidx.core.app.NotificationCompat.Builder(this, "music_playback_channel")
        .setContentTitle("Music Player")
        .setContentText("Playing")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .build()

    startForeground(1, notification)
}

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "music_playback_channel",
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun playFile(path: String) {
        log("playFile called: $path")
        mainHandler.post {
            try {
                val mediaItem = MediaItem.fromUri("file://$path")
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.play()
                log("Playback commands issued")
            } catch (e: Exception) {
                log("EXCEPTION: ${e.message}")
            }
        }
    }

    fun pause() {
        mainHandler.post {
            player?.pause()
        }
    }

    fun resume() {
        mainHandler.post {
            player?.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mainHandler.post {
            player?.seekTo(positionMs)
        }
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }
}
