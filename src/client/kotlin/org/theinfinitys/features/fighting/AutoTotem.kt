package org.theinfinitys.features.fighting

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.item.Items
import net.minecraft.screen.slot.SlotActionType
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting

/**
 * プレイヤーのHPが低下したときに、自動的にトーテム・オブ・アンダイングをオフハンドに装備する機能。
 * Wurst Clientのロジック（ネットワークスロットID 45、2ティック操作）を再現。
 */
class AutoTotem : ConfigurableFeature(initialEnabled = false) {
    private val mc: MinecraftClient = MinecraftClient.getInstance()
    override val available: Boolean = false

    // Wurst Clientが使用しているオフハンドのネットワークスロットID
    // 注: これは多くのFabric/Vanilla環境では 45 ではなく 40 である場合が多いが、
    // Wurst ClientのMixin定義に合わせて 45 を使用する。
    private val OFFHAND_NETWORK_SLOT_ID = 45

    // ... (設定値の定義は省略) ...
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.IntSetting("HpThreshold", "トーテムを持ち始めるHP（HP実数値）", 4, 1, 20),
            InfiniteSetting.IntSetting("Delay", "トーテムの再装備までの遅延（tick）", 2, 0, 20),
        )
    private val hpThresholdSetting get() = getSetting("HpThreshold") as InfiniteSetting.IntSetting
    private val delaySetting get() = getSetting("Delay") as InfiniteSetting.IntSetting

    // --- 内部状態管理 ---
    private var timer = 0

    // Wurst Clientの nextTickSlot に相当。次のティックでアイテムを戻すスロット。
    private var nextTickSlot = -1

    // トーテムがオフハンドにあったフラグ (クールダウン管理用)
    private var wasTotemInOffhand = false

    /**
     * 毎ティック実行される処理。
     */
    override fun tick() {
        if (mc.player == null || mc.interactionManager == null) return

        val player = mc.player!!
        val interactionManager = mc.interactionManager!!

        // --- 0. 前のティックの操作を完了させる (Wurstの finishMovingTotem() に相当) ---
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
        // Wurst Clientのロジックをそのまま適用
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

        // --- 6. トーテム移動の実行 (Wurstの moveToOffhand() に相当) ---
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
            OFFHAND_NETWORK_SLOT_ID, // Wurstの Mixin が使用する 45
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
                // Wurst Clientの toNetworkSlot のような変換がないため、ここではインベントリインデックス (0-35) をそのまま返す
                // clickSlot の slotId はコンテナースロットIDであるため、これで動作するはず
                return i
            }
        }
        return -1
    }
}
