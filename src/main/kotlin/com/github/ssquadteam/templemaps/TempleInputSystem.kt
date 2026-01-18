package com.github.ssquadteam.templemaps

import com.hypixel.hytale.component.*
import com.hypixel.hytale.component.dependency.*
import com.hypixel.hytale.component.query.*
import com.hypixel.hytale.component.system.tick.*
import com.hypixel.hytale.protocol.*
import com.hypixel.hytale.protocol.packets.player.*
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.movement.*
import com.hypixel.hytale.server.core.modules.entity.component.*
import com.hypixel.hytale.server.core.modules.entity.player.*
import com.hypixel.hytale.server.core.universe.*
import com.hypixel.hytale.server.core.universe.world.storage.*
import com.hypixel.hytale.server.core.util.*
import java.awt.event.KeyEvent
import kotlin.math.abs

/**
 * Input system for TempleMaps - maps Hytale player input to TempleOS.
 *
 * Hotbar Slot Modes (0-7):
 * 0 - Mouse Mode: WASD = mouse movement, Jump = left click
 * 1 - Arrow Keys: WASD = arrows, Jump = Enter
 * 2 - System Keys: W=ESC, A=F1, S=F5, D=Menu (F10)
 * 3 - Window Management: W=Alt+M, A=Alt+V, S=Alt+H, D=Ctrl+Alt+N, Jump=Ctrl+B
 * 4 - Zoom/Scroll: W=Zoom, A=ScrollL, S=Unzoom, D=ScrollR, Jump=Recenter
 * 5 - Terminal: W=Ctrl+Alt+T, A=Tab, S=Shift+Tab, D=Ctrl+M, Jump=Enter
 * 6 - Text Nav: W=PageUp, A=Home, S=PageDown, D=End, Jump=Space
 * 7 - Modifiers: W=Shift, A=Ctrl, S=Alt, D=ReleaseAll, Jump=RightClick
 */
class TempleInputSystem : EntityTickingSystem<EntityStore>() {

    private val inputQuery: Query<EntityStore> = Query.and(
        PlayerInput.getComponentType(),
        TransformComponent.getComponentType(),
        PlayerRef.getComponentType()
    )

    private val deps: Set<Dependency<EntityStore>> = setOf(
        SystemDependency(Order.BEFORE, PlayerSystems.ProcessPlayerInput::class.java)
    )

    // Track state per mode
    private var lastHotbarSlot: Byte = 0
    private var jumpCooldown = 0
    private var movementCooldown = 0

    // Jump state tracking to prevent double-triggers
    private var wasJumping = false
    private var lastY = 0.0

    // Movement tracking for WASD detection
    private var lastDeltaX = 0.0
    private var lastDeltaZ = 0.0

    override fun tick(
        dt: Float,
        index: Int,
        archetypeChunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val engine = TempleMapsPlugin.instance.engine
        if (!engine.isRunning()) return

        val playerInput = archetypeChunk.getComponent(index, PlayerInput.getComponentType()) ?: return
        val transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType()) ?: return
        val playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType()) ?: return

        if (!TempleMapsPlugin.instance.isPlayerPlaying(playerRef)) return

        val ref = archetypeChunk.getReferenceTo(index)

        // Get current hotbar slot (this is the MODE)
        val playerComponent = store.getComponent(ref, Player.getComponentType())
        var currentSlot = 0
        if (playerComponent != null) {
            currentSlot = playerComponent.inventory.activeHotbarSlot.toInt()
            lastHotbarSlot = currentSlot.toByte()
        }

        // Decrement cooldowns
        if (jumpCooldown > 0) jumpCooldown--
        if (movementCooldown > 0) movementCooldown--

        val movementQueue = playerInput.movementUpdateQueue

        for (i in 0 until movementQueue.size) {
            val update = movementQueue[i]
            if (update is PlayerInput.AbsoluteMovement) {
                val currentPos = transform.position

                // Detect jump (Y increase) for "Space" action
                // Only trigger once per jump - when we first start going up from ground level
                val deltaY = update.y - lastY
                val isRising = deltaY > 0.05

                if (isRising && !wasJumping && jumpCooldown == 0) {
                    // Just started jumping - trigger action
                    handleJump(engine, currentSlot)
                    jumpCooldown = 25  // Higher cooldown to prevent double-clicks
                    wasJumping = true
                } else if (!isRising && deltaY < -0.01) {
                    // Falling down - reset jump state
                    wasJumping = false
                }
                lastY = update.y

                // Teleport player back to prevent actual movement
                val xzDistSq = (update.x - currentPos.x) * (update.x - currentPos.x) +
                               (update.z - currentPos.z) * (update.z - currentPos.z)

                if (xzDistSq > 0.01) {
                    val clientTeleport = ClientTeleport(
                        i.toByte(),
                        ModelTransform(
                            PositionUtil.toPositionPacket(currentPos),
                            PositionUtil.toDirectionPacket(transform.rotation),
                            null
                        ),
                        true
                    )
                    playerRef.packetHandler.write(clientTeleport)
                }

                // Detect WASD from movement direction
                val deltaX = update.x - currentPos.x
                val deltaZ = update.z - currentPos.z

                if ((abs(deltaZ) > 0.001 || abs(deltaX) > 0.001) && movementCooldown == 0) {
                    var w = false
                    var a = false
                    var s = false
                    var d = false

                    if (abs(deltaZ) > abs(deltaX)) {
                        if (deltaZ < 0) w = true else s = true
                    } else {
                        if (deltaX < 0) a = true else d = true
                    }

                    handleWASD(engine, currentSlot, w, a, s, d)
                    movementCooldown = 3

                    lastDeltaX = deltaX
                    lastDeltaZ = deltaZ
                }
            }
        }

        movementQueue.clear()
    }

    private fun handleJump(engine: TempleEngine, slot: Int) {
        when (slot) {
            0 -> engine.mouseLeftClick()           // Mouse mode: left click
            1 -> engine.pressEnter()               // Arrow mode: Enter
            2 -> engine.pressSpace()               // System: Space
            3 -> engine.pressCtrlB()               // Window: Toggle border
            4 -> {                                 // Zoom: Recenter
                engine.pressKey(KeyEvent.VK_CONTROL)
                engine.tapKey(KeyEvent.VK_RIGHT)
                engine.releaseKey(KeyEvent.VK_CONTROL)
            }
            5 -> engine.pressEnter()               // Terminal: Enter
            6 -> engine.pressSpace()               // Text nav: Space/Select
            7 -> engine.mouseRightClick()          // Modifiers: Right click
        }
    }

    private fun handleWASD(engine: TempleEngine, slot: Int, w: Boolean, a: Boolean, s: Boolean, d: Boolean) {
        when (slot) {
            0 -> handleMouseMode(engine, w, a, s, d)
            1 -> handleArrowMode(engine, w, a, s, d)
            2 -> handleSystemMode(engine, w, a, s, d)
            3 -> handleWindowMode(engine, w, a, s, d)
            4 -> handleZoomMode(engine, w, a, s, d)
            5 -> handleTerminalMode(engine, w, a, s, d)
            6 -> handleTextNavMode(engine, w, a, s, d)
            7 -> handleModifierMode(engine, w, a, s, d)
        }
    }

    // Slot 0: Mouse Mode - WASD moves cursor
    private fun handleMouseMode(engine: TempleEngine, w: Boolean, a: Boolean, s: Boolean, d: Boolean) {
        val dx = when {
            a && !d -> -1
            d && !a -> 1
            else -> 0
        }
        val dy = when {
            w && !s -> -1  // Up is negative Y in screen coords
            s && !w -> 1
            else -> 0
        }
        if (dx != 0 || dy != 0) {
            engine.moveMouse(dx, dy, false)
        }
    }

    // Slot 1: Arrow Keys Mode
    private fun handleArrowMode(engine: TempleEngine, w: Boolean, a: Boolean, s: Boolean, d: Boolean) {
        if (w) engine.pressArrowUp()
        if (a) engine.pressArrowLeft()
        if (s) engine.pressArrowDown()
        if (d) engine.pressArrowRight()
    }

    // Slot 2: System Keys Mode
    private fun handleSystemMode(engine: TempleEngine, w: Boolean, a: Boolean, s: Boolean, d: Boolean) {
        if (w) engine.pressEscape()
        if (a) engine.pressF1()
        if (s) engine.pressF5()
        if (d) engine.tapKey(KeyEvent.VK_F10)  // Menu key
    }

    // Slot 3: Window Management Mode
    private fun handleWindowMode(engine: TempleEngine, w: Boolean, a: Boolean, s: Boolean, d: Boolean) {
        if (w) engine.pressAltM()      // Maximize
        if (a) engine.pressAltV()      // Tile Vertical
        if (s) engine.pressAltH()      // Tile Horizontal
        if (d) engine.pressCtrlAltN()  // Next Window
    }

    // Slot 4: Zoom/Scroll Mode
    private fun handleZoomMode(engine: TempleEngine, w: Boolean, a: Boolean, s: Boolean, d: Boolean) {
        if (w) engine.pressCtrlAltZ()  // Zoom
        if (s) engine.pressCtrlAltZ()  // Toggle zoom
        if (a) {
            // Scroll left
            engine.pressKey(KeyEvent.VK_CONTROL)
            engine.moveMouse(-3, 0)
            engine.releaseKey(KeyEvent.VK_CONTROL)
        }
        if (d) {
            // Scroll right
            engine.pressKey(KeyEvent.VK_CONTROL)
            engine.moveMouse(3, 0)
            engine.releaseKey(KeyEvent.VK_CONTROL)
        }
    }

    // Slot 5: Terminal Mode
    private fun handleTerminalMode(engine: TempleEngine, w: Boolean, a: Boolean, s: Boolean, d: Boolean) {
        if (w) engine.pressCtrlAltT()  // New Terminal
        if (a) engine.pressTab()
        if (s) {
            // Shift+Tab
            engine.pressKey(KeyEvent.VK_SHIFT)
            engine.pressTab()
            engine.releaseKey(KeyEvent.VK_SHIFT)
        }
        if (d) engine.pressCtrlM()     // Personal Menu
    }

    // Slot 6: Text Navigation Mode
    private fun handleTextNavMode(engine: TempleEngine, w: Boolean, a: Boolean, s: Boolean, d: Boolean) {
        if (w) engine.pressPageUp()
        if (a) engine.pressHome()
        if (s) engine.pressPageDown()
        if (d) engine.pressEnd()
    }

    // Slot 7: Modifier Toggle Mode
    private fun handleModifierMode(engine: TempleEngine, w: Boolean, a: Boolean, s: Boolean, d: Boolean) {
        if (w) engine.toggleShift()
        if (a) engine.toggleCtrl()
        if (s) engine.toggleAlt()
        if (d) engine.releaseAllModifiers()
    }

    override fun getQuery(): Query<EntityStore> = inputQuery

    override fun getDependencies(): Set<Dependency<EntityStore>> = deps
}
