package dev.eppopkaaddons.websocket.packets

import dev.eppopkaaddons.utils.dungeons.map.handlers.ScoreCalculation
import dev.eppopkaaddons.websocket.WebSocketPacket

object S2CPacketDungeonMimic: WebSocketPacket {
    override fun handle() = ScoreCalculation::mimicKilled.set(true)
}