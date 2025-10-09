package org.theinfinitys.features.rendering

import net.minecraft.entity.LivingEntity
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting

class ExtraSensory : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.BooleanSetting("Player", "プレイヤーを認識できるようになります", true),
            InfiniteSetting.BooleanSetting("Mob", "モブを認識できるようになります", true),
            InfiniteSetting.BooleanSetting("Item", "アイテムーを認識できるようになります", true),
            InfiniteSetting.BooleanSetting("Portal", "ポータルを認識できるようになります", true),
            InfiniteSetting.BooleanSetting("Tag", "タグ表示を強化します。", true),
        )

    fun getColorOfEntity(entity: LivingEntity): Int = RadarRenderer.getBaseDotColor(entity)

    fun addHealth(
        entity: LivingEntity,
        originalName: MutableText,
    ): Text {
        val health = entity.health.toInt()
        val maxHealth = entity.maxHealth.toInt()

        // 体力と最大体力を表示するテキストを生成
        val healthText =
            Text
                .literal(" [")
                .formatted(Formatting.GRAY)
                .append(
                    Text
                        .literal("$health")
                        .formatted(getHealthColor(health, maxHealth)),
                ).append(
                    Text
                        .literal("/$maxHealth]")
                        .formatted(Formatting.GRAY),
                )

        // オリジナルの表示名に体力情報を追加して返します
        return originalName.append(healthText)
    }

    /**
     * 体力に応じて色を決定します。
     */
    private fun getHealthColor(
        health: Int,
        maxHealth: Int,
    ): Formatting {
        val percentage = health.toFloat() / maxHealth.toFloat()

        return when {
            percentage > 0.75f -> Formatting.GREEN
            percentage > 0.50f -> Formatting.YELLOW
            percentage > 0.25f -> Formatting.GOLD
            else -> Formatting.RED
        }
    }
}
