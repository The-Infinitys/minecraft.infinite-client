package org.infinite.mixin.features.rendering.hyperui;

import net.minecraft.client.gui.hud.InGameHud;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.ui.HyperUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class CancelRenderUiMixin {
  // HyperUiが有効かどうかをチェックするヘルパーメソッド
  @Unique
  private static boolean shouldCancel() {
    // nullチェックは省略していますが、必要に応じてInfiniteClient.INSTANCEがnullでないことを確認してください
    return InfiniteClient.INSTANCE.isFeatureEnabled(HyperUi.class);
  }

  /**
   * 体力バー（HP）の描画をキャンセルします。 InGameHud#renderHealthBar(MatrixStack) のようなメソッドにインジェクトします。 * @param ci
   * CallbackInfo for canceling the method execution.
   */
  @Inject(method = "renderHealthBar", at = @At("HEAD"), cancellable = true)
  private void infinite$cancelHealthRender(CallbackInfo ci) {
    if (shouldCancel()) ci.cancel();
  }

  @Inject(method = "renderAirBubbles", at = @At("HEAD"), cancellable = true)
  private void infinite$cancelAirBubbles(CallbackInfo ci) {
    if (shouldCancel()) ci.cancel();
  }

  /** 満腹度バー（Food）の描画をキャンセルします。 InGameHud#renderFoodBar(MatrixStack) のようなメソッドにインジェクトします。 */
  @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
  private void infinite$cancelFoodRender(CallbackInfo ci) {
    if (shouldCancel()) ci.cancel();
  }

  /** 防御力/アーマーバー（Armor）の描画をキャンセルします。 InGameHud#renderArmor(MatrixStack) のようなメソッドにインジェクトします。 */
  @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
  private static void infinite$cancelArmorRender(CallbackInfo ci) {
    if (shouldCancel()) ci.cancel();
  }

  // 乗り物の体力（Vehicle Health）の描画も同様に、該当する描画メソッドにインジェクトします。
  // 例: renderMountHealth (バージョンやLoaderによってメソッド名が異なります)
  @Inject(method = "renderMountHealth", at = @At("HEAD"), cancellable = true)
  private void infinite$cancelMountHealthRender(CallbackInfo ci) {
    if (shouldCancel()) ci.cancel();
  }

  @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
  private void infinite$cancelCrossHairRender(CallbackInfo ci) {
    if (shouldCancel()) ci.cancel();
  }

  @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
  private void infinite$cancelHotbarRender(CallbackInfo ci) {
    if (shouldCancel()) ci.cancel();
  }

  @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
  private void infinite$cancelHeldItemTooltipRender(CallbackInfo ci) {
    if (shouldCancel()) ci.cancel();
  }
}
