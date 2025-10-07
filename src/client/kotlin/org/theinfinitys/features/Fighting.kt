package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.fighting.AutoTotem
import org.theinfinitys.features.fighting.CounterAttack
import org.theinfinitys.features.fighting.ImpactAttack
import org.theinfinitys.features.fighting.KillAura
import org.theinfinitys.features.fighting.NoAttack
import org.theinfinitys.features.fighting.PlayerManager
import org.theinfinitys.features.fighting.Reach
import org.theinfinitys.features.fighting.SuperAttack

val fighting =
    listOf(
        feature(
            "KillAura",
            KillAura(),
            "近くのエンティティを自動的に攻撃します。",
        ),
        feature(
            "Reach",
            Reach(),
            "プレイヤーの到達距離を拡張し、より遠くのブロックやエンティティを操作できるようにします。",
        ),
        feature(
            "NoAttack",
            NoAttack(),
            "村人やペットなどの特定のエンティティへの攻撃を防止します。",
        ),
        feature(
            "PlayerManager",
            PlayerManager(),
            "フレンドと敵のプレイヤーを管理します。",
        ),
        feature(
            "SuperAttack",
            SuperAttack(),
            "自動でクリティカル攻撃を出します。",
        ),
        feature(
            "CounterAttack",
            CounterAttack(),
            "エンティティから攻撃を受けた際に、自動的に反撃します。",
        ),
        feature(
            "ImpactAttack",
            ImpactAttack(),
            "エンティティに連続攻撃を仕掛けます。",
        ),
        feature("AutoTotem", AutoTotem(), "自動でトーテムを持ちます"),
    )
