package org.infinite.libs.graphics.render

import net.minecraft.util.Identifier
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBTTBakedChar
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.stbtt_GetCodepointHMetrics
import org.lwjgl.stb.STBTruetype.stbtt_GetFontVMetrics
import org.lwjgl.stb.STBTruetype.stbtt_ScaleForPixelHeight

class Font(
    val identifier: Identifier,
    val fontSize: Float,
    val textureIdentifier: Identifier,
    val bakedCharData: STBTTBakedChar.Buffer,
    val fontInfo: STBTTFontinfo,
) {
    // フォントの高さなどを計算するヘルパーメソッド
    val ascent: Float
    val descent: Float
    val lineGap: Float

    init {
        val scale = stbtt_ScaleForPixelHeight(fontInfo, fontSize)
        val pAscent = BufferUtils.createIntBuffer(1)
        val pDescent = BufferUtils.createIntBuffer(1)
        val pLineGap = BufferUtils.createIntBuffer(1)
        stbtt_GetFontVMetrics(fontInfo, pAscent, pDescent, pLineGap)

        ascent = pAscent.get(0) * scale
        descent = pDescent.get(0) * scale
        lineGap = pLineGap.get(0) * scale
    }

    fun getWidth(text: String): Float {
        var width = 0f
        val scale = stbtt_ScaleForPixelHeight(fontInfo, fontSize)
        val pAdvanceWidth = BufferUtils.createIntBuffer(1)
        val pLeftSideBearing = BufferUtils.createIntBuffer(1)

        for (char in text) {
            val charCode = char.code
            if (charCode !in 32..127) {
                // サポートされていない文字はスキップまたは代替処理
                continue
            }
            stbtt_GetCodepointHMetrics(fontInfo, charCode, pAdvanceWidth, pLeftSideBearing)
            width += pAdvanceWidth.get(0) * scale
        }
        return width
    }

    val height: Float
        get() = ascent - descent + lineGap
}
