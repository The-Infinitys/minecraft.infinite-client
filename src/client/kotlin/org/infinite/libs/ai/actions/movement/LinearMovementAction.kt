package org.infinite.libs.ai.actions.movement

import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.BlockView
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.libs.client.control.ControllerInterface
import kotlin.math.abs
import kotlin.math.atan2

class LinearMovementAction(
    val pos: Vec3d,
    val movementRange: Double = 1.0,
    val heightRange: Int? = null,
    val onSuccessAction: () -> Unit = {},
    val onFailureAction: () -> Unit = {},
) : AiAction() {
    val stepHeight: Float = player?.stepHeight ?: 0.6f
    val safeFallHeight: Int = player?.safeFallDistance ?: 3
    val jumpHeight: Float = 1.25f // 確実なジャンプ高さを設定
    private var lastPos: Vec3d? = null
    private var currentStackTicks = 0
    private val stackDetectionThreshold = 20
    private val minMovementDistanceSq = 0.001 * 0.001
    private var isStuck: Boolean = false

    // 💡 Auto Jumpの元の状態を保持するプロパティ
    private var originalAutoJump: Boolean = false
    // ----------------------------------------

    override fun onFailure() {
        // 終了時にAuto Jumpを元の状態に戻す
        options.autoJump.value = originalAutoJump
        onFailureAction()
    }

    override fun onSuccess() {
        // 終了時にAuto Jumpを元の状態に戻す
        options.autoJump.value = originalAutoJump
        onSuccessAction()
    }

    override fun state(): AiActionState {
        // スタック検知と終了時のAuto Jumpリセット
        if (isStuck) {
            options.autoJump.value = originalAutoJump
            return AiActionState.Failure
        }

        val tPos = pos
        val pPos =
            playerPos ?: run {
                options.autoJump.value = originalAutoJump // 失敗前にリセット
                return AiActionState.Failure
            }
        val dx = abs(tPos.x - pPos.x)
        val dz = abs(tPos.z - pPos.z)
        val inRangeXZ = (dx <= movementRange) && (dz <= movementRange)
        val dy = abs(tPos.y - pPos.y)
        val inRangeY = heightRange == null || dy <= heightRange

        return if (inRangeY && inRangeXZ) {
            // 成功する直前にAuto Jumpを元の状態に戻す
            options.autoJump.value = originalAutoJump
            AiActionState.Success
        } else {
            AiActionState.Progress
        }
    }

    override fun tick() {
        val pPos = playerPos ?: return
        val world = world ?: return

        // 💡 ティック開始時、最初に一度だけAuto Jumpを強制有効化
        if (currentStackTicks == 0 && lastPos == null) {
            originalAutoJump = options.autoJump.value // 元の設定を保存
            options.autoJump.value = true // Auto Jumpを有効化
        }

        // 1. スタック検知ロジック
        if (lastPos != null) {
            val distanceSq = pPos.squaredDistanceTo(lastPos!!)

            if (distanceSq < minMovementDistanceSq) {
                currentStackTicks++
            } else {
                currentStackTicks = 0
            }

            if (currentStackTicks >= stackDetectionThreshold) {
                isStuck = true
                ControllerInterface.release(options.forwardKey)
                ControllerInterface.release(options.jumpKey)
                return
            }
        }
        lastPos = pPos

        // 2. ターゲットへの向きの維持
        player?.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, pos)
        player?.pitch = 0f

        // 3. 次のブロックの安全検証
        if (checkSafetyAndMove(pPos, world)) {
            // 安全が確認された場合のみ移動キーを押す
            ControllerInterface.press(options.forwardKey)
        } else {
            // 安全でない場合、移動とジャンプを停止し、スタックとして処理するためにティック数を増やす
            ControllerInterface.release(options.forwardKey)
            ControllerInterface.release(options.jumpKey)
            currentStackTicks++
            return
        }
    }

    /**
     * 次のブロックへの移動の安全性をチェックし、必要に応じてジャンプを行う
     * @return 移動可能かつ安全であれば true
     */
    private fun checkSafetyAndMove(
        pPos: Vec3d,
        world: BlockView,
    ): Boolean {
        // ターゲット方向（水平面）の角度を計算 (ラジアン)
        val targetDirection = atan2(pos.z - pPos.z, pos.x - pPos.x)

        // プレイヤーの目の前 0.5 ブロック先の位置を計算
        val checkX = pPos.x + kotlin.math.cos(targetDirection) * 0.5
        val checkZ = pPos.z + kotlin.math.sin(targetDirection) * 0.5

        // プレイヤーの目の前、足元の高さにあるブロック
        val frontGroundPos = BlockPos.ofFloored(checkX, pPos.y - 0.5, checkZ).up()
        val frontBlockState = world.getBlockState(frontGroundPos)

        // --- A. 上り坂/障害物のジャンプ検証 ---

        // 目の前のブロックの当たり判定を取得
        val collisionShape = frontBlockState.getCollisionShape(world, frontGroundPos)

        // 1. 当たり判定が空でない（=移動を阻害する）かをチェック (階段、柵などに対応)
        val isBlockingMovement = !collisionShape.isEmpty

        // 2. 胸の高さのブロックが空気か（頭をぶつけないか）
        val chestBlockPos = frontGroundPos.up()
        val isChestFree = world.getBlockState(chestBlockPos).isAir

        if (isBlockingMovement && isChestFree) {
            // 障害物の上面の相対高さ (0.0～1.0)
            val blockMaxY = collisionShape.getMax(Direction.Axis.Y)

            // 💡 葉ブロックなどの薄い当たり判定を無視する
            if (blockMaxY < 0.1f) {
                // 非常に薄い当たり判定を持つブロックなので、無視して先に進む
                // 落下検証へ
            } else {
                // 障害物の上面の絶対高さ
                val obstacleTopY = frontGroundPos.y + blockMaxY

                // 障害物の相対的な高さ (プレイヤーの足元 (pPos.y) からの垂直距離)
                val obstacleHeightFromFeet = obstacleTopY - pPos.y

                // 登る必要がある高さがステップ高さを超えているか
                if (obstacleHeightFromFeet > stepHeight) {
                    // ジャンプ高さを超えるか
                    if (obstacleHeightFromFeet <= jumpHeight) {
                        // **ジャンプで超えられる**
                        ControllerInterface.press(options.jumpKey)
                        return true // 移動可能
                    } else {
                        // **ジャンプで超えられない**
                        ControllerInterface.release(options.jumpKey)
                        return false // 移動停止
                    }
                }
                // else: ステップで登れる高さなのでジャンプは不要
            }
        }

        // 障害物が無い、またはステップで登れる高さ、または無視されたブロックの場合はジャンプキーをリセット
        ControllerInterface.release(options.jumpKey)

        // --- B. 下り坂/崖の落下検証 ---

        // 目の前のブロックの足元が空気ブロックであるか（崖かどうかのチェック）
        val nextFootBlockPos = frontGroundPos.down()
        var fallDistance = 0
        var currentCheckPos = nextFootBlockPos

        // 落下地点を見つけるために下に探索
        while (currentCheckPos.y > (pPos.y - safeFallHeight - 2)) {
            val blockState = world.getBlockState(currentCheckPos)
            if (blockState.isAir) {
                // 空気ブロックが見つかった
                fallDistance++
                currentCheckPos = currentCheckPos.down()
            } else {
                // 地面が見つかった
                break
            }
        }

        if (fallDistance > 0) {
            // 落下距離を計算
            val actualFallHeight = fallDistance

            if (actualFallHeight > safeFallHeight) {
                // **安全落下高さを超える**
                return false // 移動停止
            }
        }

        // 上りも下りも安全（またはジャンプで対処済み）
        return true
    }
}
