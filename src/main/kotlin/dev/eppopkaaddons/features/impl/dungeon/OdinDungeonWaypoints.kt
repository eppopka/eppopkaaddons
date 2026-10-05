package dev.eppopkaaddons.features.impl.dungeon

import dev.eppopkaaddons.commands.CommandBuilder
import dev.eppopkaaddons.config.PogObject
import dev.eppopkaaddons.config.types.ButtonSetting
import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.config.types.DropdownSetting
import dev.eppopkaaddons.config.types.KeybindSetting
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.TextInputSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.event.impl.DungeonEvent
import dev.eppopkaaddons.event.impl.KeyboardEvent
import dev.eppopkaaddons.event.impl.MouseClickEvent
import dev.eppopkaaddons.event.impl.RenderWorldEvent
import dev.eppopkaaddons.event.impl.WorldChangeEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.features.impl.dungeon.waypoints.OdinWaypointCodec
import dev.eppopkaaddons.features.impl.dungeon.waypoints.RouteSteps
import dev.eppopkaaddons.utils.ChatUtils
import dev.eppopkaaddons.utils.ColorUtils.withAlpha
import dev.eppopkaaddons.utils.MathUtils.add
import dev.eppopkaaddons.utils.NumbersUtils.toFixed
import dev.eppopkaaddons.utils.PlayerUtils
import dev.eppopkaaddons.utils.dungeons.map.utils.ScanUtils
import dev.eppopkaaddons.utils.location.LocationUtils
import dev.eppopkaaddons.utils.render.Render2D.drawCenteredString
import dev.eppopkaaddons.utils.render.Render2D.drawString
import dev.eppopkaaddons.utils.render.RenderHelper.width
import dev.eppopkaaddons.utils.render.world.Render3D.renderBoxBounds
import dev.eppopkaaddons.utils.render.world.Render3D.renderLine
import dev.eppopkaaddons.utils.render.world.Render3D.renderString
import com.mojang.brigadier.arguments.StringArgumentType
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMinecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.util.concurrent.*

/**
 * Ported from Odin (github.com/odtheking/Odin), original by Bonsai / odtheking.
 *
 * Waypoints are placed by right clicking a block while edit mode is on, and are
 * stored per room in room-local coordinates, so the same waypoint lands correctly
 * in every rotation of that room. Secret waypoints disappear once the secret is
 * collected and come back when the room is re-entered.
 *
 * Route packs are exchanged through the clipboard in Odin's own format, so
 * anything shared for Odin can be imported here with `/ea dwp import`.
 *
 * The mod already ships its own `DungeonWaypoints` (with `/ndw`); this one is a
 * separate, self-contained feature so both can coexist.
 */
object OdinDungeonWaypoints: Feature(
    description = "Odin: custom waypoints for dungeon rooms. Right click a block with edit mode on to place one, /ea dwp import to load a route.",
    name = "Odin: Dungeon Waypoints"
) {
    private val editKeybind by KeybindSetting("Toggle Edit Mode", UKeyboard.KEY_NONE)
        .section("Editing")
        .withDescription("Turns edit mode on and off. Works even outside the menu.")

    private val allowEdits by ToggleSetting("Allow Edits", false)
        .withDescription("While on, right clicking a block places or removes a waypoint instead of using the item.")

    private val showEditorHud by ToggleSetting("Editor HUD", true)
        .withDescription("Shows what the next waypoint will look like, or details of the one you are looking at.")

    private val titleScale by SliderSetting("Title Scale", 1f, 0.1f, 4f, 0.1f)
        .withDescription("Scale of waypoint titles rendered in the world.")

    private val titleColorMode by DropdownSetting("Title Color", 0, listOf("Waypoint Color", "White", "Custom"))
        .withDescription("Whether titles follow the colour of their waypoint or use a fixed one.")

    private val titleCustomColor by ColorSetting("Custom Title Color", Color.WHITE, false)
        .withDescription("Colour used for every title when Title Color is set to Custom.")
        .showIf { titleColorMode.value == 2 }

    private val titleCentered by ToggleSetting("Title In Block", false)
        .withDescription("On: the title sits in the middle of the block. Off: it floats above it.")

    private val titleOffsetX by SliderSetting("Title Offset X", 0f, - 3f, 3f, 0.05f)
        .withDescription("Moves the title relative to its waypoint.")

    private val titleOffsetY by SliderSetting("Title Offset Y", 0f, - 3f, 3f, 0.05f)
    private val titleOffsetZ by SliderSetting("Title Offset Z", 0f, - 3f, 3f, 0.05f)

    private val globalPhase by ToggleSetting("Global Through Walls", false)
        .withDescription("Forces every waypoint to render through walls, ignoring its own setting.")

    private val addLines by ToggleSetting("Add Lines", false)
        .section("Route Lines")
        .withDescription("Connects numbered waypoints in order, so a numbered route draws itself as a path. Titles may carry a note (\"1 item\") or several steps (\"2 / 4\").")

    private val lineColor by ColorSetting("Line Color", Color(255, 255, 255, 200), true)
        .withDescription("Colour of the route line.")
        .showIf { addLines.value }

    private val lineWidth by SliderSetting("Line Width", 2f, 1f, 6f, 0.5f)
        .showIf { addLines.value }

    private val lineRainbow by ToggleSetting("Rainbow Line", false)
        .withDescription("Cycles the line through the hue range instead of using a fixed colour.")
        .showIf { addLines.value }

    private val linePhase by ToggleSetting("Line Through Walls", true)
        .withDescription("Draws the route line through blocks.")
        .showIf { addLines.value }

    private val lineSkipCollected by ToggleSetting("Skip Collected", true)
        .withDescription("Leaves out waypoints whose secret is already taken, so the line always points at what is left.")
        .showIf { addLines.value }

    private val lineFollow by ToggleSetting("Follow Progress", true)
        .withDescription("Only draws the route from the step you are standing on onwards, so passed segments disappear.")
        .showIf { addLines.value }

    private val lineReach by SliderSetting("Step Radius", 4f, 1f, 15f, 0.5f)
        .withDescription("How close you must be to a waypoint for it to count as the step you are on.")
        .showIf { showRouteHud.value || (addLines.value && lineFollow.value) }

    private val lineAhead by SliderSetting("Segments Ahead", 1, 1, 20, 1)
        .withDescription("How many segments are drawn at once, counted from the step you are on.")
        .showIf { addLines.value && lineFollow.value }

    private val showRouteHud by ToggleSetting("Route Progress HUD", true)
        .section("Route Progress")
        .withDescription("Shows the current numbered route step and total, for example 3/12.")

    private val nextType by DropdownSetting("Waypoint Type", 1, OdinWaypointCodec.TYPE_NAMES)
        .section("Next Waypoint")
        .withDescription("Secret waypoints hide themselves once you collect the secret; the others always stay.")

    private val nextColor by ColorSetting("Color", Color(85, 255, 85), true)
        .withDescription("Colour of the next waypoint you place.")

    private val nextTitle by TextInputSetting("Title", "")
        .withDescription("Text drawn above the next waypoint. Start it with a step number to join the route: \"1\", \"1 item\", or \"2 / 4\" for a block used twice.")

    private val nextFilled by ToggleSetting("Filled", false)
        .withDescription("Fill the box instead of only drawing its outline.")

    private val nextDepth by ToggleSetting("Depth Check", false)
        .withDescription("On: hidden behind blocks. Off: visible through walls.")

    private val useBlockSize by ToggleSetting("Use Block Size", true)
        .withDescription("Match the box to the shape of the block you clicked.")

    private val sizeX by SliderSetting("Size X", 1.0f, 0.1f, 5f, 0.05f).showIf { ! useBlockSize.value }
    private val sizeY by SliderSetting("Size Y", 1.0f, 0.1f, 5f, 0.05f).showIf { ! useBlockSize.value }
    private val sizeZ by SliderSetting("Size Z", 1.0f, 0.1f, 5f, 0.05f).showIf { ! useBlockSize.value }

    private val importButton by ButtonSetting("Import From Clipboard") { importFromClipboard() }
        .section("Sharing")
        .withDescription("Loads a waypoint route copied from Odin (or from this mod) into the current profile.")

    private val exportButton by ButtonSetting("Export To Clipboard") { exportToClipboard() }
        .withDescription("Copies every saved waypoint to the clipboard, in Odin's format.")

    private val resetRoom by ButtonSetting("Reset Current Room") {
        val room = roomKey() ?: return@ButtonSetting ChatUtils.modMessage("&cYou must be in a dungeon room!")
        if (storage.get().remove(room) == null) return@ButtonSetting ChatUtils.modMessage("&cNo waypoints in this room.")
        storage.save()
        active.clear()
        ChatUtils.modMessage("&aCleared every waypoint in &e$room&a.")
    }.section("Management").withDescription("Deletes all waypoints of the room you are standing in.")

    private val resetAll by ButtonSetting("Reset All Rooms") {
        storage.set(mutableMapOf())
        active.clear()
        ChatUtils.modMessage("&aCleared every saved waypoint.")
    }.withDescription("Deletes every saved waypoint in every room.")

    /**
     * Room-local waypoint as it is written to disk.
     *
     * [box] holds the offsets of the rendered box relative to the block, matching
     * Odin's own `aabb` field so imported packs keep their exact shapes. It stays
     * null for waypoints that simply use the block they sit on.
     */
    data class Waypoint(
        val pos: BlockPos,
        val color: Color,
        val filled: Boolean,
        val depth: Boolean,
        val type: Int,
        val title: String? = null,
        val box: BoxData? = null
    ) {
        data class BoxData(
            val minX: Double = 0.0,
            val minY: Double = 0.0,
            val minZ: Double = 0.0,
            val maxX: Double = 1.0,
            val maxY: Double = 1.0,
            val maxZ: Double = 1.0
        )
    }

    /** A waypoint resolved into world coordinates for the room we are in. */
    private class Active(val worldPos: BlockPos, val data: Waypoint) {
        var collected = false

        val box: AABB = data.box?.let { b ->
            AABB(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ).move(worldPos)
        } ?: AABB(worldPos)

        /** Middle of the box, where the route line connects. */
        val center: Vec3 = Vec3(
            (box.minX + box.maxX) / 2.0,
            (box.minY + box.maxY) / 2.0,
            (box.minZ + box.maxZ) / 2.0
        )

        /** Title parsed as route steps; a block reused later carries several. */
        val steps: List<Int> = RouteSteps.parse(data.title)
    }

    private val storage = PogObject("odinDungeonWaypoints", mutableMapOf<String, MutableList<Waypoint>>())
    private val active = CopyOnWriteArrayList<Active>()
    private var routeCursor = 0

    private data class RouteEntry(val step: Int?, val waypoint: Active)

    override fun init() {
        hudElement("Odin Waypoint Editor", enabled = { showEditorHud.value }, shouldDraw = { allowEdits.value }) { ctx, e ->
            drawEditorHud(ctx, e)
        }
        hudElement("Odin Route Progress", enabled = { showRouteHud.value }, shouldDraw = { routeEntries().isNotEmpty() }) { ctx, e ->
            drawRouteProgressHud(ctx, e)
        }

        register<KeyboardEvent.KeyPressed> {
            if (UMinecraft.currentScreenObj != null || event.action != GLFW.GLFW_PRESS) return@register
            if (! editKeybind.matches(event.keyEvent.key, mouse = false)) return@register
            toggleEdits()
            event.isCanceled = true
        }

        // Placing has to eat the click, otherwise you also swing/use the held item.
        register<MouseClickEvent> {
            if (! allowEdits.value) return@register
            if (UMinecraft.currentScreenObj != null) return@register
            if (event.button != 1 || event.action != GLFW.GLFW_PRESS) return@register
            if (! LocationUtils.inDungeon) return@register

            if (handlePlacement()) event.isCanceled = true
        }

        register<DungeonEvent.RoomEvent.onEnter> { reload() }
        register<DungeonEvent.BossEnterEvent> { reload() }

        register<DungeonEvent.SecretEvent> {
            // Secret waypoints vanish as soon as the secret they mark is taken.
            val nearest = active.asSequence()
                .filter { it.data.type == TYPE_SECRET && ! it.collected }
                .minByOrNull { it.worldPos.distSqr(event.pos) }
                ?: return@register

            if (nearest.worldPos.distSqr(event.pos) <= 36) nearest.collected = true
        }

        register<RenderWorldEvent> {
            if (! LocationUtils.inDungeon) return@register
            val route = routeEntries()
            updateRouteProgress(route)

            for (wp in active) {
                if (wp.collected) continue
                val phase = globalPhase.value || ! wp.data.depth

                event.ctx.renderBoxBounds(
                    wp.box,
                    wp.data.color,
                    wp.data.color.withAlpha((wp.data.color.alpha * 0.4f).toInt()),
                    outline = true,
                    fill = wp.data.filled,
                    phase = phase
                )

                wp.data.title?.takeIf(String::isNotBlank)?.let { title ->
                    val baseY = if (titleCentered.value) wp.center.y else wp.box.maxY + 0.3
                    val at = Vec3(
                        wp.center.x + titleOffsetX.value,
                        baseY + titleOffsetY.value,
                        wp.center.z + titleOffsetZ.value
                    )
                    event.ctx.renderString(title, at, titleColor(wp.data.color), titleScale.value, phase)
                }
            }

            if (addLines.value) drawRouteLines(event.ctx, route)

            // Preview of the block you are aiming at while editing.
            if (! allowEdits.value) return@register
            val target = PlayerUtils.getSelectionBlock() ?: return@register
            if (active.any { it.worldPos == target }) return@register

            event.ctx.renderBoxBounds(
                previewBox(target),
                nextColor.value,
                nextColor.value.withAlpha(70),
                outline = true,
                fill = nextFilled.value,
                phase = true
            )
        }

        register<WorldChangeEvent> {
            active.clear()
            routeCursor = 0
        }
    }

    fun CommandBuilder.installCommandTree() {
        description("Odin waypoint routes: import, export or clear")
        runs { ChatUtils.modMessage("&bUsage: /ea dwp <import|export|clear|clearall|title>") }

        literal("import") {
            description("Imports a waypoint route from the clipboard")
            runs { importFromClipboard() }
        }

        literal("export") {
            description("Copies every saved waypoint to the clipboard")
            runs { exportToClipboard() }
        }

        literal("clear") {
            description("Deletes the waypoints of the current room")
            runs {
                val room = roomKey() ?: return@runs ChatUtils.modMessage("&cYou must be in a dungeon room!")
                if (storage.get().remove(room) == null) return@runs ChatUtils.modMessage("&cNo waypoints in this room.")
                storage.save()
                active.clear()
                ChatUtils.modMessage("&aCleared every waypoint in &e$room&a.")
            }
        }

        literal("clearall") {
            description("Deletes every saved waypoint")
            runs {
                storage.set(mutableMapOf())
                active.clear()
                ChatUtils.modMessage("&aCleared every saved waypoint.")
            }
        }

        literal("title") {
            description("Sets the title used for the next waypoint you place")
            runs { ChatUtils.modMessage("&bCurrent title: &f${nextTitle.value.ifBlank { "(none)" }}") }

            argument("text", StringArgumentType.greedyString()) {
                runs { ctx ->
                    nextTitle.value = StringArgumentType.getString(ctx, "text").takeUnless { it == "-" } ?: ""
                    ChatUtils.modMessage("&aNext waypoint title: &f${nextTitle.value.ifBlank { "(none)" }}")
                }
            }
        }
    }

    // —— clipboard sharing ——

    private fun importFromClipboard() {
        val clipboard = runCatching { mc.keyboardHandler.clipboard }.getOrNull()?.trim()
        if (clipboard.isNullOrEmpty()) return ChatUtils.modMessage("&cThe clipboard is empty.")

        val decoded = OdinWaypointCodec.decode(clipboard)
            ?: return ChatUtils.modMessage("&cCould not read a waypoint route from the clipboard. &7Is the data valid?")

        val merged = storage.get()
        var rooms = 0
        var added = 0

        for ((room, waypoints) in decoded) {
            val list = merged.getOrPut(room) { mutableListOf() }
            rooms ++

            for (wp in waypoints) {
                // Re-importing the same route must not stack duplicates on one block.
                list.removeIf { it.pos == wp.pos }
                list.add(wp)
                added ++
            }
        }

        storage.save()
        reload()
        ChatUtils.modMessage("&aImported &e$added &awaypoints across &e$rooms &arooms.")
    }

    private fun exportToClipboard() {
        val data = storage.get()
        if (data.isEmpty()) return ChatUtils.modMessage("&cThere is nothing to export.")

        val encoded = OdinWaypointCodec.encode(data)
            ?: return ChatUtils.modMessage("&cFailed to encode the waypoints.")

        mc.keyboardHandler.clipboard = encoded
        ChatUtils.modMessage("&aCopied &e${data.values.sumOf { it.size }} &awaypoints to the clipboard.")
    }

    // —— route lines ——

    /** Colour used for a title, per the Title Color setting. */
    private fun titleColor(waypointColor: Color) = when (titleColorMode.value) {
        1 -> Color.WHITE
        2 -> titleCustomColor.value
        else -> waypointColor
    }

    /**
     * Connects the waypoints of a numbered route in step order.
     *
     * A waypoint takes part when its title starts with a step number; the rest of
     * the title is free text ("1 item"), and a block used twice lists every step it
     * serves ("2 / 4"), so it appears at each of those places in the path.
     *
     * With Follow Progress on, only the segments from the step you are standing on
     * are drawn: walking the route makes the passed part disappear and keeps the
     * next hop in front of you.
     */
    private fun drawRouteLines(ctx: dev.eppopkaaddons.utils.render.world.RenderContext, entries: List<RouteEntry>) {
        val visibleEntries = entries
            .filter { ! (lineSkipCollected.value && it.waypoint.collected) }
        val route = visibleEntries
            .map(RouteEntry::waypoint)
        if (route.size < 2) return

        var from = visibleEntries.indexOf(entries.getOrNull(routeCursor)).coerceAtLeast(0).coerceAtMost(route.lastIndex)
        var segments = route.size - 1

        if (lineFollow.value) {
            from = from.coerceAtMost(route.size - 2)
            segments = lineAhead.value
        } else {
            from = 0
        }

        val phase = linePhase.value
        val width = lineWidth.value
        val last = (from + segments).coerceAtMost(route.size - 1)

        for (i in from until last) {
            val color = if (! lineRainbow.value) lineColor.value
            else {
                // Hue drifts along the path and with time, so the direction of travel
                // is readable at a glance.
                val hue = (((System.currentTimeMillis() % 4000L) / 4000f) + i / route.size.toFloat()) % 1f
                val c = Color(Color.HSBtoRGB(hue, 0.8f, 1f))
                Color(c.red, c.green, c.blue, lineColor.value.alpha)
            }

            ctx.renderLine(route[i].center, route[i + 1].center, color, width, phase)
        }
    }

    private fun toggleEdits() {
        allowEdits.value = ! allowEdits.value
        ChatUtils.modMessage("Waypoint editing ${if (allowEdits.value) "&aenabled" else "&cdisabled"}&f.")
    }

    /** Room identifier used as the storage key; boss rooms get one key per floor. */
    private fun roomKey(): String? {
        val floor = LocationUtils.dungeonFloorNumber ?: return null
        if (LocationUtils.inBoss) return "B$floor"
        return ScanUtils.currentRoom?.name
    }

    /** Corner and rotation needed to convert between world and room-local coords. */
    private fun roomTransform(): Pair<BlockPos, Int>? {
        if (LocationUtils.inBoss) return BlockPos.ZERO to 0
        val room = ScanUtils.currentRoom ?: return null
        return (room.clayPos ?: BlockPos.ZERO) to (360 - (room.rotation ?: 0))
    }

    /** Rebuilds [active] from what is saved for the room we just entered. */
    private fun reload() {
        active.clear()
        routeCursor = 0
        val key = roomKey() ?: return
        val (corner, rotation) = roomTransform() ?: return
        val saved = storage.get()[key] ?: return

        for (wp in saved) {
            val world = ScanUtils.getRealCoord(wp.pos, corner, rotation)
            active.add(Active(world, wp))
        }
    }

    /**
     * Route priority is explicit: an untitled opaque FF5555 waypoint is START,
     * followed by title prefixes 1, 2, 3... A multi-step title such as "2 / 4"
     * contributes both visits.
     */
    private fun routeEntries(): List<RouteEntry> {
        val start = active.firstOrNull { wp ->
            wp.data.title.isNullOrBlank() &&
                wp.data.color.red == 0xFF &&
                wp.data.color.green == 0x55 &&
                wp.data.color.blue == 0x55 &&
                wp.data.color.alpha == 0xFF
        }
        val numbered = active.asSequence()
            .filter { it.steps.isNotEmpty() }
            .flatMap { wp -> wp.steps.asSequence().map { step -> RouteEntry(step, wp) } }
            .sortedBy { it.step }
            .toList()
        return buildList {
            start?.let { add(RouteEntry(null, it)) }
            addAll(numbered)
        }
    }

    private fun updateRouteProgress(route: List<RouteEntry>) {
        if (route.isEmpty()) {
            routeCursor = 0
            return
        }
        routeCursor = routeCursor.coerceIn(0, route.lastIndex)
        val player = mc.player?.position() ?: return
        val reachSq = lineReach.value.toDouble() * lineReach.value.toDouble()
        val reached = route.indices.lastOrNull { route[it].waypoint.center.distanceToSqr(player) <= reachSq } ?: return
        // Touching START intentionally begins the route again. Other waypoints
        // only advance progress, preventing nearby old steps from moving it back.
        if (route[reached].step == null) routeCursor = reached
        else if (reached >= routeCursor) routeCursor = reached
    }

    private fun drawRouteProgressHud(ctx: GuiGraphicsExtractor, example: Boolean): Pair<Float, Float> {
        val route = routeEntries()
        val total = if (example) 12 else route.mapNotNull(RouteEntry::step).maxOrNull() ?: 0
        val current = if (example) "3" else route.getOrNull(routeCursor)?.step?.toString() ?: "START"
        val label = "$current/$total"
        val width = maxOf(104f, label.width() + 34f)
        ctx.drawCenteredString("&8[ &cDUNGEON ROUTE &8]", width / 2f, 0f)
        ctx.drawCenteredString("&f$label", width / 2f, 12f)
        return width to 23f
    }

    /** Right click while editing: toggles a waypoint on the block being looked at. */
    private fun handlePlacement(): Boolean {
        val key = roomKey() ?: return false
        val (corner, rotation) = roomTransform() ?: return false
        val target = PlayerUtils.getSelectionBlock() ?: return false
        val relative = ScanUtils.getRelativeCoord(target, corner, rotation)

        val list = storage.get().getOrPut(key) { mutableListOf() }

        if (list.removeIf { it.pos == relative }) {
            active.removeIf { it.worldPos == target }
            storage.save()
            ChatUtils.modMessage("&cRemoved waypoint at ${target.toShortString()}.")
            return true
        }

        val box = previewBox(target)
        val waypoint = Waypoint(
            pos = relative,
            color = nextColor.value,
            filled = nextFilled.value,
            depth = nextDepth.value,
            type = nextType.value,
            title = nextTitle.value.takeIf(String::isNotBlank),
            box = Waypoint.BoxData(
                box.minX - target.x, box.minY - target.y, box.minZ - target.z,
                box.maxX - target.x, box.maxY - target.y, box.maxZ - target.z
            )
        )

        list.add(waypoint)
        storage.save()
        active.add(Active(target, waypoint))
        ChatUtils.modMessage("&aAdded ${OdinWaypointCodec.TYPE_NAMES[nextType.value]} waypoint at ${target.toShortString()}.")
        return true
    }

    /**
     * Box the next waypoint would occupy: either the real shape of the block, or
     * a manually sized box centred on it.
     */
    private fun previewBox(pos: BlockPos): AABB {
        if (! useBlockSize.value) {
            return AABB(pos).inflate(
                (sizeX.value - 1.0) / 2.0,
                (sizeY.value - 1.0) / 2.0,
                (sizeZ.value - 1.0) / 2.0
            )
        }

        val level = mc.level ?: return AABB(pos)
        val shape = level.getBlockState(pos).getShape(level, pos)
        return if (shape.isEmpty) AABB(pos) else shape.bounds().move(pos)
    }

    private fun drawEditorHud(ctx: GuiGraphicsExtractor, example: Boolean): Pair<Float, Float> {
        val hovered = if (example) null
        else PlayerUtils.getSelectionBlock()?.let { target -> active.firstOrNull { it.worldPos == target } }

        val title = "&fEditing Waypoints &8| &f${if (hovered == null) "Placing" else "Viewing"}"
        val text = when {
            example -> "&fType: &dNormal&7, &2Filled&7, &cThrough Walls&7, &2Block Size"
            hovered != null -> describe(hovered.data)
            else -> describeNext()
        }

        val width = maxOf(title.width(), text.width()).toFloat()
        ctx.drawCenteredString(title, width / 2f, 0f)
        ctx.drawString(text, 0f, 10f, if (hovered != null) hovered.data.color else nextColor.value)
        return width to 19f
    }

    private fun describeNext() = buildString {
        append("&fType: &d${OdinWaypointCodec.TYPE_NAMES[nextType.value]}")
        nextTitle.value.takeIf(String::isNotBlank)?.let { append("&7, &fTitle: &a$it") }
        append("&7, ${if (nextFilled.value) "&2Filled" else "&3Outline"}")
        append("&7, ${if (nextDepth.value) "&2Depth Check" else "&cThrough Walls"}")
        append("&7, ")
        append(
            if (useBlockSize.value) "&2Block Size"
            else "&3Size: ${sizeX.value.toFixed(2)}x${sizeY.value.toFixed(2)}x${sizeZ.value.toFixed(2)}"
        )
    }

    private fun describe(wp: Waypoint) = buildString {
        append("&fType: &d${OdinWaypointCodec.TYPE_NAMES.getOrElse(wp.type) { "None" }}")
        wp.title?.takeIf(String::isNotBlank)?.let { append("&7, &fTitle: &a$it") }
        append("&7, ${if (wp.filled) "&2Filled" else "&3Outline"}")
        append("&7, ${if (wp.depth) "&2Depth Check" else "&cThrough Walls"}")

        val box = wp.box
        if (box != null) {
            val sx = (box.maxX - box.minX).toFloat()
            val sy = (box.maxY - box.minY).toFloat()
            val sz = (box.maxZ - box.minZ).toFloat()
            append("&7, &3Size: ${sx.toFixed(2)}x${sy.toFixed(2)}x${sz.toFixed(2)}")
        }
    }
}

private const val TYPE_SECRET = 2
