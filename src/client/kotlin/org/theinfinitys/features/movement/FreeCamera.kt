package org.theinfinitys.features.movement

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting

/**
 * FreeCamera (自由視点カメラ) Feature。
 * プレイヤー本体をその場に固定し、カメラを自由操作する。
 * FakePlayerの管理は依存関係にあるFreeze Featureが行う。
 */
class FreeCamera : ConfigurableFeature(initialEnabled = false) {
    override val available: Boolean = false

    private val speedSetting =
        InfiniteSetting.FloatSetting(
            "Speed",
            "Freecam中の移動速度を設定します。",
            1.0f,
            0.1f,
            5.0f,
        )

    override val settings: List<InfiniteSetting<*>> = listOf(speedSetting)
    override val depends: List<Class<out ConfigurableFeature>> = listOf(Freeze::class.java)
}
