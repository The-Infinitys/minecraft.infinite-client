package org.theinfinitys.mixin.features.rendering.detailinfo;

import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.ContainerUser; // onOpen/onCloseの引数として追加
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.detailinfo.DetailInfo;

@Mixin(ShulkerBoxBlockEntity.class)
public class ShulkerBoxMixin {
  @Unique
  private boolean shouldCancel() {
    // 設定チェックのロジックを統合
    DetailInfo detailInfo = InfiniteClient.INSTANCE.getFeature(DetailInfo.class);
    // DetailInfo.class の代わりに ShulkerBoxBlockEntity.class を使うか検討
    // ロジックは現状維持
    return detailInfo != null
        && InfiniteClient.INSTANCE.isSettingEnabled(DetailInfo.class, "InnerChest")
        && detailInfo.getShouldCancelScanScreen();
  }

  // ChestMixinに合わせて、アニメーション制御用のフラグとして使用
  @Unique private static boolean cancelFlag = false;

  // アニメーションの停止 (getAnimationProgress)
  // ChestMixinと同様に、RETURNで戻り値を操作する
  @Inject(
      method = "getAnimationProgress",
      at = @At("RETURN"), // 戻り値取得後
      cancellable = true)
  private void infiniteClient$forceZeroShulkerAnimation(
      float tickProgress, CallbackInfoReturnable<Float> cir) {
    if (shouldCancel()) {
      cancelFlag = true;
    }
    if (cancelFlag) {
      // 完全に閉じたらフラグをリセット (ChestMixinのロジックを適用)
      if (cir.getReturnValue() == 1.0f) {
        cancelFlag = false;
      }
      cir.setReturnValue(0.0F);
    }
  }

  // サウンドの停止 (onOpen)
  // shouldCancel() が true の時に直接キャンセル
  @Inject(method = "onOpen", at = @At("HEAD"), cancellable = true)
  public void infiniteClient$cancelShulkerOpenSound(ContainerUser viewer, CallbackInfo ci) {
    if (cancelFlag) {
      ci.cancel();
    }
  }

  // サウンドの停止 (onClose)
  // shouldCancel() が true の時に直接キャンセル
  @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
  // onOpen/onCloseはContainerUserを引数に取るため追加
  public void infiniteClient$cancelShulkerCloseSound(ContainerUser viewer, CallbackInfo ci) {
    if (cancelFlag) {
      ci.cancel();
    }
  }
}
