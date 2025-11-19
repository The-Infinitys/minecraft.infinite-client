package org.infinite.mixin.features.movement.brawler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.infinite.InfiniteClient;
import org.infinite.features.movement.brawler.Brawler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class BrawlerMixin {

  // Suppress for MinecraftClient.getInstance()
  @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
  private void infinite$onInteractBlock(
      net.minecraft.client.network.ClientPlayerEntity player,
      Hand hand,
      BlockHitResult hitResult,
      CallbackInfoReturnable<ActionResult> cir) {
    Brawler brawerFeature = InfiniteClient.INSTANCE.getFeature(Brawler.class);

    if (brawerFeature != null && brawerFeature.isEnabled()) {
      World world = MinecraftClient.getInstance().world;
      player.getStackInHand(hand);
      if (world != null) {
        String blockId =
            Registries.BLOCK
                .getId(world.getBlockState(hitResult.getBlockPos()).getBlock())
                .toString();

        if (brawerFeature.getInteractCancelBlocks().getValue().contains(blockId)) {
          // Interact with the currently held item instead of the block
          player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(hand, 0, 0.0f, 0.0f));

          // Return SUCCESS to prevent further processing of the block interaction
          cir.setReturnValue(ActionResult.SUCCESS);
          cir.cancel();
        }
      }
    }
  }
}
