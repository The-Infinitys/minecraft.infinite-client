package org.infinite.mixin.features.rendering.supersight;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.sight.SuperSight;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPlayerEntity.class)
public abstract class SuperSightMixin extends AbstractClientPlayerEntity {

  public SuperSightMixin(ClientWorld world, GameProfile profile) {
    super(world, profile);
  }

  public boolean hasStatusEffect(RegistryEntry<StatusEffect> effect) {
    // Feature: SuperSight

    // NightVision
    if (effect == StatusEffects.NIGHT_VISION
        && InfiniteClient.INSTANCE.isSettingEnabled(SuperSight.class, "FullBright")) return true;

    // AntiBlind (BLINDNESS, DARKNESS)
    if (InfiniteClient.INSTANCE.isSettingEnabled(SuperSight.class, "AntiBlind")) {
      if (effect == StatusEffects.BLINDNESS || effect == StatusEffects.DARKNESS) return false;
    }

    // 💡 修正点: 無限再帰を防ぐため、superを使って元のメソッドを呼び出す
    return super.hasStatusEffect(effect);

    // NOTE: ClientPlayerEntityはabstractではないため、thisを ClientPlayerEntity にキャストして呼び出している可能性があります。
    // より確実な方法は @Redirect または @Overwrite を使用することですが、
    // 現在の構造を維持するなら super を使用してください。
  }
}
