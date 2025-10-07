package org.theinfinitys.ai.utils

import net.minecraft.util.math.Vec3d

/**
 * Kotlinのマイナス演算子 (-) を Vec3d にオーバーロードするための拡張関数。
 * @param other 減算する Vec3d
 * @return 結果の Vec3d
 */
operator fun Vec3d.minus(other: Vec3d): Vec3d = this.subtract(other)

/**
 * Kotlinの乗算演算子 (*) を Vec3d の定数倍にオーバーロードするための拡張関数。
 * @param multiplier 乗算するスカラー値
 * @return 結果の Vec3d
 */
operator fun Vec3d.times(multiplier: Double): Vec3d = this.multiply(multiplier)

// MoveTask で使用している Vec3dのプロパティを解決
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
val Vec3d.x: Double
    get() = this.getX()

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
val Vec3d.z: Double
    get() = this.getZ()
