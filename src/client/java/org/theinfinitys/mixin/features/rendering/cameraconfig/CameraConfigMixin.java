package org.theinfinitys.mixin.features.rendering.cameraconfig;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.camera.CameraConfig;
import org.theinfinitys.features.rendering.overlay.AntiOverlay;

@Mixin(Camera.class)
public abstract class CameraConfigMixin {

  /**
   * clipToSpaceメソッドに渡されるdesiredCameraDistance変数を変更し、 サードパーソンカメラの距離をカスタム設定で上書きします。
   * CameraConfigが有効でない場合は、元の距離が維持されます。
   */
  @ModifyVariable(at = @At("HEAD"), method = "clipToSpace(F)F", argsOnly = true)
  private float changeClipToSpaceDistance(float desiredCameraDistance) {
    // getSettingFloat を使用して設定値を取得します。
    // CameraConfigが無効な場合や設定が見つからない場合は、desiredCameraDistanceが返されます。
    return InfiniteClient.INSTANCE.getSettingFloat(
        CameraConfig.class, "CameraDistance", desiredCameraDistance // フォールバック時のデフォルト値
        );
  }

  /** clipToSpaceメソッドの実行をキャンセルし、 サードパーソンカメラがブロックにクリップされる（めり込んで距離が縮む）のを防ぎます。 */
  @Inject(at = @At("HEAD"), method = "clipToSpace(F)F", cancellable = true)
  private void onClipToSpace(float desiredCameraDistance, CallbackInfoReturnable<Float> cir) {
    // ClipBlock設定が有効な場合、クリッピングを防止するために元の距離をそのまま返します。
    if (InfiniteClient.INSTANCE.isSettingEnabled(CameraConfig.class, "ClipBlock")) {
      cir.setReturnValue(desiredCameraDistance);
    }
  }

  /** getSubmersionTypeメソッドの実行をキャンセルし、 液体内のオーバーレイ（水中の青い霧など）を無効にします。 */
  @Inject(
      at = @At("HEAD"),
      method = "getSubmersionType()Lnet/minecraft/block/enums/CameraSubmersionType;",
      cancellable = true)
  private void onGetSubmersionType(CallbackInfoReturnable<CameraSubmersionType> cir) {
    // NoLiquidOverlay設定が有効な場合、サブマージョンタイプをNONEに設定します。
    if (InfiniteClient.INSTANCE.isSettingEnabled(AntiOverlay.class, "NoLiquidOverlay")) {
      cir.setReturnValue(CameraSubmersionType.NONE);
    }
  }
}
