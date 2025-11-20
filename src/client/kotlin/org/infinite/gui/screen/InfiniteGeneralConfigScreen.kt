package org.infinite.gui.screen

import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.option.OptionsScreen
import net.minecraft.text.Text

class InfiniteGeneralConfigScreen(
    optionsScreen: OptionsScreen,
) : Screen(Text.literal("Infinite Client General Settings")) {
    private val parent: Screen = optionsScreen
}
