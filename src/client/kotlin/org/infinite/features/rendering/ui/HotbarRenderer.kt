package org.infinite.features.rendering.ui

import net.minecraft.text.Text
import net.minecraft.util.Arm
import org.infinite.features.rendering.sensory.esp.ItemEsp
import org.infinite.gui.theme.ThemeColors
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.libs.client.player.ClientInterface
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.item.enchantLevel
import kotlin.math.roundToInt

class HotbarRenderer(
    private val config: UiRenderConfig,
) : ClientInterface() {
    fun render(
        graphics2D: Graphics2D,
        colors: ThemeColors,
    ) {
        renderHotBar(graphics2D, colors)
        // 選択中のアイテム情報を描画
        renderSelectedItemInfo(graphics2D, colors)
    }

    private fun renderHotBar(
        graphics2D: Graphics2D,
        colors: ThemeColors,
    ) {
        val width = graphics2D.width
        val height = graphics2D.height
        val padding = config.padding.toDouble()
        val player = player ?: return
        val accentColor = colors.primaryColor
        val borderColor = colors.secondaryColor
        val backgroundColor = colors.backgroundColor
        val stacks =
            MutableList(9) { i ->
                InventoryManager.get(InventoryManager.InventoryIndex.Hotbar(i))
            }
        val offHandStack = InventoryManager.get(InventoryManager.InventoryIndex.OffHand())
        val stackBoxSize = 21.0
        val hotbarWidth = stackBoxSize * 9
        val hotbarY = height - stackBoxSize - padding
        val centerX = width / 2
        graphics2D.rect(
            centerX - hotbarWidth / 2,
            hotbarY,
            centerX + hotbarWidth / 2,
            hotbarY + stackBoxSize,
            backgroundColor,
        )
        val itemSize = 16.0
        val innerPadding = (stackBoxSize - itemSize) / 2
        if (!offHandStack.isEmpty) {
            val offHandItemX =
                centerX +
                    when (player.mainArm) {
                        Arm.LEFT -> hotbarWidth / 2 + padding
                        Arm.RIGHT -> -hotbarWidth / 2 - padding * 2 - stackBoxSize
                    }
            val startX = offHandItemX + innerPadding
            val startY =
                hotbarY + innerPadding / 2
            graphics2D.rect(
                startX,
                startY,
                offHandItemX + stackBoxSize,
                hotbarY + stackBoxSize - innerPadding / 2,
                backgroundColor,
            )
            graphics2D.drawItem(offHandStack, startX + innerPadding / 2, startY + innerPadding / 2)
        }
        val selectedSlot = player.inventory.selectedSlot
        for (i in 0..8) {
            val hotbarX = centerX - hotbarWidth / 2 + (i * stackBoxSize)
            val stack = stacks[i]
            // 1. 選択スロットのハイライト（ボーダー）の描画
            if (i == selectedSlot) {
                // 選択されているスロットを強調表示する
                graphics2D.drawBorder(
                    hotbarX, // スロットサイズより少し大きく
                    hotbarY,
                    stackBoxSize,
                    stackBoxSize,
                    accentColor,
                    2.0,
                )
            } else {
                graphics2D.drawBorder(
                    hotbarX, // スロットサイズより少し大きく
                    hotbarY,
                    stackBoxSize,
                    stackBoxSize,
                    borderColor,
                    1.0,
                )
            }
            // 3. アイテムの描画
            if (!stack.isEmpty) {
                val itemX = hotbarX + innerPadding
                val itemY = hotbarY + innerPadding
                graphics2D.drawItem(stack, itemX.roundToInt(), itemY.roundToInt())
            }
        }
    }

    private fun renderSelectedItemInfo(
        graphics2D: Graphics2D,
        colors: ThemeColors,
    ) {
        val player = client.player ?: return
        val selectedStack = player.mainHandStack

        if (selectedStack.isEmpty) {
            return // アイテムがない場合は何もしない
        }

        val textRenderer = client.textRenderer
        val width = graphics2D.width
        val centerX = width / 2
        val padding = config.padding.toDouble()
        val fontHeight = textRenderer.fontHeight.toDouble()
        val expBarHeight = fontHeight * 1.5
        val hotbarBoxSize = 22
        val hotbarHeight = hotbarBoxSize + padding
        val hotbarWidth = 9 * hotbarBoxSize
        val bottomY = graphics2D.height - hotbarHeight - expBarHeight - padding
        val iconSize = 16.0
        val leftSideTexts = mutableListOf<Triple<String, Int, Boolean>>() // Text, Color, Shadow
        leftSideTexts.add(Triple(selectedStack.name.string, ItemEsp.rarityColor(selectedStack), false))
        leftSideTexts.add(Triple("x${selectedStack.count}", colors.secondaryColor, true))

        // 右側情報 (耐久値、エンチャント)
        val rightSideTexts = mutableListOf<Triple<String, Int, Boolean>>() // Text, Color, Shadow

        // 1. 耐久値
        if (selectedStack.isDamageable) {
            val durability = selectedStack.maxDamage - selectedStack.damage
            val maxDurability = selectedStack.maxDamage
            val durabilityText = "$durability/$maxDurability"
            rightSideTexts.add(Triple(durabilityText, colors.foregroundColor, true))
        }

        // 2. エンチャント
        val originalEnchantments =
            selectedStack.enchantments.enchantmentEntries
                .map { it.key }
                .filter { it != null && it.key != null }
                .map { it.key.get() }
        for (enchantment in originalEnchantments) {
            val level = enchantLevel(selectedStack, enchantment)
            val enchantmentText =
                Text.translatable("enchantment.${enchantment.value.toTranslationKey()}").string + level.toRomanNumerics()
            rightSideTexts.add(Triple(enchantmentText, colors.yellowAccentColor, true))
        }
        // アイコンの中央 Y 座標 (bottomY を情報の描画エリアの下端とする)
        val iconCenterY = bottomY - padding - iconSize / 2.0
        val iconY = (iconCenterY - iconSize / 2.0).roundToInt()
        val iconX = (width / 2.0 - iconSize / 2.0).roundToInt()
        graphics2D.drawItem(selectedStack, iconX, iconY)
        for (i in 0 until leftSideTexts.size) {
            val startX = centerX - hotbarWidth / 2.0
            val startY = iconY - i * fontHeight
            val (text, color, shadow) = leftSideTexts.reversed()[i]
            graphics2D.drawText(text, startX, startY, color, shadow)
        }
        for (i in 0 until rightSideTexts.size) {
            val endX = centerX + hotbarWidth / 2.0
            val startY = iconY - i * fontHeight
            val (text, color, shadow) = rightSideTexts.reversed()[i]
            val width = graphics2D.textWidth(text)
            val startX = endX - width
            graphics2D.drawText(text, startX, startY, color, shadow)
        }
    }

    // toRomanNumerics 関数は変更なし
    private fun Int.toRomanNumerics(): String {
        if (this !in 1..3999) {
            if (this < 0) {
                return "-" + (-this).toRomanNumerics()
            }
            return this.toString()
        }
        val romanNumeralsMap =
            listOf(
                1000 to "M",
                900 to "CM",
                500 to "D",
                400 to "CD",
                100 to "C",
                90 to "XC",
                50 to "L",
                40 to "XL",
                10 to "X",
                9 to "IX",
                5 to "V",
                4 to "IV",
                1 to "I",
            )

        var remaining = this
        val result = StringBuilder()

        for ((value, symbol) in romanNumeralsMap) {
            while (remaining >= value) {
                result.append(symbol)
                remaining -= value
            }
        }

        return result.toString()
    }
}
