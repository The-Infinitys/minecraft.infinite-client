package org.theinfinitys.mixin.features.automatic.aimode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.MutableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.automatic.aimode.AIMode;

@Mixin(InGameHud.class)
public class AIModeHudMixin {

  @Inject(method = "render", at = @At("TAIL"))
  private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(AIMode.class)) {
      MinecraftClient client = MinecraftClient.getInstance();
      MutableText text = InfiniteClient.INSTANCE.rainbowText("Infinite Client AI Mode");
      int textWidth = client.textRenderer.getWidth(text);
      int x = client.getWindow().getScaledWidth() - textWidth - 2; // 2 pixels padding from right
      int y =
          client.getWindow().getScaledHeight()
              - client.textRenderer.fontHeight
              - 2; // 2 pixels padding from bottom
      context.drawTextWithShadow(
          client.textRenderer, text, x, y, 0xFFFFFFFF); // White shadow for readability
    }
  }
}
