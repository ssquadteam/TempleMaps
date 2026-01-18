package com.github.ssquadteam.templemaps

import Hardware.Video.GraphicsCardListener
import Hardware.Keyboard.JPCKeyboardAdapter
import Hardware.Mouse.JPCMouseAdapter
import Main.Systems.AT386System
import Main.Systems.JPCSystem
import java.awt.event.KeyEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * TempleEngine wraps the h0MER247 JPC x86 emulator for running Windows 95/TempleOS
 * in the Hytale VideoMaps system.
 */
class TempleEngine : GraphicsCardListener {

    private var system: JPCSystem? = null
    private var keyAdapter: JPCKeyboardAdapter? = null
    private var mouseAdapter: JPCMouseAdapter? = null

    private val running = AtomicBoolean(false)

    private var mouseX = 320
    private var mouseY = 240
    private val mouseSpeed = 5
    private val mouseSpeedSlow = 2

    private var shiftHeld = false
    private var ctrlHeld = false
    private var altHeld = false

    var displayWidth = 640
        private set
    var displayHeight = 480
        private set

    private val frameBuffer = AtomicReference<IntArray?>(null)
    private var frameData: IntArray? = null

    enum class BootType { CDROM, HDA, FDA }

    override fun onInit(frameData: IntArray, width: Int, height: Int) {
        this.frameData = frameData
        this.displayWidth = width
        this.displayHeight = height
        println("[TempleMaps] VGA initialized: ${width}x${height}")
    }

    override fun onRedraw() {
        frameData?.let { data ->
            val rgba = convertToRGBA(data)
            frameBuffer.set(rgba)
        }
    }

    fun start(imagePath: String, bootType: BootType = BootType.HDA, ramMB: Int = 64): Boolean {
        if (running.get()) {
            return false
        }

        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            println("[TempleMaps] Image file not found: $imagePath")
            return false
        }

        try {
            println("[TempleMaps] Creating AT386 system with ${ramMB}MB RAM")

            val sys = AT386System(this)
            system = sys

            keyAdapter = sys.keyAdapter
            mouseAdapter = sys.mouseAdapter

            sys.forEachConfiguration { category, configs ->
                if (category == "IDE Pri.") {
                    configs.forEach { config ->
                        val label = config.label ?: ""
                        if (label == "Master") {
                            try {
                                println("[TempleMaps] Setting IDE Primary Master to: $imagePath")
                                config.setValue(imagePath)
                            } catch (e: Exception) {
                                println("[TempleMaps] Could not set IDE config: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

            running.set(true)

            sys.run { exception ->
                println("[TempleMaps] Emulator error: ${exception.message}")
                exception.printStackTrace()
                running.set(false)
            }

            println("[TempleMaps] Emulator started from: $imagePath")
            return true

        } catch (e: Exception) {
            println("[TempleMaps] Failed to start emulator: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    fun stop() {
        running.set(false)
        system?.stop()
        system = null
        keyAdapter = null
        mouseAdapter = null
        println("[TempleMaps] Emulator stopped")
    }

    fun isRunning(): Boolean = running.get() && system?.isStopped != true

    private fun convertToRGBA(argbPixels: IntArray): IntArray {
        val rgba = IntArray(argbPixels.size)
        for (i in argbPixels.indices) {
            val argb = argbPixels[i]
            val a = (argb shr 24) and 0xFF
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            rgba[i] = (r shl 24) or (g shl 16) or (b shl 8) or a
        }
        return rgba
    }

    fun getFrame(): IntArray? = frameBuffer.get()

    // =====================
    // Keyboard Input Methods
    // =====================

    fun pressKey(keyCode: Int): Boolean {
        return keyAdapter?.pressKeyCode(keyCode) ?: false
    }

    fun releaseKey(keyCode: Int): Boolean {
        return keyAdapter?.releaseKeyCode(keyCode) ?: false
    }

    fun tapKey(keyCode: Int, durationMs: Long = 50) {
        pressKey(keyCode)
        if (durationMs > 0) {
            Thread.sleep(durationMs)
        }
        releaseKey(keyCode)
    }

    fun typeText(text: String) {
        keyAdapter?.typeString(text)
    }

    fun pressEscape() = tapKey(KeyEvent.VK_ESCAPE)
    fun pressEnter() = tapKey(KeyEvent.VK_ENTER)
    fun pressSpace() = tapKey(KeyEvent.VK_SPACE)
    fun pressTab() = tapKey(KeyEvent.VK_TAB)
    fun pressF1() = tapKey(KeyEvent.VK_F1)
    fun pressF5() = tapKey(KeyEvent.VK_F5)

    fun pressArrowUp() = tapKey(KeyEvent.VK_UP)
    fun pressArrowDown() = tapKey(KeyEvent.VK_DOWN)
    fun pressArrowLeft() = tapKey(KeyEvent.VK_LEFT)
    fun pressArrowRight() = tapKey(KeyEvent.VK_RIGHT)

    fun pressPageUp() = tapKey(KeyEvent.VK_PAGE_UP)
    fun pressPageDown() = tapKey(KeyEvent.VK_PAGE_DOWN)
    fun pressHome() = tapKey(KeyEvent.VK_HOME)
    fun pressEnd() = tapKey(KeyEvent.VK_END)

    fun toggleShift(): Boolean {
        shiftHeld = !shiftHeld
        if (shiftHeld) pressKey(KeyEvent.VK_SHIFT) else releaseKey(KeyEvent.VK_SHIFT)
        return shiftHeld
    }

    fun toggleCtrl(): Boolean {
        ctrlHeld = !ctrlHeld
        if (ctrlHeld) pressKey(KeyEvent.VK_CONTROL) else releaseKey(KeyEvent.VK_CONTROL)
        return ctrlHeld
    }

    fun toggleAlt(): Boolean {
        altHeld = !altHeld
        if (altHeld) pressKey(KeyEvent.VK_ALT) else releaseKey(KeyEvent.VK_ALT)
        return altHeld
    }

    fun releaseAllModifiers() {
        if (shiftHeld) { releaseKey(KeyEvent.VK_SHIFT); shiftHeld = false }
        if (ctrlHeld) { releaseKey(KeyEvent.VK_CONTROL); ctrlHeld = false }
        if (altHeld) { releaseKey(KeyEvent.VK_ALT); altHeld = false }
    }

    fun isShiftHeld() = shiftHeld
    fun isCtrlHeld() = ctrlHeld
    fun isAltHeld() = altHeld

    fun pressCtrlAltDel() {
        keyAdapter?.sendCtrlAltDelete()
    }

    fun pressCtrlAltT() {
        pressKey(KeyEvent.VK_CONTROL)
        pressKey(KeyEvent.VK_ALT)
        tapKey(KeyEvent.VK_T)
        releaseKey(KeyEvent.VK_ALT)
        releaseKey(KeyEvent.VK_CONTROL)
    }

    fun pressCtrlAltN() {
        pressKey(KeyEvent.VK_CONTROL)
        pressKey(KeyEvent.VK_ALT)
        tapKey(KeyEvent.VK_N)
        releaseKey(KeyEvent.VK_ALT)
        releaseKey(KeyEvent.VK_CONTROL)
    }

    fun pressCtrlAltZ() {
        pressKey(KeyEvent.VK_CONTROL)
        pressKey(KeyEvent.VK_ALT)
        tapKey(KeyEvent.VK_Z)
        releaseKey(KeyEvent.VK_ALT)
        releaseKey(KeyEvent.VK_CONTROL)
    }

    fun pressCtrlB() {
        pressKey(KeyEvent.VK_CONTROL)
        tapKey(KeyEvent.VK_B)
        releaseKey(KeyEvent.VK_CONTROL)
    }

    fun pressCtrlM() {
        pressKey(KeyEvent.VK_CONTROL)
        tapKey(KeyEvent.VK_M)
        releaseKey(KeyEvent.VK_CONTROL)
    }

    fun pressAltM() {
        pressKey(KeyEvent.VK_ALT)
        tapKey(KeyEvent.VK_M)
        releaseKey(KeyEvent.VK_ALT)
    }

    fun pressAltV() {
        pressKey(KeyEvent.VK_ALT)
        tapKey(KeyEvent.VK_V)
        releaseKey(KeyEvent.VK_ALT)
    }

    fun pressAltH() {
        pressKey(KeyEvent.VK_ALT)
        tapKey(KeyEvent.VK_H)
        releaseKey(KeyEvent.VK_ALT)
    }

    fun pressShiftEscape() {
        pressKey(KeyEvent.VK_SHIFT)
        tapKey(KeyEvent.VK_ESCAPE)
        releaseKey(KeyEvent.VK_SHIFT)
    }

    // =====================
    // Mouse Input Methods
    // =====================

    fun moveMouse(dx: Int, dy: Int, slow: Boolean = false) {
        val speed = if (slow) mouseSpeedSlow else mouseSpeed
        val actualDx = dx * speed
        val actualDy = dy * speed

        mouseX = (mouseX + actualDx).coerceIn(0, displayWidth - 1)
        mouseY = (mouseY + actualDy).coerceIn(0, displayHeight - 1)

        mouseAdapter?.moveBy(actualDx, actualDy)
    }

    fun mouseClick(button: Int = 1) {
        val adapterButton = when (button) {
            1 -> 0  // left
            2 -> 2  // right
            4 -> 1  // middle
            else -> 0
        }
        mouseAdapter?.setButton(adapterButton, true)
        Thread.sleep(50)
        mouseAdapter?.setButton(adapterButton, false)
    }

    fun mouseLeftClick() = mouseClick(1)
    fun mouseRightClick() = mouseClick(2)
    fun mouseMiddleClick() = mouseClick(4)

    fun setMousePosition(x: Int, y: Int) {
        val dx = x - mouseX
        val dy = y - mouseY
        mouseX = x.coerceIn(0, displayWidth - 1)
        mouseY = y.coerceIn(0, displayHeight - 1)
        mouseAdapter?.moveBy(dx, dy)
    }

    fun getMouseX() = mouseX
    fun getMouseY() = mouseY

    private var dragging = false

    fun startDrag() {
        dragging = true
        mouseAdapter?.setButton(0, true)
    }

    fun endDrag() {
        dragging = false
        mouseAdapter?.setButton(0, false)
    }

    fun isDragging() = dragging

    fun dragTo(x: Int, y: Int) {
        if (!dragging) startDrag()
        val dx = x - mouseX
        val dy = y - mouseY
        mouseX = x.coerceIn(0, displayWidth - 1)
        mouseY = y.coerceIn(0, displayHeight - 1)
        mouseAdapter?.moveBy(dx, dy)
    }
}
