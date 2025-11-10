package org.infinite.libs.graphics.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.resource.Resource
import net.minecraft.util.Identifier
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBTTAlignedQuad
import org.lwjgl.stb.STBTTBakedChar
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype.stbtt_BakeFontBitmap
import org.lwjgl.stb.STBTruetype.stbtt_GetBakedQuad
import org.lwjgl.stb.STBTruetype.stbtt_InitFont
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.jvm.optionals.getOrNull
import kotlin.math.roundToInt

object TextRenderer {
    // ロードされたすべてのフォントを保持
    private val loadedFonts: MutableMap<Identifier, Font> = mutableMapOf()

    // フォールバックの優先順位リスト（描画時にこの順に試行）
    private val fallbackOrder: MutableList<Identifier> = mutableListOf()

    // フォントアトラスのサイズ
    private const val ATLAS_WIDTH = 2048
    private const val ATLAS_HEIGHT = 2048
    private const val FONT_SIZE = 64f

    // 描画するASCII文字の範囲 (スペース ' ' からチルダ '~' まで)
    internal const val FIRST_CHAR = 32
    internal const val CHAR_COUNT = 96 // 32から127まで (127 - 32 + 1)

    // デフォルトフォントのキー
    lateinit var defaultFontKey: Identifier

    /**
     * 指定されたIdentifierのフォントをロードし、アトラスを作成します。
     */
    fun loadFont(
        identifier: Identifier,
        fontSize: Float,
    ): Font {
        if (loadedFonts.containsKey(identifier)) {
            return loadedFonts[identifier]!!
        }

        val fontBuffer: ByteBuffer
        var resource: Resource?
        var inputStream: InputStream? = null
        try {
            // リソースの読み込み
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
            throw RuntimeException("Failed to load font: $identifier", e)
        } finally {
            inputStream?.close()
        }

        val fontInfo = STBTTFontinfo.create()
        if (!stbtt_InitFont(fontInfo, fontBuffer)) {
            throw RuntimeException("Failed to initialize font info: $identifier")
        }

        // フォントアトラスのNativeImageを作成
        val nativeImage = NativeImage(ATLAS_WIDTH, ATLAS_HEIGHT, false)
        val bitmap = BufferUtils.createByteBuffer(ATLAS_WIDTH * ATLAS_HEIGHT)

        // グリフのパッキング
        val cdata = STBTTBakedChar.malloc(CHAR_COUNT) // ASCII 32-127
        stbtt_BakeFontBitmap(
            fontBuffer,
            fontSize,
            bitmap,
            ATLAS_WIDTH,
            ATLAS_HEIGHT,
            FIRST_CHAR, // 最初の文字 (スペース)
            cdata,
        )

        // NativeImageにビットマップデータをコピー
        for (y in 0 until ATLAS_HEIGHT) {
            for (x in 0 until ATLAS_WIDTH) {
                val alpha = bitmap.get(y * ATLAS_WIDTH + x).toInt() and 0xFF
                // ARGB形式でコピー (R, G, BをFFにして白文字にし、アルファをビットマップから取得)
                nativeImage.setColor(x, y, (alpha shl 24) or (0xFFFFFF))
            }
        }

        // テクスチャマネージャーに登録
        val fontTextureIdentifier =
            Identifier.of(
                identifier.namespace,
                "font/${identifier.path.replace(".ttf", "")}_${fontSize.roundToInt()}",
            )

        MinecraftClient.getInstance().textureManager.registerTexture(
            fontTextureIdentifier,
            NativeImageBackedTexture({ fontTextureIdentifier.toString() }, nativeImage),
        )

        val font = Font(identifier, fontSize, fontTextureIdentifier, cdata, fontInfo)
        loadedFonts[identifier] = font
        return font
    }

    /**
     * STB_Truetypeの標準的な方法でテキストをレンダリングします。
     */
    fun render(
        graphics: Graphics2D,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        size: Float? = null,
        font: Font? = null,
    ) {
        val defaultFont = font ?: loadedFonts[defaultFontKey] ?: return // デフォルトフォントがない場合は何もしない
        val size = size ?: defaultFont.fontSize
        val scale = size / defaultFont.fontSize
        val fontSize = defaultFont.ascent * scale

        // 初期X位置をスケーリングの逆数で割って設定
        val xPosBuffer = BufferUtils.createFloatBuffer(1)
        xPosBuffer.put(0, x / scale)

        val yPosBuffer = BufferUtils.createFloatBuffer(1)
        yPosBuffer.put(0, y / scale)
        val quad = STBTTAlignedQuad.malloc()

        for (char in text) {
            val charCode = char.code

            // 描画に使用するフォントとBakedCharデータを決定（フォールバック）
            var fontToUse: Font = defaultFont
            var bakedCharDataToUse: STBTTBakedChar.Buffer = defaultFont.bakedCharData

            // 1. デフォルトフォントが文字を持っているかチェック
            if (charCode !in FIRST_CHAR until (FIRST_CHAR + CHAR_COUNT)) {
                // 2. フォールバック順序を探索
                val fallbackFontId =
                    fallbackOrder.firstOrNull { id ->
                        // フォールバックフォントはすべて同じASCII範囲をカバーすると仮定
                        loadedFonts.containsKey(id)
                    }

                if (fallbackFontId != null) {
                    fontToUse = loadedFonts[fallbackFontId]!!
                    bakedCharDataToUse = fontToUse.bakedCharData
                } else {
                    // どのフォントでも描画できない場合はスキップ
                    continue
                }
            }

            // stbtt_GetBakedQuadは、渡されたxPosBufferを更新し、次の文字の開始位置を書き込む
            stbtt_GetBakedQuad(
                bakedCharDataToUse, // 選択されたフォントのデータ
                ATLAS_WIDTH,
                ATLAS_HEIGHT,
                charCode - FIRST_CHAR,
                xPosBuffer,
                yPosBuffer,
                quad,
                true,
            )

            // quadから描画に必要な情報を取得 (これらは元のサイズ（font.fontSize）でのピクセル値)
            var x0 = quad.x0()
            var y0 = quad.y0()
            var x1 = quad.x1()
            var y1 = quad.y1()

            // 取得した元のサイズでの座標を、要求されたサイズに合わせてスケーリング
            x0 *= scale
            y0 *= scale
            x1 *= scale
            y1 *= scale

            // UV座標は0.0〜1.0で返される
            val u0 = quad.s0() * ATLAS_WIDTH
            val v0 = quad.t0() * ATLAS_HEIGHT
            val u1 = quad.s1() * ATLAS_WIDTH
            val v1 = quad.t1() * ATLAS_HEIGHT

            val width = x1 - x0
            val height = y1 - y0
            val texWidth = u1 - u0
            val texHeight = v1 - v0
            val fontWidth = ATLAS_WIDTH.toFloat()
            val fontHeight = ATLAS_HEIGHT.toFloat()

            graphics.drawRotatedTexture(
                fontToUse.textureIdentifier, // 選択されたフォントのテクスチャ
                x0,
                y0 + fontSize,
                width,
                height,
                0f,
                color,
                u0,
                v0,
                texWidth,
                texHeight,
                fontWidth,
                fontHeight,
            )
            // xPosBufferはstbtt_GetBakedQuadが内部で更新しており、
            // その値は次の文字の元のサイズでのベースラインX位置を示しているため、
            // 次の文字に進むためにここでの操作は不要です。
        }

        quad.free()
    }

    /**
     * 指定されたアセットディレクトリ以下のすべての .ttf ファイルを再帰的に検索し、ロードします。
     * @param namespace フォントリソースのネームスペース (例: "modid")
     * @param baseDirectory アセット内のベースディレクトリ (例: "fonts")
     * @param FONT_SIZE ロードするフォントサイズ
     * @return ロードされた Font オブジェクトのリスト
     */
    private fun loadFontsFromAssetDirectory(
        namespace: String,
        baseDirectory: String,
    ): List<Font> {
        val loaded = mutableListOf<Font>()
        val resourceManager = MinecraftClient.getInstance().resourceManager

        // .ttf で終わるすべてのリソースIdentifierを検索
        val resourceIdentifiers =
            resourceManager
                .findResources(
                    baseDirectory,
                ) { identifier -> identifier.namespace == namespace && identifier.path.endsWith(".ttf") }
                .keys

        for (id in resourceIdentifiers) {
            try {
                // 個々のフォントをロード
                val font = loadFont(id, FONT_SIZE)
                loaded.add(font)
            } catch (e: Exception) {
                InfiniteClient.error("Failed to load font: $id. Error: ${e.message}")
            }
        }
        return loaded
    }

    /**
     * フォントの初期化とフォールバック順序の設定を行います。
     */
    fun initFonts(defaultId: Identifier) {
        // 1. すべての .ttf フォントを検索・ロード
        val loaded =
            loadFontsFromAssetDirectory(
                defaultId.namespace,
                defaultId.path
                    .split("/")
                    .dropLast(1)
                    .joinToString("/"),
            )

        // 2. ロードされたフォントをフォールバック順序リストに追加
        fallbackOrder.clear()
        loaded.forEach { font ->
            // デフォルトフォントは一旦リストに追加せず、後で先頭に追加する
            if (font.identifier != defaultId) {
                fallbackOrder.add(font.identifier)
            }
        }

        // 3. デフォルトフォントを決定し、フォールバック順序の先頭に設定
        if (loadedFonts.containsKey(defaultId)) {
            defaultFontKey = defaultId
            fallbackOrder.add(0, defaultId) // デフォルトを最優先として先頭に追加
        } else {
            InfiniteClient.error("Default font not loaded: $defaultId. Using first loaded font as default.")
            if (fallbackOrder.isNotEmpty()) {
                defaultFontKey = fallbackOrder.first()
            } else {
                throw RuntimeException("No fonts loaded and default font not found.")
            }
        }
        InfiniteClient.info("Loaded ${loadedFonts.size} fonts. Default: $defaultFontKey")
    }
}
