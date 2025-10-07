package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.movement.AutoMine
import org.theinfinitys.features.movement.AutoWalk
import org.theinfinitys.features.movement.FastBreak
import org.theinfinitys.features.movement.FeatherWalk
import org.theinfinitys.features.movement.FreeCamera
import org.theinfinitys.features.movement.Freeze
import org.theinfinitys.features.movement.SafeWalk
import org.theinfinitys.features.movement.SuperSprint
import org.theinfinitys.features.movement.WaterHover

val movement =
    listOf(
        feature(
            "SuperSprint",
            SuperSprint(),
            "スプリントを拡張します",
        ),
        feature(
            "SafeWalk",
            SafeWalk(),
            "地形の橋から落下せずに安全に移動できるようになります",
        ),
        feature(
            "Freeze",
            Freeze(),
            "有効にしている間は、サーバーにデータを送信しません",
        ),
        feature(
            "AutoWalk",
            AutoWalk(),
            "自動で前進します",
        ),
        feature(
            "AutoMine",
            AutoMine(),
            "自動でブロックを採掘します",
        ),
        feature(
            "FastBreak",
            FastBreak(),
            "ブロックを壊す際のインターバルを削除します",
        ),
        feature(
            "FeatherWalk",
            FeatherWalk(),
            "畑などで走ったりしないようにします。",
        ),
        feature(
            "WaterHover",
            WaterHover(),
            "水中で勝手に沈まないようにします。",
        ),
        feature("FreeCamera", FreeCamera(), "いつでもスペクテイターモードに慣れます。"),
    )
