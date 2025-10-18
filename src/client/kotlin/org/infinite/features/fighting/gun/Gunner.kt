package org.infinite.features.fighting.gun

import net.minecraft.client.MinecraftClient
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.CrossbowItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.inventory.InventoryManager
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting

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
    private val fireMode: FeatureSetting.EnumSetting<FireMode> =
        FeatureSetting.EnumSetting(
            "FireMode",
            "The firing mode for shooting.",
            FireMode.FULL_AUTO,
            FireMode.entries,
        )
    private val fastReload: FeatureSetting.BooleanSetting =
        FeatureSetting.BooleanSetting(
            "FastReload",
            "If enabled, reloading become fast",
            false,
        )
    private val changeMode: FeatureSetting.EnumSetting<ChangeMode> =
        FeatureSetting.EnumSetting(
            "ChangeMode",
            "How to toggle reload and shot",
            ChangeMode.Fixed,
            ChangeMode.entries,
        )
    private val additionalInterval: FeatureSetting.IntSetting =
        FeatureSetting.IntSetting("AdditionalInterval", "Additional interval for full-auto", 3, 0, 10)
    override val settings: List<FeatureSetting<*>> = listOf(fireMode, fastReload, changeMode, additionalInterval)
    var state: GunnerState = GunnerState.IDLE
    var mode: GunnerMode = GunnerMode.RELOAD
    override val level = FeatureLevel.CHEAT

    fun gunnerCount(): Int {
        // クロスボウで使用可能なアイテムのIdentifierを取得
        val arrowItem = Registries.ITEM.get(Identifier.of("minecraft:arrow"))
        val tippedArrowItem = Registries.ITEM.get(Identifier.of("minecraft:tipped_arrow"))
        val spectralArrowItem = Registries.ITEM.get(Identifier.of("minecraft:spectral_arrow"))
        val fireworkItem = Registries.ITEM.get(Identifier.of("minecraft:firework_rocket"))
        val playerInventory = InfiniteClient.playerInterface.inventory

        // 各アイテムの個数を合計
        val arrowCount = playerInventory.count(arrowItem)
        val tippedArrowCount = playerInventory.count(tippedArrowItem)
        val spectralArrowCount = playerInventory.count(spectralArrowItem)
        val fireworkCount = playerInventory.count(fireworkItem)

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
        switchMode()
        val manager = InfiniteClient.playerInterface.inventory
        when (mode) {
            GunnerMode.SHOT -> {
                val mainHandItem = manager.get(InventoryManager.Other.MAIN_HAND)
                if (isLoadedCrossbow(mainHandItem)) {
                    state = GunnerState.READY
                } else {
                    state = GunnerState.IDLE
                    val loadedCrossbow = findFirstLoadedCrossbow()
                    val readyToSet =
                        (fireMode.value == FireMode.FULL_AUTO && intervalCount == 0) ||
                            (fireMode.value == FireMode.SEMI_AUTO && !InfiniteClient.playerInterface.options.useKey.isPressed)
                    if (loadedCrossbow != null && readyToSet) {
                        intervalCount = additionalInterval.value
                        manager.swap(InventoryManager.Other.MAIN_HAND, loadedCrossbow)
                    } else {
                        if (intervalCount > 0) intervalCount--
                        val emptySlot = manager.findFirstEmptyBackpackSlot()
                        if (emptySlot != null) {
                            manager.swap(InventoryManager.Other.MAIN_HAND, emptySlot)
                        }
                    }
                }
            }

            GunnerMode.RELOAD -> {
                state = GunnerState.IDLE
                val mainHandItem = manager.get(InventoryManager.Other.MAIN_HAND)
                if (!isUnloadedCrossbow(mainHandItem)) {
                    val loadedCrossbow = findFirstUnloadedCrossbow()
                    if (loadedCrossbow != null) {
                        manager.swap(InventoryManager.Other.MAIN_HAND, loadedCrossbow)
                    } else {
                        val emptySlot = manager.findFirstEmptyBackpackSlot()
                        if (emptySlot != null) {
                            manager.swap(InventoryManager.Other.MAIN_HAND, emptySlot)
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

    fun totalCrossbows(): Int = InfiniteClient.playerInterface.inventory.count(getCrossbowItem())

    fun loadedCrossbows(): Int {
        var count = 0
        // ホットバー
        for (i in 0 until 9) {
            val stack = InfiniteClient.playerInterface.inventory.get(InventoryManager.Hotbar(i))
            if (isLoadedCrossbow(stack)) count++
        }

        // バックパック
        for (i in 0 until 27) {
            val stack = InfiniteClient.playerInterface.inventory.get(InventoryManager.Backpack(i))
            if (isLoadedCrossbow(stack)) count++
        }
        // オフハンド
        val offHand = InfiniteClient.playerInterface.inventory.get(InventoryManager.Other.OFF_HAND)
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

    private fun findFirstLoadedCrossbow(): InventoryManager.InventoryIndex? {
        for (i in 0 until 27) {
            val stack = InfiniteClient.playerInterface.inventory.get(InventoryManager.Backpack(i))
            if (isLoadedCrossbow(stack)) {
                return InventoryManager.Backpack(i)
            }
        }

        for (i in 0 until 9) {
            val stack = InfiniteClient.playerInterface.inventory.get(InventoryManager.Hotbar(i))
            if (isLoadedCrossbow(stack)) {
                return InventoryManager.Hotbar(i)
            }
        }
        val offHand = InfiniteClient.playerInterface.inventory.get(InventoryManager.Other.OFF_HAND)
        if (isLoadedCrossbow(offHand)) {
            return InventoryManager.Other.OFF_HAND
        }

        return null
    }

    private fun findFirstUnloadedCrossbow(): InventoryManager.InventoryIndex? {
        for (i in 0 until 27) {
            val stack = InfiniteClient.playerInterface.inventory.get(InventoryManager.Backpack(i))
            if (stack.item is CrossbowItem && !isLoadedCrossbow(stack)) {
                return InventoryManager.Backpack(i)
            }
        }
        for (i in 0 until 9) {
            val stack = InfiniteClient.playerInterface.inventory.get(InventoryManager.Hotbar(i))
            if (stack.item is CrossbowItem && !isLoadedCrossbow(stack)) {
                return InventoryManager.Hotbar(i)
            }
        }
        val offHand = InfiniteClient.playerInterface.inventory.get(InventoryManager.Other.OFF_HAND)
        if (offHand.item is CrossbowItem && !isLoadedCrossbow(offHand)) {
            return InventoryManager.Other.OFF_HAND
        }

        return null
    }

    override fun render3d(graphics3D: Graphics3D) {
        GunnerRenderer.renderOrbit(graphics3D)
    }
}
