package org.infinite.features

import org.infinite.feature
import org.infinite.features.fighting.aimassist.AimAssist
import org.infinite.features.fighting.armor.ArmorManager
import org.infinite.features.fighting.counter.CounterAttack
import org.infinite.features.fighting.gun.Gunner
import org.infinite.features.fighting.impact.ImpactAttack
import org.infinite.features.fighting.killaura.KillAura
import org.infinite.features.fighting.lockon.LockOn
import org.infinite.features.fighting.reach.Reach
import org.infinite.features.fighting.shield.AutoShield
import org.infinite.features.fighting.superattack.SuperAttack
import org.infinite.features.fighting.totem.AutoTotem

val fighting =
    listOf(
        feature("AutoShield", AutoShield()),
        feature(
            "KillAura",
            KillAura(),
        ),
        feature(
            "Reach",
            Reach(),
        ),
        feature(
            "SuperAttack",
            SuperAttack(),
        ),
        feature(
            "CounterAttack",
            CounterAttack(),
        ),
        feature(
            "ImpactAttack",
            ImpactAttack(),
        ),
        feature(
            "ArmorManager",
            ArmorManager(),
        ),
        feature(
            "Gunner",
            Gunner(),
        ),
        feature(
            "AutoTotem",
            AutoTotem(),
        ),
        feature(
            "AimAssist",
            AimAssist(),
        ),
        feature(
            "LockOn",
            LockOn(),
        ),
    )
