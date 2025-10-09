package org.theinfinitys.features.rendering

import drawBorder
import net.minecraft.block.Block
import net.minecraft.block.entity.BlastFurnaceBlockEntity
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.FurnaceBlockEntity
import net.minecraft.block.entity.HopperBlockEntity
import net.minecraft.block.entity.LootableContainerBlockEntity
import net.minecraft.block.entity.SmokerBlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ColorHelper
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting

// --- データ構造 ---
data class InventoryData(
    val type: InventoryType,
    val items: List<ItemStack>,
)

enum class InventoryType {
    CHEST, // 標準的なチェスト (27スロット) やその他の小型インベントリ
    FURNACE, // かまど、溶鉱炉、燻製器 (3スロット)
    HOPPER, // ホッパー (5スロット)
    GENERIC, // その他のインベントリ
}
// --- データ構造ここまで ---

// クラス名を DetailInfo に変更
class DetailInfo : ConfigurableFeature(initialEnabled = false) {
    // BlockInfo, EntityInfoの設定を追加
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.BooleanSetting("BlockInfo", "ブロック情報を表示します。", true),
            InfiniteSetting.BooleanSetting("InnerChest", "チェストの中身も取得します。", true),
            InfiniteSetting.BooleanSetting("EntityInfo", "エンティティ情報を表示します。", true),
            InfiniteSetting.IntSetting("PaddingTop", "上からの余白", 0, 0, 100),
            InfiniteSetting.IntSetting("Width", "ウィジェットの幅を設定します。", 50, 25, 100),
        )

    var shouldCancelScanScreen: Boolean = false
    var scanTargetBlockEntity: BlockEntity? = null
    var scannedInventoryData: HashMap<BlockPos, InventoryData> = hashMapOf()
    private var scanTimer = 0

    var targetDetail: TargetDetail? = null

    // --- 追加: リーチ判定結果を保持するフィールド ---
    var isTargetInReach: Boolean = false
    // ---------------------------------------------

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
        isTargetInReach = false // 毎ティックリセット

        val client = MinecraftClient.getInstance() ?: return
        val world = client.world ?: return
        val clientCommonNetworkHandler = client.networkHandler ?: return
        val hitResult = client.crosshairTarget ?: return
        val player = client.player ?: return

        // 目の位置からの距離を計算
        val hitDistance = hitResult.pos.distanceTo(player.eyePos)
        // クライアント側の標準的なリーチ距離を設定 (クリエイティブ: 6.0, サバイバル: 4.5)
        // 厳密には属性に依存しますが、ここでは一般的な値を採用
        val reachDistance = if (player.abilities.creativeMode) 6.0 else 4.5

        if (hitDistance <= reachDistance) {
            isTargetInReach = true
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

                    // LootableContainerBlockEntity (チェストなど) および Furnace系, Hopper系をチェック
                    if (blockEntity is LootableContainerBlockEntity ||
                        blockEntity is FurnaceBlockEntity ||
                        blockEntity is SmokerBlockEntity ||
                        blockEntity is BlastFurnaceBlockEntity ||
                        blockEntity is HopperBlockEntity
                    ) {
                        if (scanTimer <= 0) {
                            if (getSetting("InnerChest")?.value == true) {
                                // リーチ外でもインベントリをスキャンするために、ヒットしたブロックの情報を利用してパケットを送信
                                // 注意: サーバー側はリーチチェックを行うため、実際にリーチ外のインベントリは開けません。
                                // このロジックは、クライアントが**開こうとする**モーションを利用して、サーバーが送り返す**コンテナ情報**を捕まえることを意図しています。
                                if (client.currentScreen == null) {
                                    clientCommonNetworkHandler.sendPacket(
                                        PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, blockHitResultCasted, 0),
                                    )
                                    scanTargetBlockEntity = blockEntity
                                    shouldCancelScanScreen = true
                                    scanTimer = 20
                                }
                            }
                        } else {
                            scanTimer--
                        }
                    } else {
                        // 対象ブロックがコンテナでなくなった場合、インベントリ情報をクリアする（任意）
                        scannedInventoryData.remove(blockPos)
                    }
                }
            }

            else -> {
                // 何もヒットしなかった場合
            }
        }
    }

    fun handleChestContents(
        syncId: Int,
        items: MutableList<ItemStack>,
    ) {
        if (scanTargetBlockEntity != null) {
            shouldCancelScanScreen = false
            val entity = scanTargetBlockEntity as BlockEntity
            var itemStacks = items as List<ItemStack>
            val inventoryType =
                when (entity) {
                    is FurnaceBlockEntity, is SmokerBlockEntity, is BlastFurnaceBlockEntity -> InventoryType.FURNACE
                    is HopperBlockEntity -> InventoryType.HOPPER
                    is ChestBlockEntity -> {
                        // チェストの場合、アイテムリストが大型チェストのサイズ(54)やそれ以上で送られてくる可能性があるため、標準の27スロットに絞る (大型チェストの場合は最大54)
                        // ここでは、LootableContainerBlockEntityだがChestBlockEntityとして、27スロットを想定する
                        if (itemStacks.size > 54) {
                            // 念の為、過剰なスロットをカット
                            itemStacks = itemStacks.take(54)
                        }
                        InventoryType.CHEST
                    }

                    else -> InventoryType.GENERIC
                }

            scannedInventoryData[entity.pos] = InventoryData(inventoryType, itemStacks)
            scanTargetBlockEntity = null
            MinecraftClient.getInstance().networkHandler?.sendPacket(CloseHandledScreenC2SPacket(syncId))
        }
    }
}

// --- DetailInfoRenderer ---

object DetailInfoRenderer {
    // UIの描画定数
    private const val BORDER_WIDTH = 2
    private const val BAR_HEIGHT = 4
    private const val BAR_PADDING = 5
    private val INNER_COLOR = ColorHelper.getArgb(192, 0, 0, 0) // 背景色 (半透明の黒)

    // --- 追加: リーチ外の場合に使用する灰色 ---
    private val OUT_OF_REACH_COLOR = ColorHelper.getArgb(255, 150, 150, 150) // 明るめの灰色
    // -----------------------------------------

    // HPバーや破壊バー用のグラデーションを計算するヘルパー関数
    private fun getGradientColor(progress: Float): Int {
        val clampedProgress = progress.coerceIn(0.0f, 1.0f)
        val r: Int
        val g: Int
        val b: Int

        // 0.0 ~ 0.5: 赤から黄色 (Rは固定、Gが増加)
        if (clampedProgress <= 0.5f) {
            val p = clampedProgress * 2.0f
            r = 255
            g = (255 * p).toInt()
            b = 0
        } else {
            // 0.5 ~ 1.0: 黄色から水色 (Rが減少し、Bが増加)
            val p = (clampedProgress - 0.5f) * 2.0f
            r = (255 * (1.0f - p)).toInt()
            g = 255
            b = (255 * p).toInt()
        }

        return ColorHelper.getArgb(255, r, g, b)
    }

    // --- 追加: リーチに応じて色を切り替える関数 ---

    /**
     * ターゲットがリーチ内の場合は虹色、リーチ外の場合は灰色を返します。
     * @param isInReach ターゲットがリーチ内かどうか
     */
    private fun getFeatureColor(isInReach: Boolean): Int =
        if (isInReach) {
            getRainbowColor()
        } else {
            OUT_OF_REACH_COLOR
        }
    // ---------------------------------------------

    /**
     * UIの背景と枠を描画します。
     */
    private fun drawBackgroundAndBorder(
        context: DrawContext,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        featureColor: Int, // 修正: rainbowColor から featureColor に
    ) {
        // 1. 内部の背景を塗りつぶし
        context.fill(startX, startY, endX, endY, INNER_COLOR)

        // 2. 虹色/灰色の枠を描画
        context.fill(startX, startY, endX, startY + BORDER_WIDTH, featureColor) // 上枠
        context.fill(startX, endY - BORDER_WIDTH, endX, endY, featureColor) // 下枠
        context.fill(startX, startY + BORDER_WIDTH, startX + BORDER_WIDTH, endY - BORDER_WIDTH, featureColor) // 左枠
        context.fill(endX - BORDER_WIDTH, startY + BORDER_WIDTH, endX, endY - BORDER_WIDTH, featureColor) // 右枠
    }

    // ---------------------------------------------------------------------------------------------
    // インベントリ詳細描画ロジック (UIの高さを動的に計算するために、必要な高さを返す)
    // ---------------------------------------------------------------------------------------------

    /**
     * インベントリの内容を描画し、描画に必要な**総高さ**を返します。
     * (アイテムアイコンの描画は行わず、高さ計算のみを行う。)
     */
    private fun calculateInventoryHeight(
        client: MinecraftClient,
        inventoryData: InventoryData,
        uiWidth: Int,
    ): Int {
        val font = client.textRenderer
        val padding = 5
        val itemPadding = 2
        val slotSize = 16
        var requiredHeight = 0

        if (inventoryData.items.isEmpty()) return 0

        // "Inventory: (...)" の行
        requiredHeight += font.fontHeight + 2

        // アイテム描画領域の計算
        val maxItemsPerRow: Int
        val fixedRows: Int? // null の場合は自動計算

        when (inventoryData.type) {
            InventoryType.FURNACE -> {
                maxItemsPerRow = 3
                fixedRows = 1
            }

            InventoryType.HOPPER -> {
                maxItemsPerRow = 5
                fixedRows = 1
            }

            InventoryType.CHEST -> {
                maxItemsPerRow = 9
                fixedRows = null // 大型チェスト(54)も考慮し、自動計算を優先
            }

            InventoryType.GENERIC -> {
                maxItemsPerRow =
                    ((uiWidth - 2 * BORDER_WIDTH - 2 * padding) / (slotSize + itemPadding)).coerceAtLeast(1)
                fixedRows = null
            }
        }

        val totalItems = inventoryData.items.size
        val rowsNeeded =
            if (totalItems == 0) {
                0
            } else {
                val calculatedRows = (totalItems + maxItemsPerRow - 1) / maxItemsPerRow
                if (fixedRows != null) calculatedRows.coerceAtMost(fixedRows) else calculatedRows
            }

        // アイテムスロットの高さ
        if (rowsNeeded > 0) {
            requiredHeight += rowsNeeded * slotSize + (rowsNeeded - 1).coerceAtLeast(0) * itemPadding
            requiredHeight += padding // アイテム表示後の下部パディング
        }

        return requiredHeight
    }

    /**
     * インベントリの内容を描画し、描画後の次のY座標を返します。
     */
    private fun drawInventoryContents(
        context: DrawContext,
        client: MinecraftClient,
        inventoryData: InventoryData,
        startX: Int,
        currentY: Int,
        uiWidth: Int,
        // --- 修正: isTargetInReach を引数に追加 ---
        isTargetInReach: Boolean,
        // -------------------------------------
    ): Int {
        val font = client.textRenderer
        val padding = 5
        val itemPadding = 2
        val slotSize = 16
        var drawingY = currentY

        // 1. インベントリ情報のヘッダー
        val headerText = Text.literal("Inventory: (${inventoryData.type})")
        val headerX = startX + BORDER_WIDTH + padding
        context.drawText(
            font,
            headerText,
            headerX,
            drawingY,
            0xFFFFFFFF.toInt(),
            true,
        )
        drawingY += font.fontHeight + 2

        // アイテムがない場合はここで終了
        if (inventoryData.items.isEmpty()) {
            return drawingY + padding
        }

        // 2. レイアウト設定 (calculateInventoryHeight と同じロジック)
        val maxItemsPerRow: Int
        val fixedRows: Int?

        // UIの描画領域の幅 (両端の枠線とパディングを除く)
        val innerContentWidth = uiWidth - 2 * BORDER_WIDTH - 2 * padding

        when (inventoryData.type) {
            InventoryType.FURNACE -> {
                maxItemsPerRow = 3
                fixedRows = 1
            }

            InventoryType.HOPPER -> {
                maxItemsPerRow = 5
                fixedRows = 1
            }

            InventoryType.CHEST -> {
                maxItemsPerRow = 9
                fixedRows = null
            }

            InventoryType.GENERIC -> {
                // GENERICの場合、表示領域から計算
                maxItemsPerRow =
                    (innerContentWidth / (slotSize + itemPadding)).coerceAtLeast(1)
                fixedRows = null
            }
        }

        // 3. アイテムの行と列を計算
        val totalItems = inventoryData.items.size
        val rowsNeeded =
            if (totalItems == 0) {
                0
            } else {
                val calculatedRows = (totalItems + maxItemsPerRow - 1) / maxItemsPerRow
                if (fixedRows != null) calculatedRows.coerceAtMost(fixedRows) else calculatedRows
            }

        val itemsStartY = drawingY
        var rowCount = 0

        // 4. アイテムを中央に寄せて描画
        for (row in 0 until rowsNeeded) {
            val startItemIndex = row * maxItemsPerRow
            val itemsInCurrentRow = (totalItems - startItemIndex).coerceAtMost(maxItemsPerRow)

            // その行のアイテム表示に必要な合計幅を計算
            val totalRowWidth = itemsInCurrentRow * slotSize + (itemsInCurrentRow - 1).coerceAtLeast(0) * itemPadding

            // 中央寄せのための開始X座標を計算
            // (内側の幅 - 行の合計幅) / 2 + startX + 枠線 + パディング
            val rowStartX = startX + BORDER_WIDTH + padding + (innerContentWidth - totalRowWidth) / 2

            // アイテムの描画Y座標
            val itemDrawingY = itemsStartY + row * (slotSize + itemPadding)

            for (col in 0 until itemsInCurrentRow) {
                val index = startItemIndex + col
                // 範囲外アクセスを避けるためにチェック
                if (index >= inventoryData.items.size) continue

                val itemStack = inventoryData.items[index]

                val itemX = rowStartX + col * (slotSize + itemPadding)
                // --- 修正: リーチ判定に基づいて色を取得 ---
                val featureColor = getFeatureColor(isTargetInReach)
                context.drawBorder(itemX, itemDrawingY, slotSize, slotSize, featureColor)
                // -------------------------------------
                context.drawItem(itemStack, itemX, itemDrawingY)
                val itemCount = itemStack.count
                if (itemCount > 1) {
                    context.drawText(
                        font,
                        itemCount.toString(),
                        itemX + slotSize / 2,
                        itemDrawingY + slotSize / 2,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }
            }
            rowCount = row + 1
        }

        // 描画が完了したY座標 + アイテムの高さ + パディングを次の描画開始Y座標とする
        return itemsStartY + rowCount * (slotSize + itemPadding) + padding
    }

    // ---------------------------------------------------------------------------------------------
    // ブロック詳細描画ロジック (高さ計算とコンテンツ描画の両方を担う)
    // ---------------------------------------------------------------------------------------------

    /**
     * ブロックコンテンツを描画し、描画に必要な**総高さ**を返します。
     */
    private fun drawBlockContent(
        context: DrawContext,
        client: MinecraftClient,
        detail: DetailInfo.TargetDetail.BlockDetail,
        feature: DetailInfo,
        startX: Int,
        startY: Int,
        uiWidth: Int,
        drawOnly: Boolean, // true の場合、実際に描画。false の場合、高さのみを計算。
        // --- 修正: isTargetInReach を引数に追加 ---
        isTargetInReach: Boolean,
        // -------------------------------------
    ): Int {
        val font = client.textRenderer
        val padding = 5
        val iconSize = 18
        val iconX = startX + BORDER_WIDTH + padding
        val iconY = startY + BORDER_WIDTH + padding
        val textX = iconX + iconSize + padding

        // 1. アイコンと名前/IDの行
        var requiredHeight = BORDER_WIDTH + padding + iconSize + padding

        // 描画フェーズ
        if (drawOnly) {
            // 1. アイコンの描画
            val blockIconStack = ItemStack(detail.block)
            context.drawItemWithoutEntity(blockIconStack, iconX, iconY, 0)

            // 2. 名前とIDの描画
            val blockId = Registries.BLOCK.getId(detail.block).toString()
            val infoName = detail.block.name.string

            context.drawText(font, Text.literal(infoName), textX, iconY + 1, 0xFFFFFFFF.toInt(), true)
            val nameWidth = font.getWidth(infoName)
            context.drawText(
                font,
                Text.literal("($blockId)"),
                textX + nameWidth + 5,
                iconY + 1,
                ColorHelper.getArgb(192, 255, 255, 255),
                true,
            )
        }

        // 3. チェストの中身の計算/描画
        val inventoryData = feature.scannedInventoryData[detail.pos]
        var contentY = startY + requiredHeight // アイコン行の下からのY座標

        if (inventoryData != null && inventoryData.items.isNotEmpty()) {
            val inventoryHeight = calculateInventoryHeight(client, inventoryData, uiWidth)
            requiredHeight += inventoryHeight

            if (drawOnly) {
                // --- 修正: drawInventoryContents に isTargetInReach を渡す ---
                contentY =
                    drawInventoryContents(context, client, inventoryData, startX, contentY, uiWidth, isTargetInReach)
                // --------------------------------------------------------
            }
        }

        // 4. Pos情報の行
        requiredHeight += font.fontHeight + 2 // PosTextの高さ + 少しのパディング

        if (drawOnly) {
            val infoPos = detail.pos
            val posText = "Pos: x=${infoPos?.x}, y=${infoPos?.y}, z=${infoPos?.z}"
            context.drawText(font, Text.literal(posText), iconX, contentY, 0xFFFFFFFF.toInt(), true)
            contentY += font.fontHeight + 2
        } else {
            contentY += font.fontHeight + 2
        }

        // 5. 枠線と下部パディング
        requiredHeight += BORDER_WIDTH + padding // 下部の境界線とパディング

        return requiredHeight
    }

    // ---------------------------------------------------------------------------------------------
    // エンティティ詳細描画ロジック (高さ計算とコンテンツ描画の両方を担う)
    // ---------------------------------------------------------------------------------------------

    /**
     * エンティティコンテンツを描画し、描画に必要な**総高さ**を返します。
     */
    private fun drawEntityContent(
        context: DrawContext,
        client: MinecraftClient,
        detail: DetailInfo.TargetDetail.EntityDetail,
        startX: Int,
        startY: Int,
        uiWidth: Int,
        drawOnly: Boolean, // true の場合、実際に描画。false の場合、高さのみを計算。
    ): Int {
        val font = client.textRenderer
        val padding = 5
        val iconX = startX + BORDER_WIDTH + padding
        val iconY = startY + BORDER_WIDTH + padding

        // 1. 名前/IDの行
        var requiredHeight =
            BORDER_WIDTH + padding + font.fontHeight + padding // TopPadding + TextHeight + BottomPadding

        if (drawOnly) {
            // 1. 名前とIDの描画
            val entityName = detail.entity.type.name.string
            val entityId = Registries.ENTITY_TYPE.getId(detail.entity.type).toString()

            context.drawText(font, Text.literal(entityName), iconX, iconY + 1, 0xFFFFFFFF.toInt(), true)

            val nameWidth = font.getWidth(entityName)
            context.drawText(
                font,
                Text.literal("($entityId)"),
                iconX + nameWidth + 5,
                iconY + 1,
                ColorHelper.getArgb(192, 255, 255, 255),
                true,
            )
        }

        // 2. HPバー領域の高さ
        val entity = detail.entity
        val hasBar = entity is LivingEntity // LivingEntityならHPバーを表示
        if (hasBar) {
            // TextHeight + TextPadding + BarHeight + BarPadding + Border
            requiredHeight += font.fontHeight + 2 + BAR_HEIGHT + BAR_PADDING
        }

        // 3. Pos情報の行 (エンティティなので常に表示)
        requiredHeight += font.fontHeight + 2 // PosTextの高さ + 少しのパディング

        var contentY = startY + requiredHeight - (font.fontHeight + 2) // PosTextのY座標

        if (drawOnly) {
            val infoPos = detail.entity.blockPos
            val posText = "Pos: x=${infoPos.x}, y=${infoPos.y}, z=${infoPos.z}"
            context.drawText(font, Text.literal(posText), iconX, contentY, 0xFFFFFFFF.toInt(), true)
        }

        // 4. 枠線と下部パディング
        requiredHeight += font.fontHeight + 2 + BORDER_WIDTH + padding // 下部の境界線とパディング

        return requiredHeight
    }

    /**
     * HPバーや破壊過程バーを描画します。
     * @param progress 進捗度 (0.0f から 1.0f)
     * @param infoText バーの上に表示するテキスト (破壊時間など)
     */
    private fun drawBar(
        context: DrawContext,
        startX: Int,
        endX: Int,
        endY: Int,
        progress: Float,
        infoText: Text,
    ) {
        val barY = endY - BORDER_WIDTH - BAR_HEIGHT - BAR_PADDING
        val barStartX = startX + BORDER_WIDTH + BAR_PADDING
        val barEndX = endX - BORDER_WIDTH - BAR_PADDING
        val barWidth = barEndX - barStartX
        val font = MinecraftClient.getInstance().textRenderer

        // 1. 進捗に応じてバーの幅を計算
        val fillWidth = (barWidth * progress).toInt()

        // 2. バーの背景（空き部分）を描画 (暗い半透明グレー)
        val barBackgroundColor = ColorHelper.getArgb(128, 50, 50, 50)
        context.fill(barStartX, barY, barEndX, barY + BAR_HEIGHT, barBackgroundColor)

        // 3. グラデーションの進捗部分を描画 (赤->水色)
        if (fillWidth > 0) {
            for (x in 0 until fillWidth) {
                val colorProgress = x.toFloat() / barWidth.toFloat()
                val color = getGradientColor(colorProgress)
                context.fill(barStartX + x, barY, barStartX + x + 1, barY + BAR_HEIGHT, color)
            }
        }

        // 4. 情報テキストを描画 (バーの左上)
        context.drawText(
            font,
            infoText,
            barStartX,
            barY - font.fontHeight - 2, // バーの上に配置
            0xFFFFFFFF.toInt(),
            true,
        )
    }

    /**
     * ブロックの破壊過程バーの上に残り時間を描画します。
     */
    private fun getBreakingTimeText(
        progress: Float,
        client: MinecraftClient,
    ): Text {
        val player = client.player ?: return Text.empty()
        val world = client.world ?: return Text.empty()
        val interactionManager = client.interactionManager ?: return Text.empty()

        val blockPos = interactionManager.currentBreakingPos
        val blockState = client.world?.getBlockState(blockPos)
        // calcBlockBreakingDelta は、そのブロックを破壊するのに必要な進行度（1.0fで破壊完了）を1ティックあたりで返す
        val destroySpeed = blockState?.calcBlockBreakingDelta(player, world, blockPos) ?: 0.0f

        if (destroySpeed <= 0.0001f) return Text.literal("Indestructible")

        val totalTicks = 1.0f / destroySpeed
        val remainingTicks = (1.0f - progress) * totalTicks
        val totalSeconds = totalTicks / 20.0f
        val remainingSeconds = remainingTicks / 20.0f

        val totalSecStr = "%.1f".format(totalSeconds)
        val remainingSecStr = "%.1f".format(remainingSeconds)

        return Text.literal("Time: $remainingSecStr / $totalSecStr s")
    }

    /**
     * 詳細情報UIを描画します。
     */
    fun render(
        context: DrawContext,
        client: MinecraftClient,
        detailInfoFeature: DetailInfo,
    ) {
        val detail = detailInfoFeature.targetDetail ?: return
        val interactionManager = client.interactionManager ?: return
        // --- 修正: リーチ判定結果を取得 ---
        val isTargetInReach = detailInfoFeature.isTargetInReach
        // --------------------------------

        val screenWidth = client.window.scaledWidth

        // --- 1. UIの幅と必要な高さを計算 ---
        val widthSetting = detailInfoFeature.getSetting("Width")?.value as? Int ?: return
        val startY = detailInfoFeature.getSetting("PaddingTop")?.value as? Int ?: return

        val uiWidth = (screenWidth * widthSetting / 100)
        val startX = (screenWidth / 2) - (uiWidth / 2)
        val endX = startX + uiWidth

        // **高さを動的に計算**
        val requiredHeight =
            when (detail) {
                is DetailInfo.TargetDetail.BlockDetail -> {
                    // drawOnly=falseで高さを計算
                    // --- 修正: isTargetInReach を渡す ---
                    drawBlockContent(
                        context,
                        client,
                        detail,
                        detailInfoFeature,
                        startX,
                        startY,
                        uiWidth,
                        drawOnly = false,
                        isTargetInReach = isTargetInReach,
                    )
                    // ----------------------------------
                }

                is DetailInfo.TargetDetail.EntityDetail -> {
                    // drawOnly=falseで高さを計算
                    drawEntityContent(context, client, detail, startX, startY, uiWidth, drawOnly = false)
                }
            }

        val endY = startY + requiredHeight

        // --- 修正: リーチ判定に基づいて色を取得 ---
        val featureColor = getFeatureColor(isTargetInReach)
        // --------------------------------------

        // --- 2. 背景と枠の描画 ---
        // --- 修正: featureColor を渡す ---
        drawBackgroundAndBorder(context, startX, startY, endX, endY, featureColor)
        // --------------------------------

        // --- 3. 詳細情報コンテンツとバーの描画 (drawOnly=trueで実際の描画) ---
        when (detail) {
            is DetailInfo.TargetDetail.BlockDetail -> {
                // --- 修正: isTargetInReach を渡す ---
                drawBlockContent(
                    context,
                    client,
                    detail,
                    detailInfoFeature,
                    startX,
                    startY,
                    uiWidth,
                    drawOnly = true,
                    isTargetInReach = isTargetInReach,
                )
                // ----------------------------------

                // 破壊バーの描画 (リーチ外でも破壊状態は表示)
                if (interactionManager.isBreakingBlock) {
                    val progress = interactionManager.currentBreakingProgress.coerceIn(0.0f, 1.0f)
                    val infoText = getBreakingTimeText(progress, client)
                    drawBar(context, startX, endX, endY, progress, infoText)
                }
            }

            is DetailInfo.TargetDetail.EntityDetail -> {
                drawEntityContent(context, client, detail, startX, startY, uiWidth, drawOnly = true)

                // HPバーの描画
                val entity = detail.entity
                if (entity is LivingEntity) {
                    val progress = entity.health / entity.maxHealth
                    val infoText = Text.literal("HP: ${"%.1f".format(entity.health)} / ${entity.maxHealth}")
                    drawBar(context, startX, endX, endY, progress, infoText)
                }
            }
        }
    }

    private fun getRainbowColor(): Int {
        val rainbowDuration = 6000L
        val colors =
            intArrayOf(
                0xFFFF0000.toInt(),
                0xFFFFFF00.toInt(),
                0xFF00FF00.toInt(),
                0xFF00FFFF.toInt(),
                0xFF0000FF.toInt(),
                0xFFFF00FF.toInt(),
                0xFFFF0000.toInt(),
            )
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime % rainbowDuration
        val progress = elapsedTime.toFloat() / rainbowDuration.toFloat()
        val numSegments = colors.size - 1
        val segmentLength = 1.0f / numSegments
        val currentSegmentIndex = (progress / segmentLength).toInt().coerceAtMost(numSegments - 1)
        val segmentProgress = (progress % segmentLength) / segmentLength
        val startColor = colors[currentSegmentIndex]
        val endColor = colors[currentSegmentIndex + 1]

        return ColorHelper.getArgb(
            255,
            (ColorHelper.getRed(startColor) * (1 - segmentProgress) + ColorHelper.getRed(endColor) * segmentProgress).toInt(),
            (ColorHelper.getGreen(startColor) * (1 - segmentProgress) + ColorHelper.getGreen(endColor) * segmentProgress).toInt(),
            (ColorHelper.getBlue(startColor) * (1 - segmentProgress) + ColorHelper.getBlue(endColor) * segmentProgress).toInt(),
        )
    }
}
