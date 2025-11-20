package org.infinite.features.movement

import org.infinite.features.Feature
import org.infinite.features.FeatureCategory
import org.infinite.features.movement.adventure.Adventure
import org.infinite.features.movement.braek.FastBreak
import org.infinite.features.movement.braek.LinearBreak
import org.infinite.features.movement.braek.VeinBreak
import org.infinite.features.movement.brawler.Brawler
import org.infinite.features.movement.builder.Builder
import org.infinite.features.movement.fall.AntiFall
import org.infinite.features.movement.fall.QuickLand
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

class MovementFeatureCategory :
    FeatureCategory(
        "Movement",
        mutableListOf(
            Feature(
                HoverVehicle(),
            ),
            Feature(
                AntiHunger(),
            ),
            Feature(
                AntiFall(),
            ),
            Feature(
                QuickLand(),
            ),
            Feature(
                SuperSprint(),
            ),
            Feature(
                SafeWalk(),
            ),
            Feature(
                Freeze(),
            ),
            Feature(
                AutoWalk(),
            ),
            Feature(
                AutoMine(),
            ),
            Feature(
                FastBreak(),
            ),
            Feature(
                LinearBreak(),
            ),
            Feature(
                VeinBreak(),
            ),
            Feature(
                FeatherWalk(),
            ),
            Feature(
                WaterHover(),
            ),
            Feature(
                SuperFly(),
            ),
            Feature(
                HighStep(),
            ),
            Feature(
                QuickMove(),
            ),
            Feature(
                Adventure(),
            ),
            Feature(
                Brawler(),
            ),
            Feature(
                Builder(),
            ),
        ),
    )
