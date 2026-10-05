package dev.eppopkaaddons.ui.clickgui

import dev.eppopkaaddons.config.ConfigManager
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.features.impl.general.AetherOSSettings
import dev.eppopkaaddons.features.impl.visual.ItemAura
import dev.eppopkaaddons.features.impl.visual.RotatingItem
import dev.eppopkaaddons.ui.clickgui.components.settings.Style
import dev.eppopkaaddons.ui.clickgui.enums.SpatialCategory
import dev.eppopkaaddons.ui.clickgui.foundation.*
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.pill
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.rounded
import dev.eppopkaaddons.ui.clickgui.foundation.GlassRenderer.surface
import dev.eppopkaaddons.ui.utils.Resolution
import dev.eppopkaaddons.ui.utils.TextInputHandler
import dev.eppopkaaddons.utils.render.Render2D.drawHorizontalGradient
import dev.eppopkaaddons.utils.render.Render2D.drawLine
import gg.essential.universal.UKeyboard
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.awt.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ClickGuiScreen : Screen(Component.literal("Eppopka Spatial Interface")), ClickGuis.ConfigScreen {
    private enum class Scene { HUB, CATEGORY }
    private data class CategoryHit(val category: SpatialCategory, val rect: Rect)
    private data class ModuleHit(val feature: Feature, val rect: Rect)
    private data class Shockwave(val x: Float, val y: Float, val color: Color, val born: Long)

    private val opening = Spring(0f, 235f, 24f)
    private val panelReveal = Spring(0f, 285f, 27f)
    private val categorySwap = Spring(1f, 360f, 31f)
    private val burstMotion = SpatialCategory.entries.associateWith { Spring(0f, 305f, 22f) }
    private val cardMotion = SpatialCategory.entries.associateWith { Spring(0f, 390f, 32f) }
    private val rowMotion = HashMap<Feature, Spring>()
    private val stateMotion = HashMap<Feature, Spring>()
    private val cursorX = Spring(0f, 92f, 18f)
    private val cursorY = Spring(0f, 92f, 18f)
    private val inspector = SettingInspector()
    private val categoryHits = ArrayList<CategoryHit>()
    private val moduleHits = ArrayList<ModuleHit>()
    private val shockwaves = ArrayList<Shockwave>()
    private val modulesCache = SpatialCategory.entries.associateWith { it.modules() }
    private val allModules by lazy(LazyThreadSafetyMode.NONE) {
        SpatialCategory.entries.flatMap(::modules).distinctBy { it.jsonName }.sortedBy { it.name }
    }
    private var searchQuery = ""
    private val searchHandler = TextInputHandler(
        { searchQuery },
        {
            searchQuery = it
            searchScroll = 0f
            selectedFeature = null
        },
    )
    private var searchScroll = 0f
    private var searchMaxScroll = 0f
    private var searchBox = Rect(0f, 0f, 0f, 0f)
    private var settingsButton = Rect(0f, 0f, 0f, 0f)
    private val openedAt = System.nanoTime()
    private var scene = Scene.HUB
    private var category: SpatialCategory? = null
    private var scroll = 0f
    private var maxScroll = 0f
    private var cleanedUp = false
    private var orbitDragging = false

    override var selectedFeature: Feature?
        get() = inspector.selected()
        set(value) {
            inspector.bind(value)
            syncCameraMode()
        }

    init {
        SpatialCameraController.openMenu()
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!isLiveItemAuraPreview() && AetherOSSettings.showBackground.value && AetherOSSettings.backgroundBlur.value) extractBlurredBackground(ctx)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        FrameClock.tick()
        Resolution.push(ctx)
        val mx = Resolution.getMouseX(mouseX.toDouble())
        val my = Resolution.getMouseY(mouseY.toDouble())
        cursorX.to((mx / Resolution.width - .5f) * 2f)
        cursorY.to((my / Resolution.height - .5f) * 2f)
        val motion = AetherOSSettings.animations.value && !AetherOSSettings.reducedMotion.value
        val intro = if (motion) opening.to(1f).coerceIn(0f, 1f) else 1f
        renderEdgeAtmosphere(ctx, intro)
        if (scene == Scene.HUB) {
            if (searchQuery.isBlank()) renderHub(ctx, mx, my, intro) else renderSearchResults(ctx, mx, my, intro)
            renderHubControls(ctx, mx, my, intro)
        } else {
            renderCategory(ctx, mx, my, intro)
        }
        renderShockwaves(ctx, intro)
        Resolution.pop(ctx)
    }

    private fun renderEdgeAtmosphere(ctx: GuiGraphicsExtractor, alpha: Float) {
        if (isLiveItemAuraPreview()) return
        if (!AetherOSSettings.showBackground.value || AetherOSSettings.backgroundStyle.value == 3) return
        val opacity = (AetherOSSettings.backgroundOpacity.value * alpha).coerceIn(0f, 1f)
        val background = AetherOSSettings.currentPalette.background
        when (AetherOSSettings.backgroundStyle.value) {
            1 -> ctx.fill(0, 0, Resolution.width.toInt(), Resolution.height.toInt(), AetherOSSettings.background((150 * opacity).toInt()).rgb)
            2 -> {
                ctx.fill(0, 0, Resolution.width.toInt(), Resolution.height.toInt(), AetherOSSettings.background((92 * opacity).toInt()).rgb)
                val grid = AetherOSSettings.currentAccent.alpha((28 * opacity).toInt())
                var x = 0f
                while (x <= Resolution.width) {
                    ctx.drawLine(x, 0f, x, Resolution.height, grid, .6f)
                    x += 34f
                }
                var y = 0f
                while (y <= Resolution.height) {
                    ctx.drawLine(0f, y, Resolution.width, y, grid, .6f)
                    y += 34f
                }
            }
            else -> {
                ctx.drawHorizontalGradient(0f, 0f, Resolution.width * .5f, Resolution.height, Color(background.red, background.green, background.blue, (82 * opacity).toInt()), Color(background.red, background.green, background.blue, (28 * opacity).toInt()))
                ctx.drawHorizontalGradient(Resolution.width * .5f, 0f, Resolution.width * .5f, Resolution.height, Color(background.red, background.green, background.blue, (28 * opacity).toInt()), Color(background.red, background.green, background.blue, (118 * opacity).toInt()))
            }
        }
    }

    private fun renderHub(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, alpha: Float) {
        categoryHits.clear()
        val projected = SpatialCategory.entries.mapIndexed { index, item ->
            Triple(item, WorldSpaceRenderHandler.hubCard(item, index) ?: fallbackHubCard(index), index)
        }
        val center = WorldSpaceRenderHandler.hubCenter(.1)
        val elapsedMs = (FrameClock.now - openedAt) / 1_000_000L
        val motion = AetherOSSettings.animations.value && !AetherOSSettings.reducedMotion.value
        val originX = center?.x ?: Resolution.width * .5f
        val originY = center?.y ?: Resolution.height * .5f

        projected.forEach { (item, worldTarget, index) ->
            val target = biosHubTarget(index, worldTarget)
            val unfold = if (motion) {
                burstMotion.getValue(item)
                    .to(if (elapsedMs >= 75L + index * 62L) 1f else 0f)
                    .coerceIn(0f, 1.16f)
            } else 1f
            val visible = unfold.coerceIn(0f, 1f)
            val depth = .72f + index * .09f
            val parallaxX = -cursorX.value * 10f * depth
            val parallaxY = -cursorY.value * 7f * depth
            val targetX = target.centerX + parallaxX
            val flightTime = FrameClock.now / 1_000_000_000.0
            val flightX = sin(flightTime * .72 + index * 1.83).toFloat() * (3.5f + depth * 2f)
            val flightY = sin(flightTime * 1.08 + index * 1.31).toFloat() * (5f + depth * 2.5f)
            val targetY = target.centerY + parallaxY
            val cardX = originX + (targetX + flightX - originX) * unfold
            val cardY = originY + (targetY + flightY - originY) * unfold
            val initialScale = .12f + visible * .88f
            val base = Rect(
                cardX - target.width * initialScale * .5f,
                cardY - target.height * initialScale * .5f,
                target.width * initialScale,
                target.height * initialScale,
            )
            val hovered = visible > .82f && base.contains(mouseX.toFloat(), mouseY.toFloat())
            val hover = cardMotion.getValue(item).to(if (hovered) 1f else 0f).coerceIn(0f, 1f)
            val card = base.scaled(1f + hover * .075f)
            categoryHits += CategoryHit(item, card)
            val accent = Color(item.accent, true)
            val cardAlpha = alpha * visible
            if (center != null && visible > .08f) {
                ctx.drawLine(
                    originX,
                    originY,
                    card.centerX,
                    card.centerY,
                    Color(accent.red, accent.green, accent.blue, (24f * cardAlpha).toInt()),
                    .8f,
                )
            }
            ctx.surface(
                card,
                8f,
                AetherOSSettings.surface((232 * cardAlpha).toInt(), elevated = hovered),
                if (hovered) accent.alpha((125 * cardAlpha).toInt()) else AetherOSSettings.border((55 * cardAlpha).toInt()),
            )
            ctx.rounded(Rect(card.x + 1f, card.y + 1f, card.width - 2f, 2f), 1f, accent.alpha((185 * cardAlpha).toInt()))
            val icon = Rect(card.x + 10f, card.centerY - 13f, 26f, 26f)
            ctx.rounded(icon, 5f, accent.alpha(((28 + hover * 42) * cardAlpha).toInt()))
            FontSystem.centered(ctx, item.mark, icon.centerX, icon.y + 7f, accent.alpha((255 * cardAlpha).toInt()), .62f, true)
            FontSystem.draw(ctx, item.title.uppercase(), card.x + 45f, card.y + 10f, Color(252, 252, 255, (255 * cardAlpha).toInt()), .72f, true)
            FontSystem.draw(ctx, item.eyebrow, card.x + 45f, card.y + 31f, Color(161, 170, 186, (242 * cardAlpha).toInt()), .39f, true)
            FontSystem.draw(ctx, "SYS-${(index + 1).toString().padStart(2, '0')}", card.right - 45f, card.y + 8f, accent.alpha((210 * cardAlpha).toInt()), .38f, true)
            FontSystem.draw(ctx, modules(item).size.toString().padStart(2, '0'), card.right - 19f, card.bottom - 15f, Color(190, 198, 211, (220 * cardAlpha).toInt()), .4f, true)
        }

        val titleAlpha = alpha * if (motion) burstMotion.values.maxOf { it.value }.coerceIn(0f, 1f) else 1f
        val label = Rect(Resolution.width * .5f - 126f, 19f, 252f, 29f)
        ctx.pill(label, Color(6, 9, 15, (184 * titleAlpha).toInt()))
        ctx.rounded(Rect(label.x + 1f, label.bottom - 2f, label.width - 2f, 1f), .5f, Color(116, 132, 255, (210 * titleAlpha).toInt()))
        FontSystem.centered(ctx, "EA BIOS  //  SELECT SYSTEM", label.centerX, label.y + 8f, Color(240, 244, 252, (248 * titleAlpha).toInt()), .62f, true)
        hint(ctx, Rect(Resolution.width * .5f - 142f, Resolution.height - 39f, 284f, 25f), "LMB SELECT  ·  HOLD RMB ORBIT  ·  ESC CLOSE", alpha)
    }

    private fun fallbackHubCard(index: Int): Rect {
        val angle = -PI / 2.0 + index * PI * 2.0 / SpatialCategory.entries.size
        val centerX = Resolution.width * .5f + cos(angle).toFloat() * 235f
        val centerY = Resolution.height * .5f + sin(angle).toFloat() * 132f
        return Rect(centerX - 75f, centerY - 32f, 150f, 64f)
    }

    private fun biosHubTarget(index: Int, world: Rect): Rect {
        val rightSide = index == 0 || index == 2 || index == 4
        val row = when (index) {
            0, 1 -> 0
            2, 3 -> 1
            else -> 2
        }
        val yFractions = floatArrayOf(.29f, .52f, .75f)
        val centerX = Resolution.width * if (rightSide) .80f else .20f
        val worldDriftX = (world.centerX - Resolution.width * .5f) * .045f
        val worldDriftY = (world.centerY - Resolution.height * .5f) * .075f
        val width = 166f * (world.width / 150f).coerceIn(.92f, 1.08f)
        val height = 58f * (world.height / 64f).coerceIn(.92f, 1.08f)
        val centerY = Resolution.height * yFractions[row] + worldDriftY
        return Rect(centerX + worldDriftX - width * .5f, centerY - height * .5f, width, height)
    }

    private fun renderHubControls(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, alpha: Float) {
        val accent = AetherOSSettings.currentAccent
        searchBox = Rect(Resolution.width * .5f - 118f, 56f, 236f, 27f)
        settingsButton = Rect(Resolution.width - 166f, 19f, 148f, 29f)

        val searchHovered = searchBox.contains(mouseX.toFloat(), mouseY.toFloat())
        ctx.surface(
            searchBox,
            7f,
            AetherOSSettings.surface(((if (searchHovered || searchHandler.listening) 238 else 218) * alpha).toInt(), elevated = searchHovered),
            if (searchHandler.listening) accent.alpha((150 * alpha).toInt()) else AetherOSSettings.border((35 * alpha).toInt()),
        )
        ctx.rounded(Rect(searchBox.x + 10f, searchBox.centerY - 4f, 7f, 7f), 3.5f, accent.alpha((210 * alpha).toInt()))
        ctx.drawLine(searchBox.x + 16f, searchBox.centerY + 2f, searchBox.x + 20f, searchBox.centerY + 6f, accent.alpha((210 * alpha).toInt()), 1f)
        searchHandler.x = searchBox.x + 24f
        searchHandler.y = searchBox.y + 2f
        searchHandler.width = searchBox.width - 78f
        searchHandler.height = searchBox.height - 4f
        if (searchQuery.isBlank() && !searchHandler.listening) {
            FontSystem.draw(ctx, "SEARCH MODULES", searchBox.x + 27f, searchBox.y + 8f, Color(171, 179, 193, (225 * alpha).toInt()), .5f, true)
        } else {
            searchHandler.draw(ctx, mouseX.toFloat(), mouseY.toFloat())
        }
        FontSystem.draw(ctx, "CTRL+F", searchBox.right - 48f, searchBox.y + 8f, accent.alpha((220 * alpha).toInt()), .43f, true)

        val settingsHovered = settingsButton.contains(mouseX.toFloat(), mouseY.toFloat())
        ctx.surface(
            settingsButton,
            7f,
            AetherOSSettings.surface(((if (settingsHovered) 238 else 218) * alpha).toInt(), elevated = settingsHovered),
            if (settingsHovered) accent.alpha((120 * alpha).toInt()) else AetherOSSettings.border((35 * alpha).toInt()),
        )
        FontSystem.draw(ctx, "GUI SETTINGS", settingsButton.x + 13f, settingsButton.y + 9f, Color(235, 239, 247, (250 * alpha).toInt()), .53f, true)
        FontSystem.draw(ctx, "CFG", settingsButton.right - 30f, settingsButton.y + 9f, accent.alpha((220 * alpha).toInt()), .43f, true)
    }

    private fun renderSearchResults(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, alpha: Float) {
        categoryHits.clear()
        moduleHits.clear()
        val accent = AetherOSSettings.currentAccent
        val query = searchQuery.trim()
        val results = allModules.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description?.contains(query, ignoreCase = true) == true ||
                ownerCategory(it).title.contains(query, ignoreCase = true)
        }
        val panel = Rect(Resolution.width - 392f, 96f, 374f, Resolution.height - 116f)
        ctx.surface(panel, 10f, AetherOSSettings.surface((238 * alpha).toInt()), AetherOSSettings.border((42 * alpha).toInt()))
        ctx.rounded(Rect(panel.x + 1f, panel.y + 1f, panel.width - 2f, 2f), 1f, accent.alpha((205 * alpha).toInt()))
        FontSystem.draw(ctx, "SEARCH INDEX", panel.x + 16f, panel.y + 14f, Color(247, 249, 253, (255 * alpha).toInt()), .75f, true)
        FontSystem.draw(ctx, "${results.size.toString().padStart(2, '0')} MATCHES", panel.right - 78f, panel.y + 15f, accent.alpha((230 * alpha).toInt()), .45f, true)

        val viewport = Rect(panel.x + 10f, panel.y + 43f, panel.width - 20f, panel.height - 75f)
        val stride = 55f
        val total = results.size * stride
        searchMaxScroll = (total - viewport.height).coerceAtLeast(0f)
        searchScroll = searchScroll.coerceIn(0f, searchMaxScroll)
        ctx.enableScissor(viewport.x.toInt(), viewport.y.toInt(), viewport.right.toInt(), viewport.bottom.toInt())
        results.forEachIndexed { index, feature ->
            val row = Rect(viewport.x, viewport.y + index * stride - searchScroll, viewport.width, 48f)
            if (row.bottom < viewport.y || row.y > viewport.bottom) return@forEachIndexed
            val hovered = row.contains(mouseX.toFloat(), mouseY.toFloat())
            val hover = rowMotion.getOrPut(feature) { Spring() }.to(if (hovered) 1f else 0f).coerceIn(0f, 1f)
            val state = stateMotion.getOrPut(feature) { Spring(if (feature.enabled) 1f else 0f) }
                .to(if (feature.enabled) 1f else 0f).coerceIn(0f, 1f)
            val category = ownerCategory(feature)
            val categoryAccent = Color(category.accent, true)
            val visual = row.moved(dx = -hover * 4f).copy(width = row.width + hover * 4f)
            moduleHits += ModuleHit(feature, visual)
            ctx.rounded(visual, 7f, Color(255, 255, 255, (9 + hover * 16).toInt()))
            ctx.rounded(Rect(visual.x + 9f, visual.centerY - 12f, 24f, 24f), 5f, categoryAccent.alpha((28 + state * 40).toInt()))
            FontSystem.centered(ctx, category.mark, visual.x + 21f, visual.centerY - 7f, categoryAccent, .5f, true)
            FontSystem.draw(ctx, FontSystem.ellipsize(feature.name, visual.width - 135f, .68f), visual.x + 43f, visual.y + 8f, Color(245, 247, 251), .68f, true)
            FontSystem.draw(ctx, category.title.uppercase(), visual.x + 43f, visual.y + 29f, categoryAccent.alpha(215), .4f, true)
            FontSystem.draw(ctx, if (feature.enabled) "ON" else "OFF", visual.right - 31f, visual.y + 18f, if (feature.enabled) accent else Color(139, 146, 160), .48f, true)
        }
        ctx.disableScissor()
        if (results.isEmpty()) {
            FontSystem.centered(ctx, "NO MODULES MATCH \"$query\"", viewport.centerX, viewport.centerY - 5f, Color(154, 162, 177), .62f, true)
        }
        if (searchMaxScroll > 0f) {
            val thumb = (viewport.height * viewport.height / total).coerceAtLeast(28f)
            ctx.pill(Rect(viewport.right - 3f, viewport.y + searchScroll / searchMaxScroll * (viewport.height - thumb), 3f, thumb), accent.alpha(180))
        }
        hint(ctx, Rect(panel.centerX - 130f, panel.bottom - 25f, 260f, 18f), "LMB TOGGLE  //  RMB INSPECT", alpha)
    }

    private fun ownerCategory(feature: Feature): SpatialCategory =
        SpatialCategory.entries.firstOrNull { feature in modules(it) } ?: SpatialCategory.GENERAL

    private fun openGuiSettings() {
        searchHandler.listening = false
        category = SpatialCategory.GENERAL
        scene = Scene.CATEGORY
        scroll = 0f
        maxScroll = 0f
        panelReveal.snap(0f)
        selectedFeature = AetherOSSettings
        Style.playClickSound(1.08f)
    }

    private fun renderCategory(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, alpha: Float) {
        val current = category ?: return
        val reveal = if (AetherOSSettings.animations.value && !AetherOSSettings.reducedMotion.value) panelReveal.to(1f).coerceIn(0f, 1f) else 1f
        val panelBase = WorldSpaceRenderHandler.controlPanel()
        val rightEdgeX = Resolution.width - panelBase.width - 18f
        var panel = panelBase
            .copy(x = rightEdgeX, y = panelBase.y.coerceIn(16f, Resolution.height - panelBase.height - 16f))
            .moved(dx = (1f - reveal) * 58f)
            .scaled(.9f + reveal * .1f)
        if (isLiveItemAuraPreview()) {
            // Keep the lower-right real hand/aura pass unobscured.
            panel = panel.copy(x = 92f.coerceAtMost(Resolution.width - panel.width - 18f))
        }
        val sidebar = projectedSidebar(reveal)
        renderSidebar(ctx, sidebar, current, mouseX, mouseY, alpha * reveal)
        val swap = categorySwap.to(1f).coerceIn(0f, 1f)
        val contentPanel = panel.moved(dx = (1f - swap) * 14f)
        val contentAlpha = alpha * reveal * (.35f + swap * .65f)
        if (selectedFeature != null) inspector.render(ctx, contentPanel, mouseX, mouseY, contentAlpha, Color(current.accent, true))
        else renderModulePanel(ctx, contentPanel, current, mouseX, mouseY, contentAlpha)
        if (current == SpatialCategory.VISUAL && !isLiveItemAuraPreview()) {
            val p = WorldSpaceRenderHandler.anchor(.45, -1.04, -.08, 1.7)?.let(WorldSpaceRenderHandler::project)
            if (p != null) FontSystem.centered(ctx, "LIVE CHARACTER PREVIEW", p.x, p.y, Color(207, 210, 220, (225 * alpha).toInt()), .58f, true)
        }
    }

    private fun projectedSidebar(reveal: Float): Rect {
        val projected = WorldSpaceRenderHandler.anchor(2.12, .05, -.1, 2.1)?.let(WorldSpaceRenderHandler::project)
        if (projected == null) return Rect(16f, Resolution.height * .5f - 151f, 66f, 302f)
        val scale = projected.scale.coerceIn(.9f, 1.08f)
        val width = 66f * scale
        val height = 302f * scale
        val x = (projected.x - width * .5f - (1f - reveal) * 35f).coerceIn(12f, 24f)
        val y = (projected.y - height * .5f).coerceIn(12f, Resolution.height - height - 12f)
        return Rect(x, y, width, height)
    }

    private fun renderSidebar(ctx: GuiGraphicsExtractor, area: Rect, selected: SpatialCategory, mouseX: Int, mouseY: Int, alpha: Float) {
        ctx.surface(area, 20f, AetherOSSettings.surface((230 * alpha).toInt()), AetherOSSettings.border((40 * alpha).toInt()))
        categoryHits.clear()
        val gap = (area.height - 18f) / SpatialCategory.entries.size
        SpatialCategory.entries.forEachIndexed { i, item ->
            val button = Rect(area.x + 9f, area.y + 9f + i * gap, area.width - 18f, gap - 8f)
            val hover = button.contains(mouseX.toFloat(), mouseY.toFloat())
            val active = item == selected
            val accent = Color(item.accent, true)
            if (hover || active) ctx.rounded(button, 13f, if (active) accent.alpha((44 * alpha).toInt()) else Color(255,255,255,(18*alpha).toInt()))
            if (active) ctx.pill(Rect(button.x - 1f, button.y + 11f, 4f, button.height - 22f), accent.alpha((255 * alpha).toInt()))
            FontSystem.centered(ctx, item.mark, button.centerX, button.centerY - 7f, if (active) accent.alpha((255 * alpha).toInt()) else Color(191,195,207,(245*alpha).toInt()), .84f, true)
            categoryHits += CategoryHit(item, button)
        }
    }

    private fun renderModulePanel(ctx: GuiGraphicsExtractor, panel: Rect, current: SpatialCategory, mouseX: Int, mouseY: Int, alpha: Float) {
        val accent = Color(current.accent, true)
        ctx.surface(panel, 20f, AetherOSSettings.surface((238 * alpha).toInt()), AetherOSSettings.border((42 * alpha).toInt()))
        FontSystem.draw(ctx, current.title, panel.x + 21f, panel.y + 17f, Color(253, 253, 255, (255 * alpha).toInt()), 1.14f, true)
        val list = modules(current)
        FontSystem.draw(ctx, "${list.count { it.enabled }} ACTIVE  /  ${list.size} MODULES", panel.x + 21f, panel.y + 48f, accent.alpha((238 * alpha).toInt()), .57f, true)
        val viewport = Rect(panel.x + 12f, panel.y + 76f, panel.width - 24f, panel.height - 108f)
        val rowStride = 64f
        val total = list.size * rowStride
        maxScroll = (total - viewport.height).coerceAtLeast(0f)
        scroll = scroll.coerceIn(0f, maxScroll)
        moduleHits.clear()
        ctx.enableScissor(viewport.x.toInt(), viewport.y.toInt(), viewport.right.toInt(), viewport.bottom.toInt())
        list.forEachIndexed { index, feature ->
            val row = Rect(viewport.x, viewport.y + index * rowStride - scroll, viewport.width, 56f)
            if (row.bottom >= viewport.y && row.y <= viewport.bottom) {
                val hover = rowMotion.getOrPut(feature) { Spring() }.to(if (row.contains(mouseX.toFloat(), mouseY.toFloat())) 1f else 0f).coerceIn(0f, 1f)
                val state = stateMotion.getOrPut(feature) { Spring(if (feature.enabled) 1f else 0f) }.to(if (feature.enabled) 1f else 0f).coerceIn(0f, 1f)
                val visual = row.moved(dx = hover * -5f).copy(width = row.width + hover * 5f)
                moduleHits += ModuleHit(feature, visual)
                ctx.rounded(visual, 14f, Color(255, 255, 255, (10 + hover * 17).toInt()))
                val icon = Rect(visual.x + 11f, visual.centerY - 17f, 34f, 34f)
                ctx.pill(icon, accent.alpha((23 + state * 41).toInt()))
                FontSystem.centered(ctx, feature.name.take(1).uppercase(), icon.centerX, icon.y + 10f, if (feature.enabled) accent else Color(166,171,185), .76f, true)
                FontSystem.draw(ctx, FontSystem.ellipsize(feature.name, visual.width - 125f, .82f), visual.x + 56f, visual.y + 10f, Color(249, 249, 252), .82f, true)
                FontSystem.draw(ctx, if (feature.enabled) "ACTIVE" else "INACTIVE", visual.x + 56f, visual.y + 35f, if (feature.enabled) accent else Color(149,154,168), .52f, true)
                val toggle = Rect(visual.right - 53f, visual.centerY - 10f, 41f, 20f)
                ctx.pill(toggle, mix(Color(255,255,255,24), accent.alpha(220), state))
                ctx.pill(Rect(toggle.x + 3f + state * 21f, toggle.y + 3f, 14f, 14f), Color(249,250,252))
            }
        }
        ctx.disableScissor()
        if (list.isEmpty()) FontSystem.centered(ctx, "No modules assigned", viewport.centerX, viewport.centerY, Color(170,175,188), .78f)
        if (maxScroll > 0f) {
            val h = (viewport.height * viewport.height / total).coerceAtLeast(30f)
            ctx.pill(Rect(viewport.right - 3f, viewport.y + scroll / maxScroll * (viewport.height - h), 3f, h), accent.alpha(185))
        }
        hint(ctx, Rect(panel.centerX - 135f, panel.bottom - 29f, 270f, 21f), "LMB TOGGLE  ·  RMB INSPECT / BACK", alpha)
    }

    private fun hint(ctx: GuiGraphicsExtractor, rect: Rect, value: String, alpha: Float) {
        ctx.pill(rect, Color(6, 9, 15, (214 * alpha).toInt()))
        FontSystem.centered(ctx, value, rect.centerX, rect.y + 6f, Color(196, 200, 212, (242 * alpha).toInt()), .52f, true)
    }

    private fun renderShockwaves(ctx: GuiGraphicsExtractor, alpha: Float) {
        val lifetime = 720_000_000L
        shockwaves.removeAll { FrameClock.now - it.born >= lifetime }
        shockwaves.forEach { wave ->
            val age = ((FrameClock.now - wave.born).toDouble() / lifetime).toFloat().coerceIn(0f, 1f)
            val eased = 1f - (1f - age) * (1f - age)
            val radius = 12f + eased * 132f
            val opacity = ((1f - age) * (1f - age) * 145f * alpha).toInt()
            repeat(56) { index ->
                val angle = index * PI * 2.0 / 56.0
                val x = wave.x + cos(angle).toFloat() * radius
                val y = wave.y + sin(angle).toFloat() * radius
                ctx.rounded(Rect(x - 1.25f, y - 1.25f, 2.5f, 2.5f), 1.25f, wave.color.alpha(opacity))
            }
        }
    }

    private fun emitShockwave(rect: Rect, color: Color) {
        if (!AetherOSSettings.clickRipples.value || !AetherOSSettings.animations.value || AetherOSSettings.reducedMotion.value) return
        shockwaves += Shockwave(rect.centerX, rect.centerY, color, FrameClock.now)
    }

    override fun mouseClicked(event: MouseButtonEvent, bl: Boolean): Boolean {
        val x = Resolution.getMouseX(event.x); val y = Resolution.getMouseY(event.y); val button = event.button()
        if (scene == Scene.HUB) {
            if (settingsButton.contains(x.toFloat(), y.toFloat()) && button == 0) {
                openGuiSettings()
                return true
            }
            if (searchBox.contains(x.toFloat(), y.toFloat()) && button == 0) {
                searchHandler.listening = true
                searchHandler.mouseClicked(x.toFloat(), y.toFloat(), event)
                return true
            }
            if (searchQuery.isNotBlank()) {
                moduleHits.asReversed().firstOrNull { it.rect.contains(x.toFloat(), y.toFloat()) }?.let {
                    if (button == 0) {
                        it.feature.toggle()
                        emitShockwave(it.rect, AetherOSSettings.currentAccent)
                        Style.playClickSound(if (it.feature.enabled) 1.14f else .86f)
                    } else if (button == 1) {
                        val owner = ownerCategory(it.feature)
                        enter(owner)
                        selectedFeature = it.feature
                        searchHandler.listening = false
                    }
                    return true
                }
                return true
            }
            if (button == 0) categoryHits.asReversed().firstOrNull { it.rect.contains(x.toFloat(), y.toFloat()) }?.let {
                emitShockwave(it.rect, Color(it.category.accent, true))
                enter(it.category)
            }
            if (button == 1 && searchQuery.isBlank()) {
                orbitDragging = true
                SpatialCameraController.beginOrbit()
            }
            return true
        }
        if (selectedFeature != null) {
            val before = selectedFeature
            if (inspector.click(x, y, button)) {
                if (before != null && selectedFeature == null) syncCameraMode()
                return true
            }
        }
        if (button == 1) {
            moduleHits.asReversed().firstOrNull { it.rect.contains(x.toFloat(), y.toFloat()) }?.let { selectedFeature = it.feature; Style.playClickSound(1.08f); return true }
            if (selectedFeature != null) selectedFeature = null else backToHub()
            return true
        }
        categoryHits.firstOrNull { it.rect.contains(x.toFloat(), y.toFloat()) }?.let {
            emitShockwave(it.rect, Color(it.category.accent, true))
            switchCategory(it.category)
            return true
        }
        moduleHits.asReversed().firstOrNull { it.rect.contains(x.toFloat(), y.toFloat()) }?.let {
            it.feature.toggle()
            emitShockwave(it.rect, Color(category?.accent ?: SpatialCategory.GENERAL.accent, true))
            Style.playClickSound(if (it.feature.enabled) 1.14f else .86f)
            return true
        }
        return true
    }

    override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double): Boolean {
        if (scene == Scene.HUB && searchQuery.isNotBlank()) {
            searchScroll = (searchScroll - vertical.toFloat() * 34f).coerceIn(0f, searchMaxScroll)
            return true
        }
        if (selectedFeature != null && inspector.scroll(vertical)) return true
        if (scene == Scene.CATEGORY) { scroll = (scroll - vertical.toFloat() * 38f).coerceIn(0f, maxScroll); return true }
        return super.mouseScrolled(x, y, horizontal, vertical)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == 1) {
            orbitDragging = false
            SpatialCameraController.endOrbit()
        }
        searchHandler.mouseReleased()
        inspector.release(event.button())
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (scene == Scene.HUB && orbitDragging && event.button() == 1) {
            SpatialCameraController.orbitBy(deltaX, deltaY)
            return true
        }
        val x = Resolution.getMouseX(event.x)
        val y = Resolution.getMouseY(event.y)
        if (inspector.drag(x, y, event.button())) return true
        return super.mouseDragged(event, deltaX, deltaY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (inspector.hasModal() && inspector.key(event)) return true
        if (event.hasControlDown() && event.key == UKeyboard.KEY_F) {
            if (scene != Scene.HUB) {
                inspector.bind(null)
                category = null
                scene = Scene.HUB
                syncCameraMode()
            }
            searchHandler.listening = true
            return true
        }
        if (searchHandler.keyPressed(event)) return true
        if (event.input() == UKeyboard.KEY_ESCAPE && (searchHandler.listening || searchQuery.isNotBlank())) {
            searchHandler.listening = false
            searchQuery = ""
            searchScroll = 0f
            return true
        }
        if (inspector.key(event)) return true
        when (event.input()) {
            UKeyboard.KEY_ESCAPE -> { if (selectedFeature != null) selectedFeature = null else if (scene == Scene.CATEGORY) backToHub() else onClose(); return true }
            UKeyboard.KEY_BACKSPACE -> if (scene == Scene.CATEGORY) { backToHub(); return true }
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent) =
        inspector.char(event.codepoint.toChar()) || searchHandler.keyTyped(event) || super.charTyped(event)
    override fun openFeatureWindow(feature: Feature, preferredX: Float?, preferredY: Float?) { if (scene == Scene.HUB) enter(SpatialCategory.GENERAL); selectedFeature = feature }
    override fun isMouseOverConfigWindow(mouseX: Int, mouseY: Int) = inspector.contains(mouseX, mouseY)

    private fun enter(next: SpatialCategory) {
        category = next; scene = Scene.CATEGORY; scroll = 0f; maxScroll = 0f
        inspector.bind(null)
        moduleHits.clear(); categoryHits.clear()
        panelReveal.snap(0f)
        syncCameraMode()
        Style.playClickSound(1.07f)
    }

    /**
     * Category-to-category changes keep the shell mounted. Only its data changes,
     * so an old hit list or a second reveal animation can never overlap the new
     * rows for one frame.
     */
    private fun switchCategory(next: SpatialCategory) {
        if (next == category) return
        inspector.bind(null)
        category = next
        scroll = 0f
        maxScroll = 0f
        moduleHits.clear()
        categoryHits.clear()
        categorySwap.snap(0f)
        syncCameraMode()
        Style.playClickSound(1.03f)
    }

    private fun backToHub() {
        inspector.bind(null)
        scene = Scene.HUB
        category = null
        syncCameraMode()
        Style.playClickSound(.9f)
    }

    override fun onClose() {
        cleanup()
        super.onClose()
    }

    override fun removed() {
        cleanup()
        super.removed()
    }

    private fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        SpatialCameraController.forceRestore()
        ConfigManager.flushIfDue(true)
        ConfigManager.save()
    }

    private fun isLiveItemAuraPreview() = selectedFeature === ItemAura || selectedFeature === RotatingItem

    private fun syncCameraMode() {
        when {
            isLiveItemAuraPreview() -> SpatialCameraController.useLiveFirstPersonPreview()
            scene == Scene.HUB -> SpatialCameraController.openMenu()
            category == SpatialCategory.VISUAL -> SpatialCameraController.focusVisual()
            else -> SpatialCameraController.openMenu()
        }
    }

    private fun modules(category: SpatialCategory) = modulesCache.getValue(category)

    /** Read-only bridge used by the level renderer; UI ownership stays here. */
    fun isSpatialHub() = scene == Scene.HUB
    fun spatialCategory(): SpatialCategory? = category
    fun spatialSelectedFeatureName(): String? = selectedFeature?.name
    fun isLiveItemAuraPreviewActive() = isLiveItemAuraPreview()
}
