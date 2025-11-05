package org.infinite.mixin.features.movement.linearbreak;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.infinite.InfiniteClient;
import org.infinite.features.movement.braek.LinearBreak;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LinearBreakが有効な場合に、ClientPlayerInteractionManager.attackBlock()の実行をキャンセルし、
 * 通常のブロック破壊操作を無効化するためのMixin。
 */
@Mixin(ClientPlayerInteractionManager.class)
public class CancelNormalAttackBlockMixin {

  /**
   * ClientPlayerInteractionManager#attackBlock(BlockPos, Direction) の先頭に介入します。
   * このメソッドは、プレイヤーが左クリックを押し始めたときに通常のブロック破壊を開始するために呼ばれます。 * @param pos 破壊対象のブロック位置
   *
   * @param direction 破壊する面
   * @param cir コールバック情報（戻り値の操作に使用）
   */
  @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
  private void infinite$cancelAttackBlockIfLinearBreakActive(
      BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(LinearBreak.class)) {
      LinearBreak linearBreak = InfiniteClient.INSTANCE.getFeature(LinearBreak.class);
      if (linearBreak != null && pos != linearBreak.getCurrentBreakingPos()) {
        linearBreak.add(pos);
        cir.setReturnValue(false);
        cir.cancel();
      }
    }
  }
}
