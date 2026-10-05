package dev.eppopkaaddons.utils.render.world

import dev.eppopkaaddons.EppopkaAddons.mc
import dev.eppopkaaddons.event.EventBus
import dev.eppopkaaddons.event.impl.RenderWorldEvent
import dev.eppopkaaddons.init.types.ISelfInit
import dev.eppopkaaddons.ui.clickgui.ClickGuiScreen
import dev.eppopkaaddons.ui.clickgui.enums.SpatialCategory
import dev.eppopkaaddons.utils.render.world.Render3D.renderBillboardPanel
import dev.eppopkaaddons.utils.render.world.Render3D.renderLine
import dev.eppopkaaddons.utils.render.world.Render3D.renderString
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native level renderer for the /ea interface.
 *
 * World-only environment for the spatial UI.
 *
 * Interactive cards and panels are intentionally NOT drawn here. ClickGuiScreen
 * renders each real widget once from a projected world anchor and uses that same
 * projected geometry for hit-testing. Keeping decorative world geometry here
 * avoids the old duplicate, non-interactive hologram panels.
 */
object SpatialInterfaceWorldRenderer : ISelfInit {
    override fun init() {
        EventBus.register<RenderWorldEvent> {
            val screen = mc.screen as? ClickGuiScreen ?: return@register
            if (!screen.isSpatialHub()) renderCategory(event.ctx, screen)
        }
    }

    private fun renderCategory(ctx: RenderContext, screen: ClickGuiScreen) {
        val category = screen.spatialCategory() ?: return
        if (category == SpatialCategory.VISUAL && !screen.isLiveItemAuraPreviewActive()) renderVisualStudio(ctx)
    }

    /**
     * A non-destructive character studio: the normal level is never modified.
     * A depth-tested dark cyclorama is placed behind the avatar and a luminous
     * world grid is rendered around it. Leaving Visual removes both next frame.
     */
    private fun renderVisualStudio(ctx: RenderContext) {
        val player = mc.player ?: return
        val focus = player.position().add(0.0, player.bbHeight * .56, 0.0)
        val fromCamera = focus.subtract(ctx.camera.pos).normalize()
        val backdrop = focus.add(fromCamera.scale(2.25)).add(0.0, 1.3, 0.0)
        ctx.renderBillboardPanel(
            backdrop, 14.0, 9.0,
            Color(2, 5, 10, 246), Color(83, 120, 255, 110),
            phase = false,
        )

        val yaw = Math.toRadians(player.yRot.toDouble())
        val right = Vec3(cos(yaw), 0.0, sin(yaw))
        val forward = Vec3(-sin(yaw), 0.0, cos(yaw))
        val floorCenter = player.position().add(0.0, .025, 0.0)
        val radius = 8
        for (i in -radius..radius) {
            val major = i % 4 == 0
            val color = if (major) Color(94, 125, 255, 112) else Color(55, 86, 158, 58)
            val offset = i * .5
            ctx.renderLine(
                floorCenter.add(right.scale(offset)).subtract(forward.scale(radius * .5)),
                floorCenter.add(right.scale(offset)).add(forward.scale(radius * .5)),
                color, if (major) 1.65 else .75, true,
            )
            ctx.renderLine(
                floorCenter.add(forward.scale(offset)).subtract(right.scale(radius * .5)),
                floorCenter.add(forward.scale(offset)).add(right.scale(radius * .5)),
                color, if (major) 1.65 else .75, true,
            )
        }

        repeat(3) { ring ->
            val y = .18 + ring * .56
            val half = 1.18 + ring * .10
            val c = Color(112, 139, 255, 62 - ring * 12)
            ctx.renderLine(focus.add(right.scale(-half)).add(0.0, y, 0.0), focus.add(right.scale(half)).add(0.0, y, 0.0), c, 1.0, true)
        }
        ctx.renderString("VISUAL CALIBRATION SPACE", focus.add(0.0, 2.32, 0.0), Color(166, 184, 255), .62f, true)
    }
}