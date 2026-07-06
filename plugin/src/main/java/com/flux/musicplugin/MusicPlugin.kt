package com.flux.musicplugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

class MusicPlugin(godot: Godot) : GodotPlugin(godot) {

    private var service: MusicPlaybackService? = null
    private var isBound = false
    private var pendingPath: String? = null

    init {
        android.util.Log.d("MusicPlugin", "MusicPlugin CONSTRUCTED: ${this.hashCode()}")
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MusicPlaybackService.LocalBinder
            service = localBinder.getService()
            isBound = true
            service?.setLogCallback { message ->
                emitSignal("plugin_log", message)
            }

            emitSignal("plugin_log", "Service connected, plugin instance: " + this@MusicPlugin.hashCode())

            pendingPath?.let { path ->
                service?.playFile(path)
                pendingPath = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            emitSignal("plugin_log", "Service DISCONNECTED, plugin instance: " + this@MusicPlugin.hashCode())
        }
    }

    override fun getPluginName(): String {
        return "MusicPlugin"
    }

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        val signals = mutableSetOf<SignalInfo>()
        signals.add(SignalInfo("plugin_log", String::class.java))
        return signals
    }

    private fun ensureServiceStarted() {
        val context = activity ?: return
        val intent = Intent(context, MusicPlaybackService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    @UsedByGodot
    fun playFile(path: String) {
        emitSignal("plugin_log", "playFile() on instance: " + this.hashCode() + " isBound=" + isBound)
        if (isBound) {
            service?.playFile(path)
        } else {
            pendingPath = path
            ensureServiceStarted()
        }
    }

    @UsedByGodot
    fun pause() {
        emitSignal("plugin_log", "pause() on instance: " + this.hashCode() + " isBound=" + isBound)
        service?.pause()
    }

    @UsedByGodot
    fun resume() {
        emitSignal("plugin_log", "resume() on instance: " + this.hashCode() + " isBound=" + isBound)
        service?.resume()
    }

    @UsedByGodot
    fun seekTo(positionMs: Long) {
        service?.seekTo(positionMs)
    }

    @UsedByGodot
    fun getCurrentPosition(): Long {
        return service?.getCurrentPosition() ?: 0L
    }

    @UsedByGodot
    fun getDuration(): Long {
        return service?.getDuration() ?: 0L
    }
}
