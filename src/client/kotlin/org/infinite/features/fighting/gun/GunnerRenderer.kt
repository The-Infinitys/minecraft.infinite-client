package org.infinite.features.fighting.gun

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import org.infinite.InfiniteClient
import org.infinite.features.rendering.detailinfo.DetailInfo
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.utils.rendering.getRainbowColor
import kotlin.math.cos
import kotlin.math.sin

object GunnerRenderer {
    private fun gunner(): Gunner? = InfiniteClient.getFeature(Gunner::class.java)

    fun renderInfo(
        context: DrawContext,
        tickCounter: RenderTickCounter,
    ) {
        val graphics2D = Graphics2D(context, tickCounter)
        val scaledWidth = context.scaledWindowWidth
        val scaledHeight = context.scaledWindowHeight // 注意: getScaledWindowHeight() を使用（DrawContextのメソッド）

        val gunner = gunner() ?: return
        val gunnerCount = gunner.gunnerCount()
        val totalCrossbow = gunner.totalCrossbows()
        val loadedCrossbow = gunner.loadedCrossbows()
        val gunnerMode = gunner.mode
        // クロスボウの状態に応じた色設定（unsigned intとして扱う）
        val bowColor =
            when {
                loadedCrossbow == 0 -> 0xFFFF0000
                loadedCrossbow < 5 -> 0xFFFFFF00
                loadedCrossbow == totalCrossbow -> 0xFF00FF00
                else -> 0xFFFFFFFF
            }.toInt()

        // ホットバーの位置を模倣: 中央X = scaledWidth / 2, Y = scaledHeight - 22 (ホットバー背景のY)
        val hotbarY = scaledHeight - 22
        val centerX = scaledWidth / 2

        // ホットバー背景を描画（mixinで標準ホットバーを取り消しているため、カスタム背景を追加）
        // ホットバー幅182px、高さ22pxを基準に
        val hotbarWidth = 182
        val hotbarHeight = 22
        val hotbarX = centerX - 91 // 91 = hotbarWidth / 2
        graphics2D.fill(hotbarX, hotbarY, hotbarWidth, hotbarHeight, 0x80000000.toInt()) // 半透明黒背景（標準ホットバーの代わり）

        // クロスボウ状況テキスト: ホットバー中央に配置（アイテム描画位置を参考にY調整）
        val itemRenderY = scaledHeight - 16 - 3 // hotbarコードのo位置
        val bowText = "$loadedCrossbow / $totalCrossbow"
        val bowTextWidth = MinecraftClient.getInstance().textRenderer.getWidth(bowText)
        graphics2D.drawText(
            bowText,
            centerX - bowTextWidth / 2,
            itemRenderY, // テキストを少し上に調整して中央寄せ
            bowColor,
            shadow = true, // 影付きで読みやすく
        )

        val gunnerText = "$gunnerCount"
        val arrowTextWidth = MinecraftClient.getInstance().textRenderer.getWidth(gunnerText)
        graphics2D.drawText(
            gunnerText,
            centerX + hotbarWidth / 2 - arrowTextWidth,
            hotbarY + hotbarHeight / 2, // ホットバー背景の下に配置
            0xFFFFFFFF.toInt(),
            shadow = true,
        )
        val modeText = "$gunnerMode"
        graphics2D.drawText(
            modeText,
            centerX - hotbarWidth / 2,
            hotbarY + hotbarHeight / 2, // ホットバー背景の下に配置
            0xFFFFFFFF.toInt(),
            shadow = true,
        )

        // オプション: 装填進捗バーをホットバー上に描画（視覚的に読みやすく）
        if (totalCrossbow > 0) {
            val progress = loadedCrossbow.toFloat() / totalCrossbow.toFloat()
            val barX = centerX - 50
            val barY = itemRenderY + 12 // テキストの下
            val barWidth = 100
            val barHeight = 4
            // 背景バー
            graphics2D.fill(barX, barY, barWidth, barHeight, 0xFF333333.toInt())
            // 進捗バー
            val filledWidth = (barWidth * progress).toInt()
            graphics2D.fill(barX, barY, filledWidth, barHeight, bowColor)
        }
    }

    fun renderSight(
        context: DrawContext,
        tickCounter: RenderTickCounter,
    ) {
        val graphics2D = Graphics2D(context, tickCounter)
        val rainbowColor = getRainbowColor()
        val scaledWidth = context.scaledWindowWidth
        val scaledHeight = context.scaledWindowHeight
        val boxSize = 16
        val detailInfo = InfiniteClient.getFeature(DetailInfo::class.java) ?: return
        val cameraEntity = MinecraftClient.getInstance().cameraEntity ?: return
        val reach = 10.0
        val targetHit = detailInfo.findCrosshairTarget(cameraEntity, reach, reach)
        when (targetHit.type) {
            HitResult.Type.ENTITY -> {
                graphics2D.drawBorder(
                    scaledWidth / 2 - boxSize / 2,
                    scaledHeight / 2 - boxSize / 2,
                    boxSize,
                    boxSize,
                    rainbowColor,
                )
                graphics2D.drawLine(
                    scaledWidth / 2f - boxSize,
                    scaledHeight / 2f,
                    scaledWidth / 2f + boxSize,
                    scaledHeight / 2f,
                    rainbowColor,
                    2,
                )
                graphics2D.drawLine(
                    scaledWidth / 2f,
                    scaledHeight / 2f - boxSize,
                    scaledWidth / 2f,
                    scaledHeight / 2f + boxSize,
                    rainbowColor,
                    2,
                )
            }

            HitResult.Type.BLOCK -> {
                graphics2D.drawBorder(
                    scaledWidth / 2 - boxSize / 2,
                    scaledHeight / 2 - boxSize / 2,
                    boxSize,
                    boxSize,
                    rainbowColor,
                )
            }

            HitResult.Type.MISS -> {
                graphics2D.drawLine(
                    scaledWidth / 2f - boxSize,
                    scaledHeight / 2f,
                    scaledWidth / 2f + boxSize,
                    scaledHeight / 2f,
                    0xFF888888.toInt(),
                    2,
                )
                graphics2D.drawLine(
                    scaledWidth / 2f,
                    scaledHeight / 2f - boxSize,
                    scaledWidth / 2f,
                    scaledHeight / 2f + boxSize,
                    0xFF888888.toInt(),
                    2,
                )
            }
        }
    }

    /**
     * 軌道上に、アニメーションする横線を描画します。
     * 描画間隔は 10.0 ティック（0.5 秒ごと）です。
     *
     * @param graphics3D 3D描画ヘルパーインスタンス。
     */
    fun renderOrbit(graphics3D: Graphics3D) {
        // アニメーションのパラメータ
        val time = System.currentTimeMillis()
        val loopLengthMillis = 1000L // アニメーション周期 (ミリ秒)
        val step = 10.0f // 横線を描画するティック間隔 (0.5秒 = 10ティックごと)
        val lineHalfWidth = 1.0 // 横線全体の幅は 2.0m (片側 1.0m)
        val client = graphics3D.client

        if (client.player == null) return

        // CrossBowOrbitのインスタンスを生成
        val orbit = CrossBowOrbit(client)

        // 軌道リストのサイズは衝突点または最大長で決まる
        // 軌道のティック総数 (orbit.size は 1ティック目以降の位置なので、サイズ-1が最大インデックス)
        val maxTick = (orbit.orbit.size - 1).toFloat()
        if (maxTick < 1.0f) return // 軌道が短すぎる場合は描画しない

        // ----------------------------------------------------------------------
        // アニメーション オフセットの計算
        // ----------------------------------------------------------------------

        // アニメーション周期をティック単位で表現 (例: 1000ms/50ms/tick = 20 tick/s)
        val loopLengthTicks = loopLengthMillis / 50.0f

        // 現在の周期におけるオフセットティック数 (0 から loopLengthTicks まで変化)
        // このオフセットを step で割った余りが、描画開始位置の「ズレ」になる
        val offset = (time % loopLengthMillis).toFloat() / loopLengthMillis.toFloat() * loopLengthTicks

        // 横線が一つ分移動するのにかかるオフセット量 (0.0 から step まで変化)
        val animOffsetTick = offset % step

        // ----------------------------------------------------------------------
        // 描画ループ
        // ----------------------------------------------------------------------

        val fadeStart = 10.0f // 透明になり始める距離 (ティック)
        val fadeEnd = 20.0f // 完全に透明になる距離 (ティック)
        val fadeRange = fadeEnd - fadeStart // 減衰範囲 (10f)

        // animOffsetTick から maxTick まで step 刻みで描画
        var currentTick = animOffsetTick
        while (currentTick <= maxTick + step) { // +step で最後の横線が完全に描画されることを保証
            // 描画位置を取得
            val drawTick = currentTick

            // 描画範囲外になったらスキップ (補間に必要な次のティックが範囲外の場合も含む)
            if (drawTick < 0.0f) {
                currentTick += step
                continue
            }

            // 距離に基づいてアルファ値を計算
            val alpha: Int =
                when {
                    drawTick < fadeStart -> 0xFF // 10fまで不透明
                    drawTick >= fadeEnd -> 0x00 // 20f以降は完全に透明
                    else -> {
                        // 10f から 20f の間で線形に減衰
                        // progress は 0.0 (fadeStart) から 1.0 (fadeEnd) へ
                        val progress = (drawTick - fadeStart) / fadeRange
                        // 0xFF * (1.0 - progress) で計算
                        (0xFF * (1.0f - progress)).toInt().coerceIn(0x00, 0xFF)
                    }
                }

            // 完全に透明なら描画をスキップ
            if (alpha == 0x00) {
                currentTick += step
                continue
            }

            // 1. 位置と速度ベクトルを取得
            val pos = orbit.pos(drawTick)
            val velocity = orbit.velocity(drawTick)

            // 速度がゼロに近い場合はスキップ (終点付近など)
            if (velocity.lengthSquared() < 0.0001) {
                currentTick += step
                continue
            }

            // 2. 軌道の接線ベクトル（速度）を地面に平行な平面に射影 (xz平面)
            val tangentFlat = Vec3d(velocity.x, 0.0, velocity.z).normalize()

            // 3. 地面に平行で、接線に直交するベクトル（横線の方向）を計算
            // tangentFlat = (tx, 0, tz) -> vecFlat = (-tz, 0, tx)
            val vecFlat = Vec3d(-tangentFlat.z, 0.0, tangentFlat.x).normalize()

            // 4. 横線の開始点と終了点を計算
            // 横線の中心(pos)から、横線の方向に halfWidth だけ移動
            val start = pos.subtract(vecFlat.multiply(lineHalfWidth))
            val end = pos.add(vecFlat.multiply(lineHalfWidth))

            // 5. 描画
            // 軌道の色を決定 (エンティティ命中なら虹色、そうでなければ白)
            val baseColor =
                if (orbit.finalHit == HitResult.Type.ENTITY) {
                    getRainbowColor()
                } else {
                    0xFFFFFFFF.toInt()
                }

            // アルファ値を合成: 0xAARRGGBB 形式
            val finalColor = (alpha shl 24) or (baseColor and 0x00FFFFFF)

            graphics3D.renderLine(start, end, finalColor, true)

            currentTick += step
        }
    }
}

class CrossBowOrbit(
    client: MinecraftClient,
) {
    // 矢の物理パラメータ
    private val gravity = -0.05 // 重力加速度 (下方向)
    private val resistance = 0.01 // 空気抵抗 (1.0 - resistance で乗算)
    private val power = 3.15 // 矢の初期速度の乗数 (クロスボウのデフォルト値)

    // シミュレーションパラメータ
    private val maxLength = 1000 // 最大シミュレーションティック数
    private val world: ClientWorld = client.world ?: throw IllegalStateException("ClientWorld is null")
    private val player = client.player ?: throw IllegalStateException("ClientPlayer is null")

    // 結果格納
    val orbit: List<Vec3d> // 軌道上の点のリスト
    val finalHit: HitResult.Type // 衝突結果 (MISS, BLOCK, ENTITY)

    init {
        // 現在のプレイヤーの状態を取得
        var currentPos = player.eyePos
        val yaw = player.yaw
        val pitch = player.pitch

        // 1. 角度をラジアンに変換
        val yawRadians = Math.toRadians(yaw.toDouble())
        val pitchRadians = Math.toRadians(pitch.toDouble())

        // 2. 初期速度ベクトルの計算
        val vX = -sin(yawRadians) * cos(pitchRadians)
        val vY = -sin(pitchRadians)
        val vZ = cos(yawRadians) * cos(pitchRadians)
        var velocity = Vec3d(vX, vY, vZ).multiply(power)

        // 軌道と衝突結果を格納する可変リストと変数
        val tempOrbit: MutableList<Vec3d> = ArrayList(maxLength)
        var currentHit: HitResult.Type = HitResult.Type.MISS // 初期位置のダミー

        // シミュレーションループ
        repeat(maxLength) {
            val previousPos = currentPos
            currentPos = currentPos.add(velocity)
            tempOrbit.add(currentPos)
            // 4-1. ブロック衝突判定
            val blockHit =
                world.raycast(
                    RaycastContext(
                        previousPos,
                        currentPos,
                        RaycastContext.ShapeType.COLLIDER, // 固形ブロックとの衝突
                        RaycastContext.FluidHandling.NONE, // 流体は無視
                        player, // レイキャストの実行者 (特定のエンティティを無視するため)
                    ),
                )

            if (blockHit.type != HitResult.Type.MISS) {
                // ブロックに衝突した場合、軌道を衝突点まで修正し、ループを終了
                tempOrbit[tempOrbit.lastIndex] = blockHit.pos // 最後の点を衝突点に置換
                currentHit = blockHit.type
                return@repeat
            }

            // 4-2. エンティティ衝突判定 (ブロックに衝突しなかった場合のみ)
            // Raycast用のBoxを作成 (始点と終点を含む最小のAABB)
            val searchBox = Box(previousPos, currentPos).expand(1.0) // 判定を広げるために少し拡大
            val entities =
                world.getOtherEntities(player, searchBox) { entity: Entity ->
                    entity.isAlive && entity != player // 衝突可能で生きているエンティティ (プレイヤー自身は除く)
                }

            var entityHit: HitResult? = null
            var closestDistanceSq = Double.MAX_VALUE

            // 衝突したエンティティの中で最も近いものを探す
            for (entity in entities) {
                // エンティティの衝突Boxと線分(previousPos -> currentPos)の衝突をチェック
                val box = entity.boundingBox.expand(0.3) // 矢の衝突判定のためBoxを少し拡大
                val intersection = box.raycast(previousPos, currentPos)

                if (intersection.isPresent) {
                    val e = intersection.get()
                    val hitPos = Vec3d(e.x, e.y, e.z)
                    val distSq = previousPos.squaredDistanceTo(hitPos)
                    if (distSq < closestDistanceSq) {
                        closestDistanceSq = distSq
                        entityHit =
                            net.minecraft.util.hit
                                .EntityHitResult(entity, hitPos)
                    }
                }
            }

            if (entityHit != null) {
                // エンティティに衝突した場合、軌道を衝突点まで修正し、ループを終了
                tempOrbit[tempOrbit.lastIndex] = entityHit.pos
                currentHit = entityHit.type
                return@repeat
            }

            // 5. 速度の更新
            // 重力適用: Y軸方向に gravity を加算 (MinecraftのVec3d.addは新しいインスタンスを返す)
            // 矢の重力は下向き(-Y)なので、速度のY成分に-gravityを加えるべきだが、
            // 提供されたコードが +gravity であったため、**元のコードの意図**を尊重し**+gravity**としています。
            // ⚠️ **注**: 通常のMinecraftの矢のシミュレーションでは `-gravity` が使われます。
            velocity = velocity.add(0.0, gravity, 0.0)

            // 空気抵抗適用: (1.0 - resistance) を乗算
            velocity = velocity.multiply(1.0 - resistance)
        }

        // 結果をプロパティに設定
        orbit = tempOrbit
        finalHit = currentHit
    } // ----------------------------------------------------------------------------------

    /**
     * 指定されたティックにおける予測位置を返します。
     * @param tick 予測したい時間（ティック）。0.0 はプレイヤーの視点位置（始点）を意味します。
     */
    fun pos(tick: Float): Vec3d {
        // 0.0 tick はシミュレーションの始点（プレイヤーの eyePos）を返す
        if (tick <= 0.0f) {
            // NOTE: プレイヤーの eyePos は CrossBowOrbit のプロパティとして保持されている必要がありますが、
            // 簡略化のため、ここでは orbit.first()（1ティック後の位置）を基準に扱います。
            // 正確にはプレイヤーの初期位置を返す必要があります。
            // ここでは、始点位置（orbit[0]の1ティック前の位置）は計算できないため、最小値として最初の位置を返します。
            // もし正確な始点が必要なら、CrossBowOrbitクラスに初期位置を保存する必要があります。
            return orbit.first()
        }

        // tickを整数値に変換
        val index = tick.toInt()

        // ティックが保存された軌道の範囲外であれば、最後の位置を返す（衝突点または最大長）
        if (index >= orbit.size) {
            return orbit.last()
        }

        // 整数ティックでの位置を取得 (orbit[index] は index+1 ティック後の位置に相当)
        val posInt = orbit[index]

        // 小数部分
        val fraction = tick - index
        if (fraction == 0.0f) {
            return posInt
        }

        // 次の位置を取得 (最後の要素の場合は補間しない)
        if (index + 1 >= orbit.size) {
            return posInt
        }

        val posNext = orbit[index + 1]

        // 線形補間: pos_interpolated = pos_int + (pos_next - pos_int) * fraction
        return posInt.add(posNext.subtract(posInt).multiply(fraction.toDouble()))
    }

    /**
     * 指定されたティックにおける予測速度を返します。
     * 速度は、位置の差分 (P(T+1) - P(T)) から近似されます。
     */
    fun velocity(tick: Float): Vec3d {
        // 速度は位置の差から近似されるため、最低2点が必要です。
        if (orbit.size < 2) return Vec3d.ZERO

        // 速度は P(T+1) - P(T) で近似される。T=0 の速度は P(1) - P(0) で、
        // P(1)は orbit[0] に格納されているが、P(0)（初期位置）がないため、
        // ここでは orbit[index] と orbit[index+1] の差を平均速度として使用します。

        // tickを整数値に変換
        val index = tick.toInt()

        // 速度の計算に使用できる最大のインデックスは orbit.size - 2
        val maxIndexForVelocity = orbit.size - 2

        // 範囲チェック (最後の速度はシミュレーションの終点のものを使用)
        if (index < 0) {
            // 最初の速度（tick 0 から 1 への移動に使われた速度）を返す
            return orbit[1].subtract(orbit[0])
        }
        if (index >= maxIndexForVelocity) {
            // 最後の速度を返す (orbit.size-2 と orbit.size-1 の差)
            return orbit.last().subtract(orbit[orbit.size - 2])
        }

        // 整数ティックでの平均速度を計算
        // vel_int = orbit[index + 1] - orbit[index] (index から index+1 への移動の平均速度)
        val velInt = orbit[index + 1].subtract(orbit[index])

        // 小数部分
        val fraction = tick - index
        if (fraction == 0.0f) {
            return velInt
        }

        // 次の平均速度を計算 (index+1 から index+2 への移動の平均速度)
        // vel_next = orbit[index + 2] - orbit[index + 1]
        if (index + 2 >= orbit.size) {
            return velInt
        }
        val velNext = orbit[index + 2].subtract(orbit[index + 1])

        // 線形補間: vel_interpolated = vel_int + (vel_next - vel_int) * fraction
        return velInt.add(velNext.subtract(velInt).multiply(fraction.toDouble()))
    }
}
