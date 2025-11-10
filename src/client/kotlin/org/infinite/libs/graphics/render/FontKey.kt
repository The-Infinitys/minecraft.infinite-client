package org.infinite.libs.graphics.render

/**
 * フォントを識別するためのキー
 * @param fontName フォント名 (例: "NotoSansJP", "Arial")
 * @param style スタイル (例: "Regular", "Bold", "Italic")
 */
data class FontKey(
    val fontName: String,
    val style: String = "regular",
) {
    override fun toString(): String = "${fontName}_$style"
}
