package com.github.ssquadteam.templemaps

import com.github.ssquadteam.talelib.command.TaleCommand
import com.github.ssquadteam.talelib.command.TaleContext
import com.github.ssquadteam.talelib.message.*
import com.github.ssquadteam.videomaps.MapDisplayManager
import java.awt.event.KeyEvent
import java.io.File

class TempleCommand : TaleCommand("temple", "Run x86 OSes on the world map") {

    companion object {
        const val DISPLAY_ID = "temple_display"
    }

    init {
        subCommand(ListImagesCommand())
        subCommand(StartCommand())
        subCommand(StopCommand())
        subCommand(JoinCommand())
        subCommand(LeaveCommand())
        subCommand(StatusCommand())
        subCommand(TypeCommand())
        subCommand(KeyCommand())
        subCommand(ClickCommand())
        subCommand(MouseCommand())
        subCommand(CliCommand())
    }

    override fun onExecute(ctx: TaleContext) {
        ctx.reply("x86 Emulator Commands:".info())
        ctx.reply("  /temple list - List available disk images".muted())
        ctx.reply("  /temple start <image> - Start OS (e.g., win95, freedos)".muted())
        ctx.reply("  /temple stop - Stop TempleOS".muted())
        ctx.reply("  /temple join - Join as player".muted())
        ctx.reply("  /temple leave - Leave session".muted())
        ctx.reply("  /temple status - Show status".muted())
        ctx.reply("  /temple type <text> - Type text".muted())
        ctx.reply("  /temple key <key> - Send key".muted())
        ctx.reply("  /temple click [times <n>] - Left click (or n times)".muted())
        ctx.reply("  /temple mouse <x> <y> - Move mouse".muted())
        ctx.reply("  /temple cli <cmd> - Run CLI command".muted())
    }

    class StartCommand : TaleCommand("start", "Start an OS from disk image") {
        private val imageArg = stringArg("image", "Image filename (e.g., win95.img, freedos.iso)")

        override fun onExecute(ctx: TaleContext) {
            val engine = TempleMapsPlugin.instance.engine

            if (engine.isRunning()) {
                ctx.reply("Emulator is already running! Use /temple stop first.".error())
                return
            }

            val imageName = ctx.get(imageArg)
            val imagesDir = File("mods/SSquadTeam_TempleMaps")

            var imageFile = File(imagesDir, imageName)
            if (!imageFile.exists()) {
                for (ext in listOf(".img", ".iso", ".IMG", ".ISO")) {
                    imageFile = File(imagesDir, "$imageName$ext")
                    if (imageFile.exists()) break
                }
            }

            if (!imageFile.exists()) {
                ctx.reply("Image not found: $imageName".error())
                ctx.reply("Place disk images in: mods/SSquadTeam_TempleMaps/".muted())
                ctx.reply("Supported: .img (HDD), .iso (CD-ROM)".muted())
                return
            }

            val ext = imageFile.extension.lowercase()
            val bootType = when (ext) {
                "iso" -> TempleEngine.BootType.CDROM
                "img" -> TempleEngine.BootType.HDA
                else -> TempleEngine.BootType.HDA
            }

            val ramMB = when {
                imageName.contains("win95", ignoreCase = true) -> 480
                imageName.contains("win98", ignoreCase = true) -> 512
                imageName.contains("dos", ignoreCase = true) -> 64
                else -> 256
            }

            if (!MapDisplayManager.exists(DISPLAY_ID)) {
                MapDisplayManager.create {
                    id = DISPLAY_ID
                    startChunkX = 0
                    startChunkZ = 0
                    widthChunks = 20
                    heightChunks = 15
                }
            }

            ctx.reply("Starting ${imageFile.name} (${bootType.name}, ${ramMB}MB RAM)...".info())

            try {
                if (engine.start(imageFile.absolutePath, bootType, ramMB)) {
                    Thread.sleep(1000)

                    MapDisplayManager.startAnimation(
                        displayId = DISPLAY_ID,
                        frameProvider = TempleMapsPlugin.instance.frameProvider,
                        frameCount = 0,
                        fps = 30,
                        loop = true
                    )

                    ctx.reply("Emulator started!".success())
                    ctx.reply("Use /temple join to play.".muted())
                } else {
                    ctx.reply("Failed to start emulator. Check console.".error())
                }
            } catch (e: Exception) {
                ctx.reply("Failed to start: ${e.message}".error())
                e.printStackTrace()
            }
        }
    }

    class ListImagesCommand : TaleCommand("list", "List available disk images") {
        override fun onExecute(ctx: TaleContext) {
            val imagesDir = File("mods/SSquadTeam_TempleMaps")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
                ctx.reply("Images folder created: mods/SSquadTeam_TempleMaps/".info())
                ctx.reply("Place .img or .iso files there.".muted())
                return
            }

            val images = imagesDir.listFiles { f ->
                f.extension.lowercase() in listOf("img", "iso")
            }

            if (images.isNullOrEmpty()) {
                ctx.reply("No disk images found.".error())
                ctx.reply("Place .img or .iso files in: mods/SSquadTeam_TempleMaps/".muted())
                return
            }

            ctx.reply("Available disk images:".info())
            images.forEach { img ->
                val type = if (img.extension.lowercase() == "iso") "CD-ROM" else "HDD"
                val sizeMB = img.length() / (1024 * 1024)
                ctx.reply("  ${img.nameWithoutExtension} ($type, ${sizeMB}MB)".muted())
            }
        }
    }

    class StopCommand : TaleCommand("stop", "Stop the emulator") {
        override fun onExecute(ctx: TaleContext) {
            val engine = TempleMapsPlugin.instance.engine

            if (!engine.isRunning()) {
                ctx.reply("Emulator is not running.".error())
                return
            }

            MapDisplayManager.stopAnimation(DISPLAY_ID)
            engine.stop()
            ctx.reply("Emulator stopped.".success())
        }
    }

    class JoinCommand : TaleCommand("join", "Join emulator session") {
        override fun onExecute(ctx: TaleContext) {
            val player = ctx.requirePlayer() ?: return
            val engine = TempleMapsPlugin.instance.engine

            if (!engine.isRunning()) {
                ctx.reply("Emulator is not running! Use /temple start first.".error())
                return
            }

            TempleMapsPlugin.instance.addPlayer(player)
            ctx.reply("Joined! Open your map (M) to view.".success())
            ctx.reply("Controls (change mode via hotbar slots 1-8):".info())
            ctx.reply("  Slot 1: Mouse Mode - WASD moves cursor, Jump clicks".muted())
            ctx.reply("  Slot 2: Arrow Keys - WASD = arrows, Jump = Enter".muted())
            ctx.reply("  Slot 3: System - W=ESC, A=F1, S=F5, D=Menu".muted())
            ctx.reply("  Slot 4: Windows - Maximize, Tile, Next Window".muted())
            ctx.reply("  Slot 5: Zoom - Zoom In/Out, Scroll".muted())
            ctx.reply("  Slot 6: Terminal - New Terminal, Tab".muted())
            ctx.reply("  Slot 7: Text Nav - PageUp/Down, Home/End".muted())
            ctx.reply("  Slot 8: Modifiers - Toggle Shift/Ctrl/Alt".muted())
        }
    }

    class LeaveCommand : TaleCommand("leave", "Leave emulator session") {
        override fun onExecute(ctx: TaleContext) {
            val player = ctx.requirePlayer() ?: return
            TempleMapsPlugin.instance.removePlayer(player)
            ctx.reply("Left emulator session.".success())
        }
    }

    class StatusCommand : TaleCommand("status", "Show emulator status") {
        override fun onExecute(ctx: TaleContext) {
            val engine = TempleMapsPlugin.instance.engine

            if (!engine.isRunning()) {
                ctx.reply("Emulator is not running.".muted())
                return
            }

            ctx.reply("Emulator Status:".info())
            ctx.reply("  Running: Yes".muted())
            ctx.reply("  Display: ${engine.displayWidth}x${engine.displayHeight}".muted())
            ctx.reply("  Mouse: (${engine.getMouseX()}, ${engine.getMouseY()})".muted())

            val mods = mutableListOf<String>()
            if (engine.isShiftHeld()) mods.add("SHIFT")
            if (engine.isCtrlHeld()) mods.add("CTRL")
            if (engine.isAltHeld()) mods.add("ALT")
            ctx.reply("  Modifiers: ${if (mods.isEmpty()) "None" else mods.joinToString(" ")}".muted())

            val display = MapDisplayManager.get(DISPLAY_ID)
            if (display != null) {
                ctx.reply("  Viewers: ${display.getViewers().size}".muted())
            }
        }
    }

    class TypeCommand : TaleCommand("type", "Type text into the emulator") {
        private val textArg = stringArg("text", "Text to type")

        override fun onExecute(ctx: TaleContext) {
            val engine = TempleMapsPlugin.instance.engine

            if (!engine.isRunning()) {
                ctx.reply("Emulator is not running!".error())
                return
            }

            val text = ctx.get(textArg)
            engine.typeText(text)
            ctx.reply("Typed: $text".success())
        }
    }

    class KeyCommand : TaleCommand("key", "Send a key to the emulator") {
        private val keyArg = stringArg("key", "Key name (ESC, ENTER, F1-F12, etc.)")

        override fun onExecute(ctx: TaleContext) {
            val engine = TempleMapsPlugin.instance.engine

            if (!engine.isRunning()) {
                ctx.reply("Emulator is not running!".error())
                return
            }

            val keyName = ctx.get(keyArg).uppercase()
            when (keyName) {
                "ESC", "ESCAPE" -> engine.tapKey(KeyEvent.VK_ESCAPE)
                "ENTER", "RETURN" -> engine.tapKey(KeyEvent.VK_ENTER)
                "SPACE" -> engine.tapKey(KeyEvent.VK_SPACE)
                "TAB" -> engine.tapKey(KeyEvent.VK_TAB)
                "BACKSPACE", "BS" -> engine.tapKey(KeyEvent.VK_BACK_SPACE)
                "DELETE", "DEL" -> engine.tapKey(KeyEvent.VK_DELETE)
                "INSERT", "INS" -> engine.tapKey(KeyEvent.VK_INSERT)

                "F1" -> engine.tapKey(KeyEvent.VK_F1)
                "F2" -> engine.tapKey(KeyEvent.VK_F2)
                "F3" -> engine.tapKey(KeyEvent.VK_F3)
                "F4" -> engine.tapKey(KeyEvent.VK_F4)
                "F5" -> engine.tapKey(KeyEvent.VK_F5)
                "F6" -> engine.tapKey(KeyEvent.VK_F6)
                "F7" -> engine.tapKey(KeyEvent.VK_F7)
                "F8" -> engine.tapKey(KeyEvent.VK_F8)
                "F9" -> engine.tapKey(KeyEvent.VK_F9)
                "F10" -> engine.tapKey(KeyEvent.VK_F10)
                "F11" -> engine.tapKey(KeyEvent.VK_F11)
                "F12" -> engine.tapKey(KeyEvent.VK_F12)

                "UP" -> engine.tapKey(KeyEvent.VK_UP)
                "DOWN" -> engine.tapKey(KeyEvent.VK_DOWN)
                "LEFT" -> engine.tapKey(KeyEvent.VK_LEFT)
                "RIGHT" -> engine.tapKey(KeyEvent.VK_RIGHT)

                "PAGEUP", "PGUP" -> engine.tapKey(KeyEvent.VK_PAGE_UP)
                "PAGEDOWN", "PGDN" -> engine.tapKey(KeyEvent.VK_PAGE_DOWN)
                "HOME" -> engine.tapKey(KeyEvent.VK_HOME)
                "END" -> engine.tapKey(KeyEvent.VK_END)

                "PRINTSCREEN", "PRTSC" -> engine.tapKey(KeyEvent.VK_PRINTSCREEN)
                "SCROLLLOCK" -> engine.tapKey(KeyEvent.VK_SCROLL_LOCK)
                "PAUSE", "BREAK" -> engine.tapKey(KeyEvent.VK_PAUSE)
                "NUMLOCK" -> engine.tapKey(KeyEvent.VK_NUM_LOCK)
                "CAPSLOCK" -> engine.tapKey(KeyEvent.VK_CAPS_LOCK)

                "SHIFT" -> {
                    val held = engine.toggleShift()
                    ctx.reply("Shift: ${if (held) "Held" else "Released"}".info())
                    return
                }
                "CTRL", "CONTROL" -> {
                    val held = engine.toggleCtrl()
                    ctx.reply("Ctrl: ${if (held) "Held" else "Released"}".info())
                    return
                }
                "ALT" -> {
                    val held = engine.toggleAlt()
                    ctx.reply("Alt: ${if (held) "Held" else "Released"}".info())
                    return
                }

                "CTRLALTDEL", "CAD" -> {
                    engine.pressCtrlAltDel()
                    ctx.reply("Sent Ctrl+Alt+Delete".success())
                    return
                }

                else -> {
                    if (keyName.length == 1) {
                        engine.typeText(keyName.lowercase())
                    } else {
                        ctx.reply("Unknown key: $keyName".error())
                        ctx.reply("Available: ESC, ENTER, SPACE, TAB, F1-F12, UP/DOWN/LEFT/RIGHT".muted())
                        ctx.reply("          PAGEUP, PAGEDOWN, HOME, END, INSERT, DELETE".muted())
                        ctx.reply("          SHIFT, CTRL, ALT (toggles), CTRLALTDEL".muted())
                        return
                    }
                }
            }
            ctx.reply("Sent key: $keyName".success())
        }
    }

    class ClickCommand : TaleCommand("click", "Left click at cursor position") {
        init {
            subCommand(MultiClickCommand())
        }

        override fun onExecute(ctx: TaleContext) {
            val engine = TempleMapsPlugin.instance.engine

            if (!engine.isRunning()) {
                ctx.reply("Emulator is not running!".error())
                return
            }

            engine.mouseLeftClick()
            ctx.reply("Clicked at (${engine.getMouseX()}, ${engine.getMouseY()})".success())
        }

        class MultiClickCommand : TaleCommand("times", "Click multiple times") {
            private val countArg = intArg("count", "Number of clicks (1-10)")

            override fun onExecute(ctx: TaleContext) {
                val engine = TempleMapsPlugin.instance.engine

                if (!engine.isRunning()) {
                    ctx.reply("Emulator is not running!".error())
                    return
                }

                val count = ctx.get(countArg).coerceIn(1, 10)

                repeat(count) { i ->
                    engine.mouseLeftClick()
                    if (i < count - 1) {
                        Thread.sleep(100)
                    }
                }

                ctx.reply("Clicked $count times at (${engine.getMouseX()}, ${engine.getMouseY()})".success())
            }
        }
    }

    class MouseCommand : TaleCommand("mouse", "Move mouse to position") {
        private val xArg = intArg("x", "X coordinate")
        private val yArg = intArg("y", "Y coordinate")

        override fun onExecute(ctx: TaleContext) {
            val engine = TempleMapsPlugin.instance.engine

            if (!engine.isRunning()) {
                ctx.reply("Emulator is not running!".error())
                return
            }

            val x = ctx.get(xArg)
            val y = ctx.get(yArg)

            engine.setMousePosition(x, y)
            ctx.reply("Mouse moved to ($x, $y)".success())
        }
    }

    class CliCommand : TaleCommand("cli", "Type command and press Enter") {
        private val cmdArg = stringArg("command", "CLI command (e.g., Dir())")

        override fun onExecute(ctx: TaleContext) {
            val engine = TempleMapsPlugin.instance.engine

            if (!engine.isRunning()) {
                ctx.reply("Emulator is not running!".error())
                return
            }

            var command = ctx.get(cmdArg)

            if (!command.endsWith(";")) {
                command += ";"
            }

            engine.typeText(command)
            Thread.sleep(50)
            engine.pressEnter()

            ctx.reply("Executed: $command".success())
        }
    }
}
