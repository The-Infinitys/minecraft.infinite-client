package org.infinite.mixin.infinite.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.infinite.InfiniteClient;
import org.infinite.global.rendering.theme.ThemeSetting;
import org.infinite.utils.rendering.ColorKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

  @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
  private void infinite$renderBackground(
      DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(ThemeSetting.class)) {
      Screen screen = (Screen) (Object) this; // Mixin対象のインスタンスを取得

      // 背景色で画面全体を塗りつぶす
      int backgroundColor = InfiniteClient.INSTANCE.getCurrentColors().getBackgroundColor();
      int color = ColorKt.transparent(backgroundColor, 128);
      context.fill(0, 0, screen.width, screen.height, color);

      ci.cancel(); // 元の背景描画をキャンセル
    }
  }
}
