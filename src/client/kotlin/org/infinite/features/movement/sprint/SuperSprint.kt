package org.infinite.features.movement.sprint

import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting
import kotlin.math.atan2

class SuperSprint : ConfigurableFeature(initialEnabled = false) {
    private val onlyWhenForward =
        FeatureSetting.BooleanSetting(
            "OnlyWhenForward",
            true,
        )
    private val evenIfHungry =
        FeatureSetting.BooleanSetting(
            "EvenIfHungry",
            false,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            onlyWhenForward,
            evenIfHungry,
        )

    override fun disabled() {
        MinecraftClient
            .getInstance()
            .options
            ?.sprintKey
            ?.isPressed = false
    }

    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val options = client.options ?: return
        if (!evenIfHungry.value) { // If EvenIfHungry is false, check hunger
            if (player.hungerManager.foodLevel <= 6) {
                player.isSprinting = false // Stop sprinting if hunger is too low
                return // Prevent further sprint logic
            }
        }
        if (!options.sprintKey.isPressed) {
            options.sprintKey.isPressed = !player.isGliding && options.forwardKey.isPressed
        }
        if (!onlyWhenForward.value) {
            val pressedForward = options.forwardKey.isPressed
            val pressedBack = options.backKey.isPressed
            val pressedLeft = options.leftKey.isPressed
            val pressedRight = options.rightKey.isPressed
            val movementKeyPressed = pressedForward || pressedBack || pressedLeft || pressedRight
            if (movementKeyPressed) {
                player.isSprinting = true
                val currentYaw = player.yaw // 現在のカメラの向き（視線）
                var deltaYaw: Double // ラジアン
                val moveZ = (if (pressedForward) 1 else 0) - (if (pressedBack) 1 else 0)
                val moveX = (if (pressedRight) 1 else 0) - (if (pressedLeft) 1 else 0)
                if (moveZ != 0 || moveX != 0) {
                    deltaYaw = atan2(moveX.toDouble(), moveZ.toDouble())
                    val calculatedYaw = (currentYaw + Math.toDegrees(deltaYaw)).toFloat()
                    val networkHandler = client.networkHandler
                    if (networkHandler != null) {
                        // PlayerMoveC2SPacket.LookAndOnGround で向きをサーバーに強制
                        val packet =
                            PlayerMoveC2SPacket.LookAndOnGround(
                                calculatedYaw,
                                player.pitch, // Pitchは変更しない
                                player.isOnGround,
                                player.horizontalCollision,
                            )
                        networkHandler.sendPacket(packet)
                    }
                }
            }
        }
    }
}
