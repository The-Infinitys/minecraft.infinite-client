package org.theinfinitys.mixin.features.rendering.detailinfo;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.detailinfo.DetailInfo;

// ターゲットクラスの直接のスーパークラス（LootableContainerBlockEntity）を継承させるか、継承を外す
// 元のコードの記述に従い、抽象クラスとして定義します。
@Mixin(ChestBlockEntity.class)
public abstract class ChestMixin {
  @Unique
  private static boolean shouldCancel() {
    // 設定チェックのロジックを統合
    DetailInfo detailInfo = InfiniteClient.INSTANCE.getFeature(DetailInfo.class);
    return detailInfo != null
        && InfiniteClient.INSTANCE.isSettingEnabled(DetailInfo.class, "InnerChest")
        && detailInfo.getShouldCancelScanScreen();
  }

  @Unique private static Boolean cancelFlag = false;

  // アニメーションの停止 (getAnimationProgress)
  @Inject(method = "getAnimationProgress", at = @At("RETURN"), cancellable = true)
  private void infiniteClient$forceZeroChestAnimation(
      float tickProgress, CallbackInfoReturnable<Float> cir) {
    if (shouldCancel()) {
      cancelFlag = true;
    }
    if (cancelFlag) {
      if (cir.getReturnValue() == 1.0f) {
        cancelFlag = false;
      }
      cir.setReturnValue(0.0F);
    }
  }

  // サウンドの停止 (playSound - static)
  @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
  private static void infiniteClient$cancelChestSound(
      World world, BlockPos pos, BlockState state, SoundEvent soundEvent, CallbackInfo ci) {
    if (cancelFlag) {
      ci.cancel();
    }
  }
}
