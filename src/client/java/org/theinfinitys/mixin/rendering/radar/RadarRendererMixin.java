package org.theinfinitys.mixin.rendering.radar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient; // Featureを取得するために必要と仮定
import org.theinfinitys.features.rendering.gui.Radar;
import org.theinfinitys.features.rendering.gui.RadarRenderer;

@Mixin(InGameHud.class)
public abstract class RadarRendererMixin {

  // InGameHudのrenderメソッドの描画処理の最後にフック
  @Inject(method = "render", at = @At("TAIL"))
  private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
    // MinecraftClientインスタンスを取得
    MinecraftClient client = MinecraftClient.getInstance();

    // プレイヤーがnullの場合、またはワールドにいない場合は描画しない
    if (client.player == null || client.world == null) {
      return;
    }

    // Radarフィーチャーのインスタンスを取得 (InfiniteClientにgetFeatureメソッドが存在すると仮定)
    Radar radarFeature = InfiniteClient.INSTANCE.getFeature(Radar.class);

    // Featureが存在し、かつ有効な場合に描画を実行
    if (radarFeature != null && radarFeature.isEnabled()) {
      // RadarRendererに描画処理を委譲
      RadarRenderer.INSTANCE.render(context, client, radarFeature);
    }
  }
}
