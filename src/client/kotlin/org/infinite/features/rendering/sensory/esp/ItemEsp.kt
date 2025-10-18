package org.infinite.features.rendering.sensory.esp

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.ItemEntity
import net.minecraft.util.Rarity
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.infinite.libs.graphics.Graphics3D // Graphics3D をインポート
import org.infinite.libs.graphics.render.RenderUtils

object ItemEsp {
    private fun itemEntities(): List<ItemEntity> {
        return MinecraftClient
            .getInstance()
            .world
            ?.entities
            ?.filter {
                it is ItemEntity
            }?.map {
                it as ItemEntity
            }
            ?: return emptyList()
    }

    /**
     * Graphics3D を利用してアイテムエンティティのアウトラインを描画します。
     *
     * @param graphics3d 描画コンテキスト
     */
    fun render(graphics3d: Graphics3D) {
        val tickProgress = graphics3d.tickProgress
        val expand = 0.1

        val items = itemEntities()
        val renderBoxes =
            items.map {
                RenderUtils.LinedColorBox(
                    rarityColor(it),
                    itemBox(it, tickProgress)
                        .offset(0.0, expand, 0.0)
                        .expand(expand),
                )
            }

        // Graphics3D のラッパーメソッドを利用し、MatrixStack の管理を Graphics3D に任せる
        // vcp.draw() は graphics3d.render() で最後に実行されるため、ここでは不要
        graphics3d.renderLinedColorBoxes(renderBoxes, true)
    }

    private fun rarityColor(entity: ItemEntity): Int =
        when (entity.stack.rarity) {
            Rarity.COMMON -> 0xFFFFFFFF
            Rarity.UNCOMMON -> 0xFFFFFF00
            Rarity.RARE -> 0xFF00FFFF
            Rarity.EPIC -> 0xFFFF00FF
            else -> 0xFFFFFFFF
        }.toInt()

    private fun itemBox(
        entity: ItemEntity,
        tickProgress: Float,
    ): Box {
        // When an entity is removed, it stops moving and its lastRenderX/Y/Z
        // values are no longer updated.
        if (entity.isRemoved) return entity.boundingBox
        val offset: Vec3d =
            itemPos(
                entity,
                tickProgress,
            ).subtract(entity.entityPos)
        return entity.boundingBox.offset(offset)
    }

    private fun itemPos(
        entity: ItemEntity,
        partialTicks: Float,
    ): Vec3d {
        if (entity.isRemoved) return entity.entityPos

        val x: Double = MathHelper.lerp(partialTicks.toDouble(), entity.lastRenderX, entity.x)
        val y: Double = MathHelper.lerp(partialTicks.toDouble(), entity.lastRenderY, entity.y)
        val z: Double = MathHelper.lerp(partialTicks.toDouble(), entity.lastRenderZ, entity.z)
        return Vec3d(x, y, z)
    }
}
