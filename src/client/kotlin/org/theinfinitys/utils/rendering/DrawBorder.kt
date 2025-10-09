import net.minecraft.client.gui.DrawContext

fun DrawContext.drawBorder(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    color: Int,
) {
    // 上の線: y から y + 1 (高さ 1px)
    this.fill(x, y, x + width, y + 1, color)

    // 右の線: x + width - 1 から x + width (幅 1px)
    // Y座標は上下の線と重ならないように、y+1からy+height-1の範囲にする
    this.fill(x + width - 1, y + 1, x + width, y + height - 1, color)

    // 下の線: y + height - 1 から y + height (高さ 1px)
    // X座標は左右の線と重ならないように、x+1からx+width-1の範囲にする
    this.fill(x, y + height - 1, x + width, y + height, color)

    // 左の線: x から x + 1 (幅 1px)
    // Y座標は上下の線と重ならないように、y+1からy+height-1の範囲にする
    this.fill(x, y + 1, x + 1, y + height - 1, color)
}
