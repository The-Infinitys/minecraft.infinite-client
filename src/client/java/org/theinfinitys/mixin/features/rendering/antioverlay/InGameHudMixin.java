package org.theinfinitys.mixin.features.rendering.antioverlay;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.overlay.AntiOverlay;

@Mixin(InGameHud.class)
public class InGameHudMixin {

  @Inject(
      at = @At("HEAD"),
      method =
          "renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V",
      cancellable = true)
  private void onRenderOverlay(
      DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
    if (texture == null) {
      return;
    }

    String path = texture.getPath();

    // 1. カボチャのぼかしオーバーレイのキャンセル
    if ("textures/misc/pumpkinblur.png".equals(path)
        && InfiniteClient.INSTANCE.isSettingEnabled(AntiOverlay.class, "NoPumpkinOverlay")) {
      ci.cancel();
      return;
    }

    // 2. パウダー・スノーのアウトライン/オーバーレイのキャンセル
    // オリジナルコードでは"NoDarknessOverlay"が使われていますが、テクスチャ名から判断して修正を提案します。
    // （"NoDarknessOverlay"が「パウダー・スノーのアウトライン」も制御していると仮定します。）
    if ("textures/misc/powder_snow_outline.png".equals(path)
        && InfiniteClient.INSTANCE.isSettingEnabled(AntiOverlay.class, "NoDarknessOverlay")) {
      ci.cancel();
    }
  }

  @Inject(at = @At("HEAD"), method = "renderVignetteOverlay", cancellable = true)
  private void onRenderVignetteOverlay(DrawContext context, Entity entity, CallbackInfo ci) {

    // ビネット（暗さ）オーバーレイのキャンセル
    // 「NoDarknessOverlay」設定を流用し、ビネットも制御すると仮定します。
    if (InfiniteClient.INSTANCE.isSettingEnabled(AntiOverlay.class, "NoDarknessOverlay")) {
      ci.cancel();
    }
  }
}
