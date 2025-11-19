package org.infinite.features.fighting.gun

import net.minecraft.client.MinecraftClient
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.CrossbowItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.utils.backpack.BackPackManager
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.libs.client.inventory.InventoryManager.InventoryIndex
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting
import org.infinite.settings.Property
import org.lwjgl.glfw.GLFW

enum class FireMode {
    SEMI_AUTO,
    FULL_AUTO,
}

enum class GunnerState {
    IDLE,
    READY,
}

enum class GunnerMode {
    SHOT,
    RELOAD,
}

enum class ChangeMode {
    Fixed,
    Toggle,
}

class Gunner : ConfigurableFeature(initialEnabled = false) {
    override val toggleKeyBind: Property<Int> = Property(GLFW.GLFW_KEY_G)
    private val fireMode: FeatureSetting.EnumSetting<FireMode> =
        FeatureSetting.EnumSetting(
            "FireMode",
            FireMode.FULL_AUTO,
            FireMode.entries,
        )
    private val fastReload: FeatureSetting.BooleanSetting =
        FeatureSetting.BooleanSetting(
            "FastReload",
            false,
        )
    private val changeMode: FeatureSetting.EnumSetting<ChangeMode> =
        FeatureSetting.EnumSetting(
            "ChangeMode",
            ChangeMode.Fixed,
            ChangeMode.entries,
        )
    private val additionalInterval: FeatureSetting.IntSetting =
        FeatureSetting.IntSetting(
            "AdditionalInterval",
            3,
            0,
            10,
        )
    override val settings: List<FeatureSetting<*>> = listOf(fireMode, fastReload, changeMode, additionalInterval)
    var state: GunnerState = GunnerState.IDLE
    var mode: GunnerMode = GunnerMode.RELOAD
    override val level = FeatureLevel.Cheat

    fun gunnerCount(): Int {
        // クロスボウで使用可能なアイテムのIdentifierを取得
        val arrowItem = Registries.ITEM.get(Identifier.of("minecraft:arrow"))
        val tippedArrowItem = Registries.ITEM.get(Identifier.of("minecraft:tipped_arrow"))
        val spectralArrowItem = Registries.ITEM.get(Identifier.of("minecraft:spectral_arrow"))
        val fireworkItem = Registries.ITEM.get(Identifier.of("minecraft:firework_rocket"))

        // 各アイテムの個数を合計
        val arrowCount = InventoryManager.count(arrowItem)
        val tippedArrowCount = InventoryManager.count(tippedArrowItem)
        val spectralArrowCount = InventoryManager.count(spectralArrowItem)
        val fireworkCount = InventoryManager.count(fireworkItem)

        return arrowCount + tippedArrowCount + spectralArrowCount + fireworkCount
    }

    override fun start() {
        state = GunnerState.IDLE
        mode = GunnerMode.RELOAD
    }

    override fun stop() {
        state = GunnerState.IDLE
        mode = GunnerMode.RELOAD
    }

    private var intervalCount = 0

    override fun tick() {
        if (client.currentScreen != null) return
        switchMode()
        val manager = InventoryManager
        val backPackManager = InfiniteClient.getFeature(BackPackManager::class.java)

        backPackManager?.register {
            // --- 👇 追加された花火オフハンドのロジック ---
            val offHand = manager.get(InventoryIndex.OffHand())
            val fireworkRocketItem = Registries.ITEM.get(Identifier.of("minecraft:firework_rocket"))

            // オフハンドが花火アイテムでない、かつ、花火アイテムがある場合
            if (offHand.item != fireworkRocketItem) {
                val fireworkIndex = findFirstStarFirework() // 星付き花火を検索

                if (fireworkIndex != null) {
                    // オフハンドと花火をスワップ
                    manager.swap(InventoryIndex.OffHand(), fireworkIndex)
                    // スワップ後は、クロスボウ関連のtick処理をスキップ
                    return@register
                }
            }
            // --- 👆 ここまで追加された花火オフハンドのロジック ---

            // tick()全体をregisterで囲む
            when (mode) {
                GunnerMode.SHOT -> {
                    val mainHandItem = manager.get(InventoryIndex.MainHand())
                    if (isLoadedCrossbow(mainHandItem)) {
                        state = GunnerState.READY
                    } else {
                        state = GunnerState.IDLE
                        val loadedCrossbow = findFirstLoadedCrossbow()
                        val readyToSet =
                            (fireMode.value == FireMode.FULL_AUTO && intervalCount == 0) ||
                                (fireMode.value == FireMode.SEMI_AUTO && !options.useKey.isPressed)
                        if (loadedCrossbow != null && readyToSet) {
                            intervalCount = additionalInterval.value
                            manager.swap(InventoryIndex.MainHand(), loadedCrossbow)
                        } else {
                            if (intervalCount > 0) intervalCount--
                            val emptySlot = manager.findFirstEmptyBackpackSlot()
                            if (emptySlot != null) {
                                manager.swap(InventoryIndex.MainHand(), emptySlot)
                            }
                        }
                    }
                }

                GunnerMode.RELOAD -> {
                    state = GunnerState.IDLE
                    val mainHandItem = manager.get(InventoryIndex.MainHand())
                    if (!isUnloadedCrossbow(mainHandItem)) {
                        val loadedCrossbow = findFirstUnloadedCrossbow()
                        if (loadedCrossbow != null) {
                            manager.swap(InventoryIndex.MainHand(), loadedCrossbow)
                        } else {
                            val emptySlot = manager.findFirstEmptyBackpackSlot()
                            if (emptySlot != null) {
                                manager.swap(InventoryIndex.MainHand(), emptySlot)
                            }
                        }
                    }
                }
            }
        }
    }

    private var beforeSneaked = false

    private fun switchMode() {
        val isKeyPressed =
            MinecraftClient
                .getInstance()
                .options.sneakKey.isPressed
        when (changeMode.value) {
            ChangeMode.Fixed ->
                mode =
                    if (isKeyPressed) {
                        GunnerMode.RELOAD
                    } else {
                        GunnerMode.SHOT
                    }

            ChangeMode.Toggle -> {
                if (isKeyPressed && !beforeSneaked) {
                    beforeSneaked = true
                    mode = if (mode == GunnerMode.RELOAD) GunnerMode.SHOT else GunnerMode.RELOAD
                } else if (!isKeyPressed) {
                    beforeSneaked = false
                }
            }
        }
    }

    private fun getCrossbowItem(): Item = Registries.ITEM.get(Identifier.of("minecraft:crossbow"))

    fun totalCrossbows(): Int = InventoryManager.count(getCrossbowItem())

    fun loadedCrossbows(): Int {
        var count = 0
        // ホットバー
        for (i in 0 until 9) {
            val stack = InventoryManager.get(InventoryIndex.Hotbar(i))
            if (isLoadedCrossbow(stack)) count++
        }

        // バックパック
        for (i in 0 until 27) {
            val stack = InventoryManager.get(InventoryIndex.Backpack(i))
            if (isLoadedCrossbow(stack)) count++
        }
        // オフハンド
        val offHand = InventoryManager.get(InventoryIndex.OffHand())
        if (isLoadedCrossbow(offHand)) count++

        return count
    }

    private fun isLoadedCrossbow(stack: ItemStack): Boolean {
        if (stack.item != getCrossbowItem()) return false
        val chargedProjectiles = stack.get(DataComponentTypes.CHARGED_PROJECTILES)
        return chargedProjectiles != null && !chargedProjectiles.projectiles.isEmpty()
    }

    private fun isUnloadedCrossbow(stack: ItemStack): Boolean {
        if (stack.item != getCrossbowItem()) return false
        val chargedProjectiles = stack.get(DataComponentTypes.CHARGED_PROJECTILES)
        return chargedProjectiles != null && chargedProjectiles.projectiles.isEmpty()
    }

    private fun findFirstLoadedCrossbow(): InventoryIndex? {
        for (i in 0 until 27) {
            val stack = InventoryManager.get(InventoryIndex.Backpack(i))
            if (isLoadedCrossbow(stack)) {
                return InventoryIndex.Backpack(i)
            }
        }

        for (i in 0 until 9) {
            val stack = InventoryManager.get(InventoryIndex.Hotbar(i))
            if (isLoadedCrossbow(stack)) {
                return InventoryIndex.Hotbar(i)
            }
        }
        val offHand = InventoryManager.get(InventoryIndex.OffHand())
        if (isLoadedCrossbow(offHand)) {
            return InventoryIndex.OffHand()
        }

        return null
    }

    private fun findFirstUnloadedCrossbow(): InventoryIndex? {
        for (i in 0 until 27) {
            val stack = InventoryManager.get(InventoryIndex.Backpack(i))
            if (stack.item is CrossbowItem && !isLoadedCrossbow(stack)) {
                return InventoryIndex.Backpack(i)
            }
        }
        for (i in 0 until 9) {
            val stack = InventoryManager.get(InventoryIndex.Hotbar(i))
            if (stack.item is CrossbowItem && !isLoadedCrossbow(stack)) {
                return InventoryIndex.Hotbar(i)
            }
        }
        val offHand = InventoryManager.get(InventoryIndex.OffHand())
        if (offHand.item is CrossbowItem && !isLoadedCrossbow(offHand)) {
            return InventoryIndex.OffHand()
        }

        return null
    }

    // --- 👇 追加された花火ロジック関連のプライベート関数 ---

    /**
     * ItemStackが、星（ペイロード）を持つ花火アイテムであるか判定する。
     * * NOTE: 厳密には FireworkExplosion の Type を確認するべきだが、ここでは
     * 花火ロケットであり、何らかのペイロードを持っていること（ロケット花火として機能すること）
     * をもって「星の入った花火」と見なす。
     */
    private fun isStarFirework(stack: ItemStack): Boolean {
        // 1. アイテムが花火ロケットか確認
        val fireworkRocketItem = Registries.ITEM.get(Identifier.of("minecraft:firework_rocket"))
        if (stack.item != fireworkRocketItem) return false

        // 2. ペイロード（花火の星）のコンポーネントを取得
        val firework = stack.get(DataComponentTypes.FIREWORKS)
        // ペイロードが null でない、かつ、花火の星リストが空でないことを確認
        return firework != null && !firework.explosions.isEmpty()
    }

    /**
     * インベントリ（バックパックとホットバー）から最初の星付き花火を検索する。
     */
    private fun findFirstStarFirework(): InventoryIndex? {
        // バックパック (0-26)
        for (i in 0 until 27) {
            val index = InventoryIndex.Backpack(i)
            val stack = InventoryManager.get(index)
            if (isStarFirework(stack)) return index
        }
        // ホットバー (0-8)
        for (i in 0 until 9) {
            val index = InventoryIndex.Hotbar(i)
            val stack = InventoryManager.get(index)
            if (isStarFirework(stack)) return index
        }
        return null
    }

    // --- 👆 ここまで追加された花火ロジック関連のプライベート関数 ---

    override fun render2d(graphics2D: Graphics2D) {
        GunnerRenderer.renderInfo(graphics2D)
    }

    override fun render3d(graphics3D: Graphics3D) {
        GunnerRenderer.renderOrbit(graphics3D)
    }
}
