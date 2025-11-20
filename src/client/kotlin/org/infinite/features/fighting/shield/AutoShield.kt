package org.infinite.features.fighting.shield

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MagmaCubeEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.SlimeEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.item.Items
import net.minecraft.util.math.Vec3d
import org.infinite.InfiniteClient
import org.infinite.feature.ConfigurableFeature
import org.infinite.libs.client.aim.AimInterface
import org.infinite.libs.client.aim.task.AimTask
import org.infinite.libs.client.aim.task.condition.ImmediatelyAimTaskCondition
import org.infinite.libs.client.aim.task.config.AimCalculateMethod
import org.infinite.libs.client.aim.task.config.AimPriority
import org.infinite.libs.client.aim.task.config.AimTarget
import org.infinite.libs.client.control.ControllerInterface
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.libs.client.inventory.InventoryManager.InventoryIndex
import org.infinite.settings.FeatureSetting

class AutoShield : ConfigurableFeature(initialEnabled = false) {
    // --- 設定項目の定義は省略 ---
    private val detectionRangeSetting: FeatureSetting.DoubleSetting =
        FeatureSetting.DoubleSetting("DetectionRange", 4.0, 1.0, 10.0)
    private val autoEquipSetting: FeatureSetting.BooleanSetting = FeatureSetting.BooleanSetting("AutoEquip", true)
    private val predictionTicksSetting: FeatureSetting.IntSetting =
        FeatureSetting.IntSetting("PredictionTicks", 10, 1, 40)
    private val meleeRangeMultiplierSetting: FeatureSetting.DoubleSetting =
        FeatureSetting.DoubleSetting("MeleeRangeMultiplier", 1.5, 0.5, 3.0)

    override val settings: List<FeatureSetting<*>> =
        listOf(
            detectionRangeSetting,
            autoEquipSetting,
            predictionTicksSetting,
            meleeRangeMultiplierSetting,
        )

    class AutoShieldTarget : AimTarget.EntityTarget(e = MinecraftClient.getInstance().player!!) {
        val player: ClientPlayerEntity
            get() = MinecraftClient.getInstance().player!!
        override val entity: Entity
            get() {
                val autoShield = InfiniteClient.getFeature(AutoShield::class.java) ?: return player
                val detectionRange = autoShield.detectionRangeSetting.value
                return autoShield.getThreateningEntity(detectionRange) ?: player
            }
    }

    var isAimTaskRegistered: Boolean = false

    override fun onTick() {
        val playerEntity = player ?: return
        val detectionRange = detectionRangeSetting.value
        val controller = ControllerInterface
        val useKeyBinding = options.useKey

        // --- ステップ 1: 危険な状況を検知する ---
        val threateningEntity: Entity? = getThreateningEntity(detectionRange)

        if (threateningEntity != null) {
            val manager = InventoryManager
            val offHandItem = manager.get(InventoryIndex.OffHand())
            var hasShieldInOffHand = offHandItem.item == Items.SHIELD

            // ... (装備ロジックは省略) ...
            if (!hasShieldInOffHand && autoEquipSetting.value) {
                val targetSlot = manager.findFirst(Items.SHIELD)
                if (targetSlot != null) {
                    manager.swap(InventoryIndex.OffHand(), targetSlot)
                    hasShieldInOffHand = true
                }
            }

            // --- 🛡️ 照準制御の追加: 危険源の方向を向く ---
            if (threateningEntity is ProjectileEntity || threateningEntity is HostileEntity) {
                if (!isAimTaskRegistered) {
                    isAimTaskRegistered = true
                    val aimTask =
                        AimTask(
                            priority = AimPriority.Immediately,
                            target = AutoShieldTarget(),
                            condition = ImmediatelyAimTaskCondition(),
                            calcMethod = AimCalculateMethod.Immediate,
                            onSuccess = {
                                isAimTaskRegistered = false
                            },
                            onFailure = {
                                isAimTaskRegistered = false
                            },
                        )
                    AimInterface.addTask(aimTask)
                }
            }

            // --- 盾構えロジック ---
            if (hasShieldInOffHand) {
                controller.press(
                    key = useKeyBinding,
                    // 継続条件をthreateningEntityがnullでないことに依存させる
                    condition = { playerEntity.isAlive && getThreateningEntity(detectionRange) != null },
                )
            } else {
                controller.release(useKeyBinding, 1)
            }
        } else {
            // --- 危険がない場合は構えを解除する ---
            controller.release(useKeyBinding, 1)
        }
    }

    /**
     * 周囲の危険なエンティティを検知し、最初に見つかった危険エンティティを返します。
     */
    private fun getThreateningEntity(range: Double): Entity? {
        val currentWorld = world ?: return null
        val currentPlayer = player ?: return null

        val box = currentPlayer.boundingBox.expand(range)

        val entities =
            currentWorld.getOtherEntities(currentPlayer, box) { entity ->
                entity is LivingEntity || entity is ProjectileEntity
            }

        for (entity in entities) {
            if (isExplosionThreat(entity)) return entity
            if (isProjectileThreat(entity)) return entity
            if (isMeleeThreat(entity)) return entity
        }

        return null
    }

    // isExplosionThreat (変更なし)
    private fun isExplosionThreat(entity: Entity): Boolean {
        if (entity is CreeperEntity) {
            val fuseTicks = entity.getLerpedFuseTime(client.renderTickCounter.getTickProgress(false))
            return fuseTicks < 30
        }
        return false
    }

    // isProjectileThreat (変更なし)
    private fun isProjectileThreat(entity: Entity): Boolean {
        if (entity !is ProjectileEntity) return false
        val playerBox = player?.boundingBox ?: return false
        val predictionTicks = predictionTicksSetting.value
        var pos: Vec3d = entity.getLerpedPos(0f)
        val velocity: Vec3d = entity.velocity

        repeat(predictionTicks) {
            pos = pos.add(velocity)
            val projectileBox = entity.boundingBox.offset(pos.subtract(entity.getLerpedPos(0f)))
            if (playerBox.intersects(projectileBox)) {
                return true
            }
        }
        return false
    }

    /**
     * ⚔️ 近接攻撃（敵対モブ）の脅威を判定
     * - モブが防御開始リーチ内にいるか、かつ、攻撃準備が整っているかを考慮します。
     */
    private fun isMeleeThreat(entity: Entity): Boolean {
        val playerEntity = player ?: return false
        // 1. 距離判定: 設定された倍率に基づき防御開始リーチ内かチェック
        val mobReach = 1.0 + (entity.width / 2.0) + (playerEntity.width / 2.0)
        val requiredDistance = mobReach * meleeRangeMultiplierSetting.value
        val distance = entity.distanceTo(playerEntity)
        if (entity is SlimeEntity || entity is MagmaCubeEntity) {
            // スライム系は距離が近ければ（近接モブの防御開始リーチ内であれば）脅威と見なす
            val mobReach = 1.0 + (entity.width / 2.0) + (playerEntity.width / 2.0)
            val requiredDistance = mobReach * meleeRangeMultiplierSetting.value
            // ターゲットの有無に関わらず、防御開始距離内なら防御
            if (distance < requiredDistance) {
                return true
            }
        }
        if (entity !is HostileEntity) return false

        if (distance >= requiredDistance) {
            return false // 距離が遠すぎる
        }
        // 2. 攻撃準備完了判定: 距離が近接リーチ内の場合、攻撃準備が整っているかを確認
        // MobEntityの攻撃クールダウンや攻撃モーションをチェック（高度な判断）

        // 敵対モブがプレイヤーをターゲットにしているか
        val mobEntity = (entity as? MobEntity) ?: return false
        val isTargetingPlayer = mobEntity.target == playerEntity
        val lastAttacked = mobEntity.lastAttackTime
        val attackSpeedAttribute =
            mobEntity.attributes.getCustomInstance(EntityAttributes.ATTACK_SPEED)
        val attackSpeedAttributeValue = attackSpeedAttribute?.value ?: 4.0
        val attackSpeed = 20 / attackSpeedAttributeValue
        val currentTick = world?.time ?: return false
        // 攻撃準備完了の最も簡単なチェック:
        // リーチ内に入っていて、かつ、プレイヤーをターゲットにしているなら、攻撃が「差し迫っている」と判断

        if (isTargetingPlayer && (lastAttacked + attackSpeed) > currentTick) {
            InfiniteClient.log("\nAttacked: $lastAttacked\n Time:$currentTick")
            return true
        }

        // 攻撃準備が整っている具体的なロジック（例：ウィザーなど特殊なモブの攻撃前のモーション）が
        // ない場合、リーチ内に入った時点で防御を開始するのが安全です。

        // ターゲットを持たないモブ（例: プレイヤーに気づいていないクリーパー）を考慮し、
        // ターゲットがなくとも近接リーチ内であれば防御開始とします。（元のロジックを維持しつつ、安全性を優先）
        return true
    }
}
