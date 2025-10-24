package org.infinite.mixin.features.server;

import java.util.stream.Stream;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.text.Text;
import org.infinite.InfiniteClient;
import org.infinite.features.server.AutoConnect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
  @Unique private int autoReconnectTimer = -1;
  @Unique private ButtonWidget autoReconnectButton;
  @Shadow @Final private DisconnectionInfo info;
  @Shadow @Final private Screen parent;
  @Shadow @Final private DirectionalLayoutWidget grid;

  protected DisconnectedScreenMixin(Text title) { // コンストラクタをprotectedに変更
    super(title);
  }

  // AutoConnect Featureを取得するためのユーティリティメソッド
  @Unique
  private AutoConnect getAutoConnectFeature() {
    return InfiniteClient.INSTANCE.getFeature(AutoConnect.class);
  }

  @Inject(at = @At("TAIL"), method = "init()V")
  private void onInit(CallbackInfo ci) {
    Text reason = info.reason();
    autoReconnectTimer = -1;
    System.out.println("Disconnected: " + reason.getString()); // TextをStringに変換
    addReconnectButtons();
  }

  @Unique
  private void addReconnectButtons() {
    AutoConnect autoConnect = getAutoConnectFeature();
    if (autoConnect == null) {
      InfiniteClient.INSTANCE.error("AutoConnect feature not found.");
      return;
    }

    // 1. Reconnectボタン
    ButtonWidget reconnectButton =
        grid.add(
            ButtonWidget.builder(Text.literal("Reconnect"), b -> autoConnect.reconnect(parent))
                .width(200)
                .build());

    // 2. AutoReconnectボタン
    autoReconnectButton =
        grid.add(
            ButtonWidget.builder(Text.literal("AutoReconnect"), b -> pressAutoReconnect())
                .width(200)
                .build());

    grid.refreshPositions();
    Stream.of(reconnectButton, autoReconnectButton).forEach(this::addDrawableChild);

    // Featureが有効な場合、タイマーを設定
    if (autoConnect.isEnabled()) {
      autoReconnectTimer = autoConnect.getWaitTicks().getValue();
    }
  }

  @Unique
  private void pressAutoReconnect() {
    AutoConnect autoConnect = getAutoConnectFeature();
    autoReconnectTimer = autoConnect.getWaitTicks().getValue();
  }

  /** tick()メソッド: 画面が描画されるたびに呼ばれ、タイマーのカウントダウンと再接続処理を行う */
  @Override
  public void tick() {
    AutoConnect autoConnect = getAutoConnectFeature();
    if (autoConnect == null || autoReconnectButton == null) return;

    // AutoReconnectが無効な場合、ボタンのテキストをリセットして終了
    if (autoConnect.isDisabled()) {
      autoReconnectButton.setMessage(Text.literal("AutoReconnect"));
      return;
    }

    // ボタンのテキストを更新 (残り秒数を表示)
    // タイマー値(tick)を20.0で割って秒数に変換し、切り上げ
    double secondsLeft = autoReconnectTimer / 20.0;
    autoReconnectButton.setMessage(Text.literal("AutoReconnect (" + (int) secondsLeft + ")"));
    if (autoReconnectTimer > 0) {
      autoReconnectTimer--;
    }
    if (autoReconnectTimer == 0) {
      autoConnect.reconnect(parent);
    }
  }
}
