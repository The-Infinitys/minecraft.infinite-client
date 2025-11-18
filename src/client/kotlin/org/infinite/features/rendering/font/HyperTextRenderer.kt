package org.infinite.features.rendering.font

import net.minecraft.client.font.*
import net.minecraft.client.font.TextHandler.WidthRetriever
import net.minecraft.text.Style
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.random.Random
import org.infinite.InfiniteClient

class HyperTextRenderer(
    val fontManager: FontManager,
) : TextRenderer(fontManager.anyFonts), WidthRetriever {
    // 注入の必要性をチェックするメソッド
    private fun shouldInject(): Boolean = InfiniteClient.isFeatureEnabled(HyperFont::class.java) && isEnabled
    override var handler: TextHandler = TextHandler(this)
    fun getWidth(codePoint: Int, style: Style?): Float {
        val bakedGlyph =
            if (shouldInject())
                this.getGlyph(codePoint, style)
            else
                this.getGlyphs(style!!.getFont()).get(
                    codePoint
                )
        return bakedGlyph.metrics().getAdvance(style.isBold)
    }

    // カスタムフォントの識別子を保持するクラス
    class HyperFonts(
        val regularIdentifier: Identifier,
        val italicIdentifier: Identifier,
        val boldIdentifier: Identifier,
        val italicBoldIdentifier: Identifier,
    )

    // カスタムフォントのIdentifier
    private val hyperFonts =
        HyperFonts(
            Identifier.of("minecraft", "infinite_regular"),
            Identifier.of("minecraft", "infinite_italic"),
            Identifier.of("minecraft", "infinite_bold"),
            Identifier.of("minecraft", "infinite_bolditalic"),
        )
    private val random = Random.create()
    private fun getHyperFontIdentifier(style: Style): Identifier {
        return when {
            style.isBold && style.isItalic -> hyperFonts.italicBoldIdentifier
            style.isBold -> hyperFonts.boldIdentifier
            style.isItalic -> hyperFonts.italicIdentifier
            else -> hyperFonts.regularIdentifier
        }
    }

    override fun getGlyph(
        codePoint: Int,
        style: Style?,
    ): BakedGlyph? {
        if (!shouldInject()) {
            return super.getGlyph(codePoint, style)
        }
        val fontId = getHyperFontIdentifier(style)
        val fontStorage: FontStorage = fontManager.getStorageInternal(fontId)
        val glyphProvider: GlyphProvider = fontStorage.getGlyphs(false)
        var bakedGlyph = glyphProvider.get(codePoint)
        if (style.isObfuscated && codePoint != 32) {
            val i = MathHelper.ceil(bakedGlyph.metrics.getAdvance(false))
            bakedGlyph = glyphProvider.getObfuscated(random, i)
        }

        return bakedGlyph
    }

    private var isEnabled = false
    fun enable() {
        isEnabled = true
    }

    fun disable() {
        isEnabled = false
    }
}