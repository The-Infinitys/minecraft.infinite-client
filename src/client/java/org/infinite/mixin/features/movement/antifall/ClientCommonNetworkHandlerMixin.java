package org.infinite.mixin.features.movement.antifall;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.infinite.InfiniteClient;
import org.infinite.features.movement.fall.AntiFall;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin implements ClientCommonPacketListener {
  @WrapOperation(
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;)V"),
      method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V")
  private void wrapSendPacket(
      ClientConnection connection, Packet<?> packet, Operation<Void> original) {
    var client = MinecraftClient.getInstance();
    if ((client.player != null)
        && InfiniteClient.INSTANCE.isFeatureEnabled(AntiFall.class)
        && (client.interactionManager != null)
        && (packet instanceof PlayerMoveC2SPacket playerMoveC2SPacket)
        && (!client.player.isGliding())
        && (!client.player.isSneaking())) {
      var targetPacket = onGroundPacket(playerMoveC2SPacket);
      original.call(connection, targetPacket);
    } else {
      original.call(connection, packet);
    }
  }

  @Unique
  private PlayerMoveC2SPacket onGroundPacket(PlayerMoveC2SPacket packet) {
    if (packet instanceof PlayerMoveC2SPacket.Full)
      return new PlayerMoveC2SPacket.Full(
          packet.getX(0),
          packet.getY(0),
          packet.getZ(0),
          packet.getYaw(0),
          packet.getPitch(0),
          true,
          packet.horizontalCollision());

    if (packet instanceof PlayerMoveC2SPacket.PositionAndOnGround)
      return new PlayerMoveC2SPacket.PositionAndOnGround(
          packet.getX(0), packet.getY(0), packet.getZ(0), true, packet.horizontalCollision());

    if (packet instanceof PlayerMoveC2SPacket.LookAndOnGround)
      return new PlayerMoveC2SPacket.LookAndOnGround(
          packet.getYaw(0), packet.getPitch(0), true, packet.horizontalCollision());

    return new PlayerMoveC2SPacket.OnGroundOnly(true, packet.horizontalCollision());
  }
}
