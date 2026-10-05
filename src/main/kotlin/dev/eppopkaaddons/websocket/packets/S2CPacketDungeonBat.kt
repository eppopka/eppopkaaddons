package dev.eppopkaaddons.websocket.packets

import dev.eppopkaaddons.utils.dungeons.map.handlers.ScoreCalculation
import dev.eppopkaaddons.websocket.WebSocketPacket

object S2CPacketDungeonBat: WebSocketPacket {
    override fun handle() = ScoreCalculation::batKilled.set(true)
}