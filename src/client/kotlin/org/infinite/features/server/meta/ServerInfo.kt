package org.infinite.features.server.meta

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ServerInfo
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.settings.FeatureSetting

class ServerInfo : ConfigurableFeature() {
    override val settings: List<FeatureSetting<*>> = emptyList()
    override val level: FeatureLevel = FeatureLevel.Utils
    override val preRegisterCommands: List<String> = emptyList()
    override val togglable: Boolean = false

    fun show(): Int {
        val info = getCurrentServerInfo()
        if (info != null) {
            var serverInfoText = ""
            serverInfoText += "Name: ${info.name}\n"
            serverInfoText += "Address: ${info.address}\n"
            serverInfoText += "Version: ${info.version.string}\n"
            serverInfoText += "Protocol Version: ${info.protocolVersion}\n"
            serverInfoText += "Ping: ${info.ping}ms\n"
            info.players?.let { players ->
                serverInfoText += "Players: ${players.online}/${players.max}\n"
            }
            serverInfoText += "Resource Pack Policy: ${info.resourcePackPolicy.name}\n"
            serverInfoText += "Server Type: ${info.serverType.name}\n"
            InfiniteClient.log("\n$serverInfoText")
        } else {
            InfiniteClient.error("Failed to get Server Info")
        }
        return 1
    }

    override fun enabled() {
        disable()
    }

    override fun registerCommands(builder: LiteralArgumentBuilder<FabricClientCommandSource>) {
        builder.then(
            ClientCommandManager.literal("show").executes { _ -> show() },
        )
    }

    fun getCurrentServerInfo(): ServerInfo? {
        val client = MinecraftClient.getInstance()
        return client.currentServerEntry
    }
}
