package org.theinfinitys.mixin.features.rendering.xray;

import net.minecraft.block.AbstractBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.xray.XRay;

// 対象とするクラスはMinecraftのバージョンによって異なります
@Mixin(AbstractBlock.AbstractBlockState.class)
public class XRayGammaMixin {
  // GameOptions#getGamma()などのガンマ値を取得するメソッドにInject
  // 適切なターゲットメソッドはMinecraftのバージョンとリフレクション情報に依存します
  @Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true)
  private void xray$forceFullLight(CallbackInfoReturnable<Float> cir) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(XRay.class)) {
      cir.setReturnValue(1f); // 非常に明るい値
      cir.cancel(); // 元のメソッドの実行をキャンセル
    }
  }

  @Inject(method = "getLuminance", at = @At("HEAD"), cancellable = true)
  private void xray$forceFullLuminance(CallbackInfoReturnable<Integer> cir) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(XRay.class)) {
      cir.setReturnValue(15); // 非常に明るい値
      cir.cancel(); // 元のメソッドの実行をキャンセル
    }
  }
}
