package org.infinite.features.rendering.sensory.esp

import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.entity.state.LivingEntityRenderState
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.infinite.features.rendering.sensory.ExtraSensory
import org.infinite.libs.graphics.Graphics3D // Graphics3D をインポート
import org.infinite.libs.graphics.render.RenderUtils
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

object MobEsp {
    private fun livingEntities(): List<LivingEntity> {
        // 現在のワールドに存在する、プレイヤー自身ではないLivingEntityのリストを取得
        val client = MinecraftClient.getInstance()
        val world = client.world

        return world
            ?.entities
            ?.filter {
                // LivingEntity かつ プレイヤー自身ではない
                it is LivingEntity && it !is PlayerEntity
            }?.map {
                it as LivingEntity
            }
            ?: return emptyList()
    }

    /**
     * Graphics3D を利用してMobエンティティのアウトラインを描画します。
     *
     * @param graphics3d 描画コンテキスト
     */
    fun render(
        graphics3d: Graphics3D,
        value: ExtraSensory.Method, // Graphics3D を引数として受け取る
    ) {
        if (value == ExtraSensory.Method.OutLine) return
        // Graphics3D から tickProgress を取得
        val tickProgress = graphics3d.tickProgress
        val expand = 0.05 // 描画するBoxをわずかに拡張

        val mobs = livingEntities()
        val renderBoxes =
            mobs.map {
                RenderUtils.ColorBox(
                    mobColor(it),
                    mobBox(it, tickProgress)
                        .expand(expand), // Boxを拡張
                )
            }

        // Graphics3D のラッパーメソッドを利用し、MatrixStack の管理とフラッシュを Graphics3D に任せる
        graphics3d.renderLinedColorBoxes(renderBoxes, true)
    }

    /**
     * モブの種別に基づいて色を決定する
     */
    private fun mobColor(entity: LivingEntity): Int =
        when (entity) {
            is HostileEntity ->
                org.infinite.InfiniteClient
                    .theme()
                    .colors.redAccentColor // 敵対モブ -> 赤
            is PassiveEntity ->
                org.infinite.InfiniteClient
                    .theme()
                    .colors.greenAccentColor // 友好モブ -> 緑
            // 中立モブ、あるいはどちらにも分類されないモブ -> 黄
            else ->
                org.infinite.InfiniteClient
                    .theme()
                    .colors.yellowAccentColor
        }

    /**
     * モブの現在の描画位置に基づいてBoxを取得する
     */
    private fun mobBox(
        entity: LivingEntity,
        tickProgress: Float,
    ): Box {
        if (entity.isRemoved) return entity.boundingBox

        val offset: Vec3d =
            mobPos(
                entity,
                tickProgress,
            ).subtract(entity.entityPos)
        return entity.boundingBox.offset(offset)
    }

    /**
     * tickProgress (partialTicks) を使用して、モブの補間された位置を計算する
     */
    private fun mobPos(
        entity: LivingEntity,
        partialTicks: Float,
    ): Vec3d {
        if (entity.isRemoved) return entity.entityPos

        // MathHelper.lerp を使用して位置を補間
        val x: Double = MathHelper.lerp(partialTicks.toDouble(), entity.lastRenderX, entity.x)
        val y: Double = MathHelper.lerp(partialTicks.toDouble(), entity.lastRenderY, entity.y)
        val z: Double = MathHelper.lerp(partialTicks.toDouble(), entity.lastRenderZ, entity.z)
        return Vec3d(x, y, z)
    }

    fun handleRenderState(
        entity: MobEntity,
        state: LivingEntityRenderState,
        tickProgress: Float,
        ci: CallbackInfo,
    ) {
        state.outlineColor = mobColor(entity)
    }
}
