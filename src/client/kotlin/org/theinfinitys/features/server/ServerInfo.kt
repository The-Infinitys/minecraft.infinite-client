package org.theinfinitys.features.server

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ServerInfo
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.InfiniteClient
import org.theinfinitys.settings.InfiniteSetting

class ServerInfo : ConfigurableFeature(initialEnabled = true) {
    override val settings: List<InfiniteSetting<*>> = emptyList()
    override val level: FeatureLevel = FeatureLevel.UTILS

    override fun tick() {
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
        disable()
    }

    fun getCurrentServerInfo(): ServerInfo? {
        val client = MinecraftClient.getInstance()
        return client.currentServerEntry
    }
}
