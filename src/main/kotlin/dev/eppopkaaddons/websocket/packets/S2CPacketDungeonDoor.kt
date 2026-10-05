package dev.eppopkaaddons.websocket.packets

import dev.eppopkaaddons.utils.dungeons.map.core.DoorTile
import dev.eppopkaaddons.utils.dungeons.map.core.DoorType
import dev.eppopkaaddons.utils.dungeons.map.core.Unknown
import dev.eppopkaaddons.utils.dungeons.map.handlers.DungeonScanner
import dev.eppopkaaddons.websocket.WebSocketPacket

class S2CPacketDungeonDoor(val x: Int, val z: Int, val col: Int, val row: Int, val doorType: DoorType): WebSocketPacket {
    override fun handle() {
        val idx = row * 11 + col
        if (DungeonScanner.dungeonList[idx] !is Unknown) return
        DungeonScanner.dungeonList[idx] = DoorTile(x, z, doorType)
    }
}