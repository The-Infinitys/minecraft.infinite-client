package org.infinite.features

import org.infinite.feature
import org.infinite.features.fighting.aimassist.AimAssist
import org.infinite.features.fighting.armor.ArmorManager
import org.infinite.features.fighting.counter.CounterAttack
import org.infinite.features.fighting.gun.Gunner
import org.infinite.features.fighting.impact.ImpactAttack
import org.infinite.features.fighting.killaura.KillAura
import org.infinite.features.fighting.lockon.LockOn
import org.infinite.features.fighting.noattack.NoAttack
import org.infinite.features.fighting.playermanager.PlayerManager
import org.infinite.features.fighting.reach.Reach
import org.infinite.features.fighting.superattack.SuperAttack
import org.infinite.features.fighting.totem.AutoTotem

val fighting =
    listOf(
        feature(
            "feature.fighting.killaura.name",
            KillAura(),
            "feature.fighting.killaura.description",
        ),
        feature(
            "feature.fighting.reach.name",
            Reach(),
            "feature.fighting.reach.description",
        ),
        feature(
            "feature.fighting.noattack.name",
            NoAttack(),
            "feature.fighting.noattack.description",
        ),
        feature(
            "feature.fighting.playermanager.name",
            PlayerManager(),
            "feature.fighting.playermanager.description",
        ),
        feature(
            "feature.fighting.superattack.name",
            SuperAttack(),
            "feature.fighting.superattack.description",
        ),
        feature(
            "feature.fighting.counterattack.name",
            CounterAttack(),
            "feature.fighting.counterattack.description",
        ),
        feature(
            "feature.fighting.impactattack.name",
            ImpactAttack(),
            "feature.fighting.impactattack.description",
        ),
        feature(
            "feature.fighting.armormanager.name",
            ArmorManager(),
            "feature.fighting.armormanager.description",
        ),
        feature(
            "feature.fighting.gunner.name",
            Gunner(),
            "feature.fighting.gunner.description",
        ),
        feature(
            "feature.fighting.autototem.name",
            AutoTotem(),
            "feature.fighting.autototem.description",
        ),
        feature(
            "feature.fighting.aimassist.name",
            AimAssist(),
            "feature.fighting.aimassist.description",
        ),
        feature(
            "feature.fighting.lockon.name",
            LockOn(),
            "feature.fighting.lockon.description",
        ),
    )
