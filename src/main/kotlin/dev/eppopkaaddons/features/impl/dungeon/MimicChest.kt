package dev.eppopkaaddons.features.impl.dungeon

import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.event.impl.RenderWorldEvent
import dev.eppopkaaddons.event.impl.TickEvent
import dev.eppopkaaddons.event.impl.WorldChangeEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.utils.location.LocationUtils
import dev.eppopkaaddons.utils.render.world.Render3D.renderBoxBounds
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.status.ChunkStatus
import java.awt.Color

/** Colors trapped chests with a depth-tested overlay while inside a dungeon. */
object MimicChest : Feature(
    description = "Highlights trapped chests in dungeons. Red by default; color is customizable.",
    name = "Mimic Chest",
) {
    private val color by ColorSetting("Color", Color(255, 0, 0, 100))

    private val chests = mutableSetOf<BlockPos>()
    private var scanTicks = 0

    override fun init() {
        register<WorldChangeEvent> { reset() }

        register<TickEvent.End> {
            val level = mc.level
            val player = mc.player
            if (!LocationUtils.inDungeon || level == null || player == null) {
                reset()
                return@register
            }
            if (scanTicks > 0) {
                scanTicks--
                if (scanTicks > 0) return@register
            }
            scanTicks = 20
            chests.clear()

            // Examine block entities in loaded chunks, not every block in a cube.
            // Passing false prevents this feature from requesting/loading chunks.
            val centerX = player.blockPosition().x shr 4
            val centerZ = player.blockPosition().z shr 4
            val distance = mc.options.effectiveRenderDistance
            for (x in centerX - distance..centerX + distance) {
                for (z in centerZ - distance..centerZ + distance) {
                    val chunk = level.chunkSource.getChunk(x, z, ChunkStatus.FULL, false)
                        ?: continue
                    for ((pos, blockEntity) in chunk.blockEntities) {
                        if (blockEntity.blockState.block == Blocks.TRAPPED_CHEST) {
                            chests.add(pos.immutable())
                        }
                    }
                }
            }
        }

        register<RenderWorldEvent> {
            if (!LocationUtils.inDungeon) return@register
            val level = mc.level ?: return@register
            val tint = color.value
            if (tint.alpha == 0) return@register

            for (pos in chests) {
                if (!level.hasChunk(pos.x shr 4, pos.z shr 4)) continue
                val state = level.getBlockState(pos)
                // Stop drawing immediately when a chest is removed or replaced.
                if (state.block != Blocks.TRAPPED_CHEST) continue
                val shape = state.getShape(level, pos)
                if (shape.isEmpty) continue
                val bounds = shape.bounds().move(pos).inflate(0.002)
                event.ctx.renderBoxBounds(
                    aabb = bounds,
                    outlineColor = tint,
                    fillColor = tint,
                    outline = false,
                    fill = true,
                    phase = false,
                )
            }
        }
    }

    override fun onEnable() {
        reset()
        super.onEnable()
    }

    override fun onDisable() {
        super.onDisable()
        reset()
    }

    private fun reset() {
        chests.clear()
        scanTicks = 0
    }
}
