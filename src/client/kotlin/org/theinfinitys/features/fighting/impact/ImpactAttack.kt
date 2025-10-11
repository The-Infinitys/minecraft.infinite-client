package org.theinfinitys.features.fighting.impact

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.MathHelper
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting
import kotlin.math.sqrt

class ImpactAttack : ConfigurableFeature(initialEnabled = false) {
    // 最初に攻撃されたターゲット
    private var targetToFollow: Entity? = null

    // 攻撃ディレイのタイマー
    private var attackDelayTimer = 0

    // 設定値の定義は前と同じ
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.FloatSetting(
                "Range",
                "自動攻撃の最大射程距離を設定します。",
                4.2f,
                3.0f,
                7.0f,
            ),
            InfiniteSetting.IntSetting(
                "CPS",
                "自動攻撃の秒間クリック数を設定します。",
                10,
                1,
                20,
            ),
        )

    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val interactionManager = client.interactionManager ?: return

        // 必須チェック
        if (client.world == null || player.isDead || !isEnabled()) {
            stopAttack()
            return
        }

        // --- 1. ターゲットロックの処理 ---
        // プレイヤーが攻撃キーを押しているかチェック
        val isAttackKeyPressed = client.options.attackKey.isPressed

        if (targetToFollow == null) {
            // ターゲットが未ロックの場合: 攻撃キーが押されていれば、視線先のエンティティをターゲットに設定する

            if (isAttackKeyPressed) {
                // client.crosshairTarget は RaycastHitResult を返します
                val hitResult = client.crosshairTarget

                // 視線先がエンティティであるか確認
                if (hitResult is EntityHitResult) {
                    val potentialTarget = hitResult.entity

                    // LivingEntityであり、かつ自分自身ではないことを確認
                    if (potentialTarget is LivingEntity && potentialTarget != player) {
                        // ターゲットロック開始
                        targetToFollow = potentialTarget
                        attackDelayTimer = 0 // タイマーリセット
                    }
                }
            }
            // ターゲットがロックされなかった場合はここで終了
            if (targetToFollow == null) return
        }

        // --- 2. ロック継続と終了の条件チェック ---

        val target = targetToFollow ?: return // すでに上でnullチェックされているが、念のため

        // ターゲットが null になる条件:
        // A. 攻撃キーが離された場合（この機能のトリガー）
        // B. ターゲットが死んだ、ワールドから消えた場合
        // C. 射程外に出た場合

        // A. 攻撃キーが離されたらロック解除
        if (!isAttackKeyPressed) {
            stopAttack()
            return
        }

        // B. ターゲットの状態チェック
        if (target.isRemoved || target !is LivingEntity || !target.isAlive) {
            stopAttack()
            return
        }

        // C. 射程外チェック
        val range = ((settings.find { it.name == "Range" } as? InfiniteSetting.FloatSetting)?.value ?: 4.2f)
        if (player.distanceTo(target) > range) {
            stopAttack()
            return
        }

        // --- 3. 自動攻撃の実行 ---

        val cps = ((settings.find { it.name == "CPS" } as? InfiniteSetting.IntSetting)?.value ?: 10)
        val delayPerAttack = 20 / cps

        // 視線をターゲットに合わせる
        faceEntity(player, target)

        // ディレイタイマーを更新
        if (attackDelayTimer > 0) {
            attackDelayTimer--
            return
        }

        // 攻撃を実行し、タイマーをリセット
        // 攻撃を実行する際は、プレイヤーが「振り」のアニメーションに入るようにし、手動攻撃と同じクールダウンロジックをトリガーさせます。
        interactionManager.attackEntity(player, target)
        // 攻撃アニメーションのリセット（視覚的な問題回避のため）
        player.swingHand(player.activeHand)

        attackDelayTimer = delayPerAttack
    }

    private fun stopAttack() {
        targetToFollow = null
        attackDelayTimer = 0
    }

    override fun disabled() {
        super.disabled()
        stopAttack()
    }

    // --- ユーティリティメソッド ---

    /**
     * エンティティの方向を向くメソッド。
     * このメソッドは ConfigurableFeature から移動したものとします。
     */
    private fun faceEntity(
        player: ClientPlayerEntity,
        target: Entity,
    ) {
        val x = target.x - player.x
        val y = (target.y + target.getEyeHeight(target.pose)) - (player.y + player.getEyeHeight(player.pose))
        val z = target.z - player.z

        val dist = sqrt(x * x + z * z)
        val yaw = (MathHelper.atan2(z, x) * 180.0 / Math.PI).toFloat() - 90.0F
        val pitch = (-(MathHelper.atan2(y, dist) * 180.0 / Math.PI)).toFloat()

        player.yaw = yaw
        player.pitch = pitch
    }
}
