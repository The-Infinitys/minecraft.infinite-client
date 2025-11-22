package org.infinite.mixin.infinite.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ScrollableWidget;
import org.infinite.InfiniteClient;
import org.infinite.global.rendering.theme.ThemeSetting;
import org.infinite.global.rendering.theme.widget.ScrollbarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScrollableWidget.class)
public abstract class ScrollableWidgetMixin {

  @Inject(method = "drawScrollbar", at = @At("HEAD"), cancellable = true)
  protected void infiniteClient$drawScrollbar(
      DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
    ThemeSetting themeSetting = InfiniteClient.INSTANCE.getGlobalFeature(ThemeSetting.class);
    if (themeSetting != null && themeSetting.isEnabled()) {
      ScrollbarRenderer renderer = new ScrollbarRenderer((ScrollableWidget) (Object) this);
      renderer.renderScrollbar(
          context, mouseX, mouseY, 0.0f); // delta is not used in original drawScrollbar
      ci.cancel();
    }
  }
}
