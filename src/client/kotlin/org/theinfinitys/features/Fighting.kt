package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.fighting.aura.KillAura
import org.theinfinitys.features.fighting.counter.CounterAttack
import org.theinfinitys.features.fighting.impact.ImpactAttack
import org.theinfinitys.features.fighting.noattack.NoAttack
import org.theinfinitys.features.fighting.playermanager.PlayerManager
import org.theinfinitys.features.fighting.reach.Reach
import org.theinfinitys.features.fighting.superattack.SuperAttack
import org.theinfinitys.utils.Translation

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
    )
