package com.github.ssquadteam.templemaps

import com.github.ssquadteam.videomaps.FrameProvider
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Frame provider for VideoMaps that retrieves frames from the TempleOS emulator.
 */
class TempleFrameProvider(private val engine: TempleEngine) : FrameProvider {

    override fun getFrame(frameIndex: Int, width: Int, height: Int): IntArray? {
        if (!engine.isRunning()) return null

        val framePixels = engine.getFrame() ?: return null
        val sourceWidth = engine.displayWidth
        val sourceHeight = engine.displayHeight

        if (width == sourceWidth && height == sourceHeight) {
            return framePixels
        }

        val source = BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB)
        for (i in framePixels.indices) {
            val x = i % sourceWidth
            val y = i / sourceWidth
            val rgba = framePixels[i]
            val r = (rgba shr 24) and 0xFF
            val g = (rgba shr 16) and 0xFF
            val b = (rgba shr 8) and 0xFF
            val a = rgba and 0xFF
            val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
            source.setRGB(x, y, argb)
        }

        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = scaled.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.drawImage(source, 0, 0, width, height, null)
        g2d.dispose()

        val pixels = IntArray(width * height)
        for (i in 0 until width * height) {
            val x = i % width
            val y = i / width
            val argb = scaled.getRGB(x, y)
            val a = (argb shr 24) and 0xFF
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            pixels[i] = (r shl 24) or (g shl 16) or (b shl 8) or a
        }
        return pixels
    }
}
