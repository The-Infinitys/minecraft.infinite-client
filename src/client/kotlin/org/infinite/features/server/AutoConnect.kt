package org.infinite.features.server

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.network.ServerInfo
import org.infinite.ConfigurableFeature
import org.infinite.ConfigurableFeature.FeatureLevel
import org.infinite.settings.FeatureSetting

class AutoConnect : ConfigurableFeature() {
    var lastServer: ServerInfo? = null

    override fun start() {
        lastServer = MinecraftClient.getInstance().currentServerEntry
    }

    fun joinLastServer(mpScreen: MultiplayerScreen) {
        if (lastServer == null) return
        mpScreen.connect(lastServer)
    }

    fun reconnect(prevScreen: Screen?) {
        if (lastServer == null) return
        ConnectScreen.connect(
            prevScreen,
            MinecraftClient.getInstance(),
            ServerAddress.parse(lastServer!!.address),
            lastServer!!,
            false,
            null,
        )
    }

    val waitTicks =
        FeatureSetting.IntSetting(
            "WaitTicks",
            "feature.server.autoconnect.delay.description",
            40,
            10,
            300,
        )
    override val settings: List<FeatureSetting<*>> = listOf(waitTicks)
    override val level: FeatureLevel = FeatureLevel.UTILS
}
