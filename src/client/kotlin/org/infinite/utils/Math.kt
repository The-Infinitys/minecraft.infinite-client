package org.infinite.utils

import kotlin.math.PI

fun toRadians(direction: Float) = (direction / 180.0 * PI).toFloat()

fun toRadians(direction: Double) = direction / 180.0 * PI

/**
 * 任意の個数のNumber型の引数を取り、その平均を返します。
 * 引数が渡されない場合は 0.0 を返します。
 *
 * @param numbers 平均を計算したい数値のリスト (可変長引数)
 * @return 引数として渡された数値の平均 (Double)
 */
fun average(vararg numbers: Number): Double {
    // 1. 引数が渡されていない場合（配列が空の場合）は 0.0 を返す
    if (numbers.isEmpty()) {
        return 0.0
    }

    // 2. 配列の要素を Double 型に変換しながら合計を計算する
    val sum = numbers.sumOf { it.toDouble() }

    // 3. 合計を要素数で割って平均を計算する
    return sum / numbers.size
}
