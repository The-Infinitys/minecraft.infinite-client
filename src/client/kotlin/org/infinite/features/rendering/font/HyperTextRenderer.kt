package org.infinite.features.rendering.font

import net.minecraft.client.font.FontManager
import net.minecraft.client.font.TextRenderer
import net.minecraft.util.Identifier
import org.infinite.InfiniteClient

class HyperTextRenderer(
    val fontManager: FontManager,
) : TextRenderer(fontManager.anyFonts) {
    private fun shouldInject(): Boolean = InfiniteClient.isFeatureEnabled(HyperFont::class.java)

    class HyperFonts(
        regularIdentifier: Identifier,
        italicIdentifier: Identifier,
        boldIdentifier: Identifier,
        italicBoldIdentifier: Identifier,
    )

    val hyperFonts =
        HyperFonts(
            Identifier.of("infinite", "infinite_regular"),
            Identifier.of("infinite", "infinite_italic"),
            Identifier.of("infinite", "infinite_bold"),
            Identifier.of("infinite", "infinite_bolditalic"),
        )
}
