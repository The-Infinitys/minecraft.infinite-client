package org.theinfinitys.mixin.rendering.detailinfo;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.DetailInfo;

@Mixin(BarrelBlockEntity.class)
public abstract class BarrelMixin {
  @Unique
  private boolean shouldCancel() {
    // 設定チェックのロジックを統合
    DetailInfo detailInfo = InfiniteClient.INSTANCE.getFeature(DetailInfo.class);
    return detailInfo != null
        && InfiniteClient.INSTANCE.isSettingEnabled(DetailInfo.class, "InnerChest")
        && detailInfo.getShouldCancelScanScreen();
  }

  @Unique private boolean cancelFlag = false;

  // サウンドの停止 (playSound)
  @Inject(
      method = "playSound",
      at =
          @At(
              value = "HEAD",
              target =
                  "Lnet/minecraft/block/entity/BarrelBlockEntity;playSound(Lnet/minecraft/block/BlockState;Lnet/minecraft/sound/SoundEvent;)V"),
      cancellable = true)
  private void infiniteClient$cancelBarrelSound(
      BlockState state, SoundEvent soundEvent, CallbackInfo ci) {
    if (shouldCancel()) {
      cancelFlag = true;
      ci.cancel();
    } else if (cancelFlag) {
      cancelFlag = false;
      ci.cancel();
    }
  }

  // アニメーションの停止 (setOpen)
  @Inject(
      method = "setOpen",
      at =
          @At(
              value = "HEAD",
              target =
                  "Lnet/minecraft/block/entity/BarrelBlockEntity;setOpen(Lnet/minecraft/block/BlockState;Z)V"),
      cancellable = true)
  private void infiniteClient$cancelBarrelAnimation(
      BlockState state, boolean open, CallbackInfo ci) {
    if (shouldCancel()) {
      ci.cancel();
    }
  }
}
