package org.infinite.mixin.features.fighting.counterattack;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.infinite.InfiniteClient;
import org.infinite.features.fighting.counter.CounterAttack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class DamageListenerMixin {

    @Inject(method = "onEntityDamage", at = @At("HEAD"))
    private void infinite$onEntityDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        CounterAttack counterAttack = InfiniteClient.INSTANCE.getFeature(CounterAttack.class);
        if(counterAttack!=null&&counterAttack.isEnabled()){
            counterAttack.receive(packet);
        }
    }
}