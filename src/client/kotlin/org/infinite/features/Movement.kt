package org.infinite.features

import org.infinite.feature
import org.infinite.features.movement.braek.FastBreak
import org.infinite.features.movement.fall.AntiFall
import org.infinite.features.movement.feather.FeatherWalk
import org.infinite.features.movement.fly.SuperFly
import org.infinite.features.movement.freeze.Freeze
import org.infinite.features.movement.hunger.AntiHunger
import org.infinite.features.movement.mine.AutoMine
import org.infinite.features.movement.sprint.SuperSprint
import org.infinite.features.movement.tool.AutoTool
import org.infinite.features.movement.walk.AutoWalk
import org.infinite.features.movement.walk.SafeWalk
import org.infinite.features.movement.water.WaterHover

val movement =
    listOf(
        feature(
            "AntiHunger",
            AntiHunger(),
            "feature.movement.antihunger.description",
        ),
        feature(
            "AntiFall",
            AntiFall(),
            "feature.movement.antifall.description",
        ),
        feature(
            "AutoTool",
            AutoTool(),
            "feature.movement.autotool.description",
        ),
        feature(
            "SuperSprint",
            SuperSprint(),
            "feature.movement.supersprint.description",
        ),
        feature(
            "SafeWalk",
            SafeWalk(),
            "feature.movement.safewalk.description",
        ),
        feature(
            "Freeze",
            Freeze(),
            "feature.movement.freeze.description",
        ),
        feature(
            "AutoWalk",
            AutoWalk(),
            "feature.movement.autowalk.description",
        ),
        feature(
            "AutoMine",
            AutoMine(),
            "feature.movement.automine.description",
        ),
        feature(
            "FastBreak",
            FastBreak(),
            "feature.movement.fastbreak.description",
        ),
        feature(
            "FeatherWalk",
            FeatherWalk(),
            "feature.movement.featherwalk.description",
        ),
        feature(
            "WaterHover",
            WaterHover(),
            "feature.movement.waterhover.description",
        ),
        feature(
            "SuperFly",
            SuperFly(),
            "feature.movement.superfly.description",
        ),
    )
