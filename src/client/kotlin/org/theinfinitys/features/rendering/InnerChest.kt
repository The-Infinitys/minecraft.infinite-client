package org.theinfinitys.features.rendering

import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.InfiniteClient
import org.theinfinitys.settings.InfiniteSetting

class InnerChest : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<InfiniteSetting<*>> = listOf()
    var shouldCancelScreen: Boolean = false
    var targetBlock: BlockEntity? = null
    var stackData: HashMap<BlockPos, MutableList<ItemStack>> = hashMapOf()
    private var timer = 0

    override fun tick() {
        if (timer <= 0) {
            val client = MinecraftClient.getInstance() ?: return
            val player = client.player ?: return
            val world = client.world ?: return
            val clientCommonNetworkHandler = client.networkHandler ?: return
            // 1. raycastの最大距離を設定 (例: 5.0ブロック)
            val maxDistance = 5.0
            // 2. 視線先の情報（HitResult）を取得
            val hitResult = player.raycast(maxDistance, 0.0f, false)
            // 3. 衝突結果をチェックし、ブロックの場合の処理
            if (hitResult.type == HitResult.Type.BLOCK) {
                val blockHitResult = hitResult as BlockHitResult
                val blockPos = blockHitResult.blockPos

                // 4. BlockEntityを取得し、インベントリを持つブロックかどうかをチェック
                when (val blockEntity = world.getBlockEntity(blockPos)) {
                    is ChestBlockEntity -> {
                        if (client.currentScreen == null) {
                            clientCommonNetworkHandler.sendPacket(
                                PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, blockHitResult, 0),
                            )
                            targetBlock = blockEntity
                            shouldCancelScreen = true
                            timer = 20
                        }
                        stackData[blockEntity.pos]?.forEach { InfiniteClient.log("$it") }
                    }
                }
            }
        } else {
            timer--
        }
    }

    fun handleChestContents(
        syncId: Int,
        items: MutableList<ItemStack>,
    ) {
        if (targetBlock != null) {
            stackData[(targetBlock as BlockEntity).pos] = items
            targetBlock = null
        }
    }
}
