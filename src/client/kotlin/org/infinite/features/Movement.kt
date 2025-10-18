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

val movement =
    listOf(
        feature("feature.movement.autotool.name", AutoTool(), "feature.movement.autotool.description"),
        feature(
            "feature.movement.supersprint.name",
            SuperSprint(),
            "feature.movement.supersprint.description",
        ),
        feature(
            "feature.movement.safewalk.name",
            SafeWalk(),
            "feature.movement.safewalk.description",
        ),
        feature(
            "feature.movement.freeze.name",
            Freeze(),
            "feature.movement.freeze.description",
        ),
        feature(
            "feature.movement.autowalk.name",
            AutoWalk(),
            "feature.movement.autowalk.description",
        ),
        feature(
            "feature.movement.automine.name",
            AutoMine(),
            "feature.movement.automine.description",
        ),
        feature(
            "feature.movement.fastbreak.name",
            FastBreak(),
            "feature.movement.fastbreak.description",
        ),
        feature(
            "feature.movement.featherwalk.name",
            FeatherWalk(),
            "feature.movement.featherwalk.description",
        ),
        feature(
            "feature.movement.waterhover.name",
            WaterHover(),
            "feature.movement.waterhover.description",
        ),
        feature(
            "feature.movement.superfly.name",
            SuperFly(),
            "feature.movement.superfly.description",
        ),
    )
