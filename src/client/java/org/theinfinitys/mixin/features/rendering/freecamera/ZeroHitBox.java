package org.theinfinitys.mixin.features.rendering.freecamera; // あなたのパッケージ名に置き換えてください

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.camera.FreeCamera;

@Mixin(Entity.class)
public abstract class ZeroHitBox {

  // 衝突ボックスを返すメソッドをフックする
  @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
  private void zeroBoundingBox(CallbackInfoReturnable<Box> cir) {
    if (!InfiniteClient.INSTANCE.isFeatureEnabled(FreeCamera.class)) return;
    Entity entity = (Entity) (Object) this;
    if (entity instanceof PlayerEntity) {
      Box zeroBox = new Box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
      cir.setReturnValue(zeroBox);
      cir.cancel();
    }
  }
}
