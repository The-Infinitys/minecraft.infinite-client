package org.infinite.features.automatic.pilot

import net.minecraft.client.MinecraftClient
import net.minecraft.enchantment.Enchantments
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.inventory.InventoryManager
import org.infinite.libs.client.player.inventory.InventoryManager.Armor
import org.infinite.utils.item.enchantLevel

// 【新規】着陸に最適な地点の情報を保持するデータクラス
class LandingSpot(
    val x: Int,
    val y: Int, // 標高 (getTopYの結果)
    val z: Int,
    val score: Double, // 標高と平坦度に基づくスコア
) {
    fun horizontalDistance(): Double {
        val player = MinecraftClient.getInstance().player ?: return 0.0
        return player.y - y
    }
}

/**
 * アイテムスタックがエリトラであるかを判定します。
 */
fun isElytra(stack: ItemStack): Boolean = stack.item == Items.ELYTRA

/**
 * エリトラのインベントリ情報と耐久値を保持するためのデータクラス。
 */
internal data class ElytraInfo(
    val index: InventoryManager.InventoryIndex,
    val durability: Double,
)

/**
 * インベントリ (ホットバーとバックパック) の中で**最も耐久値の高い**エリトラの情報を返します。
 * チェストスロットに装備されているものは無視します。
 */
internal fun findBestElytraInInventory(): ElytraInfo? {
    val playerInv = MinecraftClient.getInstance().player?.inventory ?: return null
    val invManager = InfiniteClient.playerInterface.inventory
    var bestElytra: ElytraInfo? = null

    // すべてのインベントリスロット (ホットバーとバックパック) をチェック
    // ホットバー (0-8) -> InventoryManager.Hotbar(i)
    for (i in 0 until 9) {
        val stack = playerInv.getStack(i)
        if (isElytra(stack)) {
            val durability = invManager.durabilityPercentage(stack) * 100
            if ((bestElytra == null || durability > bestElytra.durability) &&
                durability > (
                    InfiniteClient
                        .getFeature(
                            AutoPilot::class.java,
                        )?.elytraThreshold
                        ?.value ?: 100
                )
            ) {
                bestElytra = ElytraInfo(InventoryManager.Hotbar(i), durability)
            }
        }
    }

    // バックパック (9-35, Backpack index 0-26) -> InventoryManager.Backpack(i)
    for (i in 0 until 27) {
        val slotIndex = 9 + i
        val stack = playerInv.getStack(slotIndex)
        if (isElytra(stack)) {
            val durability = invManager.durabilityPercentage(stack) * 100
            if ((bestElytra == null || durability > bestElytra.durability) &&
                durability > (
                    InfiniteClient
                        .getFeature(
                            AutoPilot::class.java,
                        )?.elytraThreshold
                        ?.value ?: 100
                )
            ) {
                bestElytra = ElytraInfo(InventoryManager.Backpack(i), durability)
            }
        }
    }

    return bestElytra
}

fun flightTime(): Double {
    val playerInv = MinecraftClient.getInstance().player?.inventory ?: return 0.0
    val invManager = InfiniteClient.playerInterface.inventory
    var total = currentFlightTime()

    // インベントリ内のエリトラ
    for (i in 0 until 9) {
        val stack = playerInv.getStack(i)
        if (isElytra(stack)) {
            val durability = invManager.durability(stack)
            val level = enchantLevel(stack, Enchantments.UNBREAKING)
            val multiply = 1.0 / (0.6 + 0.4 / (1.0 + level))
            total += durability * multiply
        }
    }

    for (i in 0 until 27) {
        val slotIndex = 9 + i
        val stack = playerInv.getStack(slotIndex)
        if (isElytra(stack)) {
            val durability = invManager.durability(stack)
            val level = enchantLevel(stack, Enchantments.UNBREAKING)
            val multiply = 1.0 / (0.6 + 0.4 / (1.0 + level))
            total += durability * multiply
        }
    }
    return total
}

fun currentFlightTime(): Double {
    val invManager = InfiniteClient.playerInterface.inventory
    val equippedStack = invManager.get(Armor.CHEST)
    return if (isElytra(equippedStack)) {
        val durability = invManager.durability(equippedStack)
        val level = enchantLevel(equippedStack, Enchantments.UNBREAKING)
        // 破壊不能エンチャントを考慮した耐久値
        val multiply = 1.0 / (0.6 + 0.4 / (1.0 + level))
        durability * multiply
    } else {
        0.0
    }
}

/**
 * 秒数を Dd Hh Mm Ss 形式の文字列に変換します。
 */
fun formatSecondsToDHMS(totalSeconds: Long): String {
    if (totalSeconds < 0) return "N/A"

    val secondsInDay = 24 * 60 * 60L
    val secondsInHour = 60 * 60L
    val secondsInMinute = 60L

    val days = totalSeconds / secondsInDay
    var remainingSeconds = totalSeconds % secondsInDay

    val hours = remainingSeconds / secondsInHour
    remainingSeconds %= secondsInHour

    val minutes = remainingSeconds / secondsInMinute
    val seconds = remainingSeconds % secondsInMinute

    val parts = mutableListOf<String>()
    if (days > 0) parts.add("${days}d")
    if (hours > 0 || days > 0) parts.add("${hours}h") // 日がある場合は時も表示
    if (minutes > 0 || hours > 0 || days > 0) parts.add("${minutes}m") // 時がある場合は分も表示
    parts.add("${seconds}s") // 秒は常に表示

    // 全て0秒の場合は "0s"
    if (parts.isEmpty()) return "0s"

    // 冗長にならないよう、最大3つの単位まで表示
    return parts.take(3).joinToString(" ")
}
