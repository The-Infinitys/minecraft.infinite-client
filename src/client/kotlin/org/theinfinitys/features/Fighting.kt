package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.fighting.CounterAttack
import org.theinfinitys.features.fighting.ImpactAttack
import org.theinfinitys.features.fighting.KillAura
import org.theinfinitys.features.fighting.NoAttack
import org.theinfinitys.features.fighting.PlayerManager
import org.theinfinitys.features.fighting.Reach
import org.theinfinitys.features.fighting.SuperAttack
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
