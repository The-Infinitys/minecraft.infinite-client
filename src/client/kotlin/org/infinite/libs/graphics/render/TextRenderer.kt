package org.infinite.libs.graphics.render

import net.minecraft.client.MinecraftClient
import net.minecraft.resource.Resource
import net.minecraft.util.Identifier
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.stbtt_InitFont
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.jvm.optionals.getOrNull

object TextRenderer {
    // ロードされたすべてのフォント
    private val loadedFonts = mutableMapOf<FontKey, Font>()

    // フォールバック順序（カバレッジの高い順）
    private val fallbackOrder = mutableListOf<FontKey>()

    // デフォルトフォント
    private var defaultFontKey: FontKey? = null

    private const val DEFAULT_FONT_SIZE = 64f

    // カバレッジ計算用のサンプル文字セット（よく使われる文字範囲）
    private val COVERAGE_SAMPLE_RANGES =
        listOf(
            0x0020..0x007E, // Basic Latin (ASCII)
            0x3040..0x309F, // Hiragana
            0x30A0..0x30FF, // Katakana
            0x4E00..0x9FFF, // CJK Unified Ideographs (サンプリング)
            0x0400..0x04FF, // Cyrillic
            0x0600..0x06FF, // Arabic
        )

    /**
     * TTFファイルからフォント名とスタイルを抽出
     */
    private fun extractFontKey(identifier: Identifier): FontKey {
        val path = identifier.path
        val fileName = path.substringAfterLast('/').removeSuffix(".ttf")

        // ファイル名からスタイルを抽出 (例: "NotoSansJP-Bold.ttf" -> fontName="NotoSansJP", style="Bold")
        val parts = fileName.split('-', '_')

        return if (parts.size >= 2) {
            val style = parts.last()
            val fontName = parts.dropLast(1).joinToString("-")
            FontKey(fontName, style)
        } else {
            FontKey(fileName, "Regular")
        }
    }

    /**
     * フォントをロード
     */
    fun loadFont(
        identifier: Identifier,
        fontSize: Float = DEFAULT_FONT_SIZE,
    ): Font? {
        val fontKey = extractFontKey(identifier)

        // 既にロード済み
        loadedFonts[fontKey]?.let { return it }

        val fontBuffer: ByteBuffer
        var resource: Resource?
        var inputStream: InputStream? = null

        try {
            resource =
                MinecraftClient
                    .getInstance()
                    .resourceManager
                    .getResource(identifier)
                    .getOrNull()

            if (resource == null) {
                throw IOException("Resource not found: $identifier")
            }

            inputStream = resource.inputStream as InputStream

            // InputStreamをByteBufferにコピー
            val buffer = ByteArray(4096)
            val outputStream = ByteArrayOutputStream()
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            val bytes = outputStream.toByteArray()
            fontBuffer = BufferUtils.createByteBuffer(bytes.size).put(bytes).flip()
        } catch (e: IOException) {
            InfiniteClient.error("Failed to load font: $identifier - ${e.message}")
            return null
        } finally {
            inputStream?.close()
        }

        val fontInfo = STBTTFontinfo.create()
        if (!stbtt_InitFont(fontInfo, fontBuffer)) {
            InfiniteClient.error("Failed to initialize font info: $identifier")
            return null
        }

        val font = Font(fontKey, identifier, fontSize, fontInfo)
        loadedFonts[fontKey] = font

        InfiniteClient.info("Loaded font: $fontKey from $identifier")
        return font
    }

    /**
     * フォントのカバレッジを計算（サンプリング）
     */
    private fun calculateCoverage(font: Font): Int {
        var supportedCount = 0

        for (range in COVERAGE_SAMPLE_RANGES) {
            // 大きな範囲はサンプリング（CJK等）
            val step = if (range.last - range.first > 1000) 100 else 1

            for (codepoint in range step step) {
                if (font.supportsChar(codepoint)) {
                    supportedCount++
                }
            }
        }

        return supportedCount
    }

    /**
     * 指定ディレクトリ以下のすべてのTTFファイルをロード
     */
    private fun loadFontsFromDirectory(
        namespace: String,
        baseDirectory: String,
    ): List<Font> {
        val loaded = mutableListOf<Font>()
        val resourceManager = MinecraftClient.getInstance().resourceManager

        val resourceIdentifiers =
            resourceManager
                .findResources(baseDirectory) { identifier ->
                    identifier.namespace == namespace && identifier.path.endsWith(".ttf")
                }.keys

        for (id in resourceIdentifiers) {
            loadFont(id)?.let { loaded.add(it) }
        }

        return loaded
    }

    /**
     * フォントの初期化
     */
    fun initFonts(defaultIdentifier: Identifier) {
        // 1. すべてのフォントをロード
        val baseDir =
            defaultIdentifier.path
                .split("/")
                .dropLast(2)
                .joinToString("/")
        InfiniteClient.log("Loading from: $baseDir")
        val loaded = loadFontsFromDirectory(defaultIdentifier.namespace, baseDir)

        if (loaded.isEmpty()) {
            throw RuntimeException("No fonts loaded from $baseDir")
        }

        // 2. デフォルトフォントを設定
        val defaultKey = extractFontKey(defaultIdentifier)
        if (loadedFonts.containsKey(defaultKey)) {
            defaultFontKey = defaultKey
        } else {
            // 最初にロードされたフォントをデフォルトに
            defaultFontKey = loaded.first().key
            InfiniteClient.error("Default font not found: $defaultKey. Using $defaultFontKey as default.")
        }

        // 3. カバレッジを計算してフォールバック順序を決定
        InfiniteClient.info("Calculating font coverage...")
        val coverageMap = mutableMapOf<FontKey, Int>()

        for (font in loaded) {
            val coverage = calculateCoverage(font)
            coverageMap[font.key] = coverage
            InfiniteClient.info("Font ${font.key}: coverage = $coverage")
        }

        // 4. カバレッジの高い順にソート（デフォルトフォントを最優先）
        fallbackOrder.clear()
        fallbackOrder.add(defaultFontKey!!)

        val sorted =
            coverageMap.entries
                .filter { it.key != defaultFontKey }
                .sortedByDescending { it.value }
                .map { it.key }

        fallbackOrder.addAll(sorted)

        InfiniteClient.info("Font fallback order: ${fallbackOrder.joinToString(", ")}")
        InfiniteClient.info("Loaded ${loadedFonts.size} fonts. Default: $defaultFontKey")
        val commonChars =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
                "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほ" +
                "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホ" +
                "！？、。　"
        for (font in loadedFonts.values) {
            for (char in commonChars) {
                font.getOrCreateGlyph(char.code)
            }
        }
    }

    private fun fontNotFoundError(key: FontKey): IllegalArgumentException {
        val availableFontKeyList = loadedFonts.keys.map { "FontKey(\"${it.fontName}\", \"${it.style}\")" }
        return IllegalArgumentException("$key is not fonud. available: ${availableFontKeyList.joinToString(", ")}")
    }

    /**
     * テキストをレンダリング
     */
    fun render(
        graphics: Graphics2D,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        size: Float? = null,
        fontKey: FontKey? = null,
    ) {
        val targetFontKey = fontKey ?: defaultFontKey ?: return
        val primaryFont = getFont(targetFontKey) ?: throw fontNotFoundError(targetFontKey)
        val actualSize = size ?: primaryFont.fontSize
        val scale = actualSize / primaryFont.fontSize
        var currentX = x
        val baselineY = y + primaryFont.ascent * scale
        for (char in text) {
            val codepoint = char.code

            // フォールバックを使って描画可能なフォントを探す
            var fontToUse: Font? = null
            var glyphInfo: Font.GlyphInfo? = null

            // まず指定されたフォントで試す
            if (primaryFont.supportsChar(codepoint)) {
                fontToUse = primaryFont
                glyphInfo = primaryFont.getOrCreateGlyph(codepoint)
            } else {
                for (fallbackKey in fallbackOrder) {
                    if (fallbackKey == targetFontKey) continue // 既に試した
                    val fallbackFont = loadedFonts[fallbackKey] ?: continue
                    if (fallbackFont.supportsChar(codepoint)) {
                        fontToUse = fallbackFont
                        glyphInfo = fallbackFont.getOrCreateGlyph(codepoint)
                        break
                    }
                }
            }
            // 描画可能なフォントが見つからなかった
            if (fontToUse == null || glyphInfo == null) {
                // スペースとして扱う（進める）
                currentX += primaryFont.fontSize * 0.25f * scale
                continue
            }
            // グリフを描画
            if (glyphInfo.width > 0 && glyphInfo.height > 0) {
                val renderX = currentX + glyphInfo.xOffset * scale
                val renderY = baselineY + glyphInfo.yOffset * scale
                val renderWidth = glyphInfo.width * scale
                val renderHeight = glyphInfo.height * scale

                graphics.drawRotatedTexture(
                    fontToUse.textureIdentifier,
                    renderX,
                    renderY,
                    renderWidth,
                    renderHeight,
                    0f,
                    color,
                    glyphInfo.x,
                    glyphInfo.y,
                    glyphInfo.width,
                    glyphInfo.height,
                    fontToUse.getAtlasWidth().toFloat(),
                    fontToUse.getAtlasHeight().toFloat(),
                )
            }

            // 次の文字位置へ
            currentX += glyphInfo.advanceWidth * scale
        }
    }

    /**
     * テキストの幅を計算
     */
    fun getWidth(
        text: String,
        size: Float? = null,
        fontKey: FontKey? = null,
    ): Float {
        val targetFontKey = fontKey ?: defaultFontKey ?: return 0f
        val primaryFont = loadedFonts[targetFontKey] ?: return 0f

        val actualSize = size ?: primaryFont.fontSize
        val scale = actualSize / primaryFont.fontSize

        var width = 0f

        for (char in text) {
            val codepoint = char.code

            // フォールバックを考慮
            var glyphInfo: Font.GlyphInfo? = null

            if (primaryFont.supportsChar(codepoint)) {
                glyphInfo = primaryFont.getOrCreateGlyph(codepoint)
            } else {
                for (fallbackKey in fallbackOrder) {
                    if (fallbackKey == targetFontKey) continue

                    val fallbackFont = loadedFonts[fallbackKey] ?: continue
                    if (fallbackFont.supportsChar(codepoint)) {
                        glyphInfo = fallbackFont.getOrCreateGlyph(codepoint)
                        break
                    }
                }
            }

            width +=
                if (glyphInfo != null) {
                    glyphInfo.advanceWidth * scale
                } else {
                    primaryFont.fontSize * 0.25f * scale
                }
        }

        return width
    }

    /**
     * フォントを取得
     */
    fun getFont(fontKey: FontKey): Font? = loadedFonts[fontKey]
}
