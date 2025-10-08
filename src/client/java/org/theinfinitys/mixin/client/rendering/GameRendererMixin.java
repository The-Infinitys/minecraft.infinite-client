package org.theinfinitys.mixin.client.rendering;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.CameraConfig;
import org.theinfinitys.features.rendering.SuperSight;
import org.theinfinitys.features.rendering.XRay;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

  @Inject(
      at = @At("HEAD"),
      method = "getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F",
      cancellable = true)
  private static void onGetNightVisionStrength(
      LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
    if (InfiniteClient.INSTANCE.isSettingEnabled(SuperSight.class, "FullBright")
        || InfiniteClient.INSTANCE.isFeatureEnabled(XRay.class)) cir.setReturnValue(1.0f);
  }

  @Inject(
      at = @At("HEAD"),
      method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V",
      cancellable = true)
  private void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isSettingEnabled(CameraConfig.class, "AntiHurtTilt")) ci.cancel();
  }
}
