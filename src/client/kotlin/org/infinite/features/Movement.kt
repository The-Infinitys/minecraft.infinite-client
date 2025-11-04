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
        ),
        feature(
            "AntiHunger",
            AntiHunger(),
        ),
        feature(
            "AntiFall",
            AntiFall(),
        ),
        feature(
            "SuperSprint",
            SuperSprint(),
        ),
        feature(
            "SafeWalk",
            SafeWalk(),
        ),
        feature(
            "Freeze",
            Freeze(),
        ),
        feature(
            "AutoWalk",
            AutoWalk(),
        ),
        feature(
            "AutoMine",
            AutoMine(),
        ),
        feature(
            "FastBreak",
            FastBreak(),
        ),
        feature(
            "LinearBreak",
            LinearBreak(),
        ),
        feature(
            "VeinBreak",
            VeinBreak(),
        ),
        feature(
            "FeatherWalk",
            FeatherWalk(),
        ),
        feature(
            "WaterHover",
            WaterHover(),
        ),
        feature(
            "SuperFly",
            SuperFly(),
        ),
        feature(
            "HighStep",
            HighStep(),
        ),
        feature(
            "QuickMove",
            QuickMove(),
        ),
    )
