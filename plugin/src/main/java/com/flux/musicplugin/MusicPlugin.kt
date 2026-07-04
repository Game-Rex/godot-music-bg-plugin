package com.flux.musicplugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

class MusicPlugin(godot: Godot) : GodotPlugin(godot) {

    private var service: MusicPlaybackService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MusicPlaybackService.LocalBinder
            service = localBinder.getService()
            isBound = true
            service?.setLogCallback { message ->
                emitSignal("plugin_log", message)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
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
        if (!isBound) {
            ensureServiceStarted()
        }
        service?.playFile(path)
    }

    @UsedByGodot
    fun pause() {
        service?.pause()
    }

    @UsedByGodot
    fun resume() {
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
}
