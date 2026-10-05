package dev.eppopkaaddons.ui.clickgui

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

@Suppress("unused")
class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory() = ConfigScreenFactory { ClickGuiScreen() }
}
