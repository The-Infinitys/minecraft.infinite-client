package org.infinite.mixin.infinite.client.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.infinite.libs.client.player.PlayerStatsManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

  @Inject(method = "attackEntity", at = @At("HEAD"))
  private void onClientAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
    PlayerStatsManager.INSTANCE.handleEntityAttack();
  }

  @Shadow @Final private MinecraftClient client;

  @Inject(method = "breakBlock", at = @At("RETURN"))
  private void onClientBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
    if (cir.getReturnValue() && this.client.player != null) {
      PlayerStatsManager.INSTANCE.handleBlockBreak();
    }
  }
}
