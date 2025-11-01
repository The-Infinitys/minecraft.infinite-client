package org.infinite.libs.client.aim.camera

import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CameraRoll(
    var yaw: Double,
    var pitch: Double,
) {
    /**
     * CameraRoll同士の足し算 (要素ごと)
     * operator fun plus(other: CameraRoll): CameraRoll
     */
    operator fun plus(other: CameraRoll): CameraRoll =
        CameraRoll(
            yaw = this.yaw + other.yaw,
            pitch = this.pitch + other.pitch,
        )

    /**
     * CameraRoll同士の引き算 (要素ごと)
     * operator fun minus(other: CameraRoll): CameraRoll
     */
    operator fun minus(other: CameraRoll): CameraRoll =
        CameraRoll(
            yaw = this.yaw - other.yaw,
            pitch = this.pitch - other.pitch,
        )

    operator fun times(scalar: Number): CameraRoll {
        val s = scalar.toDouble()
        return CameraRoll(
            yaw = this.yaw * s,
            pitch = this.pitch * s,
        )
    }

    operator fun div(scalar: Number): CameraRoll {
        val s = scalar.toDouble()
        if (s == 0.0) return CameraRoll(0.0, 0.0)
        return CameraRoll(
            yaw = this.yaw / s,
            pitch = this.pitch / s,
        )
    }

    fun magnitude(): Double = sqrt(this.yaw * this.yaw + this.pitch * this.pitch)

    /**
     * 最大回転速度 (maxSpeed) で移動量を制限したCameraRollを返します。
     * @param maxSpeed 最大回転速度
     * @return 制限後のCameraRoll
     */
    fun limitedBySpeed(maxSpeed: Double): CameraRoll {
        // maxSpeedが非負であることを保証 (念のため)
        require(maxSpeed >= 0.0) { "maxSpeed must be non-negative." }

        // 現在の移動量 (ノルム) を計算
        val magnitude = magnitude()
        // ノルムがmaxSpeed以下であれば、そのまま返す
        if (magnitude <= maxSpeed) {
            return this
        }

        // ノルムが0、またはmaxSpeedが0の場合は、(0, 0)を返す
        if (maxSpeed == 0.0) {
            return CameraRoll(0.0, 0.0)
        }

        // maxSpeedで制限するためにスケーリング係数を計算し、適用
        val scale = maxSpeed / magnitude
        return this * scale // 'times' operator (this.times(scale)) を使用
    }

    fun vec(): Vec3d {
        // 1. 角度をラジアンに変換 (度数で格納されていると仮定)
        // 既にラジアンで格納されている場合は、この行をコメントアウトしてください。
        val yawRad = this.yaw * PI / 180.0
        val pitchRad = this.pitch * PI / 180.0

        // 2. 角度から方向ベクトルを計算
        // Y軸が上方向、X-Z平面が水平面、+Xが初期前方と仮定した一般的な計算式
        val cosPitch = cos(pitchRad)
        val x = -sin(yawRad) * cosPitch
        val y = -sin(pitchRad) // Y軸は上下の回転(Pitch)のみに依存
        val z = cos(yawRad) * cosPitch

        // 結果は自動的に正規化されます (sin^2 + cos^2 = 1 のため)
        return Vec3d(x, y, z)
    }

    fun diffNormalize(): CameraRoll = CameraRoll(MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch))
}
