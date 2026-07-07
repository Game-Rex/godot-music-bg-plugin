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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        signals.add(SignalInfo("album_art_ready", ...))
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
        if (controller == null) return 0L
        var result = 0L
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                result = controller?.currentPosition ?: 0L
            } catch (e: Exception) {
                emitSignal("plugin_log", "getCurrentPosition THREW: ${e.message}")
            }
            latch.countDown()
        }
        latch.await(200, TimeUnit.MILLISECONDS)
        return result
    }

    @UsedByGodot
    fun getDuration(): Long {
        if (controller == null) return 0L
        var result = 0L
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                val dur = controller?.duration ?: 0L
                result = if (dur == androidx.media3.common.C.TIME_UNSET) 0L else dur
            } catch (e: Exception) {
                emitSignal("plugin_log", "getDuration THREW: ${e.message}")
            }
            latch.countDown()
        }
        latch.await(200, TimeUnit.MILLISECONDS)
        return result
    }

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        val signals = mutableSetOf<SignalInfo>()
        signals.add(SignalInfo("plugin_log", String::class.java))
        signals.add(SignalInfo("album_art_ready", String::class.java, ByteArray::class.java))
        return signals
    }

    @UsedByGodot
    fun testAlbumArtExtraction(path: String) {
        Thread {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(path)
                val art = retriever.embeddedPicture
                retriever.release()

                mainHandler.post {
                    if (art != null) {
                        emitSignal("plugin_log", "Album art extracted, size: ${art.size} bytes")
                        emitSignal("album_art_ready", path, art)
                    } else {
                        emitSignal("plugin_log", "No embedded album art found for: $path")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    emitSignal("plugin_log", "testAlbumArtExtraction EXCEPTION: ${e.message}")
                }
            }
        }.start()
    }

}
