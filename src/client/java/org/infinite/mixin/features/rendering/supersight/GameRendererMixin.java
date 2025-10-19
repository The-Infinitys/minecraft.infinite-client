package org.infinite.mixin.features.rendering.supersight;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.camera.CameraConfig;
import org.infinite.features.rendering.sight.SuperSight;
import org.infinite.features.rendering.xray.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

  @Inject(
      at = @At("HEAD"),
      method = "getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F",
      cancellable = true)
  private static void onGetNightVisionStrength(
      LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
    if (InfiniteClient.INSTANCE.isSettingEnabled(SuperSight.class, "NightVision")
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
