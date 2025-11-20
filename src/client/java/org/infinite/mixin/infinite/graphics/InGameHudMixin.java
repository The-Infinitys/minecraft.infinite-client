package org.infinite.mixin.infinite.graphics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.infinite.InfiniteClient;
import org.infinite.feature.ConfigurableFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

  // InGameHudのrenderメソッドの描画処理の最後にフック
  @Inject(method = "render", at = @At("TAIL"))
  private void onRenderAtTail(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
    InfiniteClient.INSTANCE.handle2dGraphics(context, tickCounter, ConfigurableFeature.Timing.End);
  }

  @Inject(method = "render", at = @At("HEAD"))
  private void onRenderAtHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
    InfiniteClient.INSTANCE.handle2dGraphics(
        context, tickCounter, ConfigurableFeature.Timing.Start);
  }
}
