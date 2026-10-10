package dev.eppopkaaddons.commands

import dev.eppopkaaddons.init.types.ICommandProvider
import dev.eppopkaaddons.features.impl.dungeon.OdinDungeonWaypoints
import dev.eppopkaaddons.ui.clickgui.ClickGuiScreen
import dev.eppopkaaddons.ui.hud.HudEditorScreen
import dev.eppopkaaddons.utils.GuiUtils

/** Own command namespace; safe to install alongside EppopkaAddons. */
object NaCommand : ICommandProvider {
    override fun CommandBuilder.command() {
        setName("ea", "eppopkaaddons")
        description("Opens the eppopkaaddons config GUI")
        runs { GuiUtils.setScreen(ClickGuiScreen()) }
        literal("hud") {
            description("Opens the HUD Editor")
            runs { GuiUtils.setScreen(HudEditorScreen()) }
        }
        literal("dwp") {
            with(OdinDungeonWaypoints) { installCommandTree() }
        }
    }
}
