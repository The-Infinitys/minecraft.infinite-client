package org.theinfinitys.mixin.features.rendering.antioverlay;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.ByteBuffer;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.overlay.AntiOverlay;

@Mixin(FogRenderer.class)
public class NoFogMixin {
  @WrapOperation(
      method =
          "applyFog(Lnet/minecraft/client/render/Camera;IZLnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
  private void wrapApplyFog(
      FogRenderer instance,
      ByteBuffer buffer,
      int bufPos,
      Vector4f fogColor,
      float environmentalStart,
      float environmentalEnd,
      float renderDistanceStart,
      float renderDistanceEnd,
      float skyEnd,
      float cloudEnd,
      Operation<Void> original) {
    if (InfiniteClient.INSTANCE.isSettingEnabled(AntiOverlay.class, "NoFogOverlay")) {
      renderDistanceStart = Integer.MAX_VALUE;
      renderDistanceEnd = Integer.MAX_VALUE;
      environmentalStart = Integer.MAX_VALUE;
      environmentalEnd = Integer.MAX_VALUE;
    }

    original.call(
        instance,
        buffer,
        bufPos,
        fogColor,
        environmentalStart,
        environmentalEnd,
        renderDistanceStart,
        renderDistanceEnd,
        skyEnd,
        cloudEnd);
  }
}
