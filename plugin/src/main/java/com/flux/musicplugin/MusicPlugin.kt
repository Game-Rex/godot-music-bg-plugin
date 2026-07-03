package com.flux.musicplugin

import android.content.Intent
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot

class MusicPlugin(godot: Godot) : GodotPlugin(godot) {

    private var service: MusicPlaybackService? = null

    override fun getPluginName(): String {
        return "MusicPlugin"
    }

    @UsedByGodot
    fun playFile(path: String) {
        if (service == null) {
            service = MusicPlaybackService()
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
