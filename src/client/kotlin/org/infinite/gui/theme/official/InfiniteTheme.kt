package org.infinite.gui.theme.official

import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.ThemeColors
import org.infinite.utils.rendering.getRainbowColor

class InfiniteTheme : Theme("infinite", InfiniteColor())

class InfiniteColor : ThemeColors() {
    override val backgroundColor: Int
        get() = 0x88FFFFFF.toInt()
    override val primaryColor: Int
        get() = getRainbowColor()
}
