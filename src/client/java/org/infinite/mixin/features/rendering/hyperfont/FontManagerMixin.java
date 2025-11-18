package org.infinite.mixin.features.rendering.hyperfont;

import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.TextRenderer;
import org.infinite.features.rendering.font.HyperTextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontManager.class)
public abstract class FontManagerMixin {
  @Inject(
      at = @At("HEAD"),
      method = "createTextRenderer()Lnet/minecraft/client/font/TextRenderer;",
      cancellable = true)
  public void onCreateTextRenderer(CallbackInfoReturnable<TextRenderer> cir) {
    cir.setReturnValue(new HyperTextRenderer((FontManager) (Object) this));
  }
}
