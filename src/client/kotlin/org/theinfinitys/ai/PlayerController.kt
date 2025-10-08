package org.theinfinitys.ai

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.option.KeyBinding
import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2

/**
 * PlayerInterfaceからの指示を受け取り、クライアントのキー入力をシミュレートして
 * プレイヤーエンティティを操作するコントローラークラス。
 * 実際の操作ロジックは全てここに集約されます。
 */
class PlayerController(
    val client: MinecraftClient,
) {
    private val player: ClientPlayerEntity = client.player!!

    fun getPlayer(): ClientPlayerEntity = player

    /**
     * 指定された目標位置に向かって、プレイヤーの移動と向きを制御します。
     * @param targetPos 移動目標のワールド座標
     */
    fun moveTo(targetPos: Vec3d) {
        // 向きの制御
        val dx = targetPos.x - player.x
        val dz = targetPos.z - player.z
        val yawToTarget = atan2(dz, dx)
        val targetYaw = (yawToTarget * 180.0 / Math.PI).toFloat() - 90.0f
        player.yaw = targetYaw

        // 移動キーのシミュレーション
        setKey(client.options.forwardKey, true)

        // ジャンプのシミュレーション (簡易的)
        // 簡易的な足場の上昇（PlayerEntity.stepHeightを超える場合）
        if (targetPos.y > player.y + player.stepHeight + 0.1) {
            setKey(client.options.jumpKey, true)
        } else {
            setKey(client.options.jumpKey, false)
        }
    }

    /**
     * 指定されたワールド座標に向かってプレイヤーの視線（YawとPitch）を即座に設定します。
     * @param targetPos 視線を向ける目標のワールド座標
     */
    fun lookAt(targetPos: Vec3d) {
        // デフォルトで視線から目標を見る
        lookAt(targetPos, EntityAnchorArgumentType.EntityAnchor.EYES)
    }

    /**
     * EntityAnchorArgumentType.EntityAnchorに基づき、指定された目標位置に向かって
     * プレイヤーの視線（YawとPitch）を即座に設定します。
     *
     * @param targetPos 視線を向ける目標のワールド座標
     * @param anchor プレイヤーのどの位置から目標を見るか（例: EYES/FEET）
     */
    fun lookAt(
        targetPos: Vec3d,
        anchor: EntityAnchorArgumentType.EntityAnchor,
    ) {
        if (player.isDead) return

        val startPos: Vec3d =
            when (anchor) {
                EntityAnchorArgumentType.EntityAnchor.FEET -> Vec3d(player.x, player.y, player.z) // プレイヤーの足元
                EntityAnchorArgumentType.EntityAnchor.EYES -> player.getCameraPosVec(1.0f) // プレイヤーの視線
            }

        val dx = targetPos.x - startPos.x
        val dy = targetPos.y - startPos.y
        val dz = targetPos.z - startPos.z

        val horizontalDistance = kotlin.math.sqrt(dx * dx + dz * dz)

        // Yawの計算
        val yawToTarget = atan2(dz, dx)
        val targetYaw = (yawToTarget * 180.0 / Math.PI).toFloat() - 90.0f

        // Pitchの計算
        val pitchToTarget = atan2(dy, horizontalDistance)
        val targetPitch = (-pitchToTarget * 180.0 / Math.PI).toFloat()

        player.yaw = targetYaw
        player.pitch = targetPitch
    }

    /**
     * すべての移動キー入力を解除し、プレイヤーの操作を停止します。
     */
    fun stopMovementControl() {
        setKey(client.options.forwardKey, false)
        setKey(client.options.backKey, false)
        setKey(client.options.leftKey, false)
        setKey(client.options.rightKey, false)
        setKey(client.options.jumpKey, false)
    }

    /**
     * ブロック破壊キーの制御をシミュレートします。
     * @param isBreaking 破壊を開始/継続するかどうか
     */
    fun setBreakingBlock(isBreaking: Boolean) {
        // attackKeyは攻撃とブロック破壊の両方に使用されます
        setKey(client.options.attackKey, isBreaking)
    }

    // KeyBindingのセット操作をラップするヘルパーメソッド
    private fun setKey(
        key: KeyBinding,
        pressed: Boolean,
    ) {
        key.let {
            // KeyBinding.setKeyPressedを呼び出し、キーの状態を設定
            KeyBinding.setKeyPressed(it.defaultKey, pressed)
        }
    }
}
