import net.minecraft.client.gui.DrawContext

fun DrawContext.drawBorder(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    color: Int,
) {
    this.fill(x - 1, y - 1, x + width + 1, y + 1, color)
    this.fill(x + width - 1, y - 1, x + width + 1, y + height + 1, color)
    this.fill(x - 1, y + height - 1, x + width + 1, y + height + 1, color)
    this.fill(x - 1, y - 1, x + 1, y + height + 1, color)
}
