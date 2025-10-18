package org.infinite.mixin.features.fighting.gunner;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.infinite.InfiniteClient;
import org.infinite.features.fighting.gun.Gunner;
import org.infinite.features.fighting.gun.GunnerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class GunnerHudMixin {
  @Inject(
      method = "renderHotbar",
      at =
          @At(
              value = "HEAD",
              target =
                  "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"),
      cancellable = true)
  public void renderGunInfo(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(Gunner.class)) {
      GunnerRenderer.INSTANCE.renderInfo(context, tickCounter);
      ci.cancel();
    }
  }

  @Inject(
      method = "renderCrosshair",
      at =
          @At(
              value = "HEAD",
              target =
                  "Lnet/minecraft/client/gui/hud/InGameHud;renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"),
      cancellable = true)
  private void renderGunSight(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(Gunner.class)) {
      GunnerRenderer.INSTANCE.renderSight(context, tickCounter);
      ci.cancel();
    }
  }
}
