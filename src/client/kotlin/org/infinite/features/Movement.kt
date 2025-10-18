package org.infinite.features

import org.infinite.feature
import org.infinite.features.movement.braek.FastBreak
import org.infinite.features.movement.feather.FeatherWalk
import org.infinite.features.movement.fly.SuperFly
import org.infinite.features.movement.freeze.Freeze
import org.infinite.features.movement.mine.AutoMine
import org.infinite.features.movement.sprint.SuperSprint
import org.infinite.features.movement.tool.AutoTool
import org.infinite.features.movement.walk.AutoWalk
import org.infinite.features.movement.walk.SafeWalk
import org.infinite.features.movement.water.WaterHover
import org.infinite.utils.Translation

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
