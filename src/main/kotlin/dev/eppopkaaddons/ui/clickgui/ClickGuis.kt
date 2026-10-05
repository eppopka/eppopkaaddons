package dev.eppopkaaddons.ui.clickgui

import dev.eppopkaaddons.EppopkaAddons.mc
import dev.eppopkaaddons.features.Feature
import net.minecraft.client.gui.screens.Screen

object ClickGuis {
    interface ConfigScreen {
        var selectedFeature: Feature?
        fun openFeatureWindow(feature: Feature, preferredX: Float? = null, preferredY: Float? = null)
        fun isMouseOverConfigWindow(mouseX: Int, mouseY: Int): Boolean
    }
    fun newScreen(): Screen = ClickGuiScreen()
    fun closeOpen() { if (mc.screen is ConfigScreen) mc.setScreen(null) }
}
