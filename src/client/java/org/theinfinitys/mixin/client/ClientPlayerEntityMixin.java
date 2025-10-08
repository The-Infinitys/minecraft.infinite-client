package org.theinfinitys.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.fighting.Reach;
import org.theinfinitys.features.movement.FreeCamera;
import org.theinfinitys.features.movement.SafeWalk;
import org.theinfinitys.features.movement.SuperSprint;
import org.theinfinitys.features.rendering.PortalGui;
import org.theinfinitys.features.rendering.SuperSight;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {
  @Shadow(aliases = "client")
  @Final
  protected MinecraftClient client;

  @Unique private Screen tempCurrentScreen;

  public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
    super(world, profile);
  }

  @WrapOperation(
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z",
              ordinal = 0),
      method = "tickMovement()V")
  private boolean wrapHasForwardMovement(Input input, Operation<Boolean> original) {
    // Feature: SuperSprint (Setting: OnlyWhenForward)
    if (InfiniteClient.INSTANCE.isFeatureEnabled(SuperSprint.class)
        && !InfiniteClient.INSTANCE.isSettingEnabled(SuperSprint.class, "OnlyWhenForward"))
      return input.getMovementInput().length() > 1e-5F;

    return original.call(input);
  }

  @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
  private void onTickMovement(CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(FreeCamera.class)) {
      ci.cancel();
    }
  }

  /* sendMovementPackets()のHEAD/TAILへのInject（Pre/PostMotionEventの代わり）は削除 */
  /* move()へのInject（PlayerMoveEventの代わり）は削除 */

  /**
   * PortalGui: When enabled, temporarily sets the current screen to null to prevent the
   * updateNausea() method from closing it.
   */
  @Inject(
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;",
              opcode = Opcodes.GETFIELD,
              ordinal = 0),
      method = "tickNausea(Z)V")
  private void beforeTickNausea(boolean fromPortalEffect, CallbackInfo ci) {
    // Feature: PortalGui の有効性チェック
    if (!InfiniteClient.INSTANCE.isFeatureEnabled(PortalGui.class)) return;

    tempCurrentScreen = client.currentScreen;
    client.currentScreen = null;
  }

  /** PortalGui: Restores the current screen. */
  @Inject(
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/client/network/ClientPlayerEntity;nauseaIntensity:F",
              opcode = Opcodes.GETFIELD,
              ordinal = 1),
      method = "tickNausea(Z)V")
  private void afterTickNausea(boolean fromPortalEffect, CallbackInfo ci) {
    if (tempCurrentScreen == null) return;

    client.currentScreen = tempCurrentScreen;
    tempCurrentScreen = null;
  }

  /** This mixin allows AutoSprint to enable sprinting even when the player is too hungry. */
  @Inject(at = @At("HEAD"), method = "canSprint()Z", cancellable = true)
  private void onCanSprint(CallbackInfoReturnable<Boolean> cir) {
    // Feature: SuperSprint (Setting: EvenIfHungry)
    if (InfiniteClient.INSTANCE.isSettingEnabled(SuperSprint.class, "EvenIfHungry"))
      cir.setReturnValue(true);
  }

  /** FreeCamera: Player appears as a spectator when FreeCamera is enabled. */
  public boolean isSpectator() {
    // Feature: FreeCamera の有効性チェック
    return super.isSpectator() || InfiniteClient.INSTANCE.isFeatureEnabled(FreeCamera.class);
  }

  /** SafeWalk: This is the part that makes SafeWalk work. */
  protected boolean clipAtLedge() {
    return super.clipAtLedge() || InfiniteClient.INSTANCE.isFeatureEnabled(SafeWalk.class);
  }

  /** SafeWalk: Allows SafeWalk to sneak visibly when the player is near a ledge. */
  // NOTE: adjustMovementForSneakingはClientPlayerEntityの親クラスのメソッドをオーバーライドしていると想定
  protected Vec3d adjustMovementForSneaking(Vec3d movement, MovementType type) {

    Vec3d vec3d = super.adjustMovementForSneaking(movement, type);
    SafeWalk safeWalk = InfiniteClient.INSTANCE.getFeature(SafeWalk.class);
    if (movement != null
        && InfiniteClient.INSTANCE.isFeatureEnabled(SafeWalk.class)
        && safeWalk != null) {
      safeWalk.onPreMotion();
    }
    return vec3d;
  }

  public boolean hasStatusEffect(RegistryEntry<StatusEffect> effect) {
    // Feature: SuperSight

    // NightVision
    if (effect == StatusEffects.NIGHT_VISION
        && InfiniteClient.INSTANCE.isSettingEnabled(SuperSight.class, "FullBright")) return true;

    // AntiBlind (BLINDNESS, DARKNESS)
    if (InfiniteClient.INSTANCE.isSettingEnabled(SuperSight.class, "AntiBlind")) {
      if (effect == StatusEffects.BLINDNESS || effect == StatusEffects.DARKNESS) return false;
    }

    // 💡 修正点: 無限再帰を防ぐため、superを使って元のメソッドを呼び出す
    return super.hasStatusEffect(effect);

    // NOTE: ClientPlayerEntityはabstractではないため、thisを ClientPlayerEntity にキャストして呼び出している可能性があります。
    // より確実な方法は @Redirect または @Overwrite を使用することですが、
    // 現在の構造を維持するなら super を使用してください。
  }

  public double getBlockInteractionRange() {
    // Feature: Reach
    if (InfiniteClient.INSTANCE.isFeatureEnabled(Reach.class))
      return InfiniteClient.INSTANCE.getSettingFloat(Reach.class, "ReachDistance", 4.5F);

    // super.getBlockInteractionRange()
    return 4.5;
  }

  public double getEntityInteractionRange() {
    // Feature: Reach
    if (InfiniteClient.INSTANCE.isFeatureEnabled(Reach.class))
      return InfiniteClient.INSTANCE.getSettingFloat(Reach.class, "ReachDistance", 3.0F);

    // super.getEntityInteractionRange()
    return 3.0;
  }
}
