package org.infinite.libs.graphics.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.stbtt_FindGlyphIndex
import org.lwjgl.stb.STBTruetype.stbtt_GetFontVMetrics
import org.lwjgl.stb.STBTruetype.stbtt_GetGlyphBitmapBox
import org.lwjgl.stb.STBTruetype.stbtt_GetGlyphHMetrics
import org.lwjgl.stb.STBTruetype.stbtt_MakeGlyphBitmap
import org.lwjgl.stb.STBTruetype.stbtt_ScaleForPixelHeight

/**
 * 動的アトラスを持つフォント
 */
class Font(
    val key: FontKey,
    val identifier: Identifier,
    val fontSize: Float,
    val fontInfo: STBTTFontinfo,
) {
    // テクスチャ識別子
    val textureIdentifier: Identifier =
        Identifier.of(
            identifier.namespace,
            "font/${key.fontName}_${key.style}_${fontSize.toInt()}",
        )

    // 動的アトラス管理
    private var atlasWidth = 2048
    private var atlasHeight = 2048
    private var currentX = 0
    private var currentY = 0
    private var currentRowHeight = 0

    private var nativeImage: NativeImage = NativeImage(atlasWidth, atlasHeight, false)
    private var texture: NativeImageBackedTexture? = null

    // キャッシュされたグリフ情報
    data class GlyphInfo(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val xOffset: Float,
        val yOffset: Float,
        val advanceWidth: Float,
    )

    private val glyphCache = mutableMapOf<Int, GlyphInfo>()

    // フォントメトリクス
    val scale: Float = stbtt_ScaleForPixelHeight(fontInfo, fontSize)
    val ascent: Float
    val descent: Float
    val lineGap: Float

    init {

        val pAscent = BufferUtils.createIntBuffer(1)
        val pDescent = BufferUtils.createIntBuffer(1)
        val pLineGap = BufferUtils.createIntBuffer(1)
        stbtt_GetFontVMetrics(fontInfo, pAscent, pDescent, pLineGap)

        ascent = pAscent.get(0) * scale
        descent = pDescent.get(0) * scale
        lineGap = pLineGap.get(0) * scale

        // テクスチャを登録
        registerTexture()
    }

    private fun registerTexture() {
        texture = NativeImageBackedTexture({ textureIdentifier.toString() }, nativeImage)
        MinecraftClient.getInstance().textureManager.registerTexture(textureIdentifier, texture)
    }

    /**
     * 指定された文字がこのフォントでサポートされているかチェック
     */
    fun supportsChar(codepoint: Int): Boolean {
        val glyphIndex = stbtt_FindGlyphIndex(fontInfo, codepoint)
        return glyphIndex != 0
    }

    /**
     * グリフ情報を取得（キャッシュがあればそれを返し、なければ生成）
     */
    fun getOrCreateGlyph(codepoint: Int): GlyphInfo? {
        // キャッシュチェック
        glyphCache[codepoint]?.let { return it }

        // サポートされていない文字
        if (!supportsChar(codepoint)) return null

        // グリフをベイク
        return bakeGlyph(codepoint)
    }

    private fun bakeGlyph(codepoint: Int): GlyphInfo? {
        val glyphIndex = stbtt_FindGlyphIndex(fontInfo, codepoint)
        if (glyphIndex == 0) return null

        // グリフの境界ボックスを取得
        val ix0 = BufferUtils.createIntBuffer(1)
        val iy0 = BufferUtils.createIntBuffer(1)
        val ix1 = BufferUtils.createIntBuffer(1)
        val iy1 = BufferUtils.createIntBuffer(1)
        stbtt_GetGlyphBitmapBox(fontInfo, glyphIndex, scale, scale, ix0, iy0, ix1, iy1)

        val x0 = ix0.get(0)
        val y0 = iy0.get(0)
        val x1 = ix1.get(0)
        val y1 = iy1.get(0)

        val glyphWidth = x1 - x0
        val glyphHeight = y1 - y0

        // 空のグリフ（スペースなど）
        if (glyphWidth <= 0 || glyphHeight <= 0) {
            val pAdvanceWidth = BufferUtils.createIntBuffer(1)
            val pLeftSideBearing = BufferUtils.createIntBuffer(1)
            stbtt_GetGlyphHMetrics(fontInfo, glyphIndex, pAdvanceWidth, pLeftSideBearing)

            val info =
                GlyphInfo(
                    x = 0f,
                    y = 0f,
                    width = 0f,
                    height = 0f,
                    xOffset = 0f,
                    yOffset = 0f,
                    advanceWidth = pAdvanceWidth.get(0) * scale,
                )
            glyphCache[codepoint] = info
            return info
        }

        // アトラスに配置する位置を確保
        if (currentX + glyphWidth > atlasWidth) {
            // 次の行へ
            currentX = 0
            currentY += currentRowHeight
            currentRowHeight = 0
        }

        if (currentY + glyphHeight > atlasHeight) {
            // アトラスを拡張
            expandAtlas()
            return bakeGlyph(codepoint) // 再試行
        }

        // グリフのビットマップを生成
        val bitmap = BufferUtils.createByteBuffer(glyphWidth * glyphHeight)
        stbtt_MakeGlyphBitmap(
            fontInfo,
            bitmap,
            glyphWidth,
            glyphHeight,
            glyphWidth,
            scale,
            scale,
            glyphIndex,
        )

        // アトラスにコピー
        for (y in 0 until glyphHeight) {
            for (x in 0 until glyphWidth) {
                val alpha = bitmap.get(y * glyphWidth + x).toInt() and 0xFF
                val atlasX = currentX + x
                val atlasY = currentY + y
                nativeImage.setColor(atlasX, atlasY, (alpha shl 24) or 0xFFFFFF)
            }
        }

        // テクスチャを更新
        texture?.upload()

        // グリフ情報を作成
        val pAdvanceWidth = BufferUtils.createIntBuffer(1)
        val pLeftSideBearing = BufferUtils.createIntBuffer(1)
        stbtt_GetGlyphHMetrics(fontInfo, glyphIndex, pAdvanceWidth, pLeftSideBearing)

        val info =
            GlyphInfo(
                x = currentX.toFloat(),
                y = currentY.toFloat(),
                width = glyphWidth.toFloat(),
                height = glyphHeight.toFloat(),
                xOffset = x0.toFloat(),
                yOffset = y0.toFloat(),
                advanceWidth = pAdvanceWidth.get(0) * scale,
            )

        glyphCache[codepoint] = info

        // 次の配置位置を更新
        currentX += glyphWidth
        currentRowHeight = maxOf(currentRowHeight, glyphHeight)

        return info
    }

    private fun expandAtlas() {
        val newWidth = atlasWidth * 2
        val newHeight = atlasHeight * 2

        val newImage = NativeImage(newWidth, newHeight, false)

        // コピー
        for (y in 0 until atlasHeight) {
            for (x in 0 until atlasWidth) {
                val color = nativeImage.getColorArgb(x, y)
                if (color != 0) {
                    newImage.setColor(x, y, color)
                }
            }
        }

        nativeImage.close()
        nativeImage = newImage
        atlasWidth = newWidth
        atlasHeight = newHeight

        // 重要：destroyTexture() を削除
        // texture は自動で更新される
        texture?.upload() // 再アップロードのみ
    }

    fun getWidth(text: String): Float {
        var width = 0f
        for (char in text) {
            val glyph = getOrCreateGlyph(char.code) ?: continue
            width += glyph.advanceWidth
        }
        return width
    }

    val height: Float
        get() = ascent - descent + lineGap

    fun getAtlasWidth(): Int = atlasWidth

    fun getAtlasHeight(): Int = atlasHeight
}
