package org.infinite.features.automatic.pilot

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.movement.fly.SuperFly
import org.infinite.settings.FeatureSetting
import kotlin.math.sqrt

class Location(
    val x: Int,
    val z: Int,
) {
    private val client = MinecraftClient.getInstance()
    private val player: ClientPlayerEntity
        get() = client.player!!

    fun distance(): Double {
        val cx = player.x
        val cz = player.z
        val diffX = x - cx
        val diffZ = z - cz
        return sqrt(diffX * diffX + diffZ * diffZ)
    }
}

class AutoPilot : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> = listOf()
    private val client: MinecraftClient
        get() = MinecraftClient.getInstance()
    private val player: ClientPlayerEntity
        get() = client.player!!
    private val isSuperFlyEnabled: Boolean
        get() = InfiniteClient.isFeatureEnabled(SuperFly::class.java)
    private val flySpeed: Double
        get() = InfiniteClient.playerInterface.movement.speed()
    private val moveSpeed: Double
        get() {
            val x = player.velocity.x
            val z = player.velocity.z
            return sqrt(x * x + z + z)
        }

    private val yaw: Double
        get() = client.player?.yaw?.toDouble() ?: 0.0
    private var target: Location? = null

    override fun registerCommands(builder: LiteralArgumentBuilder<FabricClientCommandSource>) {
    }
}
