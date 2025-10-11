package org.theinfinitys.mixin.rendering.detailinfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient; // Featureを取得するために必要と仮定
import org.theinfinitys.features.rendering.gui.DetailInfo;
import org.theinfinitys.features.rendering.gui.DetailInfoRenderer;

@Mixin(InGameHud.class)
public abstract class DetailInfoRenderingMixin {

  // InGameHudのrenderメソッドの描画処理の最後にフック
  @Inject(method = "render", at = @At("TAIL"))
  private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
    MinecraftClient client = MinecraftClient.getInstance();
    // プレイヤーがnullの場合、またはワールドにいない場合は描画しない
    if (client.player == null || client.world == null) {
      return;
    }
    if (InfiniteClient.INSTANCE.isFeatureEnabled(DetailInfo.class)) {
      DetailInfo detailInfo = InfiniteClient.INSTANCE.getFeature(DetailInfo.class);
      if (detailInfo != null) DetailInfoRenderer.INSTANCE.render(context, client, detailInfo);
    }
  }
}
