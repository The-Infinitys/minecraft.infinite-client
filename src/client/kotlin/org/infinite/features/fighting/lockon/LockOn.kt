package org.infinite.features.fighting.lockon

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.fighting.aimassist.AimAssist
import org.infinite.libs.client.aim.AimInterface
import org.infinite.libs.client.aim.task.AimTask
import org.infinite.libs.client.aim.task.condition.AimTaskConditionInterface
import org.infinite.libs.client.aim.task.condition.AimTaskConditionReturn
import org.infinite.libs.client.aim.task.config.AimCalculateMethod
import org.infinite.libs.client.aim.task.config.AimPriority
import org.infinite.libs.client.aim.task.config.AimTarget
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting
import org.infinite.settings.Property
import org.infinite.utils.rendering.getRainbowColor
import org.lwjgl.glfw.GLFW
import kotlin.math.acos

class LockOn : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Utils
    override val toggleKeyBind: Property<Int>
        get() = Property(GLFW.GLFW_KEY_K)
    private val range: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "Range",
            16f,
            3.0f,
            256.0f,
        )
    private val players: FeatureSetting.BooleanSetting =
        FeatureSetting.BooleanSetting(
            "Players",
            true,
        )

    enum class Priority {
        Direction, // 角度優先
        Distance, // 距離優先
        Both, // 両方
    }

    private val priority = FeatureSetting.EnumSetting("Priority", Priority.Both, Priority.entries)
    private val mobs: FeatureSetting.BooleanSetting =
        FeatureSetting.BooleanSetting(
            "Mobs",
            true,
        )
    private val fov: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "FOV",
            90.0f,
            10.0f,
            360.0f,
        )
    private val speed: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "Speed",
            1.0f,
            0.5f,
            10f,
        )
    private val method: FeatureSetting.EnumSetting<AimCalculateMethod> =
        FeatureSetting.EnumSetting(
            "Method",
            AimCalculateMethod.Linear,
            AimCalculateMethod.entries,
        )
    override val settings: List<FeatureSetting<*>> =
        listOf(
            range,
            players,
            mobs,
            fov,
            speed,
            method,
            priority,
        )

    var lockedEntity: LivingEntity? = null

    // 🎯 座標変換の結果を格納するプライベートフィールド
    private var screenPos: Graphics2D.DisplayPos? = null

    override fun enabled() {
        findAndLockTarget()
        screenPos = null // 有効化時にクリア
    }

    override fun disabled() {
        lockedEntity = null
        screenPos = null // 無効化時にクリア
    }

    fun exec() {
        if (lockedEntity == null ||
            !lockedEntity!!.isAlive ||
            (MinecraftClient.getInstance().player?.distanceTo(lockedEntity) ?: Float.MAX_VALUE) > range.value
        ) {
            lockedEntity = null
            disable()
            return
        }
        if (AimInterface.taskLength() == 0) {
            lockedEntity?.let { target ->
                AimInterface.addTask(
                    LockOnAimTask(
                        AimPriority.Preferentially,
                        AimTarget.EntityTarget(target),
                        LockOnCondition(),
                        method.value,
                        speed.value.toDouble(),
                    ),
                )
            }
        }
    }

    override fun tick() {
        exec()
    }

    // ----------------------------------------------------------------------
    // ターゲット選択のヘルパー関数
    // ----------------------------------------------------------------------

    /**
     * ターゲットへの角度 (FOV) を取得します。
     * AimAssistが利用できない場合は Double.MAX_VALUE を返して優先度を下げます。
     */
    fun getAngle(
        player: PlayerEntity,
        target: LivingEntity,
    ): Double = InfiniteClient.getFeature(AimAssist::class.java)?.calcFov(player, target) ?: Double.MAX_VALUE

    /**
     * 角度と距離を正規化し、重み付けして総合スコアを計算します (低い方が優先)。
     */
    private fun calculateCombinedScore(
        player: PlayerEntity,
        target: LivingEntity,
    ): Double {
        val distance = player.distanceTo(target).toDouble()
        val angle = getAngle(player, target)

        // 角度を少し優先させる (例: 60% 角度, 40% 距離)
        val angleWeight = 0.6
        val distanceWeight = 0.4

        // 正規化された角度 (0 から 1)
        // fov.value は FOV の全角なので、最大角度はその半分。0.001 で除算エラーを防止。
        val maxFovAngle = (fov.value / 2.0).coerceAtLeast(0.001)
        val normalizedAngle = (angle / maxFovAngle).coerceIn(0.0, 1.0)

        // 正規化された距離 (0 から 1)
        // range.value は最大距離。0.001 で除算エラーを防止。
        val maxRange = range.value.toDouble().coerceAtLeast(0.001)
        val normalizedDistance = (distance / maxRange).coerceIn(0.0, 1.0)

        // 総合スコア (低い方が優先)
        return (angleWeight * normalizedAngle) + (distanceWeight * normalizedDistance)
    }

    // ----------------------------------------------------------------------
    // ターゲット検索とロックオン
    // ----------------------------------------------------------------------
    private fun findAndLockTarget() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val world = client.world ?: return

        val candidates =
            world.entities
                .filter { it is LivingEntity }
                .filter { it != player && it.isAlive }
                .filter {
                    (players.value && it is PlayerEntity) || (mobs.value && it !is PlayerEntity)
                }.filter { player.distanceTo(it) <= range.value }
                .filter { isWithinFOV(player, it as LivingEntity, fov.value) }

        val target =
            when (priority.value) {
                Priority.Direction -> candidates.minByOrNull { getAngle(player, it as LivingEntity) }
                Priority.Distance -> candidates.minByOrNull { player.distanceTo(it) }
                Priority.Both -> candidates.minByOrNull { calculateCombinedScore(player, it as LivingEntity) }
            }

        lockedEntity = target as? LivingEntity
    }

    private fun isWithinFOV(
        player: PlayerEntity,
        target: LivingEntity,
        fovDegrees: Float,
    ): Boolean {
        val playerLookVec = player.rotationVector.normalize()
        val targetCenterVec = target.boundingBox.center
        val targetVec = targetCenterVec.subtract(player.eyePos)
        val targetLookVec = targetVec.normalize()

        val dotProduct = playerLookVec.dotProduct(targetLookVec)
        val angleRadians = acos(dotProduct.coerceIn(-1.0, 1.0))
        val angleDegrees = Math.toDegrees(angleRadians).toFloat()

        return angleDegrees <= fovDegrees / 2.0f
    }

    // ----------------------------------------------------------------------
    // 2D 描画 (render3dで計算した座標を利用)
    // ----------------------------------------------------------------------
    override fun render2d(graphics2D: Graphics2D) {
        // 3Dレンダリングで計算され、格納された画面座標を利用
        val pos = screenPos ?: return

        val x = pos.x
        val y = pos.y
        val rainbowColor = getRainbowColor()
        val boxSize = 8.0
        graphics2D.drawBorder(
            x - boxSize / 2.0,
            y - boxSize / 2.0,
            boxSize,
            boxSize,
            rainbowColor,
        )
        graphics2D.drawLine(
            (x - boxSize).toFloat(),
            y.toFloat(),
            (x + boxSize).toFloat(),
            y.toFloat(),
            rainbowColor,
            2,
        )
        graphics2D.drawLine(
            x.toFloat(),
            (y - boxSize).toFloat(),
            x.toFloat(),
            (y + boxSize).toFloat(),
            rainbowColor,
            2,
        )
    }

    // ----------------------------------------------------------------------
    // 3D 描画 (座標計算と格納、および 3D ボックス描画)
    // ----------------------------------------------------------------------
    override fun render3d(graphics3D: Graphics3D) {
        val target = lockedEntity
        if (target == null) {
            screenPos = null // ターゲットがいない場合はクリア
            return
        }

        // 1. 座標変換を実行し、プライベートフィールドに格納
        // ターゲットの目の高さの中央をターゲット座標とする
        val targetPos = target.eyePos
        screenPos = graphics3D.toDisplayPos(targetPos)

        // 画面外の場合は、screenPos が null になり、2D 描画はスキップされる

        // 2. 3D ボックスの描画 (オプション)
        // ターゲットが画面に表示されているか (screenPos != null) にかかわらず、3D描画は実行可能
        if (screenPos != null) {
            // ターゲットのヒットボックスを取得
            val box = target.boundingBox

            // 描画設定のPush (RenderSystem の操作が必要な場合)
            graphics3D.pushMatrix()

            // 例: ターゲットを囲む線を描画
            graphics3D.renderLinedBox(
                box = box,
                color = getRainbowColor(),
                isOverDraw = true, // 壁越しに表示
            )

            // 描画設定のPop
            graphics3D.popMatrix()
        }
    }
}

class LockOnCondition : AimTaskConditionInterface {
    override fun check(): AimTaskConditionReturn {
        val lockOn = InfiniteClient.getFeature(LockOn::class.java) ?: return AimTaskConditionReturn.Failure
        return if (lockOn.isEnabled()) {
            AimTaskConditionReturn.Exec
        } else {
            AimTaskConditionReturn.Success
        }
    }
}

class LockOnAimTask(
    override val priority: AimPriority,
    override val target: AimTarget,
    override val condition: LockOnCondition,
    override val calcMethod: AimCalculateMethod,
    override val multiply: Double,
) : AimTask(priority, target, condition, calcMethod, multiply) {
    override fun atSuccess() {
        // Keep aiming if the target is still valid
        InfiniteClient.getFeature(LockOn::class.java)?.let { lockOn ->
            if (lockOn.isEnabled() && lockOn.lockedEntity != null) {
                lockOn.exec()
            }
        }
    }

    override fun atFailure() {
        // If aiming fails, clear tasks and let tick() re-evaluate or disable
        InfiniteClient.getFeature(LockOn::class.java)?.let { lockOn ->
            if (lockOn.isEnabled() && lockOn.lockedEntity != null) {
                lockOn.exec()
            }
        }
    }
}
