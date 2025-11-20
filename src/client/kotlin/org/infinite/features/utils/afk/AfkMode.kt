package org.infinite.features.utils.afk

import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class AfkMode : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> = listOf()
    private var hp = 0f

    override fun onTick() {
        val currentHp = player!!.health
        if (currentHp > hp) {
            hp = currentHp
        }
        if (currentHp < hp) {
            InfiniteClient.warn(Text.translatable("afkmode.damage_detected").string)
            disable()
        }
    }

    override fun onEnabled() {
        hp = player!!.health
    }
}
