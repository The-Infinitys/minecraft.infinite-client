package org.infinite.mixin.features.server;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.infinite.InfiniteClient;
import org.infinite.features.server.AutoConnect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public class MultiPlayerScreenMixin extends Screen {

  @Unique private ButtonWidget lastServerButton;

  protected MultiPlayerScreenMixin(Text title) {
    super(title);
  }

  @Unique
  AutoConnect autoConnect() {
    return InfiniteClient.INSTANCE.getFeature(AutoConnect.class);
  }

  @Inject(at = @At("TAIL"), method = "init()V")
  private void onInit(CallbackInfo ci) {
    lastServerButton =
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Last Server"), b -> joinLastServer())
                .dimensions(width / 2 - 154, 10, 100, 20)
                .build());
    updateLastServerButton();
  }

  @Unique
  private void joinLastServer() {
    AutoConnect autoConnect = autoConnect();
    if (autoConnect != null) autoConnect.joinLastServer((MultiplayerScreen) (Object) this);
  }

  @Inject(at = @At("HEAD"), method = "connect(Lnet/minecraft/client/network/ServerInfo;)V")
  private void onConnect(ServerInfo entry, CallbackInfo ci) {
    autoConnect().setLastServer(entry);
    updateLastServerButton();
  }

  @Unique
  private void updateLastServerButton() {
    if (lastServerButton == null) return;

    AutoConnect autoConnect = autoConnect();
    lastServerButton.active = autoConnect != null && autoConnect.getLastServer() != null;
  }
}
