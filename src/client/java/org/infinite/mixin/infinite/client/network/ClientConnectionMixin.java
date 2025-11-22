package org.infinite.mixin.infinite.client.network;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.util.Identifier;
import org.infinite.InfiniteClient;
import org.infinite.global.server.protocol.ProtocolSpoofingSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

  @Inject(method = "send*", at = @At("HEAD"), cancellable = true)
  private void infiniteClient$onSendPacket(Packet<?> packet, CallbackInfo ci) {
    ProtocolSpoofingSetting protocolSpoofingSetting =
        InfiniteClient.INSTANCE.getGlobalFeature(ProtocolSpoofingSetting.class);
    if (protocolSpoofingSetting != null && protocolSpoofingSetting.isEnabled()) {
      if (packet instanceof CustomPayloadC2SPacket) {
        Identifier channel = packet.getPacketType().id();
      }
    }
  }
}
