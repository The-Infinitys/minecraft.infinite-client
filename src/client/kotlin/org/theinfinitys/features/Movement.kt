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
import org.theinfinitys.utils.Translation

val movement =
    listOf(
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
            "FreeCamera",
            FreeCamera(),
            Translation.t("movement.FreeCamera.description"),
        ),
    )
