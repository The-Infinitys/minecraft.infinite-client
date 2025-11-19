package org.infinite.features.rendering.ui

import net.minecraft.text.Text
import net.minecraft.util.Arm
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
                        Arm.RIGHT -> -hotbarWidth / 2 - padding
                    }
            val startX = offHandItemX + innerPadding / 2
            val startY =
                hotbarY + innerPadding / 2
            graphics2D.rect(
                startX,
                startY,
                offHandItemX + stackBoxSize - innerPadding / 2,
                hotbarY + stackBoxSize - innerPadding / 2,
                backgroundColor,
            )
            graphics2D.drawItem(offHandStack, startX.roundToInt(), startY.roundToInt())
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
        val screenWidth = graphics2D.width
        // val screenHeight = graphics2D.height // 使用しない
        val padding = config.padding.toDouble()

        // --- 基準Y座標の計算を修正 ---
        // 標準のホットバーY座標: graphics2D.height - 22
        // 経験値バーY座標: graphics2D.height - 22 - 5 (経験値バーの高さ)
        // 情報を表示するY座標は、経験値バーの上端からさらにパディングとフォントの高さを引いた位置
        val expBarHeight = 5.0 // 経験値バーの一般的な高さ (概算)
        val hotbarHeight = 32.0 // ホットバーの一般的な高さ (概算)

        // 画面下端を基準に、ホットバー、経験値バー、パディング、フォントの高さを引いた位置が、
        // 最初のテキストを描画する基準Y座標となる
        var currentY = graphics2D.height - hotbarHeight - expBarHeight - padding - textRenderer.fontHeight

        // --- 1. アイテム名と数量の描画 ---
        val itemName = selectedStack.name.string
        val itemNameWidth = textRenderer.getWidth(itemName)

        // アイテム名
        var itemNameX = (screenWidth - itemNameWidth) / 2.0
        graphics2D.drawText(
            itemName,
            itemNameX,
            currentY,
            colors.foregroundColor,
            true,
        )

        // 数量 (スタック可能なアイテムの場合) - アイテム名の右側に描画し、Y座標はそのまま
        if (selectedStack.count > 1) {
            val countText = "x${selectedStack.count}"
            val countTextX = (screenWidth / 2.0) + (itemNameWidth / 2.0) + padding
            graphics2D.drawText(
                countText,
                countTextX,
                currentY, // アイテム名と同じY座標
                colors.foregroundColor,
                true,
            )
        }

        // アイテム名/数量の行を描画し終わったので、次の情報のY座標を更新する
        currentY -= textRenderer.fontHeight + 2.0

        // --- 2. 耐久値 (耐久性のあるアイテムの場合) ---
        if (selectedStack.isDamageable) {
            val durability = selectedStack.maxDamage - selectedStack.damage
            val maxDurability = selectedStack.maxDamage
            val durabilityText = "Durability: $durability/$maxDurability"
            val durabilityTextWidth = textRenderer.getWidth(durabilityText)
            val durabilityTextX = (screenWidth - durabilityTextWidth) / 2.0

            graphics2D.drawText(
                durabilityText,
                durabilityTextX,
                currentY,
                colors.foregroundColor,
                true,
            )
            // 耐久値の行を描画し終わったので、次の情報のY座標を更新する
            currentY -= textRenderer.fontHeight + 2.0
        }

        val originalEnchantments =
            selectedStack.enchantments.enchantmentEntries
                .map { it.key }
                .filter { it != null && it.key != null }
                .map { it.key.get() }
        for (enchantment in originalEnchantments) {
            val level = enchantLevel(selectedStack, enchantment)

            // エンチャント名の取得は、元のコードのように TranslationKey を使用するのが正しい
            val enchantmentText =
                Text.translatable("enchantment.${enchantment.value.toTranslationKey()}").string + level.toRomanNumerics()
            val enchantTextWidth = textRenderer.getWidth(enchantmentText)
            val enchantTextX = (screenWidth - enchantTextWidth) / 2.0

            graphics2D.drawText(
                enchantmentText,
                enchantTextX,
                currentY,
                colors.yellowAccentColor, // エンチャントは金色で表示
                true,
            )
            // エンチャントの行を描画し終わったので、次の情報のY座標を更新する
            currentY -= textRenderer.fontHeight + 2.0
        }
    }

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
