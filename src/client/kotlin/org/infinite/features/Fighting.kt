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
import org.infinite.utils.Translation

val fighting =
    listOf(
        feature(
            "KillAura",
            KillAura(),
            Translation.t("fighting.killaura.description"),
        ),
        feature(
            "Reach",
            Reach(),
            Translation.t("fighting.reach.description"),
        ),
        feature(
            "NoAttack",
            NoAttack(),
            Translation.t("fighting.noattack.description"),
        ),
        feature(
            "PlayerManager",
            PlayerManager(),
            Translation.t("fighting.playermanager.description"),
        ),
        feature(
            "SuperAttack",
            SuperAttack(),
            Translation.t("fighting.superattack.description"),
        ),
        feature(
            "CounterAttack",
            CounterAttack(),
            Translation.t("fighting.counterattack.description"),
        ),
        feature(
            "ImpactAttack",
            ImpactAttack(),
            Translation.t("fighting.impactattack.description"),
        ),
        feature(
            "ArmorManager",
            ArmorManager(),
            Translation.t("fighting.armormanager.description"),
        ),
        feature(
            "Gunner",
            Gunner(),
            Translation.t("fighting.gunner.description"),
        ),
        feature(
            "AutoTotem",
            AutoTotem(),
            Translation.t("fighting.autototem.description"),
        ),
        feature(
            "AimAssist",
            AimAssist(),
            Translation.t("fighting.aimassist.description"),
        ),
        feature(
            "LockOn",
            LockOn(),
            Translation.t("fighting.lockon.description"),
        ),
    )
