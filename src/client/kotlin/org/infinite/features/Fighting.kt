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
            "KillAura",
            KillAura(),
            "feature.fighting.killaura.description",
        ),
        feature(
            "Reach",
            Reach(),
            "feature.fighting.reach.description",
        ),
        feature(
            "NoAttack",
            NoAttack(),
            "feature.fighting.noattack.description",
        ),
        feature(
            "PlayerManager",
            PlayerManager(),
            "feature.fighting.playermanager.description",
        ),
        feature(
            "SuperAttack",
            SuperAttack(),
            "feature.fighting.superattack.description",
        ),
        feature(
            "CounterAttack",
            CounterAttack(),
            "feature.fighting.counterattack.description",
        ),
        feature(
            "ImpactAttack",
            ImpactAttack(),
            "feature.fighting.impactattack.description",
        ),
        feature(
            "ArmorManager",
            ArmorManager(),
            "feature.fighting.armormanager.description",
        ),
        feature(
            "Gunner",
            Gunner(),
            "feature.fighting.gunner.description",
        ),
        feature(
            "AutoTotem",
            AutoTotem(),
            "feature.fighting.autototem.description",
        ),
        feature(
            "AimAssist",
            AimAssist(),
            "feature.fighting.aimassist.description",
        ),
        feature(
            "LockOn",
            LockOn(),
            "feature.fighting.lockon.description",
        ),
    )
