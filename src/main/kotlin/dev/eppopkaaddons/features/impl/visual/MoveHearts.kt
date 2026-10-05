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
 * Moves & scales the VANILLA hearts (health bar) instead of drawing a custom one.
 * The vanilla renderer is wrapped via Fabric's HudElementRegistry and simply
 * translated/scaled by the pose matrix, so it looks 1:1 like vanilla.
 *
 * Position/scale are edited by dragging the "Vanilla Hearts" box in the mod's HUD Editor.
 */
object MoveHearts: Feature("Move and scale the vanilla hearts. Drag the box in the HUD Editor.", name = "Move Hearts") {
    private val moveArmor by ToggleSetting("Move Armor Bar", false).withDescription("The armor bar follows the hearts.")

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

    // Invisible in normal gameplay (vanilla draws the real hearts) - only the
    // draggable placeholder box is drawn inside the HUD Editor (example mode).
    private val hud = hudElement("Vanilla Hearts") { ctx, example ->
        if (example) {
            ctx.drawRect(0f, 0f, 81f, 9f, Color(200, 40, 40, 80))
            ctx.drawBorder(0f, 0f, 81f, 9f, Color(255, 90, 90, 220))
        }
        81f to 9f
    }

    init {
        moveVanillaElement(VanillaHudElements.HEALTH_BAR) { true }
        moveVanillaElement(VanillaHudElements.ARMOR_BAR) { moveArmor.value }
    }

    // Where vanilla draws the health bar by default (bottom-left of the hotbar area)
    private fun baseX() = mc.window.guiScaledWidth / 2f - 91f
    private fun baseY() = mc.window.guiScaledHeight - 39f

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
