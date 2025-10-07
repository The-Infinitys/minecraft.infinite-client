package org.theinfinitys.features.fighting

import net.minecraft.client.MinecraftClient
import net.minecraft.item.Items
import net.minecraft.screen.slot.SlotActionType
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting

class AutoTotem : ConfigurableFeature(initialEnabled = false) {
    private val mc: MinecraftClient = MinecraftClient.getInstance()

    // 設定値の定義
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            // HpThreshold: トーテムを持ち始めるHP (半ハートではない、実数値)
            InfiniteSetting.IntSetting("HpThreshold", "トーテムを持ち始めるHP", 4, 1, 20),
        )

    /**
     * 毎ティック実行される処理。
     * プレイヤーのHPをチェックし、必要に応じてトーテムを装備する。
     */
    override fun tick() {
        // Featureが無効またはプレイヤーが接続されていない場合は処理しない
        if (isDisabled()) return
        if (mc.player == null || mc.interactionManager == null) return

        // --- 1. HPと設定値のチェック ---

        // 設定値を取得
        val hpThresholdSetting = getSetting("HpThreshold") as? InfiniteSetting.IntSetting ?: return
        val hpThreshold = hpThresholdSetting.value

        // 現在のHPを取得
        val currentHealth = mc.player!!.health // nullチェック済みのため!!を使用

        // HPがしきい値を超えている場合は処理をスキップ
        if (currentHealth > hpThreshold) return

        // すでにオフハンドにトーテムがあればスキップ
        if (mc.player!!.offHandStack.item == Items.TOTEM_OF_UNDYING) return

        // --- 2. インベントリ内のトーテムを検索 ---
        val totemSlot = findTotemInInventory()

        if (totemSlot != -1) {
            mc.interactionManager!!.clickSlot(
                mc.player!!.currentScreenHandler.syncId,
                totemSlot,
                40, // オフハンドのスロットID (インベントリ外の特殊スロット)
                SlotActionType.SWAP,
                mc.player!!,
            )
        }
    }

    /**
     * プレイヤーのメインインベントリ (ホットバーを含む0-35) からトーテムを探す。
     * @return トーテムが入っているスロットID (0-35)。見つからない場合は -1。
     */
    private fun findTotemInInventory(): Int {
        // 0-35: ホットバー (0-8) + メインインベントリ (9-35)
        for (i in 0 until 36) {
            if (mc.player!!
                    .inventory
                    .getStack(i)
                    .item == Items.TOTEM_OF_UNDYING
            ) {
                return i
            }
        }
        return -1
    }
}
