package org.infinite.libs.client.aim.task.config

enum class AimCalculateMethod {
    Linear, // 線形補間
    EaseIn, // 加速
    EaseOut, // 減速
    EaseInOut, // 両端での加速・減速
    Immediate, // 即時
}
