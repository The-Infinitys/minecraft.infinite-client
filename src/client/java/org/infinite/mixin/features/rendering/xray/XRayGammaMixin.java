package org.infinite.mixin.features.rendering.xray;

import net.minecraft.block.AbstractBlock;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.xray.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

  @Inject(method = "getLuminance", at = @At("RETURN"), cancellable = true)
  private void xray$forceFullLuminance(CallbackInfoReturnable<Integer> cir) {
    int originalValue = cir.getReturnValue();
    int modifiedValue = 15;
    if (InfiniteClient.INSTANCE.isFeatureEnabled(XRay.class)) {
      cir.setReturnValue(modifiedValue); // 非常に明るい値
    } else {
      cir.setReturnValue(originalValue);
    }
  }
}
