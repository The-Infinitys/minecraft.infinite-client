package org.theinfinitys.features.rendering.detailinfo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.BarrelBlockEntity
import net.minecraft.block.entity.BlastFurnaceBlockEntity
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BrewingStandBlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.block.entity.FurnaceBlockEntity
import net.minecraft.block.entity.HopperBlockEntity
import net.minecraft.block.entity.LootableContainerBlockEntity
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.block.entity.SmokerBlockEntity
import net.minecraft.block.enums.ChestType
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.predicate.entity.EntityPredicates
import net.minecraft.registry.Registries
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.infinite.graphics.Graphics2D
import org.theinfinitys.settings.InfiniteSetting
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.sqrt

// InventoryData, InventoryType, FurnaceData, BrewingDataの定義は変更なし
data class InventoryData(
    val type: InventoryType,
    val items: List<ItemStack>,
)

enum class InventoryType {
    CHEST,
    FURNACE,
    HOPPER,
    GENERIC,
    BREWING,
}

data class FurnaceData(
    var litTimeRemaining: Int = 0,
    var litTotalTime: Int = 0,
    var cookingTimeSpent: Int = 0,
    var cookingTotalTime: Int = 200,
    val inventory: DefaultedList<ItemStack> = DefaultedList.ofSize(3, ItemStack.EMPTY),
)

data class BrewingData(
    var brewTime: Int = 0,
    var fuel: Int = 0,
    val inventory: DefaultedList<ItemStack> = DefaultedList.ofSize(5, ItemStack.EMPTY),
)

class DetailInfo : ConfigurableFeature(initialEnabled = false) {
    private val furnaceProgressData: MutableMap<BlockPos, FurnaceData> = mutableMapOf()
    private val brewingProgressData: MutableMap<BlockPos, BrewingData> = mutableMapOf()
    private val scannedInventoryData: MutableMap<String, MutableMap<BlockPos, InventoryData>> = mutableMapOf()

    fun handleFurnaceProgress(
        syncId: Int,
        pos: BlockPos,
        propertyId: Int,
        value: Int,
    ) {
        val data = furnaceProgressData.getOrPut(pos) { FurnaceData() }
        when (propertyId) {
            0 -> data.litTimeRemaining = value
            1 -> data.litTotalTime = value
            2 -> data.cookingTimeSpent = value
            3 -> data.cookingTotalTime = value
        }
    }

    fun handleBrewingProgress(
        syncId: Int,
        pos: BlockPos,
        propertyId: Int,
        value: Int,
    ) {
        val data = brewingProgressData.getOrPut(pos) { BrewingData() }
        when (propertyId) {
            0 -> data.brewTime = value
            1 -> data.fuel = value
        }
    }

    fun getFurnaceData(pos: BlockPos): FurnaceData = furnaceProgressData[pos] ?: FurnaceData()

    fun getBrewingData(pos: BlockPos): BrewingData = brewingProgressData[pos] ?: BrewingData()

    fun findCrosshairTarget(
        camera: Entity,
        blockInteractionRange: Double,
        entityInteractionRange: Double,
    ): HitResult {
        var d = max(blockInteractionRange, entityInteractionRange)
        var e = MathHelper.square(d)
        val vec3d = camera.getCameraPosVec(1f)
        val hitResult = camera.raycast(d, 1f, false)
        val f = hitResult.getPos().squaredDistanceTo(vec3d)
        if (hitResult.type != HitResult.Type.MISS) {
            e = f
            d = sqrt(f)
        }

        val vec3d2 = camera.getRotationVec(1f)
        val vec3d3 = vec3d.add(vec3d2.x * d, vec3d2.y * d, vec3d2.z * d)
        val box = camera.boundingBox.stretch(vec3d2.multiply(d)).expand(1.0, 1.0, 1.0)
        val entityHitResult = ProjectileUtil.raycast(camera, vec3d, vec3d3, box, EntityPredicates.CAN_HIT, e)
        return if (entityHitResult != null && entityHitResult.getPos().squaredDistanceTo(vec3d) < f) {
            entityHitResult
        } else {
            hitResult
        }
    }

    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.BooleanSetting("BlockInfo", "ブロック情報を表示します。", true),
            InfiniteSetting.BooleanSetting("InnerChest", "チェストの中身も取得します。", true),
            InfiniteSetting.BooleanSetting("EntityInfo", "エンティティ情報を表示します。", true),
            InfiniteSetting.IntSetting("PaddingTop", "上からの余白", 0, 0, 100),
            InfiniteSetting.FloatSetting("Reach", "情報を表示する再長距離", 20f, 10f, 100f),
            InfiniteSetting.IntSetting("Width", "ウィジェットの幅を設定します。", 50, 25, 100),
        )

    var shouldCancelScanScreen: Boolean = false
    var scanTargetBlockEntity: BlockEntity? = null
    var expectedScreenType: ScreenHandlerType<*>? = null
    var scanTimer = 0

    var targetDetail: TargetDetail? = null
    var isTargetInReach: Boolean = false

    sealed class TargetDetail(
        val pos: BlockPos?,
        val name: String,
    ) {
        class BlockDetail(
            val block: Block,
            pos: BlockPos,
        ) : TargetDetail(pos, block.toString())

        class EntityDetail(
            val entity: Entity,
            pos: BlockPos,
            name: String,
        ) : TargetDetail(pos, name)
    }

    override fun tick() {
        targetDetail = null
        isTargetInReach = true

        val client = MinecraftClient.getInstance() ?: return
        val world = client.world ?: return
        val dimension = getDimensionKey()
        if (!scannedInventoryData.containsKey(dimension)) {
            loadData(dimension)
        }
        val clientCommonNetworkHandler = client.networkHandler ?: return
        var hitResult = client.crosshairTarget ?: return
        if (hitResult.type == HitResult.Type.MISS) {
            val entity: Entity? = client.cameraEntity
            if (entity != null) {
                if (client.world != null && client.player != null) {
                    val reach = getSetting("Reach")?.value as? Double ?: 20.0
                    hitResult = findCrosshairTarget(entity, reach, reach)
                    isTargetInReach = false
                }
            }
        }
        when (hitResult.type) {
            HitResult.Type.ENTITY -> {
                if (getSetting("EntityInfo")?.value == true) {
                    val entityHitResult = hitResult as EntityHitResult
                    val entity = entityHitResult.entity
                    val entityPos = entity.blockPos
                    val entityName = entity.type.name.string
                    targetDetail = TargetDetail.EntityDetail(entity, entityPos, entityName)
                }
            }

            HitResult.Type.BLOCK -> {
                if (getSetting("BlockInfo")?.value == true) {
                    val blockHitResultCasted = hitResult as BlockHitResult
                    val blockPos = blockHitResultCasted.blockPos
                    val blockState = world.getBlockState(blockPos)
                    val blockEntity = world.getBlockEntity(blockPos)

                    targetDetail = TargetDetail.BlockDetail(blockState.block, blockPos)

                    if (blockEntity is LootableContainerBlockEntity ||
                        blockEntity is FurnaceBlockEntity ||
                        blockEntity is SmokerBlockEntity ||
                        blockEntity is BlastFurnaceBlockEntity ||
                        blockEntity is BrewingStandBlockEntity ||
                        blockEntity is EnderChestBlockEntity
                    ) {
                        if (scanTimer <= 0) {
                            if (getSetting("InnerChest")?.value == true) {
                                if (client.currentScreen == null) {
                                    // Set expected screen type based on block entity
                                    expectedScreenType =
                                        when (blockEntity) {
                                            is ChestBlockEntity -> {
                                                val chestType = blockState.get(ChestBlock.CHEST_TYPE)
                                                if (chestType ==
                                                    ChestType.SINGLE
                                                ) {
                                                    ScreenHandlerType.GENERIC_9X3
                                                } else {
                                                    ScreenHandlerType.GENERIC_9X6
                                                }
                                            }

                                            is BarrelBlockEntity -> ScreenHandlerType.GENERIC_9X3
                                            is ShulkerBoxBlockEntity -> ScreenHandlerType.SHULKER_BOX
                                            is EnderChestBlockEntity -> ScreenHandlerType.GENERIC_9X3
                                            is HopperBlockEntity -> ScreenHandlerType.HOPPER
                                            is FurnaceBlockEntity -> ScreenHandlerType.FURNACE
                                            is SmokerBlockEntity -> ScreenHandlerType.SMOKER
                                            is BlastFurnaceBlockEntity -> ScreenHandlerType.BLAST_FURNACE
                                            is BrewingStandBlockEntity -> ScreenHandlerType.BREWING_STAND
                                            else -> null // Should not happen with the check above, but for safety
                                        }

                                    if (expectedScreenType != null) {
                                        clientCommonNetworkHandler.sendPacket(
                                            PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, blockHitResultCasted, 0),
                                        )
                                        scanTargetBlockEntity = blockEntity
                                        shouldCancelScanScreen = true
                                        scanTimer = 20
                                    }
                                }
                            }
                        } else {
                            scanTimer--
                        }
                    } else {
                        val dimension = getDimensionKey()
                        scannedInventoryData[dimension]?.remove(blockPos)
                    }
                }
            }

            else -> {
                // 何もヒットしなかった場合
            }
        }
    }

    /**
     * 修正点:
     * - ChestBlockEntity以外でも、scannedInventoryDataにデータを格納し、
     * - scanTargetBlockEntityをnullにリセットし、
     * - 画面を閉じるパケットを送信するように共通化しました。
     */
    fun handleChestContents(
        syncId: Int,
        items: MutableList<ItemStack>,
    ) {
        if (scanTargetBlockEntity != null) {
            shouldCancelScanScreen = false
            val entity = scanTargetBlockEntity as BlockEntity
            val dimension = getDimensionKey()

            val inventoryType: InventoryType
            val containerSize: Int

            when (entity) {
                is FurnaceBlockEntity, is SmokerBlockEntity, is BlastFurnaceBlockEntity -> {
                    inventoryType = InventoryType.FURNACE
                    containerSize = 3 // 燃料、材料、出力
                }

                is HopperBlockEntity -> {
                    inventoryType = InventoryType.HOPPER
                    containerSize = 5
                }

                is BrewingStandBlockEntity -> {
                    inventoryType = InventoryType.BREWING
                    containerSize = 5 // 3ポーション、1材料、1燃料
                }

                is ChestBlockEntity, is ShulkerBoxBlockEntity, is BarrelBlockEntity, is EnderChestBlockEntity -> {
                    inventoryType = InventoryType.CHEST
                    // GENERIC_9X3: 27スロット, GENERIC_9X6: 54スロット, SHULKER_BOX: 27スロット
                    // items.sizeはコンテナ + プレイヤーインベントリ (36) なので、コンテナサイズは items.size - 36
                    containerSize = items.size - 36
                }

                else -> {
                    inventoryType = InventoryType.GENERIC
                    containerSize = 0 // 未知のコンテナはスキップ
                }
            }

            // プレイヤーインベントリを除いたコンテナの中身のみを取得
            val containerItems = items.take(containerSize)

            if (!scannedInventoryData.containsKey(dimension)) {
                scannedInventoryData[dimension] = mutableMapOf()
            }

            // チェストの結合処理
            if (entity is ChestBlockEntity) {
                val world = MinecraftClient.getInstance().world ?: return
                val blockState = world.getBlockState(entity.pos)
                if (blockState.block is ChestBlock && blockState.contains(ChestBlock.CHEST_TYPE)) {
                    val chestType = blockState.get(ChestBlock.CHEST_TYPE)
                    if (chestType != ChestType.SINGLE) {
                        val facing = blockState.get(ChestBlock.FACING)
                        val otherOffset =
                            if (chestType == ChestType.RIGHT) facing.rotateYClockwise() else facing.rotateYCounterclockwise()
                        val otherPos = entity.pos.offset(otherOffset)
                        val otherState = world.getBlockState(otherPos)

                        // ダブルチェストの場合、2つのBlockPosに分割して保存
                        if (otherState.block == Blocks.CHEST && otherState.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                            val singleChestSize = 27
                            val firstHalf = containerItems.take(singleChestSize)
                            val secondHalf = containerItems.drop(singleChestSize)

                            val leftPos = if (chestType == ChestType.RIGHT) entity.pos else otherPos
                            val rightPos = if (chestType == ChestType.LEFT) entity.pos else otherPos

                            scannedInventoryData[dimension]!![leftPos] = InventoryData(inventoryType, firstHalf)
                            scannedInventoryData[dimension]!![rightPos] = InventoryData(inventoryType, secondHalf)
                        } else {
                            // シングルチェストとして保存
                            scannedInventoryData[dimension]!![entity.pos] = InventoryData(inventoryType, containerItems)
                        }
                    } else {
                        // シングルチェストとして保存
                        scannedInventoryData[dimension]!![entity.pos] = InventoryData(inventoryType, containerItems)
                    }
                } else {
                    // その他のチェストとして保存 (例: Trapped Chest)
                    scannedInventoryData[dimension]!![entity.pos] = InventoryData(inventoryType, containerItems)
                }
            } else if (containerSize > 0) {
                // チェスト以外のコンテナとして保存
                scannedInventoryData[dimension]!![entity.pos] = InventoryData(inventoryType, containerItems)
            }

            // 全てのコンテナのスキャン終了処理
            scanTargetBlockEntity = null
            MinecraftClient.getInstance().networkHandler?.sendPacket(CloseHandledScreenC2SPacket(syncId))
        }
    }

    fun getChestContents(pos: BlockPos): InventoryData? {
        val dimension = getDimensionKey()
        return scannedInventoryData[dimension]?.get(pos)
    }

    private fun getDataDirectory(dimension: String? = null): Path {
        val gameDir = FabricLoader.getInstance().gameDir
        val dataName = "inventories"
        // Use a default dimension key or the provided one
        val dimensionKey = dimension ?: getDimensionKey()
        return gameDir
            .resolve("infinite")
            .resolve("data")
            .resolve("single_player")
            .resolve("新規ワールド")
            .resolve(dimensionKey)
            .resolve(dataName)
    }

    // Helper function to get a clean dimension key
    private fun getDimensionKey(): String {
        val world = MinecraftClient.getInstance().world ?: return "minecraft_overworld"
        val dimensionId = world.registryKey.value
        return dimensionId?.toString()?.replace(":", "_") ?: "minecraft_overworld"
    }

    private fun getChunkKey(pos: BlockPos): String {
        val chunkX = pos.x shr 4
        val chunkZ = pos.z shr 4
        return "${chunkX}_$chunkZ"
    }

    @Serializable
    data class SerializableItemStack(
        val itemId: String,
        val count: Int,
    )

    @Serializable
    data class SerializableInventoryData(
        val type: String,
        val items: List<SerializableItemStack>,
    )

    @Serializable
    data class ChunkInventoryData(
        val inventories: Map<String, SerializableInventoryData>,
    )

    fun saveData() {
        val json = Json { prettyPrint = true }

        for ((dimension, invDataMap) in scannedInventoryData) {
            val dataDir = getDataDirectory(dimension).toFile()
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }

            val chunks = mutableMapOf<String, MutableMap<String, SerializableInventoryData>>()
            for ((pos, invData) in invDataMap) {
                val chunkKey = getChunkKey(pos)
                val posKey = "${pos.x}_${pos.y}_${pos.z}"
                val serialItems =
                    invData.items.map { SerializableItemStack(Registries.ITEM.getId(it.item).toString(), it.count) }
                val serialData = SerializableInventoryData(invData.type.name, serialItems)
                if (!chunks.containsKey(chunkKey)) {
                    chunks[chunkKey] = mutableMapOf()
                }
                chunks[chunkKey]!![posKey] = serialData
            }

            for ((chunkKey, invs) in chunks) {
                val chunkFile = dataDir.resolve("chunk_$chunkKey.json")
                val chunkData = ChunkInventoryData(invs)
                val jsonString = json.encodeToString(ChunkInventoryData.serializer(), chunkData)
                chunkFile.writeText(jsonString)
            }
        }
    }

    fun loadData(dimension: String) {
        val json = Json { prettyPrint = true }

        val dataDir = getDataDirectory(dimension).toFile()
        if (!dataDir.exists()) {
            return
        }

        scannedInventoryData[dimension] = mutableMapOf()
        dataDir.listFiles()?.filter { it.name.startsWith("chunk_") && it.name.endsWith(".json") }?.forEach { file ->
            val jsonString = file.readText()
            val chunkData = json.decodeFromString(ChunkInventoryData.serializer(), jsonString)
            for ((posKey, serialData) in chunkData.inventories) {
                val (x, y, z) = posKey.split("_").map { it.toInt() }
                val pos = BlockPos(x, y, z)
                val items =
                    serialData.items.map {
                        val item = Registries.ITEM.get(Identifier.of(it.itemId))
                        ItemStack(item, it.count)
                    }
                val invType = InventoryType.valueOf(serialData.type)
                scannedInventoryData[dimension]!![pos] = InventoryData(invType, items)
            }
        }
    }

    override fun render2d(graphics2D: Graphics2D) {
        DetailInfoRenderer.render(graphics2D, MinecraftClient.getInstance() ?: return, this)
    }

    override fun stop() {
        saveData()
    }
}
