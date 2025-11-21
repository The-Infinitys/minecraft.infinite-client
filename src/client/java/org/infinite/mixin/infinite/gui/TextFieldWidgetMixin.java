package org.infinite.mixin.infinite.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.infinite.InfiniteClient;
import org.infinite.global.rendering.theme.ThemeSetting;
import org.infinite.gui.theme.ThemeColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetMixin {

  // TextFieldWidgetのprotected/publicメソッド/フィールドはそのままアクセスできますが、
  // プライベートな値を取得するために@Shadowを使用します。
  // 今回は、getText(), getX(), getY(), getWidth(), getHeight(), isFocused(), isVisible(),
  // isEditable()など
  // のpublicなメソッドを使うか、TextFieldWidgetのフィールド定義を参照して@Shadowを設定する必要があります。
  // 複雑なMixinを避けるため、可能な限りpublicなメソッドを使用し、足りない場合はShadowを設定します。

  // TextFieldWidgetのプライベートフィールドを取得するための@Shadow
  // NOTE: マッピングファイルに依存しますが、通常はこれらが必要です。
  @Shadow
  public abstract String getText();

  @Shadow
  public abstract int getCursor();

  @Shadow private int selectionStart; // 選択範囲の開始インデックス
  @Shadow private int selectionEnd; // 選択範囲の終了インデックス

  @Shadow
  protected abstract boolean isEditable();

  @Shadow
  public abstract boolean isVisible();

  // テキスト描画オフセットを計算するプライベートメソッドの@Shadow
  @Shadow private int firstCharacterIndex;

  @Shadow
  public abstract int getCharacterX(int index); // テキスト内のインデックスのX座標を取得

  @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
  private void infinite$renderWidget(
      DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    // テーマ機能が有効な場合のみカスタム描画を実行
    if (InfiniteClient.INSTANCE.isFeatureEnabled(ThemeSetting.class)) {
      // TextFieldWidgetのインスタンスを取得
      TextFieldWidget textField = (TextFieldWidget) (Object) this;
      MinecraftClient client = MinecraftClient.getInstance();
      TextRenderer textRenderer = client.textRenderer;
      ThemeColors colors = InfiniteClient.INSTANCE.getCurrentColors();

      // 描画パラメータ
      int x = textField.getX();
      int y = textField.getY();
      int width = textField.getWidth();
      int height = textField.getHeight();

      // テーマの色
      int backgroundColor = colors.getBackgroundColor();
      int borderColor = colors.getPrimaryColor();
      int textColor = colors.getForegroundColor();
      int disabledTextColor = colors.getSecondaryColor();
      int cursorColor = colors.getPrimaryColor();
      // 選択範囲はARGBで、テーマカラーを少し透明にしたものなどを使用
      int selectionColor = colors.getAquaAccentColor() | 0x88000000; // 88: 53%の透明度

      // --- 1. 背景の描画 ---
      context.fill(x, y, x + width, y + height, backgroundColor);

      // --- 2. 枠線の描画 ---
      int borderWidth = 1;
      // drawBorderユーティリティメソッドがInfiniteClientにある場合
      // context.drawBorder(x, y, width, height, borderColor);
      // ユーティリティがない場合は手動で描画
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

      // --- 3. テキスト領域の設定 (パディング) ---
      int textPaddingX = 4;
      int innerX = x + textPaddingX;
      int innerY = y + (height - textRenderer.fontHeight) / 2 + 1; // 垂直方向の中央揃え + 1

      // --- 4. クリップ領域の設定 ---
      // テキストが枠外に描画されないようにクリップを設定
      context.enableScissor(innerX, y, x + width - textPaddingX, y + height);

      // --- 5. 選択範囲の描画 ---
      int start = this.selectionStart;
      int end = this.selectionEnd;

      if (start != end) {
        int minIndex = Math.min(start, end);
        int maxIndex = Math.max(start, end);

        // getCharacterXは、描画オフセットを考慮したX座標を返します
        int startX = this.getCharacterX(minIndex);
        int endX = this.getCharacterX(maxIndex);

        // 選択範囲の背景をテーマ色で描画
        context.fill(startX, y, endX, y + height, selectionColor);
      }

      // --- 6. テキストの描画 ---
      String text = this.getText();
      int actualTextColor = this.isEditable() ? textColor : disabledTextColor;

      // テキストの描画。描画開始位置はgetCharacterX(firstCharacterIndex)で取得
      int textRenderX = this.getCharacterX(this.firstCharacterIndex);
      context.drawTextWithShadow(
          textRenderer,
          text.substring(this.firstCharacterIndex),
          textRenderX,
          innerY,
          actualTextColor);

      // --- 7. カーソルの描画 (フォーカス時のみ) ---
      if (textField.isFocused() && this.isVisible()) {
        boolean showCursor = (System.currentTimeMillis() / 500L) % 2 == 0;

        if (showCursor) {
          int cursorIndex = this.getCursor();
          int cursorX = this.getCharacterX(cursorIndex);

          // カーソルの描画 (縦線)
          context.fill(
              cursorX, innerY - 1, cursorX + 1, innerY + textRenderer.fontHeight, cursorColor);
        }
      }

      // --- 8. クリップを解除 ---
      context.disableScissor();

      ci.cancel(); // 元のレンダリングをキャンセル
    }
  }
}
