package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.movement.braek.FastBreak
import org.theinfinitys.features.movement.feather.FeatherWalk
import org.theinfinitys.features.movement.fly.SuperFly
import org.theinfinitys.features.movement.freeze.Freeze
import org.theinfinitys.features.movement.mine.AutoMine
import org.theinfinitys.features.movement.sprint.SuperSprint
import org.theinfinitys.features.movement.tool.AutoTool
import org.theinfinitys.features.movement.walk.AutoWalk
import org.theinfinitys.features.movement.walk.SafeWalk
import org.theinfinitys.features.movement.water.WaterHover
import org.theinfinitys.utils.Translation

val movement =
    listOf(
        feature("AutoTool", AutoTool(), Translation.t("movement.autotool.description")),
        feature(
            "SuperSprint",
            SuperSprint(),
            Translation.t("movement.SuperSprint.description"),
        ),
        feature(
            "SafeWalk",
            SafeWalk(),
            Translation.t("movement.SafeWalk.description"),
        ),
        feature(
            "Freeze",
            Freeze(),
            Translation.t("movement.Freeze.description"),
        ),
        feature(
            "AutoWalk",
            AutoWalk(),
            Translation.t("movement.AutoWalk.description"),
        ),
        feature(
            "AutoMine",
            AutoMine(),
            Translation.t("movement.AutoMine.description"),
        ),
        feature(
            "FastBreak",
            FastBreak(),
            Translation.t("movement.FastBreak.description"),
        ),
        feature(
            "FeatherWalk",
            FeatherWalk(),
            Translation.t("movement.FeatherWalk.description"),
        ),
        feature(
            "WaterHover",
            WaterHover(),
            Translation.t("movement.WaterHover.description"),
        ),
        feature(
            "SuperFly",
            SuperFly(),
            Translation.t("movement.SuperFly.description"),
        ),
    )
