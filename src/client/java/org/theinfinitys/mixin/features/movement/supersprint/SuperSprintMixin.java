package org.theinfinitys.mixin.features.movement.supersprint;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.movement.sprint.SuperSprint;

@Mixin(ClientPlayerEntity.class)
public abstract class SuperSprintMixin extends AbstractClientPlayerEntity {

  public SuperSprintMixin(ClientWorld world, GameProfile profile) {
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

  /** This mixin allows AutoSprint to enable sprinting even when the player is too hungry. */
  @Inject(at = @At("HEAD"), method = "canSprint()Z", cancellable = true)
  private void onCanSprint(CallbackInfoReturnable<Boolean> cir) {
    // Feature: SuperSprint (Setting: EvenIfHungry)
    if (InfiniteClient.INSTANCE.isSettingEnabled(SuperSprint.class, "EvenIfHungry"))
      cir.setReturnValue(true);
  }
}
