package org.infinite.mixin.features.movement.builder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.infinite.InfiniteClient;
import org.infinite.features.movement.builder.Builder;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class BuilderMixin {
  @Shadow private GameMode gameMode;

  // エンティティ攻撃パケットをキャンセル
  @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
  private void infinite$cancelAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(Builder.class)) {
      ci.cancel(); // Builderが有効な場合、エンティティ攻撃をキャンセル
    }
  }

  // ブロックインタラクト時に、Builder機能が有効であればBlockHitResultを変更
  @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
  private void infinite$redirectInteractBlock(
      ClientPlayerEntity player,
      Hand hand,
      BlockHitResult hitResult,
      CallbackInfoReturnable<ActionResult> cir) {
    Builder builderFeature = InfiniteClient.INSTANCE.getFeature(Builder.class);
    if (builderFeature != null && builderFeature.isEnabled()) {
      BlockHitResult modifiedHitResult = getBlockHitResult(hitResult, builderFeature);

      // 修正されたBlockHitResultを使用して内部のインタラクトブロックメソッドを呼び出す
      ActionResult result = interactBlockInternal(player, hand, modifiedHitResult);
      cir.setReturnValue(result);
      cir.cancel(); // Cancel the original method as we've handled it
    }
  }

  @Unique
  private static @NotNull BlockHitResult getBlockHitResult(
      BlockHitResult hitResult, Builder builderFeature) {
    BlockPos originalPos = hitResult.getBlockPos();
    Direction currentOffset = builderFeature.getCurrentPlacementOffset();

    // 新しいBlockPosを計算
    BlockPos newPos = originalPos.offset(currentOffset);

    // 新しいBlockHitResultを作成 (面は元のヒット結果の面を使用)
    return new BlockHitResult(
        new Vec3d(newPos.getX() + 0.5, newPos.getY() + 0.5, newPos.getZ() + 0.5),
        hitResult.getSide(), // 元のヒット面のまま
        newPos,
        hitResult.isInsideBlock());
  }

  @Unique
  private MinecraftClient client() {
    return MinecraftClient.getInstance();
  }

  @Unique
  private ActionResult interactBlockInternal(
      ClientPlayerEntity player, Hand hand, BlockHitResult hitResult) {
    BlockPos blockPos = hitResult.getBlockPos();
    ItemStack itemStack = player.getStackInHand(hand);
    if (this.gameMode == GameMode.SPECTATOR) {
      return ActionResult.CONSUME;
    } else {
      boolean bl = !player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty();
      boolean bl2 = player.shouldCancelInteraction() && bl;
      ClientWorld world = client().world;
      ClientPlayNetworkHandler networkHandler = client().getNetworkHandler();
      if (!bl2 && world != null && networkHandler != null) {
        BlockState blockState = world.getBlockState(blockPos);
        if (!networkHandler.hasFeature(blockState.getBlock().getRequiredFeatures())) {
          return ActionResult.FAIL;
        }

        ActionResult actionResult =
            blockState.onUseWithItem(
                player.getStackInHand(hand), client().world, player, hand, hitResult);
        if (actionResult.isAccepted()) {
          return actionResult;
        }

        if (actionResult instanceof ActionResult.PassToDefaultBlockAction
            && hand == Hand.MAIN_HAND) {
          ActionResult actionResult2 = blockState.onUse(client().world, player, hitResult);
          if (actionResult2.isAccepted()) {
            return actionResult2;
          }
        }
      }

      if (!itemStack.isEmpty() && !player.getItemCooldownManager().isCoolingDown(itemStack)) {
        ItemUsageContext itemUsageContext = new ItemUsageContext(player, hand, hitResult);
        ActionResult actionResult3;
        if (player.isInCreativeMode()) {
          int i = itemStack.getCount();
          actionResult3 = itemStack.useOnBlock(itemUsageContext);
          itemStack.setCount(i);
        } else {
          actionResult3 = itemStack.useOnBlock(itemUsageContext);
        }

        return actionResult3;
      } else {
        return ActionResult.PASS;
      }
    }
  }
}
