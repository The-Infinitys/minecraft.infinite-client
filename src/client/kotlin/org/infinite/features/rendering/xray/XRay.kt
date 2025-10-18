package org.infinite.features.rendering.xray

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.settings.FeatureSetting

enum class XRayMode {
    Normal,
    OnlyExposed,
}

class XRay : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.EnumSetting(
                "Method",
                "feature.rendering.xray.method.description",
                XRayMode.Normal, // 初期値はNormal
                XRayMode.entries.toList(), // すべてのオプションのリスト
            ),
            FeatureSetting.BlockListSetting(
                "ThroughBlockList",
                "feature.rendering.xray.throughblocklist.description",
                mutableListOf(
                    "minecraft:water",
                    "minecraft:lava",
                    "minecraft:chest",
                    "minecraft:trapped_chest",
                    "minecraft:ender_chest",
                    "minecraft:barrel",
                    "minecraft:shulker_box", // 各種シュルカーボックスは必要に応じて追加
                    "minecraft:white_shulker_box",
                    "minecraft:orange_shulker_box",
                    "minecraft:magenta_shulker_box",
                    "minecraft:light_blue_shulker_box",
                    "minecraft:yellow_shulker_box",
                    "minecraft:lime_shulker_box",
                    "minecraft:pink_shulker_box",
                    "minecraft:gray_shulker_box",
                    "minecraft:light_gray_shulker_box",
                    "minecraft:cyan_shulker_box",
                    "minecraft:purple_shulker_box",
                    "minecraft:blue_shulker_box",
                    "minecraft:brown_shulker_box",
                    "minecraft:green_shulker_box",
                    "minecraft:red_shulker_box",
                    "minecraft:black_shulker_box",
                    "minecraft:glass",
                    "minecraft:stained_glass", // 各種ステンドグラスは必要に応じて追加
                    "minecraft:glass_pane",
                    "minecraft:stained_glass_pane",
                ),
            ),
            FeatureSetting.BlockListSetting(
                "ExposedBlockList",
                "feature.rendering.xray.exposedblocklist.description",
                mutableListOf(
                    "minecraft:ancient_debris",
                    "minecraft:anvil",
                    "minecraft:beacon",
                    "minecraft:bone_block",
                    "minecraft:bookshelf",
                    "minecraft:brewing_stand",
                    "minecraft:chain_command_block",
                    "minecraft:chest", // ThroughBlockListにもあるが、ExposedBlockListにも残すことで、XRayが有効な時に描画されるようになる
                    "minecraft:clay",
                    "minecraft:coal_block",
                    "minecraft:coal_ore",
                    "minecraft:command_block",
                    "minecraft:copper_ore",
                    "minecraft:crafting_table",
                    "minecraft:deepslate_coal_ore",
                    "minecraft:deepslate_copper_ore",
                    "minecraft:deepslate_diamond_ore",
                    "minecraft:deepslate_emerald_ore",
                    "minecraft:deepslate_gold_ore",
                    "minecraft:deepslate_iron_ore",
                    "minecraft:deepslate_lapis_ore",
                    "minecraft:deepslate_redstone_ore",
                    "minecraft:diamond_block",
                    "minecraft:diamond_ore",
                    "minecraft:dispenser",
                    "minecraft:dropper",
                    "minecraft:emerald_block",
                    "minecraft:emerald_ore",
                    "minecraft:enchanting_table",
                    "minecraft:end_portal",
                    "minecraft:end_portal_frame",
                    "minecraft:ender_chest",
                    "minecraft:furnace",
                    "minecraft:glowstone",
                    "minecraft:gold_block",
                    "minecraft:gold_ore",
                    "minecraft:hopper",
                    "minecraft:iron_block",
                    "minecraft:iron_ore",
                    "minecraft:ladder",
                    "minecraft:lapis_block",
                    "minecraft:lapis_ore",
                    "minecraft:lava",
                    "minecraft:lodestone",
                    "minecraft:mossy_cobblestone",
                    "minecraft:nether_gold_ore",
                    "minecraft:nether_portal",
                    "minecraft:nether_quartz_ore",
                    "minecraft:raw_copper_block",
                    "minecraft:raw_gold_block",
                    "minecraft:raw_iron_block",
                    "minecraft:redstone_block",
                    "minecraft:redstone_ore",
                    "minecraft:repeating_command_block",
                    "minecraft:spawner",
                    "minecraft:suspicous_sand",
                    "minecraft:tnt",
                    "minecraft:torch",
                    "minecraft:trapped_chest",
                    "minecraft:water",
                ),
            ),
        )

    // ... (enabled/disabled関数は変更なし)
    override fun enabled() {
        // Trigger world re-render when XRay is enabled
        MinecraftClient.getInstance().worldRenderer.reload()
    }

    override fun disabled() {
        // Trigger world re-render when XRay is disabled
        MinecraftClient.getInstance().worldRenderer.reload()
    }

    // 設定リストを取得する補助関数
    private fun getThroughBlockList(): Set<String> = (settings[1] as FeatureSetting.BlockListSetting).value.toSet()

    private fun getExposedBlockList(): Set<String> = (settings[2] as FeatureSetting.BlockListSetting).value.toSet()

    /**
     * ブロックがXRayで描画されるべきかどうか（全体として）を判断します。
     * この関数は、ブロックがリストに明示的に含まれているかのみをチェックします。
     */
    fun isVisible(
        block: Block,
        pos: BlockPos,
    ): Boolean {
        val blockId = Registries.BLOCK.getId(block).toString()
        val throughList = getThroughBlockList()
        val exposedList = getExposedBlockList()

        // 描画リストに明示的に含まれているブロックのみを対象とする
        if (throughList.contains(blockId)) {
            return true
        }
        if (exposedList.contains(blockId)) {
            return true
        }

        // どのリストにも含まれていない場合、描画対象外とする
        return false
    }

    // ★ 補助関数: ブロックIDがXRayのターゲット（ThroughまたはExposed）であるかを判定
    private fun isXRayTarget(blockId: String): Boolean {
        val throughList = getThroughBlockList()
        val exposedList = getExposedBlockList()
        return throughList.contains(blockId) || exposedList.contains(blockId)
    }

    // ★ 補助関数: ブロックIDがNormalモードのターゲットであるかを判定
    private fun isNormalModeTarget(blockId: String): Boolean = isXRayTarget(blockId)

    // ★ 補助関数: ブロックIDがOnlyExposedモードのターゲットであるかを判定
    private fun isOnlyExposedModeTarget(blockId: String): Boolean {
        val throughList = getThroughBlockList()
        val exposedList = getExposedBlockList()
        return throughList.contains(blockId) || exposedList.contains(blockId)
    }

    /**
     * 描画されるブロックの特定の面を描画するかどうかを判断します。
     * Normalモード: ブロック自体がExposed/Throughに含まれ、かつ隣接ブロックがExposed/Throughに含まれていなければ描画。
     * OnlyExposedモード: Throughブロックは常に描画。Exposedブロックは隣接ブロックが空気なら描画。
     * AntiAntiXRayモード: OnlyExposedに加えて、Replacedブロックも隣接ブロックが空気なら描画。
     * * ★ 追加の最適化: 隣接ブロックが同じXRay対象カテゴリに属する場合、描画をスキップする（カリング）。
     */
    fun shouldDrawSide(
        blockState: BlockState,
        blockPos: BlockPos,
        side: Direction,
        neighborState: BlockState,
    ): Boolean? {
        if (!isEnabled()) return null

        val block = blockState.block
        val blockId = Registries.BLOCK.getId(block).toString()
        val method = getSetting("Method")?.value as? XRayMode ?: return null

        val throughList = getThroughBlockList()
        val exposedList = getExposedBlockList()

        val neighborBlockId = Registries.BLOCK.getId(neighborState.block).toString()
        val isNeighborAir = neighborBlockId == "minecraft:air"

        // 1. まず、現在のブロックがXRay対象リストに載っているかチェック (装飾ブロックの除外)
        if (!isVisible(block, blockPos)) {
            return false // リストにないブロックは描画しない (falseを返すことで、Mixinで !shouldDrawSide のロジックが適用されるのを避ける)
        }

        // 2. メインの描画ロジックとカリング

        return when (method) {
            XRayMode.Normal -> {
                val isCurrentTarget = isNormalModeTarget(blockId)
                val isNeighborTarget = isNormalModeTarget(neighborBlockId)

                if (isCurrentTarget) {
                    !isNeighborTarget
                } else {
                    false
                }
            }

            XRayMode.OnlyExposed -> {
                val isCurrentTarget = isOnlyExposedModeTarget(blockId)
                val isNeighborTarget = isOnlyExposedModeTarget(neighborBlockId)

                if (!isCurrentTarget) return false // 再チェック

                // ★ 同種カテゴリのカリング: 隣接ブロックが同じXRay対象（Through/Exposed）なら描画しない
                if (isNeighborTarget) {
                    return false
                }

                // 露出ロジック（カリングチェックを通過した場合に適用）
                if (throughList.contains(blockId)) {
                    true // Throughは常に描画 **<-- 修正済み**
                } else if (exposedList.contains(blockId)) {
                    isNeighborAir // Exposedは隣接が空気なら描画 **<-- 修正済み**
                } else {
                    false
                }
            }
        }
    }
}
