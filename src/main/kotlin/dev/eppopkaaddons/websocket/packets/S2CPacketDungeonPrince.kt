package dev.eppopkaaddons.websocket.packets

import dev.eppopkaaddons.utils.dungeons.map.handlers.ScoreCalculation
import dev.eppopkaaddons.websocket.WebSocketPacket

object S2CPacketDungeonPrince: WebSocketPacket {
    override fun handle() = ScoreCalculation::princeKilled.set(true)
}