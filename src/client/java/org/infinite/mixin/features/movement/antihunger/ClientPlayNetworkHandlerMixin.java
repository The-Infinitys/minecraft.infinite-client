package org.infinite.mixin.features.movement.antihunger;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.infinite.InfiniteClient;
import org.infinite.features.movement.hunger.AntiHunger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs; // 引数を変更するために使用
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// PlayerMoveC2SPacket.PositionAndOnGround に Mixin を適用
@Mixin(PlayerMoveC2SPacket.PositionAndOnGround.class)
public abstract class ClientPlayNetworkHandlerMixin extends PlayerMoveC2SPacket {

  // Mixinの慣習に従い、抽象コンストラクタを定義
  protected ClientPlayNetworkHandlerMixin(
      double x,
      double y,
      double z,
      float yaw,
      float pitch,
      boolean onGround,
      boolean horizontalCollision,
      boolean changePosition,
      boolean changeLook) {
    super(x, y, z, yaw, pitch, onGround, horizontalCollision, changePosition, changeLook);
  }

  /**
   * PlayerMoveC2SPacket.PositionAndOnGround のコンストラクタの引数を変更します。 onGround (ブール値) を強制的に false に設定します。
   */
  @ModifyArgs(
      method = "<init>(DDDZZ)V", // PlayerMoveC2SPacket$PositionAndOnGround のコンストラクタシグネチャ
      at =
          @At(
              value = "INVOKE",
              // ターゲットは親クラス (PlayerMoveC2SPacket) のコンストラクタ呼び出し
              target =
                  "Lnet/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket;<init>(DDDFFZZZZ)V"))
  private static void infinite$forceOnGroundFalse(Args args) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(AntiHunger.class)) {
      args.set(5, false);
    }
  }
}
