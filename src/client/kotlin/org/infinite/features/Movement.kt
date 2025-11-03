package org.infinite.features

import org.infinite.feature
import org.infinite.features.movement.braek.FastBreak
import org.infinite.features.movement.braek.LinearBreak
import org.infinite.features.movement.braek.VeinBreak
import org.infinite.features.movement.fall.AntiFall
import org.infinite.features.movement.feather.FeatherWalk
import org.infinite.features.movement.fly.SuperFly
import org.infinite.features.movement.freeze.Freeze
import org.infinite.features.movement.hunger.AntiHunger
import org.infinite.features.movement.mine.AutoMine
import org.infinite.features.movement.move.QuickMove
import org.infinite.features.movement.sprint.SuperSprint
import org.infinite.features.movement.step.HighStep
import org.infinite.features.movement.vehicle.HoverVehicle
import org.infinite.features.movement.walk.AutoWalk
import org.infinite.features.movement.walk.SafeWalk
import org.infinite.features.movement.water.WaterHover

val movement =
    listOf(
        feature(
            "HoverVehicle",
            HoverVehicle(),
            "feature.movement.hovervehicle.description",
        ),
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
            "LinearBreak",
            LinearBreak(),
            "feature.movement.linearbreak.description",
        ),
        feature(
            "VeinBreak",
            VeinBreak(),
            "feature.movement.veinbreak.description",
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
        feature(
            "HighStep",
            HighStep(),
            "feature.movement.highstep.description",
        ),
        feature(
            "QuickMove",
            QuickMove(),
            "feature.movement.quickmove.description",
        ),
    )
