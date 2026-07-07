package com.flux.musicplugin

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

class MusicPlugin(godot: Godot) : GodotPlugin(godot) {

    private var controller: MediaController? = null
    private var isBuilding = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun getPluginName(): String {
        return "MusicPlugin"
    }

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        val signals = mutableSetOf<SignalInfo>()
        signals.add(SignalInfo("plugin_log", String::class.java))
        return signals
    }

    private fun ensureControllerReady(onReady: () -> Unit) {
        mainHandler.post {
            try {
                if (controller != null) {
                    onReady()
                    return@post
                }

                if (isBuilding) {
                    emitSignal("plugin_log", "Already building controller, skipping duplicate build")
                    return@post
                }

                isBuilding = true
                val context = activity
                if (context == null) {
                    emitSignal("plugin_log", "activity is NULL, aborting")
                    isBuilding = false
                    return@post
                }

                val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
                val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

                controllerFuture.addListener({
                    mainHandler.post {
                        try {
                            controller = controllerFuture.get()
                            isBuilding = false
                            emitSignal("plugin_log", "MediaController connected: " + controller.hashCode())

                            controller?.addListener(object : Player.Listener {
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    emitSignal("plugin_log", "ERROR: ${error.errorCodeName} - ${error.message}")
                                }

                                override fun onPlaybackStateChanged(state: Int) {
                                    val stateName = when (state) {
                                        Player.STATE_IDLE -> "IDLE"
                                        Player.STATE_BUFFERING -> "BUFFERING"
                                        Player.STATE_READY -> "READY"
                                        Player.STATE_ENDED -> "ENDED"
                                        else -> "UNKNOWN"
                                    }
                                    emitSignal("plugin_log", "State: $stateName")
                                }
                            })

                            onReady()
                        } catch (e: Exception) {
                            isBuilding = false
                            emitSignal("plugin_log", "controllerFuture.get() THREW: ${e.message}")
                        }
                    }
                }, MoreExecutors.directExecutor())

            } catch (e: Exception) {
                isBuilding = false
                emitSignal("plugin_log", "ensureControllerReady OUTER EXCEPTION: ${e.message}")
            }
        }
    }

    @UsedByGodot
    fun playFile(path: String) {
        emitSignal("plugin_log", "playFile called: $path")
        ensureControllerReady {
            controller?.stop()
            controller?.clearMediaItems()
            val mediaItem = MediaItem.fromUri("file://$path")
            controller?.setMediaItem(mediaItem)
            controller?.prepare()
            controller?.play()
            emitSignal("plugin_log", "playFile issued: $path")
        }
    }

    @UsedByGodot
    fun pause() {
        emitSignal("plugin_log", "pause called")
        ensureControllerReady {
            controller?.pause()
        }
    }

    @UsedByGodot
    fun resume() {
        emitSignal("plugin_log", "resume called")
        ensureControllerReady {
            controller?.play()
        }
    }

    @UsedByGodot
    fun seekTo(positionMs: Long) {
        ensureControllerReady {
            controller?.seekTo(positionMs)
        }
    }

    @UsedByGodot
    fun getCurrentPosition(): Long {
        return controller?.currentPosition ?: 0L
    }

    @UsedByGodot
    fun getDuration(): Long {
        val dur = controller?.duration ?: 0L
        return if (dur == androidx.media3.common.C.TIME_UNSET) 0L else dur
    }
}
