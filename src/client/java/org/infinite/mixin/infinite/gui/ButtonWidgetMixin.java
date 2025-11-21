package org.infinite.mixin.infinite.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import org.infinite.InfiniteClient;
import org.infinite.global.rendering.theme.ThemeSetting;
import org.infinite.gui.theme.ThemeColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PressableWidget.class)
public abstract class ButtonWidgetMixin {

  @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
  private void infinite$renderWidget(
      DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    // ThemeSettingが有効な場合にのみカスタムレンダリングを適用
    if (InfiniteClient.INSTANCE.isFeatureEnabled(ThemeSetting.class)) {
      PressableWidget button = (PressableWidget) (Object) this;
      TextRenderer textRenderer =
          MinecraftClient.getInstance()
              .textRenderer; // ButtonWidgetにはclientフィールドがあるためそこからTextRendererを取得
      int x = button.getX();
      int y = button.getY();
      int width = button.getWidth();
      int height = button.getHeight();
      Text message = button.getMessage();
      boolean active = button.active;
      boolean hovered = button.isHovered();
      ThemeColors colors = InfiniteClient.INSTANCE.getCurrentColors();
      // テーマとホバー状態に基づいて色を決定
      int backgroundColor = colors.getBackgroundColor();
      int borderColor = colors.getPrimaryColor();
      int textColor = colors.getForegroundColor();

      if (hovered) {
        backgroundColor = colors.getPrimaryColor();
        textColor = colors.getBackgroundColor();
      }

      // ボタンが無効な場合の色調整
      if (!active) {
        backgroundColor = colors.getSecondaryColor();
        textColor = colors.getForegroundColor();
      }

      // カスタム背景の描画
      context.fill(x, y, x + width, y + height, backgroundColor);

      // カスタムボーダーの描画（手動で4辺を塗る）
      int borderWidth = 1; // 枠線の太さ
      context.fill(x, y, x + width, y + borderWidth, borderColor); // Top
      context.fill(x, y + height - borderWidth, x + width, y + height, borderColor); // Bottom
      context.fill(
          x, y + borderWidth, x + borderWidth, y + height - borderWidth, borderColor); // Left
      context.fill(
          x + width - borderWidth,
          y + borderWidth,
          x + width,
          y + height - borderWidth,
          borderColor); // Right

      // テキストの描画
      context.drawCenteredTextWithShadow(
          textRenderer,
          message,
          x + width / 2,
          y + (height - textRenderer.fontHeight) / 2,
          textColor);

      ci.cancel(); // 元のレンダリングをキャンセルして、カスタムレンダリングを優先
    }
  }
}
