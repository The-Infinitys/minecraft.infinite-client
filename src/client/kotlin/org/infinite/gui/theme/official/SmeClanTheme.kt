package org.infinite.gui.theme.official

import net.minecraft.util.Identifier
import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.ThemeColors
import java.awt.Color as AwtColor

// --- The Clan Theme Definition ---

class SmeClanTheme : Theme("sme_clan", SmeClanColor(), Identifier.of("infinite", "icon/sme_clan.png"))

// --- The Clan Color Scheme ---

class SmeClanColor : ThemeColors() {
    /**
     * Deep Gunmetal Gray background for a sleek, serious, and tactical feel.
     * Hex: #1A1E24
     */
    override val backgroundColor: Int
        get() = 0xFF1A1E24.toInt()

    /**
     * Primary color: Bright Electric Blue for energy, focus, and modern flair.
     * Hex: #00BFFF (Deep Sky Blue)
     */
    override val primaryColor: Int
        get() = 0xFF00BFFF.toInt()

    /**
     * Calculates the color for a panel element, using a fade effect based on depth (Z).
     * The panels are a smooth gradient of Light Gray/White, fading into the dark background.
     */
    override fun panelColor(
        index: Int,
        length: Int,
        normalizedZ: Float,
    ): Int {
        val baseHue = (index.toFloat() / length * 0.15f) // Small hue range
        val baseColorInt = AwtColor.HSBtoRGB(baseHue, 0.1f, 0.9f) // Very light/desaturated base
        val baseR = (baseColorInt shr 16) and 0xFF
        val baseG = (baseColorInt shr 8) and 0xFF
        val baseB = baseColorInt and 0xFF
        val fadeFactor = 1.0f - normalizedZ
        val r = (baseR * fadeFactor).toInt().coerceIn(0, 255)
        val g = (baseG * fadeFactor).toInt().coerceIn(0, 255)
        val b = (baseB * fadeFactor).toInt().coerceIn(0, 255)
        val maxAlpha = 0xFF // 255 (Fully Opaque)
        val minAlpha = 0x80 // 128 (50% Opaque)
        val alpha = ((maxAlpha - minAlpha) * fadeFactor + minAlpha).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }
}
