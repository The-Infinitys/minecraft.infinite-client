package org.infinite.mixin.infinite.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.listener.TickablePacketListener;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.infinite.InfiniteClient;
import org.infinite.libs.world.WorldManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin extends ClientCommonNetworkHandler
    implements TickablePacketListener, ClientPlayPacketListener {
  @Unique
  private WorldManager worldManager() {
    return InfiniteClient.INSTANCE.getWorldManager();
  }

  private ClientPlayNetworkHandlerMixin(
      MinecraftClient client, ClientConnection connection, ClientConnectionState connectionState) {
    super(client, connection, connectionState);
  }

  @Inject(
      at = @At("TAIL"),
      method = "loadChunk(IILnet/minecraft/network/packet/s2c/play/ChunkData;)V")
  private void onLoadChunk(int x, int z, ChunkData chunkData, CallbackInfo ci) {
    worldManager().handleChunkLoad(x, z, chunkData);
  }

  @Inject(
      at = @At("TAIL"),
      method = "onBlockUpdate(Lnet/minecraft/network/packet/s2c/play/BlockUpdateS2CPacket;)V")
  private void onOnBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
    worldManager().handleBlockUpdate(packet);
  }

  @Inject(
      at = @At("TAIL"),
      method =
          "onChunkDeltaUpdate(Lnet/minecraft/network/packet/s2c/play/ChunkDeltaUpdateS2CPacket;)V")
  private void onOnChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
    worldManager().handleDeltaUpdate(packet);
  }
}
