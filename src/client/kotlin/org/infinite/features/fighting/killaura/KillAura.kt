package org.infinite.features.fighting.killaura

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.fighting.lockon.LockOn
import org.infinite.settings.FeatureSetting
import kotlin.random.Random

class KillAura : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Cheat

    // 新しい設定: プレイヤーが攻撃を開始するまでの遅延時間（tick単位）
    private val reactionDelaySetting =
        FeatureSetting.IntSetting(
            "ReactionDelay",
            0,
            0,
            40,
        )

    // 新しい設定: 攻撃頻度に加えるランダムな遅延（tick単位）
    private val randomizerSetting =
        FeatureSetting.IntSetting(
            "Randomizer",
            4,
            0, // 最小値
            10, // 最大値
        )

    private val rangeSetting =
        FeatureSetting.FloatSetting(
            "Range",
            4.2f,
            3.0f,
            7.0f,
        )

    private val attackPlayersSetting =
        FeatureSetting.BooleanSetting(
            "Players",
            true,
        )

    private val attackMobsSetting =
        FeatureSetting.BooleanSetting(
            "Mobs",
            false,
        )

    private val maxTargetsSetting =
        FeatureSetting.IntSetting(
            "MaxTargets",
            1,
            0,
            10,
        )

    private val attackFrequencySetting =
        FeatureSetting.IntSetting(
            "AttackFrequency",
            0,
            0,
            20,
        )

    private val changeAngleSetting =
        FeatureSetting.BooleanSetting(
            "ChangeAngle",
            false,
        )

    enum class Priority {
        Health,
        Distance,
        Direction,
    }

    private val prioritySetting = FeatureSetting.EnumSetting("Priority", Priority.Distance, Priority.entries)

    override val settings: List<FeatureSetting<*>> =
        listOf(
            rangeSetting,
            attackPlayersSetting,
            attackMobsSetting,
            maxTargetsSetting,
            attackFrequencySetting,
            changeAngleSetting,
            reactionDelaySetting,
            randomizerSetting,
            prioritySetting,
        )

    // --- 追加した状態管理変数 ---
    private var tickCount = 0
    private var nextAttackTick = 0
    // ------------------------

    override fun tick() {
        val player = player ?: return
        val world = world ?: return

        // ティックカウントをインクリメント
        tickCount++

        // --- 攻撃頻度と遅延の制御ロジックの追加 ---
        val reactionDelay = reactionDelaySetting.value
        // 1. ReactionDelay の初期設定
        if (nextAttackTick == 0) {
            // 初回ターゲット発見時、またはモード有効化時
            nextAttackTick = tickCount + reactionDelay
        }

        // 2. 攻撃可能かチェック
        if (tickCount < nextAttackTick) {
            // まだ攻撃可能ティックに達していない
            return
        }

        val range = rangeSetting.value
        val attackPlayers = attackPlayersSetting.value
        val attackMobs = attackMobsSetting.value
        val maxTargets = maxTargetsSetting.value
        val attackFrequency = attackFrequencySetting.value
        // val changeAngle = changeAngleSetting.value // 今回は使用しない
        val randomizer = randomizerSetting.value
        val priority = prioritySetting.value

        // --- ターゲットの選定ロジック (変更なし) ---
        val attackableEntities =
            world.entities
                .filter {
                    it != player &&
                        player.distanceTo(it) <= range && it is LivingEntity &&
                        (it is PlayerEntity && attackPlayers || it !is PlayerEntity && attackMobs)
                }.map { it as LivingEntity }
                .sortedBy {
                    when (priority) {
                        Priority.Health -> it.health.toDouble()
                        Priority.Distance -> player.distanceTo(it).toDouble()
                        Priority.Direction -> InfiniteClient.getFeature(LockOn::class.java)?.getAngle(player, it) ?: 0.0
                    }
                }.take(maxTargets)
        // ----------------------------------------
        if (attackableEntities.isEmpty()) {
            return
        }
        // 3. 攻撃実行
        if (player.getAttackCooldownProgress(0.0f) >= 1.0f || attackFrequency != 0) {
            execAttack(attackableEntities)
            val randomDelay = if (randomizer > 0) Random.nextInt(0, randomizer + 1) else 0
            nextAttackTick = tickCount + attackFrequency + randomDelay
        }
    }

    fun execAttack(entities: List<LivingEntity>) {
        val player = player ?: return
        val attackableEntities = entities.filter { !it.isInvulnerable && it.isAlive }
        if (attackableEntities.isNotEmpty()) {
            player.swingHand(player.activeHand)
            attackableEntities.forEach { interactionManager?.attackEntity(player, it) }
        }
    }
}
