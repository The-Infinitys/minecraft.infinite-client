package org.infinite.global.rendering.font

import net.minecraft.util.Identifier // Identifierの使用が想定されるためインポート
import org.infinite.features.rendering.font.HyperTextRenderer
import org.infinite.global.ConfigurableGlobalFeature
import org.infinite.settings.FeatureSetting

class FontSetting : ConfigurableGlobalFeature() {
    // ----------------------------------------------------------------------
    // 1. すべてのフォント設定プロパティを定義する
    private val regularFontIdentifierSetting =
        FeatureSetting.StringSetting("RegularFont", "minecraft:infinite_regular")
    private val italicFontIdentifierSetting =
        FeatureSetting.StringSetting("ItalicFont", "minecraft:infinite_italic")
    private val boldFontIdentifierSetting =
        FeatureSetting.StringSetting("BoldFont", "minecraft:infinite_bold")
    private val boldItalicFontIdentifierSetting =
        FeatureSetting.StringSetting("BoldItalicFont", "minecraft:infinite_bolditalic")
    // ----------------------------------------------------------------------

    // 2. すべての設定をsettingsリストに含める
    override val settings: List<FeatureSetting<*>> =
        listOf(
            regularFontIdentifierSetting,
            italicFontIdentifierSetting,
            boldFontIdentifierSetting,
            boldItalicFontIdentifierSetting,
        )

    override fun onTick() {
        val hyperTextRenderer = (client.textRenderer as? HyperTextRenderer) ?: return

        // isEnabled()チェックが最初にあるため、フィーチャーが無効の場合はフォントの切り替えは行われない
        if (isEnabled()) {
            hyperTextRenderer.enable()
        } else {
            hyperTextRenderer.disable()
        }

        // 3. 定義したプロパティを使用してdefineFontを呼び出す
        hyperTextRenderer.defineFont(
            HyperTextRenderer.HyperFonts(
                Identifier.of(regularFontIdentifierSetting.value), // StringからIdentifierに変換（Identifierの使用を想定）
                Identifier.of(italicFontIdentifierSetting.value),
                Identifier.of(boldFontIdentifierSetting.value),
                Identifier.of(boldItalicFontIdentifierSetting.value),
            ),
        )
    }
}
