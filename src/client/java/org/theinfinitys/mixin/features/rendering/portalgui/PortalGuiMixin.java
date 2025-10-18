package org.theinfinitys.mixin.features.rendering.portalgui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.portalgui.PortalGui;

@Mixin(ClientPlayerEntity.class)
public abstract class PortalGuiMixin extends AbstractClientPlayerEntity {
  @Shadow(aliases = "client")
  @Final
  protected MinecraftClient client;

  @Unique private Screen tempCurrentScreen;

  public PortalGuiMixin(ClientWorld world, GameProfile profile) {
    super(world, profile);
  }

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
}
