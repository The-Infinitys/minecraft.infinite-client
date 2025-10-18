package org.theinfinitys.features.fighting.counter

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.MathHelper
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting
import kotlin.math.sqrt

class CounterAttack : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<InfiniteSetting<*>> = listOf()

    private var lastHurtTime = 0

    private var targetToAttack: LivingEntity? = null

    private var attackDelayTicks = 0

    private var internalCooldown = 0
    private val cooldownTicks = 20

    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return

        // 内部クールダウンを減少させる
        if (internalCooldown > 0) {
            internalCooldown--
        }

        // --- 1. ダメージ検出 ---
        // プレイヤーのhurtTimeが設定され、かつ前回のtick時よりも増えた場合（つまり、今ダメージを受けた）
        if (player.hurtTime > lastHurtTime && internalCooldown <= 0) {
            // --- 2. 攻撃者の推測 ---
            val target = findBestAttacker(player)

            if (target != null) {
                // Mixinから受け取っていた情報をここで再取得/再計算
                val range = ((settings.find { it.name == "Range" } as? InfiniteSetting.FloatSetting)?.value ?: 4.2f)
                val delay = ((settings.find { it.name == "Delay" } as? InfiniteSetting.IntSetting)?.value ?: 0)

                // 範囲内か確認
                if (player.distanceTo(target) <= range) {
                    targetToAttack = target
                    attackDelayTicks = delay
                }
            }
        }
        // 最後に現在のhurtTimeを保存
        lastHurtTime = player.hurtTime

        // --- 3. 反撃実行ロジック ---
        val target = targetToAttack ?: return

        // 反撃ディレイを減少させる
        if (attackDelayTicks > 0) {
            attackDelayTicks--
            return
        }

        // ディレイが0以下になったら反撃を実行する
        if (internalCooldown <= 0) {
            executeCounterAttack(target)
            targetToAttack = null
            internalCooldown = cooldownTicks
        }
    }

    /**
     * 周囲から最も可能性の高い攻撃者を推測するメソッド
     */
    private fun findBestAttacker(player: ClientPlayerEntity): LivingEntity? {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return null
        val range = ((settings.find { it.name == "Range" } as? InfiniteSetting.FloatSetting)?.value ?: 4.2f).toDouble()

        // プレイヤーに近いLivingEntityをリストアップ
        val potentialTargets =
            world
                .getOtherEntities(player, player.boundingBox.expand(range)) { entity ->
                    entity is LivingEntity && entity != player && entity.isAlive
                }.filterIsInstance<LivingEntity>()

        // ここで、ターゲットの優先順位付けロジックを入れる（例：最も近いエンティティ、または直前に攻撃アニメーションを見せたエンティティなど）
        // 単純化のため、ここでは「最も近く、生きているLivingEntity」を返す
        return potentialTargets.minByOrNull { it.distanceTo(player) }
    }

    /**
     * 実際に反撃を実行するメソッド。
     * Mixinで要求された「別の場所でその方向に向いて攻撃を実行するメソッド」に相当します。
     */
    private fun executeCounterAttack(target: LivingEntity) {
        val client = MinecraftClient.getInstance()
        val player = client.player

        // 念のための null チェック
        if (client.world == null || client.interactionManager == null || player == null || player.isDead) {
            return
        }

        // 1. 攻撃者の方向を向く
        faceEntity(player, target)

        // 2. 攻撃を実行する
        client.interactionManager!!.attackEntity(player, target)
    }

    /**
     * エンティティの方向を向くメソッド。Mixinから移動。
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

    override fun disabled() {
        super.disabled()
        // 機能が無効になったら待機中のターゲットをクリアする
        targetToAttack = null
        attackDelayTicks = 0
        internalCooldown = 0
    }
}
