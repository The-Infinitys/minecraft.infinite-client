package org.infinite.features.rendering.font

import net.minecraft.client.font.BakedGlyph
import net.minecraft.client.font.FontManager
import net.minecraft.client.font.TextRenderer
import net.minecraft.text.Style
import net.minecraft.util.Identifier
import org.infinite.InfiniteClient

class HyperTextRenderer(
    fontManager: FontManager,
) : TextRenderer(fontManager.anyFonts) {
    // 注入の必要性をチェックするメソッド
    private fun shouldInject(): Boolean = InfiniteClient.isFeatureEnabled(HyperFont::class.java)

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

    /**
     * テキストのスタイルに基づいて使用するGlyphProviderを選択するロジック。
     */
    override fun getGlyph(
        codePoint: Int,
        style: Style,
    ): BakedGlyph? = super.getGlyph(codePoint, style)
}
