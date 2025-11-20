package org.infinite.features.fighting

import org.infinite.features.Feature
import org.infinite.features.FeatureCategory
import org.infinite.features.fighting.aimassist.AimAssist
import org.infinite.features.fighting.armor.ArmorManager
import org.infinite.features.fighting.berserk.Berserker
import org.infinite.features.fighting.counter.CounterAttack
import org.infinite.features.fighting.gun.Gunner
import org.infinite.features.fighting.impact.ImpactAttack
import org.infinite.features.fighting.killaura.KillAura
import org.infinite.features.fighting.lockon.LockOn
import org.infinite.features.fighting.mace.HyperMace
import org.infinite.features.fighting.mace.MaceAssist
import org.infinite.features.fighting.reach.Reach
import org.infinite.features.fighting.shield.AutoShield
import org.infinite.features.fighting.superattack.SuperAttack
import org.infinite.features.fighting.totem.AutoTotem

class FightingFeatureCategory :
    FeatureCategory(
        "Fighting",
        mutableListOf(
            Feature(AutoShield()),
            Feature(HyperMace()),
            Feature(MaceAssist()),
            Feature(KillAura()),
            Feature(Berserker()),
            Feature(
                Reach(),
            ),
            Feature(
                SuperAttack(),
            ),
            Feature(
                CounterAttack(),
            ),
            Feature(
                ImpactAttack(),
            ),
            Feature(
                ArmorManager(),
            ),
            Feature(
                Gunner(),
            ),
            Feature(
                AutoTotem(),
            ),
            Feature(
                AimAssist(),
            ),
            Feature(
                LockOn(),
            ),
        ),
    )
