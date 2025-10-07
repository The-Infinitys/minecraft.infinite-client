package org.theinfinitys.mixin.client.rendering;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity; // Entityをインポート
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d; // Vec3dをインポート
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.movement.FreeCamera;
import org.theinfinitys.features.rendering.CameraConfig;
import org.theinfinitys.features.rendering.SuperSight;
import org.theinfinitys.features.rendering.XRay;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

  private final MinecraftClient client = MinecraftClient.getInstance();

  @Inject(
      at = @At("HEAD"),
      method = "getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F",
      cancellable = true)
  private static void onGetNightVisionStrength(
      LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
    if (InfiniteClient.INSTANCE.isSettingEnabled(SuperSight.class, "FullBright")
        || InfiniteClient.INSTANCE.isFeatureEnabled(XRay.class)) cir.setReturnValue(1.0f);
  }

  @Inject(
      at = @At("HEAD"),
      method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V",
      cancellable = true)
  private void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isSettingEnabled(CameraConfig.class, "AntiHurtTilt")) ci.cancel();
  }

  /** FreeCamera: カメラの描画位置と視点をFreeCameraの座標に上書きする。 */
  @Inject(
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/render/GameRenderer;bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V",
              ordinal = 0),
      method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V")
  private void onRenderWorldViewBobbing(RenderTickCounter tickCounter, CallbackInfo ci) {
    FreeCamera freeCamera = InfiniteClient.INSTANCE.getFeature(FreeCamera.class);
    Entity cameraEntity = client.cameraEntity;

    // FreeCameraが有効で、カメラエンティティが存在する場合に処理
    if (freeCamera != null && freeCamera.isEnabled() && cameraEntity != null) {

      // FreeCamera.kt で管理されている座標と視点を取得
      Vec3d camPos = freeCamera.getCameraPosition();
      if (camPos == null) return;

      // プレイヤー（カメラエンティティ）の描画位置
      double playerRenderX = cameraEntity.getX();
      double playerRenderY = cameraEntity.getY();
      double playerRenderZ = cameraEntity.getZ();

      // カメラをFreeCameraの位置に移動させるために、MatrixStackを変換
      // (FreeCameraの位置 - プレイヤーの描画位置) の分だけ空間をシフトする
      double offsetX = camPos.getX() - playerRenderX;
      double offsetY = camPos.getY() - playerRenderY;
      double offsetZ = camPos.getZ() - playerRenderZ;

      // 既存の MatrixStack を取得し、変換を適用
      // renderWorldの引数に MatrixStackがないため、このインジェクションポイントでは直接のMatrixStack操作は難しい。
      // そこで、client.camera（Cameraインスタンス）の座標を一時的に上書きする間接的な方法を取る。

      // NOTE: client.cameraを上書きするMixinが最も確実ですが、ここでは既存のGameRendererMixinを最大限利用します。

      // プレイヤーの位置をFreeCameraの位置に上書きすることで、カメラがその位置を追従するように「騙す」
      // これは副作用が大きいため、最も推奨される方法ではありませんが、現在のMixin構造内での回避策です。

      // プレイヤーの座標を一時的にFreeCameraの座標で上書き
      cameraEntity.setPos(camPos.getX(), camPos.getY(), camPos.getZ());

      // 視点も上書き
      // ClientPlayerEntity の Mixin で isSpectator を true にしているため、
      // カメラは player の Yaw/Pitch を参照し続けます。
      // FreeCamera.kt の tick() で client.player の Yaw/Pitch を更新している前提で、ここでは座標のみを上書きします。

      // 警告: setPosによる座標の上書きは、Tick処理とは異なる描画のタイミングで予期せぬ挙動を引き起こす可能性があります。
    }
  }
}
