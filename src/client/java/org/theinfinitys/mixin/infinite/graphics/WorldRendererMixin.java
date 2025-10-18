package org.theinfinitys.mixin.infinite.graphics;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.infinite.client.player.fighting.AimInterface;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
  @Shadow
  public abstract void tick(Camera camera);

  @Inject(
      at = @At("RETURN"),
      method =
          "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V")
  private void onRender(
      ObjectAllocator allocator,
      RenderTickCounter tickCounter,
      boolean renderBlockOutline,
      Camera camera,
      Matrix4f positionMatrix,
      Matrix4f projectionMatrix,
      Matrix4f matrix4f2,
      GpuBufferSlice gpuBufferSlice,
      Vector4f vector4f,
      boolean bl,
      CallbackInfo ci) {
    AimInterface.INSTANCE.process();
    InfiniteClient.INSTANCE.handle3dGraphics(
        allocator,
        tickCounter,
        renderBlockOutline,
        camera,
        positionMatrix,
        projectionMatrix,
        matrix4f2,
        gpuBufferSlice,
        vector4f,
        bl);
  }
}
