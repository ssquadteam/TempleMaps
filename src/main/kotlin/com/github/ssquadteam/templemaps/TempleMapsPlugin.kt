package com.github.ssquadteam.templemaps

import com.github.ssquadteam.talelib.TalePlugin
import com.github.ssquadteam.talelib.player.name
import com.github.ssquadteam.talelib.teleport.teleport
import com.github.ssquadteam.videomaps.MapDisplayManager
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * TempleMaps - Run x86 operating systems on the Hytale world map!
 *
 * Uses h0MER247's JPC x86 emulator to run Windows 95, FreeDOS, and other
 * x86 OSes, rendering the output to the VideoMaps display system.
 */
class TempleMapsPlugin(init: JavaPluginInit) : TalePlugin(init) {

    companion object {
        lateinit var instance: TempleMapsPlugin
            private set
    }

    private val playingPlayers = ConcurrentHashMap.newKeySet<String>()

    lateinit var engine: TempleEngine
        private set
    lateinit var frameProvider: TempleFrameProvider
        private set

    private var isoPath: String = ""

    override fun onSetup() {
        instance = this
        info("TempleMaps initializing...")

        isoPath = "mods/SSquadTeam_TempleMaps/"

        val dataDir = File("mods/SSquadTeam_TempleMaps")
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }

        engine = TempleEngine()
        frameProvider = TempleFrameProvider(engine)

        entityStoreRegistry.registerSystem(TempleInputSystem())
    }

    override fun onStart() {
        taleCommands.register(TempleCommand())

        info("TempleMaps ready!")
        info("Place disk images (.img/.iso) in: mods/SSquadTeam_TempleMaps/")
        info("Use /temple list to see images, /temple start <image> to boot")
    }

    fun addPlayer(player: Player) {
        val playerRef = player.playerRef ?: return
        val world = player.world ?: return

        playingPlayers.add(playerRef.name)
        MapDisplayManager.addViewer(TempleCommand.DISPLAY_ID, player)

        world.execute {
            val position = playerRef.transform.position
            playerRef.teleport(
                position.x,
                position.y,
                position.z,
                yaw = 180f,
                pitch = 0f
            )
        }
    }

    fun removePlayer(player: Player) {
        val playerRef = player.playerRef ?: return

        playingPlayers.remove(playerRef.name)
        MapDisplayManager.removeViewer(TempleCommand.DISPLAY_ID, player)

        engine.releaseAllModifiers()
    }

    fun isPlayerPlaying(playerRef: PlayerRef): Boolean {
        return playingPlayers.contains(playerRef.username)
    }

    fun setIsoPath(path: String) {
        isoPath = path
    }

    fun getIsoPath(): String = isoPath

    override fun onShutdown() {
        playingPlayers.clear()
        engine.stop()
        info("TempleMaps disabled!")
    }
}
