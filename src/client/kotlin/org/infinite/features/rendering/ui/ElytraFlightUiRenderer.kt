package org.infinite.features.rendering.ui

import net.minecraft.util.math.MathHelper
import org.infinite.InfiniteClient
import org.infinite.features.automatic.pilot.AutoPilot
import org.infinite.features.automatic.pilot.flightTime
import org.infinite.libs.client.aim.camera.CameraRoll
import org.infinite.libs.client.player.ClientInterface
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.transparent
import org.infinite.utils.toRadians
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class ElytraFlightUiRenderer : ClientInterface() {
    fun render(graphics2D: Graphics2D) {
        val info = flightInfo(graphics2D.tickProgress) ?: return
        renderAttitudeIndicator(graphics2D, info.directions)
        renderHeight(graphics2D, info.altitude)
        // 残り飛行可能時間表示の追加
        renderFlightTime(graphics2D, info.flightTime)
    }

    val bottomLength: Int
        get() {
            val hyperUi = InfiniteClient.getFeature(HyperUi::class.java) ?: return 0
            return (hyperUi.heightSetting.value * 2).coerceAtLeast(32)
        }

    // --- 残り飛行可能時間を描画する関数の実装 ---
    private fun renderFlightTime(
        graphics2D: Graphics2D,
        remainingTimeSeconds: Double,
    ) {
        // 時:分:秒の形式に変換
        // 秒を負の値として扱うことは想定しないが、念のため0以下を最小値とする
        val safeSeconds = remainingTimeSeconds.coerceAtLeast(0.0)

        val hours = (safeSeconds / 3600).toInt()
        val minutes = ((safeSeconds % 3600) / 60).toInt()
        val seconds = (safeSeconds % 60).toInt()

        val timeString =
            when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
                else -> String.format("%02d:%02d", minutes, seconds)
            }

        val displayString = "Remaining Time: $timeString"
        val colors = InfiniteClient.theme().colors
        val foregroundColor = colors.foregroundColor

        // 画面左上隅に描画 (X=4, Y=4)
        val textX = 4
        val textY = 4

        graphics2D.drawText(
            displayString,
            textX,
            textY,
            foregroundColor,
            true, // shadow
        )
    }
    // ------------------------------------

    private fun renderAttitudeIndicator(
        graphics2D: Graphics2D,
        direction: CameraRoll,
    ) {
        val width = graphics2D.width
        val height = graphics2D.height
        val renderSize = 16
        val (centerX, centerY) = width / 2 to height - bottomLength - renderSize
        val colors = InfiniteClient.theme().colors
        val alpha = 100
        val accentColor = colors.primaryColor.transparent(alpha)
        val airColor = colors.aquaAccentColor.transparent(alpha)
        val groundColor = colors.limeAccentColor.transparent(alpha)
        val pitchHeight = sin(toRadians(direction.pitch))
        val startX = centerX - renderSize
        val startY = centerY - renderSize
        val endX = centerX + renderSize
        val endY = centerY + renderSize
        graphics2D.enableScissor(
            startX,
            startY,
            endX,
            endY,
        )
        val clipPoint = centerY - renderSize * pitchHeight
        graphics2D.fillRect(
            startX.toDouble(),
            startY.toDouble(),
            endX.toDouble(),
            clipPoint,
            airColor,
        )
        graphics2D.fillRect(
            startX.toDouble(),
            clipPoint,
            endX.toDouble(),
            endY.toDouble(),
            groundColor,
        )
        graphics2D.drawLine(
            startX,
            centerY,
            endX,
            centerY,
            accentColor,
            2,
        )
        graphics2D.disableScissor()
        val compassPoints =
            mapOf(
                0f to "S",
                90f to "W",
                180f to "N",
                270f to "E",
            )
        val clipOffset = (renderSize + graphics2D.fontHeight() * 2)
        val textOffset = sqrt(2.0) * clipOffset
        for ((degree, char) in compassPoints) {
            val relativeYaw = MathHelper.wrapDegrees(degree - direction.yaw)
            val relativeRad = toRadians(relativeYaw)
            val textX = centerX + (sin(relativeRad) * textOffset).toInt().coerceIn(-renderSize, renderSize)
            val textY = centerY - (cos(relativeRad) * textOffset).toInt().coerceIn(-renderSize, renderSize)
            val textWidth = graphics2D.textWidth(char)
            graphics2D.drawText(
                char,
                textX - textWidth / 2,
                textY - graphics2D.fontHeight() / 2,
                if (char == "N") {
                    InfiniteClient.theme().colors.purpleAccentColor
                } else {
                    InfiniteClient.theme().colors.foregroundColor
                },
                true, // shadow
            )
        }
    }

    fun renderHeight(
        graphics2D: Graphics2D,
        altitude: Double,
    ) {
        val width = graphics2D.width
        val height = graphics2D.height
        val attitudeIndicatorCenterY = height - bottomLength
        val gaugeHeight = height / 2.0
        val gaugeBottomY = attitudeIndicatorCenterY.toDouble()
        val gaugeTopY = gaugeBottomY - gaugeHeight

        val colors = InfiniteClient.theme().colors
        val foregroundBaseColor = colors.foregroundColor
        val majorMarkBaseColor = colors.primaryColor
        val backgroundColor = colors.backgroundColor.transparent(100)
        val gaugeWidth = 32.0
        val gaugeRightX = width - 4
        val gaugeLeftX = gaugeRightX - gaugeWidth

        // 1. 高度計の背景を描画
        graphics2D.fillRect(
            gaugeLeftX,
            gaugeTopY,
            gaugeRightX.toDouble(),
            gaugeBottomY,
            backgroundColor,
        )
        val pixelToMeterRatio = 0.5
        val range = gaugeHeight * pixelToMeterRatio
        // 最小範囲を保証 (例: 少なくとも±50mは表示)
        val finalRange = range.coerceAtLeast(50.0)

        val minAltitude = altitude - finalRange
        val maxAltitude = altitude + finalRange

        // 目盛りのステップ幅を範囲に合わせて調整 (例: 5m/10m/20m)
        val targetSteps = 20.0 // ゲージの高さに表示したいステップの目安
        // 最適なステップ幅の候補を計算 (1, 2, 5, 10, 20, 50, 100...)
        val rawStep = (finalRange * 2) / targetSteps
        val step = calculateCleanStep(rawStep)

        // 主要目盛りの間隔をステップの5倍に設定 (例: step=10mなら50mごと)
        val majorStep = step * 5.0

        var currentMarkAltitude = kotlin.math.floor(minAltitude / step) * step

        val textHeight = 8.0
        val textVerticalOffset = -textHeight / 2.0

        // 3. 現在の高度を示すポインター/ラインと数値表示 (変更なし)
        val pointerY = gaugeBottomY - gaugeHeight / 2.0
        val pointerColor = majorMarkBaseColor.transparent(255)

        graphics2D.drawLine(
            gaugeLeftX,
            pointerY,
            gaugeRightX.toDouble(),
            pointerY,
            pointerColor,
            2,
        )
        val currentHeightString =
            "${altitude.roundToInt()}m"
        graphics2D.drawText(
            currentHeightString,
            gaugeLeftX - graphics2D.textWidth(currentHeightString) - gaugeWidth,
            pointerY + textVerticalOffset,
            pointerColor,
            true,
        )

        // フェードアウトゾーンの設定
        val fadeZoneHeight = gaugeHeight * 0.1
        val maxAlpha = 200.0

        while (currentMarkAltitude <= maxAltitude) {
            val normalizedHeight = (currentMarkAltitude - minAltitude) / (maxAltitude - minAltitude)
            val markY = gaugeBottomY - normalizedHeight * gaugeHeight

            if (markY in gaugeTopY..gaugeBottomY) {
                // --- 透明度の計算 (変更なし) ---
                var alpha = maxAlpha
                if (markY < gaugeTopY + fadeZoneHeight) {
                    val ratio = (markY - gaugeTopY) / fadeZoneHeight
                    alpha = maxAlpha * ratio
                } else if (markY > gaugeBottomY - fadeZoneHeight) {
                    val ratio = (gaugeBottomY - markY) / fadeZoneHeight
                    alpha = maxAlpha * ratio
                }
                val currentAlpha = alpha.coerceIn(0.0, 255.0).roundToInt()

                // --- 目盛りとテキストの描画 ---
                // 主要目盛りは majorStep 間隔で描画
                val isMajorMark =
                    (currentMarkAltitude % majorStep < 0.001 || majorStep - (currentMarkAltitude % majorStep) < 0.001)

                val markLength = if (isMajorMark) 12.0 else 8.0

                val foregroundColor = foregroundBaseColor.transparent(currentAlpha)
                val majorMarkColor = majorMarkBaseColor.transparent(currentAlpha)
                val markColor = if (isMajorMark) majorMarkColor else foregroundColor

                // 目盛り線
                graphics2D.drawLine(
                    gaugeRightX - markLength,
                    markY,
                    gaugeRightX.toDouble(),
                    markY,
                    markColor,
                    1,
                )

                // テキストラベルの描画
                if (isMajorMark || (step > 5.0 && (currentMarkAltitude % step < 0.001 || step - (currentMarkAltitude % step) < 0.001))) {
                    graphics2D.drawText(
                        currentMarkAltitude.toInt().toString(),
                        gaugeLeftX - 5.0,
                        markY + textVerticalOffset,
                        markColor,
                        true,
                    )
                }
            }
            currentMarkAltitude += step
        }
    }

    // rawStepに近い、人間が読みやすい「きれいな」ステップ幅を計算するヘルパー関数
    private fun calculateCleanStep(rawStep: Double): Double {
        if (rawStep <= 0) return 1.0

        // rawStepの10の位を求める
        val magnitude = kotlin.math.floor(kotlin.math.log10(rawStep))
        val base = 10.0.pow(magnitude) // 1, 10, 100, ...

        // 候補値 (1, 2, 5) * base
        val candidates = listOf(1.0, 2.0, 5.0).map { it * base }

        // rawStepに最も近い、かつ rawStep以上の候補を選ぶ
        var bestStep = 1000000.0 // 十分大きな値
        for (candidate in candidates) {
            if (candidate >= rawStep) {
                bestStep = candidate
                break
            }
        }

        // 適切なステップが見つからなかった場合（rawStepが非常に大きいなど）
        if (bestStep == 1000000.0) {
            return 10.0.pow(magnitude + 1) // 次の10の位
        }
        // 最小ステップを5mに制限
        return bestStep.coerceAtLeast(5.0)
    }

    // ... (FlightInfoとflightInfo関数は変更なし)
    data class FlightInfo(
        val directions: CameraRoll,
        val altitude: Double,
        val speed: Double,
        val flightTime: Double,
    )

    private fun flightInfo(ticKProgress: Float): FlightInfo? {
        val player = player ?: return null
        val world = world ?: return null
        val autoPilot = InfiniteClient.getFeature(AutoPilot::class.java) ?: return null
        val yaw = player.getLerpedYaw(ticKProgress).toDouble()
        val pitch = player.getLerpedPitch(ticKProgress).toDouble()
        val directions = CameraRoll(yaw, pitch)
        val pos = player.getLerpedPos(ticKProgress)
        val seaHeight = world.seaLevel
        val altitude = pos.y - seaHeight
        val speed = autoPilot.flySpeed
        val flightTime = flightTime()
        return FlightInfo(directions, altitude, speed, flightTime)
    }
}
