// FontKey.kt
package org.infinite.libs.graphics.render.text

data class FontKey(
    val fontName: String,
    val style: String = "regular",
) {
    // 正規化なし！ そのまま比較
    override fun toString(): String = "${fontName}_$style"
}
