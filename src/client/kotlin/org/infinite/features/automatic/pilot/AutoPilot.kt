package org.infinite.features.automatic.pilot

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.vehicle.BoatEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.predicate.entity.EntityPredicates
import net.minecraft.text.Text
import net.minecraft.util.TypeFilter
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.movement.boat.HoverVehicle
import org.infinite.libs.client.aim.AimInterface
import org.infinite.libs.client.aim.camera.CameraRoll
import org.infinite.libs.client.aim.task.condition.AimTaskConditionReturn
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.libs.client.inventory.InventoryManager.InventoryIndex
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.settings.FeatureSetting
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

// ターゲットの X, Z 座標を保持するデータクラス
class Location(
    val x: Int,
    val z: Int,
) {
    private val client = MinecraftClient.getInstance()
    private val player: ClientPlayerEntity?
        get() = client.player

    /** プレイヤーからターゲットまでの水平距離を計算します。 */
    fun distance(): Double {
        if (player == null) return 0.0
        val cx = player!!.x
        val cz = player!!.z
        val diffX = x - cx
        val diffZ = z - cz
        return sqrt(diffX * diffX + diffZ * diffZ)
    }
}

// 自動操縦の状態を定義する列挙型
enum class PilotState {
    Idle, // 待機中
    JetFlying, // SuperFlyモードで飛行中
    HoverFlying, // ボートで飛行中
    FallFlying, // 速度を落とすために下降中
    RiseFlying, // 速度を上げるために上昇中
    Gliding, // 一定以上の高さになったので滑空中
    Circling, // 目標点の周りを旋回し、着陸地点を探索中
    Landing, // 着陸中
    EmergencyLanding, // 緊急着陸中
    TakingOff, // 離陸中
}

// AutoPilot メイン機能クラス
class AutoPilot : ConfigurableFeature(initialEnabled = false) {
    override fun disabled() {
        MinecraftClient
            .getInstance()
            .options.jumpKey.isPressed = false
    }

    private var reconnectInterval = 20
    val defaultFallDir = 40.0
    val defaultRiseDir = -45.0
    val bestGlidingDir = 5.7
    val fallDir =
        FeatureSetting.DoubleSetting(
            "FallDirection",
            "feature.automatic.autopilot.falldirection.description",
            defaultFallDir,
            defaultFallDir - 10,
            defaultFallDir + 10,
        )
    val riseDir =
        FeatureSetting.DoubleSetting(
            "RiseDirection",
            "feature.automatic.autopilot.risingdirection.description",
            defaultRiseDir,
            defaultRiseDir - 10,
            defaultRiseDir + 10,
        )
    val glidingDir =
        FeatureSetting.DoubleSetting(
            "GlidingDirection",
            "feature.automatic.autopilot.glidingdirection.description",
            20.0,
            bestGlidingDir,
            defaultFallDir,
        )
    val elytraThreshold =
        FeatureSetting.IntSetting(
            "ElytraThreshold",
            "feature.automatic.autopilot.elytrathreshold.description",
            5,
            1,
            50,
        )
    private val swapElytra =
        FeatureSetting.BooleanSetting(
            "SwapElytra",
            "feature.automatic.autopilot.swapelytra.description",
            true,
        )
    val standardHeight =
        FeatureSetting.IntSetting(
            "StandardHeight",
            "feature.automatic.autopilot.standardheight.description",
            512,
            256,
            1024,
        )
    val landingDir =
        FeatureSetting.DoubleSetting(
            "LandingDirectory",
            "feature.automatic.autopilot.landingdirectory.description",
            -14.0,
            -45.0,
            0.0,
        )
    val emergencyLandingThreshold =
        FeatureSetting.IntSetting(
            "EmergencyLandingThreshold",
            "feature.automatic.autopilot.emergencylandingthreshold.description",
            60,
            10,
            300,
        )
    val collisionDetectionDistance =
        FeatureSetting.IntSetting(
            "CollisionDetectionDistance",
            "feature.automatic.autopilot.collisiondetectiondistance.description",
            10,
            3,
            30,
        )
    val jetFlightMode =
        FeatureSetting.BooleanSetting(
            "JetFlight",
            "feature.automatic.autopilot.jetflight.description",
            false,
        )
    val jetSpeedLimit =
        FeatureSetting.DoubleSetting(
            "JetSpeedLimit",
            "feature.automatic.autopilot.jetspeedlimit.description",
            30.0,
            10.0,
            50.0,
        )
    val jetAcceleration =
        FeatureSetting.DoubleSetting(
            "JetAcceleration",
            "feature.automatic.autopilot.jetacceleration.description",
            0.5,
            0.0,
            1.0,
        )
    private val targetX =
        FeatureSetting.IntSetting("TargetX", "feature.automatic.autopilot.targetx.description", 0, -30000000, 30000000)
    private val targetZ =
        FeatureSetting.IntSetting("TargetZ", "feature.automatic.autopilot.targetz.description", 0, -30000000, 30000000)
    override val settings: List<FeatureSetting<*>> =
        listOf(
            targetX,
            targetZ,
            elytraThreshold,
            swapElytra,
            standardHeight,
            riseDir,
            glidingDir,
            fallDir,
            landingDir,
            emergencyLandingThreshold,
            collisionDetectionDistance,
            jetFlightMode,
            jetSpeedLimit,
            jetAcceleration,
        )
    private var fallHeight: Double = 0.0
    private var riseHeight: Double = 0.0

    private fun redeployElytra() {
        if (player == null) return
        client.options.jumpKey.isPressed = true
    }

    val flySpeed: Double
        get() {
            if (player == null) return 0.0
            val entity = player!!.vehicle ?: player!!
            val moveVelocity = entity.velocity
            val lookVelocity = entity.rotationVector
            return moveVelocity.dotProduct(lookVelocity)
        }
    val flySpeedDisplay: Double
        get() = flySpeed * 20
    var risingTime = System.currentTimeMillis()
    var fallingTime = System.currentTimeMillis()

    val riseSpeed: Double
        get() = (player?.vehicle?.velocity?.y ?: player?.velocity?.y ?: 0.0)
    val riseSpeedDisplay: Double
        get() = riseSpeed * 20.0

    private val alpha = 0.0015
    var moveSpeedAverage: Double = 0.0
    private var riseSpeedAverage: Double = 0.0

    val height: Double
        get() = player?.y ?: 0.0
    private val moveSpeed: Double
        get() {
            if (player == null) {
                return 0.0
            }
            val entity = player!!.vehicle ?: player!!
            val x = entity.velocity.x
            val z = entity.velocity.z
            return sqrt(x * x + z * z) * 20.0
        }

    val target: Location
        get() = Location(targetX.value, targetZ.value)
    var state: PilotState = PilotState.Idle
    var bestLandingSpot: LandingSpot? = null
    var aimTaskCallBack: AimTaskConditionReturn? = null

    private fun isCollidingWithTerrain(): Boolean {
        if (player == null) return false

        val entity = if (player!!.vehicle is BoatEntity) player!!.vehicle!! else player!!
        val currentPos = entity.blockPos
        val lookVec = player!!.rotationVector
        val checkDistance = collisionDetectionDistance.value

        for (i in 1..checkDistance) {
            val checkPos =
                currentPos.add((lookVec.x * i).roundToInt(), (lookVec.y * i).roundToInt(), (lookVec.z * i).roundToInt())
            val blockState = world!!.getBlockState(checkPos)
            if (!blockState.isAir) {
                return true
            }
        }

        val blockBelow = currentPos.down()
        val blockStateBelow = world!!.getBlockState(blockBelow)
        return !blockStateBelow.isAir
    }

    private fun isBoatOnWater(): Boolean {
        if (player?.vehicle !is BoatEntity) return false
        val boat = player!!.vehicle as BoatEntity
        val blockPos = boat.blockPos
        val blockState = world!!.getBlockState(blockPos)
        return blockState.isOf(Blocks.WATER)
    }

    override fun start() {
        reconnectInterval = 20
        aimTaskCallBack = null // AimTaskの状態をリセット
        state = PilotState.Idle // 状態を適切に初期化
        moveSpeedAverage = moveSpeed // 移動速度の平均をリセット
        riseSpeedAverage = riseSpeed // 上昇速度の平均をリセット
        bestLandingSpot = null // 着陸地点をリセット
    }

    override fun tick() {
        if (reconnectInterval > 0) {
            if (player != null) {
                reconnectInterval--
            }
            return
        }
        if (jetFlightMode.value) {
            if (!InfiniteClient.isFeatureEnabled(HoverVehicle::class.java)) {
                InfiniteClient.error(Text.translatable("autopilot.error.hoverboat").string)
                disable()
                return
            }
        }
        val currentMoveSpeed = moveSpeed
        val currentRiseSpeed = riseSpeed
        moveSpeedAverage = alpha * currentMoveSpeed + (1.0 - alpha) * moveSpeedAverage
        riseSpeedAverage = alpha * currentRiseSpeed + (1.0 - alpha) * riseSpeedAverage
        if (listOf(PilotState.Landing, PilotState.EmergencyLanding).contains(state)) {
            MinecraftClient
                .getInstance()
                .options.jumpKey.isPressed = false
        }
        val remainingFlightTime = flightTime()
        val isEmergencyCondition =
            if (player?.vehicle is BoatEntity || state == PilotState.Landing) {
                false
            } else {
                remainingFlightTime < emergencyLandingThreshold.value || isCollidingWithTerrain()
            }

        if (isEmergencyCondition) {
            if (state != PilotState.EmergencyLanding) {
                InfiniteClient.warn(
                    Text
                        .translatable(
                            "autopilot.emergency_landing.start",
                            remainingFlightTime.roundToInt(),
                            isCollidingWithTerrain(),
                        ).string,
                )
                state = PilotState.EmergencyLanding
                aimTaskCallBack = null
            }
        }

        val currentTarget = target
        val hoverMode = player?.vehicle is BoatEntity
        if (swapElytra.value && player?.vehicle !is BoatEntity) {
            checkAndSwapElytra()
        }

        if (player?.isGliding != true && !hoverMode) {
            if (isElytra(InventoryManager.get(InventoryIndex.Armor.Chest()))) {
                InfiniteClient.warn(Text.translatable("autopilot.elytra.flight_interrupted").string)
                redeployElytra()
            } else {
                InfiniteClient.error(Text.translatable("autopilot.elytra.not_equipped").string)
                disable()
                return
            }
        }

        val distance = currentTarget.distance()
        if (distance < 32 && state == PilotState.Landing) {
            if (player?.vehicle is BoatEntity && isBoatOnWater()) {
                InfiniteClient.info(Text.translatable("autopilot.landing.boat_completed").string)
                player!!.dismountVehicle()
                disable()
                return
            } else if (player?.vehicle !is BoatEntity) {
                InfiniteClient.info(Text.translatable("autopilot.landing.completed").string)
                disable()
                return
            }
        } else if (distance < landingStartDistance && state != PilotState.Circling && state != PilotState.Landing) {
            InfiniteClient.info(Text.translatable("autopilot.circling.start").string)
            aimTaskCallBack = null
            state = PilotState.Circling
        }

        if (jetFlightMode.value &&
            listOf(PilotState.FallFlying, PilotState.Gliding, PilotState.RiseFlying, PilotState.TakingOff).contains(
                state,
            )
        ) {
            state = if (player?.vehicle is BoatEntity) PilotState.HoverFlying else PilotState.JetFlying
        }
        if (jetFlightMode.value) {
            if (player?.vehicle is BoatEntity) {
                if (state !in listOf(PilotState.Circling, PilotState.Landing, PilotState.EmergencyLanding)) {
                    state = PilotState.HoverFlying
                }
            } else if (state == PilotState.HoverFlying) {
                val searchBox = player!!.boundingBox.expand(10.0)
                val boats =
                    world!!.getEntitiesByType(TypeFilter.instanceOf(BoatEntity::class.java), searchBox) {
                        EntityPredicates.VALID_ENTITY.test(it)
                    }
                if (boats.isNotEmpty()) {
                    val closestBoat = boats.minBy { it.distanceTo(player) }
                    if (player!!.startRiding(closestBoat)) {
                        InfiniteClient.info(Text.translatable("autopilot.hover.remounted").string)
                    } else {
                        InfiniteClient.warn(Text.translatable("autopilot.hover.remount_failed").string)
                        state = PilotState.JetFlying
                    }
                } else {
                    InfiniteClient.warn(Text.translatable("autopilot.hover.no_boat_found").string)
                    state = PilotState.JetFlying
                }
            } else {
                if (state !in listOf(PilotState.Circling, PilotState.Landing, PilotState.EmergencyLanding)) {
                    state = PilotState.JetFlying
                }
            }
        } else if (state == PilotState.Idle && player?.vehicle is BoatEntity && isBoatOnWater()) {
            state = PilotState.TakingOff
            aimTaskCallBack = null
        }

        process(currentTarget)
    }

    val landingStartDistance = 256.0

    private fun elytraDurability(): Double {
        val chestStack = InventoryManager.get(InventoryIndex.Armor.Chest())
        return InventoryManager.durabilityPercentage(chestStack) * 100
    }

    private fun isElytra(stack: ItemStack): Boolean = stack.item == Items.ELYTRA

    private fun checkAndSwapElytra() {
        if (player == null) return

        val invManager = InventoryManager
        val equippedElytraStack = invManager.get(InventoryIndex.Armor.Chest())
        val isElytraEquipped = isElytra(equippedElytraStack)
        val currentDurability = if (isElytraEquipped) elytraDurability() else 0.0

        val needsSwap = !isElytraEquipped || (currentDurability <= elytraThreshold.value)
        if (needsSwap) {
            val bestElytra = findBestElytraInInventory()
            if (bestElytra != null) {
                if (invManager.swap(InventoryIndex.Armor.Chest(), bestElytra.index)) {
                    val swapMessage =
                        if (isElytraEquipped) {
                            Text
                                .translatable(
                                    "autopilot.elytra.swapped.low_durability",
                                    currentDurability.roundToInt(),
                                    bestElytra.durability.roundToInt(),
                                ).string
                        } else {
                            Text
                                .translatable(
                                    "autopilot.elytra.swapped.not_equipped",
                                    bestElytra.durability.roundToInt(),
                                ).string
                        }
                    InfiniteClient.info(swapMessage)
                } else {
                    InfiniteClient.error(Text.translatable("autopilot.elytra.swap_failed").string)
                }
            } else if (player?.vehicle !is BoatEntity) {
                if (isElytraEquipped) {
                    if (state != PilotState.EmergencyLanding) {
                        InfiniteClient.warn(
                            Text
                                .translatable(
                                    "autopilot.elytra.no_spare.low_durability",
                                    currentDurability.roundToInt(),
                                ).string,
                        )
                        state = PilotState.EmergencyLanding
                        aimTaskCallBack = null
                    }
                } else {
                    if (state != PilotState.EmergencyLanding) {
                        InfiniteClient.warn(Text.translatable("autopilot.elytra.no_spare.not_equipped").string)
                        state = PilotState.EmergencyLanding
                        aimTaskCallBack = null
                    }
                }
            }
        }
    }

    override fun enabled() {
        moveSpeedAverage = moveSpeed
        riseSpeedAverage = riseSpeed
        state = if (player?.vehicle is BoatEntity && isBoatOnWater()) PilotState.TakingOff else PilotState.Idle
        aimTaskCallBack = null
        riseHeight = height
        fallHeight = height
        bestLandingSpot = null
    }

    private fun process(target: Location) {
        when (state) {
            PilotState.Idle -> {
                val targetSpeed = jetSpeedLimit.value
                state =
                    if (moveSpeedAverage > targetSpeed) {
                        PilotState.FallFlying
                    } else {
                        PilotState.RiseFlying
                    }
                aimTaskCallBack = null
            }

            PilotState.TakingOff -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state))
                        InfiniteClient.info(Text.translatable("autopilot.takeoff.start").string)
                    }

                    AimTaskConditionReturn.Exec -> {
                        if (player == null || player!!.vehicle !is BoatEntity) return
                        val boat = player!!.vehicle as BoatEntity
                        var velocity = boat.velocity
                        val yaw = player!!.yaw.toDouble()
                        val pitch = player!!.pitch.toDouble()
                        val moveVec = CameraRoll(yaw, pitch).vec()
                        val power = jetAcceleration.value
                        val vecY =
                            velocity.y + min(power, standardHeight.value - height)
                        val vecX = velocity.x + moveVec.x * power
                        val vecZ = velocity.z + moveVec.z * power
                        velocity = Vec3d(vecX, vecY, vecZ)
                        if (velocity.length() * 20 > jetSpeedLimit.value) {
                            velocity = velocity.normalize().multiply(jetSpeedLimit.value / 20)
                        }
                        boat.velocity = velocity
                        if (height >= standardHeight.value) {
                            aimTaskCallBack = AimTaskConditionReturn.Success
                        }
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        state = PilotState.HoverFlying
                        InfiniteClient.info(Text.translatable("autopilot.takeoff.completed").string)
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.takeoff").string)
                        disable()
                    }

                    else -> {}
                }
            }

            PilotState.JetFlying -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state))
                    }

                    AimTaskConditionReturn.Exec -> {
                        if (player == null) return
                        var velocity = player!!.velocity
                        val yaw = player!!.yaw.toDouble()
                        val pitch = player!!.pitch.toDouble()
                        val moveVec = CameraRoll(yaw, pitch).vec()
                        val power = jetAcceleration.value
                        val vecY =
                            velocity.y +
                                if (height < standardHeight.value) {
                                    min(power, standardHeight.value - velocity.y)
                                } else {
                                    -power
                                }
                        val vecX = velocity.x + moveVec.x * (if (moveSpeed < jetSpeedLimit.value) power else 0.0)
                        val vecZ = velocity.z + moveVec.z * (if (moveSpeed < jetSpeedLimit.value) power else 0.0)
                        velocity = Vec3d(vecX, vecY, vecZ)
                        if (velocity.length() * 20 > jetSpeedLimit.value) {
                            velocity = velocity.normalize().multiply(jetSpeedLimit.value / 20)
                        }
                        player!!.velocity = velocity
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        state = PilotState.Circling
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.circling").string)
                        disable()
                    }

                    else -> {}
                }
            }

            PilotState.HoverFlying -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state))
                    }

                    AimTaskConditionReturn.Exec -> {
                        if (player == null || player!!.vehicle !is BoatEntity) return
                        val boat = player!!.vehicle as BoatEntity
                        var velocity = boat.velocity
                        val yaw = player!!.yaw.toDouble()
                        val pitch = player!!.pitch.toDouble()
                        val moveVec = CameraRoll(yaw, pitch).vec()
                        val power = jetAcceleration.value
                        val vecY =
                            velocity.y +
                                if (height < standardHeight.value) {
                                    min(power, standardHeight.value - velocity.y)
                                } else {
                                    -power
                                }
                        val vecX = velocity.x + moveVec.x * (if (moveSpeed < jetSpeedLimit.value) power else 0.0)
                        val vecZ = velocity.z + moveVec.z * (if (moveSpeed < jetSpeedLimit.value) power else 0.0)
                        velocity = Vec3d(vecX, vecY, vecZ)
                        if (velocity.length() * 20 > jetSpeedLimit.value) {
                            velocity = velocity.normalize().multiply(jetSpeedLimit.value / 20)
                        }
                        boat.velocity = velocity
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        state = PilotState.Circling
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.hover_flying").string)
                        disable()
                    }

                    else -> {}
                }
            }

            PilotState.Circling -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state, bestLandingSpot))
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        state = PilotState.Landing
                        InfiniteClient.info(Text.translatable("autopilot.landing.spot_found").string)
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.circling").string)
                        disable()
                    }

                    else -> {
                        if (player?.vehicle is BoatEntity) {
                            player!!.vehicle!!.velocity = player!!.vehicle!!.velocity.add(0.0, -1.0, 0.0)
                        }
                        searchLandingSpot(target)
                    }
                }
            }

            PilotState.Landing -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state, bestLandingSpot))
                        InfiniteClient.info(Text.translatable("autopilot.landing.aimtask_start").string)
                    }

                    AimTaskConditionReturn.Exec -> {
                        if (player == null) return
                        val isBoat = player!!.vehicle is BoatEntity
                        if (!isBoat) return // Elytra landing handled by glide pitch
                        val boat = player!!.vehicle as BoatEntity
                        var velocity = boat.velocity
                        val yaw = player!!.yaw.toDouble()
                        val pitch = player!!.pitch.toDouble()
                        val moveVec = CameraRoll(yaw, pitch).vec()
                        val power = jetAcceleration.value
                        val landingSpot = bestLandingSpot ?: return
                        val dX = landingSpot.x - player!!.x
                        val dZ = landingSpot.z - player!!.z
                        val horizontalDist = sqrt(dX * dX + dZ * dZ)
                        val heightAboveSpot = player!!.y - landingSpot.y
                        val vecY: Double
                        val vecX: Double
                        val vecZ: Double
                        if (horizontalDist < 5.0 && heightAboveSpot > 0) {
                            // Vertical descend
                            vecY = velocity.y - power * 0.5 // Slow descend
                            vecX = velocity.x * 0.9 // Dampen horizontal
                            vecZ = velocity.z * 0.9
                        } else {
                            // Approach
                            val verticalThreshold = 10.0
                            vecY =
                                velocity.y +
                                if (heightAboveSpot >
                                    verticalThreshold
                                ) {
                                    -power
                                } else if (heightAboveSpot < verticalThreshold - 5) {
                                    power
                                } else {
                                    0.0
                                }
                            vecX = velocity.x + moveVec.x * power
                            vecZ = velocity.z + moveVec.z * power
                        }
                        velocity = Vec3d(vecX, vecY, vecZ)
                        boat.velocity = velocity
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        if (player?.vehicle is BoatEntity) {
                            if (isBoatOnWater()) {
                                InfiniteClient.info(Text.translatable("autopilot.landing.boat_completed").string)
                                player!!.dismountVehicle()
                            } else {
                                InfiniteClient.info(Text.translatable("autopilot.landing.boat_not_on_water").string)
                            }
                        } else {
                            InfiniteClient.info(Text.translatable("autopilot.landing.conditions_met").string)
                        }
                        disable()
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.landing").string)
                        disable()
                    }

                    else -> {}
                }
            }

            PilotState.EmergencyLanding -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state, bestLandingSpot))
                        InfiniteClient.info(Text.translatable("autopilot.emergency_landing.aimtask_start").string)
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        if (player?.vehicle is BoatEntity && isBoatOnWater()) {
                            player!!.dismountVehicle()
                        }
                        InfiniteClient.info(Text.translatable("autopilot.emergency_landing.completed").string)
                        disable()
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.emergency_landing").string)
                        disable()
                    }

                    else -> {}
                }
            }

            PilotState.FallFlying -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        riseHeight = height
                        risingTime = System.currentTimeMillis()
                        AimInterface.addTask(AutoPilotAimTask(state))
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        state = PilotState.RiseFlying
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.fallflying").string)
                        disable()
                    }

                    else -> {}
                }
            }

            PilotState.RiseFlying -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        fallHeight = height
                        fallingTime = System.currentTimeMillis()
                        AimInterface.addTask(AutoPilotAimTask(state))
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        InfiniteClient.error(Text.translatable("autopilot.error.riseflying").string)
                        disable()
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.riseflying").string)
                        disable()
                    }

                    else -> {}
                }
            }

            PilotState.Gliding -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state))
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        state = PilotState.FallFlying
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.gliding").string)
                        disable()
                    }

                    else -> {}
                }
            }
        }
    }

    private fun searchLandingSpot(target: Location) {
        if (player == null) return

        val centerX = target.x
        val centerZ = target.z
        val searchRadius = 128

        var currentBestSpot: LandingSpot? = bestLandingSpot
        val currentBestScore = currentBestSpot?.score ?: -1.0

        repeat(5) {
            val x = centerX + MathHelper.nextBetween(player!!.random, -searchRadius, searchRadius)
            val z = centerZ + MathHelper.nextBetween(player!!.random, -searchRadius, searchRadius)
            val y = world!!.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z)

            if (!isDangerousBlock(x, y, z)) {
                val flatnessScore = calculateFlatnessScore(x, z)
                val score = y.toDouble() + (flatnessScore * 10.0)
                var adjustedScore = score
                if (player?.vehicle is BoatEntity && isWaterBlock(x, y, z)) {
                    adjustedScore += 2000000000000000000.0 // さらに高いボーナスで水を優先
                }
                if (adjustedScore > currentBestScore) {
                    currentBestSpot = LandingSpot(x, y, z, adjustedScore)
                    bestLandingSpot = currentBestSpot
                }
            }
        }
    }

    private fun calculateFlatnessScore(
        x: Int,
        z: Int,
    ): Double {
        var totalDiff = 0.0
        val centerH = world!!.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z)
        var count = 0
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val neighborH = world!!.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz)
                totalDiff += abs(centerH - neighborH)
                count++
            }
        }

        if (count == 0) return 1.0
        val avgDiff = totalDiff / count.toDouble()
        return MathHelper.clamp(1.0 - (avgDiff / 5.0), 0.0, 1.0)
    }

    private fun isDangerousBlock(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val blockPos = BlockPos(x, y, z)
        val blockState = world!!.getBlockState(blockPos)
        val block = blockState.block
        if (block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.CACTUS || block == Blocks.MAGMA_BLOCK) {
            return true
        }
        val blockStateAbove = world!!.getBlockState(blockPos.up())
        val blockAbove = blockStateAbove.block
        return blockAbove == Blocks.LAVA || blockAbove == Blocks.FIRE || blockAbove == Blocks.CACTUS
    }

    private fun isWaterBlock(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val blockPos = BlockPos(x, y, z)
        val blockState = world!!.getBlockState(blockPos)
        return blockState.isOf(Blocks.WATER) || blockState.isOf(Blocks.WATER_CAULDRON)
    }

    override fun registerCommands(builder: LiteralArgumentBuilder<FabricClientCommandSource>) {
        builder.then(
            ClientCommandManager.literal("target").then(
                ClientCommandManager.argument("x", IntegerArgumentType.integer()).then(
                    ClientCommandManager.argument("z", IntegerArgumentType.integer()).executes { context ->
                        targetX.value = IntegerArgumentType.getInteger(context, "x")
                        targetZ.value = IntegerArgumentType.getInteger(context, "z")
                        InfiniteClient.info(
                            Text
                                .translatable(
                                    "autopilot.command.target_set",
                                    targetX.value,
                                    targetZ.value,
                                ).string,
                        )
                        1
                    },
                ),
            ),
        )
    }

    override fun render2d(graphics2D: Graphics2D) {
        val currentTarget = target

        val distance = currentTarget.distance()
        val speed = moveSpeedAverage
        val etaSeconds: Long =
            if (speed > 1.0) {
                (distance / speed).roundToLong()
            } else {
                -1L
            }
        val flightSeconds: Long = flightTime().roundToLong()
        val stateText =
            when (state) {
                PilotState.Idle -> Text.translatable("autopilot.state.idle").string
                PilotState.JetFlying -> Text.translatable("autopilot.state.jet_flying").string
                PilotState.HoverFlying -> Text.translatable("autopilot.state.hover_flying").string
                PilotState.FallFlying -> Text.translatable("autopilot.state.fall_flying").string
                PilotState.RiseFlying -> Text.translatable("autopilot.state.rise_flying").string
                PilotState.Gliding -> Text.translatable("autopilot.state.gliding").string
                PilotState.Circling -> Text.translatable("autopilot.state.circling").string
                PilotState.Landing -> Text.translatable("autopilot.state.landing").string
                PilotState.EmergencyLanding -> Text.translatable("autopilot.state.emergency_landing").string
                PilotState.TakingOff -> Text.translatable("autopilot.state.taking_off").string
            }

        val durability =
            if (swapElytra.value && player?.vehicle !is BoatEntity) {
                "${elytraDurability().roundToInt()}%"
            } else {
                "Disabled"
            }

        val startX = 5
        var currentY = 5
        val lineHeight = graphics2D.fontHeight() + 2
        val bgColor = InfiniteClient.theme().colors.backgroundColor
        val white = InfiniteClient.theme().colors.foregroundColor
        val primaryColor = InfiniteClient.theme().colors.primaryColor
        val durabilityValue = elytraDurability()
        val isElytraEquipped = isElytra(InventoryManager.get(InventoryIndex.Armor.Chest()))

        val durabilityColor =
            if (swapElytra.value && isElytraEquipped && durabilityValue <= elytraThreshold.value && player?.vehicle !is BoatEntity) {
                InfiniteClient.theme().colors.warnColor
            } else {
                white
            }

        val infoLines = mutableListOf<String>()
        infoLines.add(Text.translatable("autopilot.display.title", stateText).string)
        infoLines.add(Text.translatable("autopilot.display.target", currentTarget.x, currentTarget.z).string)
        infoLines.add(
            Text
                .translatable(
                    "autopilot.display.distance",
                    "%.1f".format(distance / 1000.0),
                    "%.0f".format(distance),
                ).string,
        )
        infoLines.add(Text.translatable("autopilot.display.average_speed", "%.1f".format(moveSpeedAverage)).string)
        infoLines.add(Text.translatable("autopilot.display.current_speed", "%.1f".format(flySpeedDisplay)).string)
        infoLines.add(Text.translatable("autopilot.display.average_rising", "%.1f".format(riseSpeedAverage)).string)
        infoLines.add(Text.translatable("autopilot.display.current_rising", "%.1f".format(riseSpeedDisplay)).string)
        infoLines.add(Text.translatable("autopilot.display.elytra", durability).string)
        infoLines.add(Text.translatable("autopilot.display.eta", formatSecondsToDHMS(etaSeconds)).string)
        infoLines.add(Text.translatable("autopilot.display.remain", formatSecondsToDHMS(flightSeconds)).string)
        bestLandingSpot?.let {
            infoLines.add(Text.translatable("autopilot.display.land_spot", it.y, "%.1f".format(it.score)).string)
        }

        val maxWidth = (infoLines.maxOfOrNull { graphics2D.textWidth(it) } ?: 128).coerceAtLeast(128)
        val boxWidth = maxWidth + 10
        val boxHeight = infoLines.size * lineHeight + 6

        graphics2D.fill(startX, currentY, boxWidth, boxHeight, bgColor)
        graphics2D.drawBorder(startX, currentY, boxWidth, boxHeight, primaryColor, 1)

        currentY += 4
        infoLines.forEachIndexed { index, line ->
            val color =
                when (index) {
                    0 -> primaryColor
                    6 -> durabilityColor
                    else -> white
                }
            graphics2D.drawText(line, startX + 5, currentY, color)
            currentY += lineHeight
        }
    }

    override fun render3d(graphics3D: Graphics3D) {
        val x = target.x.toDouble()
        val y = world!!.bottomY.toDouble()
        val z = target.z.toDouble()
        val height = world!!.height * 10
        val size = 2
        val box =
            RenderUtils.ColorBox(
                InfiniteClient.theme().colors.primaryColor,
                Box(x - size, y, z - size, x + size, y + height, z + size),
            )
        graphics3D.renderSolidColorBoxes(listOf(box))

        bestLandingSpot?.let {
            val size = 5.0
            val color = if (state == PilotState.Circling) 0xAA00FFFF.toInt() else 0xAA00FF00.toInt()
            val box =
                RenderUtils.ColorBox(
                    color,
                    Box(
                        it.x - size,
                        it.y.toDouble(),
                        it.z - size,
                        it.x + size,
                        it.y.toDouble() + 5.0,
                        it.z + size,
                    ),
                )
            graphics3D.renderSolidColorBoxes(listOf(box))
        }
    }
}
