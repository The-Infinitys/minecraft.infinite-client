package org.infinite.features.fighting.berserk

import net.minecraft.entity.LivingEntity
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.fighting.lockon.LockOn
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.ai.actions.movement.EntityPosMovementAction
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.libs.client.control.ControllerInterface
import org.infinite.settings.FeatureSetting

// 状態管理用の列挙型
enum class BerserkerState {
    IDLE, // 待機中
    MOVING_TO_TARGET, // AIがターゲットに接近中
    ATTACKING, // ターゲットに到達し、連続攻撃中
    COMPLETED, // 動作完了（一時停止または無効化準備）
}

class Berserker : ConfigurableFeature() {
    private val attackRangeSetting = FeatureSetting.FloatSetting("AttackRange", 7.5f, 5.0f, 10.0f)

    override val settings: List<FeatureSetting<*>> = listOf(attackRangeSetting)

    override val depends: List<Class<out ConfigurableFeature>> = listOf(LockOn::class.java)

    // --- 内部状態 (Internal State) ---
    private var currentState: BerserkerState = BerserkerState.IDLE
    private var aiMovementAction: EntityPosMovementAction? = null
    private val lockOn: LockOn?
        get() = InfiniteClient.getFeature(LockOn::class.java)
    private val targetEntity: LivingEntity?
        get() = lockOn?.lockedEntity

    override fun disabled() {
        aiMovementAction?.onFailure() // AI移動タスクをキャンセル
        aiMovementAction = null
        currentState = BerserkerState.IDLE
    }

    override fun tick() {
        client.player ?: return
        when (currentState) {
            BerserkerState.IDLE -> startMovement() // IDLEから開始
            BerserkerState.MOVING_TO_TARGET -> monitorMovement()
            BerserkerState.ATTACKING -> performContinuousAttack(targetEntity!!)
            BerserkerState.COMPLETED -> disable() // 完了したら無効化
        }
    }

    // ----------------------------------------------------------------------
    // 1. AI移動の開始
    // ----------------------------------------------------------------------
    private fun startMovement() {
        val target = targetEntity ?: return
        // AI移動アクションを作成
        aiMovementAction =
            EntityPosMovementAction(
                target = target,
                radius = (player?.entityInteractionRange ?: 3.5) / 2.0,
                height = 2, // ターゲットと同じ高さか、少し上
                // AIが目的地に到達した時の処理
                onSuccessAction = {
                    // 移動完了後、攻撃フェーズへ
                    currentState = BerserkerState.ATTACKING
                    aiMovementAction = null // アクションを破棄
                },
                // AIが失敗した時の処理 (経路が見つからないなど)
                onFailureAction = {
                    // 失敗した場合はフィーチャーを無効化
                    InfiniteClient.error("Berserker: AI移動に失敗しました。無効化します。")
                    disable()
                },
                // Baritoneが既に動作中か、ターゲットに到達したかをチェック
                stateRegister = {
                    if (target.isAlive) AiAction.AiActionState.Progress else AiAction.AiActionState.Success // ターゲットが死んだら成功として終了
                },
            )
        AiInterface.add(aiMovementAction!!) // AiActionの基底クラスに`register()`メソッドがあることを仮定
        currentState = BerserkerState.MOVING_TO_TARGET
    }

    // ----------------------------------------------------------------------
    // 2. AI移動の監視
    // ----------------------------------------------------------------------
    private fun monitorMovement() {
        aiMovementAction?.tick() // AiActionのtickを呼び出して状態を更新

        when (aiMovementAction?.state()) {
            AiAction.AiActionState.Success -> {
                aiMovementAction?.onSuccess()
                // currentState は onSuccessAction によって ATTACKING に変更される
            }

            AiAction.AiActionState.Failure -> {
                aiMovementAction?.onFailure()
                disable() // 失敗したら無効化
            }

            else -> {
                // ターゲットとの距離が近すぎる場合は、移動を中断して攻撃フェーズへ移行
                val player = client.player ?: return
                if (player.distanceTo(targetEntity) <= attackRangeSetting.value) {
                    aiMovementAction?.onSuccess() // 成功として終了させ、ATTACKINGへ移行
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // 3. 連続クリティカル攻撃
    // ----------------------------------------------------------------------
    private fun performContinuousAttack(target: LivingEntity) {
        // 攻撃可能範囲内のチェック
        val player = player ?: return
        val distance = player.distanceTo(target)
        if (distance > player.entityInteractionRange) {
            ControllerInterface.press(options.forwardKey)
        }
        if (distance > attackRangeSetting.value) {
            currentState = BerserkerState.MOVING_TO_TARGET
            return
        }
        if (player.getAttackCooldownProgress(0.0f) >= 1.0f) {
            interactionManager?.attackEntity(player, target)
        }
    }
}
