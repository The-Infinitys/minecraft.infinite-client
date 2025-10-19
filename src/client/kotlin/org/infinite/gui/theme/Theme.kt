package org.infinite.gui.theme

open class Theme(
    val name: String,
    val colors: ThemeColors,
)

open class ThemeColors {
    // デフォルトで黒（0xFF000000）
    open val backgroundColor: Int = 0xFF000000.toInt()

    // デフォルトで白（0xFFFFFFFF） - 背景の黒と対になる色
    open val foregroundColor: Int = 0xFFFFFFFF.toInt()

    // Primary Color: アプリケーションの主要な色
    open val primaryColor: Int = 0xFF6200EE.toInt()

    // Secondary Color: アプリケーションの副次的な色、Floating Action Buttonなどに使われる
    open val secondaryColor: Int = 0xFF03DAC6.toInt()

    // アクセントカラーの定義
    open val redAccentColor: Int = 0xFFB00020.toInt()
    open val yellowAccentColor: Int = 0xFFFFC107.toInt()
    open val greenAccentColor: Int = 0xFF4CAF50.toInt()
    open val aquaAccentColor: Int = 0xFF00BCD4.toInt()

    // その他の一般的なカラープロパティを追加
    open val errorColor: Int = redAccentColor // エラーメッセージなどに使う色
    open val warnColor: Int = yellowAccentColor // カードやシートなどの表面の色
    open val infoColor: Int = aquaAccentColor
}
