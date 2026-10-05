package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event
import dev.eppopkaaddons.utils.dungeons.DungeonPlayer
import dev.eppopkaaddons.utils.dungeons.enums.SecretType
import dev.eppopkaaddons.utils.dungeons.map.core.RoomState
import dev.eppopkaaddons.utils.dungeons.map.core.Tile
import dev.eppopkaaddons.utils.dungeons.map.core.UniqueRoom
import net.minecraft.core.BlockPos

abstract class DungeonEvent: Event(false) {
    abstract class RoomEvent(val room: UniqueRoom): DungeonEvent() {
        class onEnter(room: UniqueRoom): RoomEvent(room)
        class onExit(room: UniqueRoom): RoomEvent(room)

        class onStateChange(room: UniqueRoom, val oldState: RoomState, val newState: RoomState, val roomPlayers: List<DungeonPlayer>): RoomEvent(room)
    }

    class TileScannedEvent(val tile: Tile): DungeonEvent()

    class SecretEvent(val type: SecretType, val pos: BlockPos): DungeonEvent()

    class PlayerDeathEvent(val name: String, val reason: String): DungeonEvent()

    class Score(val oldScore: Int, val score: Int): DungeonEvent()
    object BossEnterEvent: DungeonEvent()
    object RunStatedEvent: DungeonEvent()
    object RunEndedEvent: DungeonEvent()
}