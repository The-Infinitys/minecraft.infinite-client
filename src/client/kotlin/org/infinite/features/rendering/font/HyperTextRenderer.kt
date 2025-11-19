package org.infinite.features.rendering.font

import net.minecraft.client.font.BakedGlyph
import net.minecraft.client.font.FontManager
import net.minecraft.client.font.FontStorage
import net.minecraft.client.font.GlyphMetrics
import net.minecraft.client.font.GlyphProvider
import net.minecraft.client.font.TextHandler
import net.minecraft.client.font.TextRenderer
import net.minecraft.text.Style
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.random.Random
import org.infinite.InfiniteClient

class HyperTextRenderer(
    val fontManager: FontManager,
) : TextRenderer(fontManager.anyFonts),
    TextHandler.WidthRetriever {
    init {
        this.handler = TextHandler(this)
    }

    private fun shouldInject(): Boolean = InfiniteClient.isFeatureEnabled(HyperFont::class.java) && isEnabled

    override fun getWidth(
        codePoint: Int,
        style: Style?,
    ): Float {
        val style = style ?: return 0f
        return this.getGlyph(codePoint, style)?.metrics?.getAdvance(style.isBold) ?: 0f
    }

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

    private fun getHyperFontIdentifier(style: Style): Identifier =
        when {
            style.isBold && style.isItalic -> hyperFonts.italicBoldIdentifier
            style.isBold -> hyperFonts.boldIdentifier
            style.isItalic -> hyperFonts.italicIdentifier
            else -> hyperFonts.regularIdentifier
        }

    class ZeroBoldOffsetMetrics(
        private val baseMetrics: GlyphMetrics,
    ) : GlyphMetrics by baseMetrics {
        override fun getAdvance(bold: Boolean): Float = super.getAdvance(bold)

        override fun getBoldOffset(): Float = 0.0f

        override fun getShadowOffset(): Float = super.getShadowOffset()
    }

    class CustomBakedGlyph(
        private val baseGlyph: BakedGlyph,
        metrics: GlyphMetrics,
    ) : BakedGlyph by baseGlyph {
        private val customMetrics = metrics

        override fun getMetrics(): GlyphMetrics = customMetrics
    }

    // HyperTextRenderer クラス内の変更
    override fun getGlyph(
        codePoint: Int,
        style: Style?,
    ): BakedGlyph? {
        val style = style ?: return null

        // shouldInject() の判定は変更なし
        if (!shouldInject()) {
            return super.getGlyph(codePoint, style)
        }

        // 1. カスタムフォントから通常のグリフを取得 (太字・斜体はIdentifierで分離済み)
        val fontId = getHyperFontIdentifier(style)
        val fontStorage: FontStorage = fontManager.getStorageInternal(fontId)
        val glyphProvider: GlyphProvider = fontStorage.getGlyphs(false)
        var bakedGlyph = glyphProvider.get(codePoint)

        // 2. 難読化処理は変更なし
        if (style.isObfuscated && codePoint != 32) {
            val i = MathHelper.ceil(bakedGlyph.metrics.getAdvance(false))
            bakedGlyph = glyphProvider.getObfuscated(random, i)
        }

        // 3. ✨ 太字の場合、getBoldOffset() が 0.0F の Metrics を持つラッパーを返す
        if (style.isBold) {
            // 太字スタイルのグリフが取得できた場合、標準の二重描画を抑制する
            val zeroBoldMetrics = ZeroBoldOffsetMetrics(bakedGlyph.metrics)
            return CustomBakedGlyph(bakedGlyph, zeroBoldMetrics)
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
