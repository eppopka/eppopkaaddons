package dev.eppopkaaddons.websocket.packets

import dev.eppopkaaddons.utils.dungeons.map.handlers.DungeonScanner
import dev.eppopkaaddons.websocket.WebSocketPacket

class S2CPacketRoomSecrets(val room: String, val secrets: Int): WebSocketPacket {
    override fun handle() {
        DungeonScanner.uniqueRooms[room]?.let {
            if (it.foundSecrets < secrets) it.foundSecrets = secrets
        }
    }
}