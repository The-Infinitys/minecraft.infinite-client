package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.fighting.*
import org.theinfinitys.utils.translation.Translation



val fighting =
    listOf(
        feature(
            "KillAura",
            KillAura(),
            Translation.t("fighting.KillAura.description"),
        ),
        feature(
            "Reach",
            Reach(),
            Translation.t("fighting.Reach.description"),
        ),
        feature(
            "NoAttack",
            NoAttack(),
            Translation.t("fighting.NoAttack.description"),
        ),
        feature(
            "PlayerManager",
            PlayerManager(),
            Translation.t("fighting.PlayerManager.description"),
        ),
        feature(
            "SuperAttack",
            SuperAttack(),
            Translation.t("fighting.SuperAttack.description"),
        ),
        feature(
            "CounterAttack",
            CounterAttack(),
            Translation.t("fighting.CounterAttack.description"),
        ),
        feature(
            "ImpactAttack",
            ImpactAttack(),
            Translation.t("fighting.ImpactAttack.description"),
        ),
    )
