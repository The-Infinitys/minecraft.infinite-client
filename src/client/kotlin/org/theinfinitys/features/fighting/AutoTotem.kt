package org.theinfinitys.features.fighting

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.item.Items
import net.minecraft.screen.slot.SlotActionType
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting

class AutoTotem : ConfigurableFeature(initialEnabled = false) {
    private val mc: MinecraftClient = MinecraftClient.getInstance()
    override val available: Boolean = false

    private val OFFHAND_NETWORK_SLOT_ID = 45

    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.IntSetting("HpThreshold", "トーテムを持ち始めるHP（HP実数値）", 4, 1, 20),
            InfiniteSetting.IntSetting("Delay", "トーテムの再装備までの遅延（tick）", 2, 0, 20),
        )
    private val hpThresholdSetting get() = getSetting("HpThreshold") as InfiniteSetting.IntSetting
    private val delaySetting get() = getSetting("Delay") as InfiniteSetting.IntSetting

    // --- 内部状態管理 ---
    private var timer = 0

    private var nextTickSlot = -1

    private var wasTotemInOffhand = false

    override fun tick() {
        if (mc.player == null || mc.interactionManager == null) return

        val player = mc.player!!
        val interactionManager = mc.interactionManager!!

        // この操作は次のティックで実行され、パケットの連続送信を防ぐ。
        if (nextTickSlot != -1) {
            // PICKUPで nextTickSlot にカーソル上のアイテムを戻す
            interactionManager.clickSlot(
                player.currentScreenHandler.syncId,
                nextTickSlot,
                0, // button: 0
                SlotActionType.PICKUP,
                player,
            )
            nextTickSlot = -1 // 完了
            return // 後処理を優先し、メインロジックをスキップ
        }
        // -----------------------------------------------------------------

        // --- 1. トーテムの確認 ---
        val nextTotemSlot = findTotemInInventory()

        if (player.offHandStack.item == Items.TOTEM_OF_UNDYING) {
            wasTotemInOffhand = true
            return
        }

        // --- 2. クールダウンの適用 ---
        if (wasTotemInOffhand) {
            timer = delaySetting.value
            wasTotemInOffhand = false
        }

        if (nextTotemSlot == -1) return

        // --- 3. HPチェック ---
        if (player.health > hpThresholdSetting.value) return

        // --- 4. 【クラッシュ対策】開いているコンテナのチェック ---
        if (mc.currentScreen is HandledScreen<*>) {
            if (mc.currentScreen !is InventoryScreen && mc.currentScreen !is CreativeInventoryScreen) {
                return // 自身のインベントリ以外が開いていればスキップ
            }
        }

        // --- 5. Delayチェック ---
        if (timer > 0) {
            timer--
            return
        }

        val offhandWasEmpty = player.offHandStack.isEmpty

        // 1. トーテムを持ち上げる
        interactionManager.clickSlot(
            player.currentScreenHandler.syncId,
            nextTotemSlot,
            0,
            SlotActionType.PICKUP,
            player,
        )

        // 2. オフハンド (ID 45) に置く
        interactionManager.clickSlot(
            player.currentScreenHandler.syncId,
            OFFHAND_NETWORK_SLOT_ID,
            0,
            SlotActionType.PICKUP,
            player,
        )

        // 3. オフハンドに元々アイテムがあった場合、次のティックで元のスロットに戻す
        if (!offhandWasEmpty) {
            nextTickSlot = nextTotemSlot // 次のティックでこのスロットにアイテムを戻す
        }
    }

    /**
     * プレイヤーのメインインベントリ (ホットバーを含む0-35) からトーテムを探す。
     */
    private fun findTotemInInventory(): Int {
        // スロット 0-35 はそのままのインベントリインデックス
        for (i in 0 until 36) {
            if (mc.player!!
                    .inventory
                    .getStack(i)
                    .item == Items.TOTEM_OF_UNDYING
            ) {
                // clickSlot の slotId はコンテナースロットIDであるため、これで動作するはず
                return i
            }
        }
        return -1
    }
}
