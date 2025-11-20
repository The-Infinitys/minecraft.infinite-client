package org.infinite.gui.theme

open class ThemeColors {
    // デフォルトで黒（0xFF000000）
    open val backgroundColor: Int = 0xFF000000.toInt()

    open fun panelColor(
        index: Int,
        length: Int,
        normalizedZ: Float,
    ): Int = 0xFFFFFFFF.toInt()

    // デフォルトで白（0xFFFFFFFF） - 背景の黒と対になる色
    open val foregroundColor: Int = 0xFFFFFFFF.toInt()

    // Primary Color: アプリケーションの主要な色
    open val primaryColor: Int = 0xFFAAAAAA.toInt()

    // Secondary Color: アプリケーションの副次的な色、Floating Action Buttonなどに使われる
    open val secondaryColor: Int = 0xFF444444.toInt()

    // アクセントカラーの定義
    open val redAccentColor: Int = 0xFFFF0000.toInt()
    open val orangeAccentColor: Int = 0xFFFF8800.toInt()
    open val yellowAccentColor: Int = 0xFFFFFF00.toInt()
    open val limeAccentColor: Int = 0xFF88FF00.toInt()
    open val greenAccentColor: Int = 0xFF00FF00.toInt()
    open val emeraldAccentColor: Int = 0xFF00FF88.toInt()
    open val aquaAccentColor: Int = 0xFF00FFFF.toInt()
    open val oceanAccentColor: Int = 0xFF0088FF.toInt()
    open val blueAccentColor: Int = 0xFF0000FF.toInt()
    open val violetAccentColor: Int = 0xFF8800FF.toInt()
    open val magentaAccentColor: Int = 0xFFFF00FF.toInt()
    open val purpleAccentColor: Int = 0xFFFF0088.toInt()

    // その他の一般的なカラープロパティを追加
    open val errorColor: Int = redAccentColor // エラーメッセージなどに使う色
    open val warnColor: Int = yellowAccentColor // カードやシートなどの表面の色
    open val infoColor: Int = aquaAccentColor
}
