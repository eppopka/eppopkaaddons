package dev.eppopkaaddons.features.impl.visual

import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.ui.clickgui.ClickGuis
import dev.eppopkaaddons.config.types.ButtonSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.ui.hud.HudEditorScreen
import dev.eppopkaaddons.utils.GuiUtils
import dev.eppopkaaddons.utils.render.Render2D.drawBorder
import dev.eppopkaaddons.utils.render.Render2D.drawRect
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.resources.Identifier
import java.awt.Color
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement as FabricHudElement


/**
 * Moves & scales the VANILLA hotbar instead of drawing a custom copy.
 * The vanilla renderer is wrapped via Fabric's HudElementRegistry and simply
 * translated/scaled by the pose matrix, so items, selection frame, attack
 * indicator etc. all stay 100% vanilla.
 *
 * Position/scale are edited by dragging the "Vanilla Hotbar" box in the mod's HUD Editor.
 */
object MoveHotbar: Feature("Move and scale the vanilla hotbar. Drag the box in the HUD Editor.", name = "Move Hotbar") {
    private val moveItemName by ToggleSetting("Move Item Name", false).withDescription("The held item name follows the hotbar.")
    private val moveInfoBar by ToggleSetting("Move XP Bar", false).withDescription("The XP/info bar (and level number) follows the hotbar.")

    private val editButton by ButtonSetting("Edit Position (HUD Editor)") {
        ClickGuis.closeOpen()
        GuiUtils.setScreen(HudEditorScreen())
    }

    private val resetButton by ButtonSetting("Reset Position") {
        hud.x = 0f
        hud.y = 0f
        hud.scale = 1f
        positionInitialized = false
    }

    private var positionInitialized = false

    // Invisible in normal gameplay (vanilla draws the real hotbar) - only the
    // draggable placeholder box is drawn inside the HUD Editor (example mode).
    private val hud = hudElement("Vanilla Hotbar") { ctx, example ->
        if (example) {
            ctx.drawRect(0f, 0f, 182f, 22f, Color(40, 120, 200, 70))
            ctx.drawBorder(0f, 0f, 182f, 22f, Color(90, 170, 255, 220))
            for (i in 1..8) ctx.drawRect(i * 20.2f, 1f, 1f, 20f, Color(90, 170, 255, 120))
        }
        182f to 22f
    }

    init {
        moveVanillaElement(VanillaHudElements.HOTBAR) { true }
        moveVanillaElement(VanillaHudElements.HELD_ITEM_TOOLTIP) { moveItemName.value }
        moveVanillaElement(VanillaHudElements.INFO_BAR) { moveInfoBar.value }
        moveVanillaElement(VanillaHudElements.EXPERIENCE_LEVEL) { moveInfoBar.value }
    }

    // Where vanilla draws the hotbar by default (bottom center)
    private fun baseX() = mc.window.guiScaledWidth / 2f - 91f
    private fun baseY() = mc.window.guiScaledHeight - 22f

    private fun ensurePosition() {
        if (positionInitialized) return
        positionInitialized = true
        // First ever use (nothing saved in config yet): snap the editor box onto the vanilla spot
        if (hud.x == 0f && hud.y == 0f) {
            hud.x = baseX()
            hud.y = baseY()
        }
    }

    private fun moveVanillaElement(id: Identifier, active: () -> Boolean) {
        HudElementRegistry.replaceElement(id) { original ->
            FabricHudElement { graphics, delta ->
                if (!enabled || !active()) original.extractRenderState(graphics, delta)
                else {
                    ensurePosition()
                    val s = hud.scale
                    val pose = graphics.pose()
                    pose.pushMatrix()
                    pose.translate(hud.x - s * baseX(), hud.y - s * baseY())
                    pose.scale(s, s)
                    original.extractRenderState(graphics, delta)
                    pose.popMatrix()
                }
            }
        }
    }
}
