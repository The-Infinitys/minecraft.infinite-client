package org.infinite.mixin.infinite.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.LoadingWidget;
import org.infinite.InfiniteClient;
import org.infinite.global.rendering.theme.ThemeSetting;
import org.infinite.global.rendering.theme.widget.LoadingWidgetRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingWidget.class)
public class LoadingWidgetMixin {

  @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
  private void infiniteClient$renderWidget(
      DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    ThemeSetting themeSetting = InfiniteClient.INSTANCE.getGlobalFeature(ThemeSetting.class);
    if (themeSetting != null && themeSetting.isEnabled()) {
      LoadingWidgetRenderer renderer = new LoadingWidgetRenderer((LoadingWidget) (Object) this);
      renderer.renderWidget(context, mouseX, mouseY, delta);
      ci.cancel();
    }
  }
}
