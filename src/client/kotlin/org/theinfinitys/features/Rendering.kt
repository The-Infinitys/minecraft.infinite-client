package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.rendering.AntiOverlay
import org.theinfinitys.features.rendering.CameraConfig
import org.theinfinitys.features.rendering.Radar
import org.theinfinitys.features.rendering.SuperSight
import org.theinfinitys.features.rendering.XRay

val rendering =
    listOf(
        feature(
            "AntiOverlay",
            AntiOverlay(),
            "ゲーム画面から不要な視覚的オーバーレイを削除します。",
        ),
        feature(
            "SuperSight",
            SuperSight(),
            "視覚情報を強化します。",
        ),
        feature(
            "XRay",
            XRay(),
            "ブロックを透視して、鉱石、洞窟、その他の隠された構造物を見つけられるようにします。",
        ),
        feature(
            "CameraConfig",
            CameraConfig(),
            "カメラの設定を変更します。",
        ),
        feature(
            "Radar",
            Radar(),
            " レーダーを表示します。",
        ),
    )
