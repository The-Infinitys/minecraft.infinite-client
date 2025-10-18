package org.infinite.mixin.features.rendering.freecamera;

import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.camera.FreeCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {

  @Inject(
      at = @At("RETURN"),
      method = "isFullCube(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Z",
      cancellable = true)
  private void onIsFullCube(BlockView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(FreeCamera.class)) cir.setReturnValue(false);
  }
}
