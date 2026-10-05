package dev.eppopkaaddons.websocket.packets

import dev.eppopkaaddons.event.EventBus
import dev.eppopkaaddons.event.impl.ChatMessageEvent
import dev.eppopkaaddons.utils.ChatUtils
import dev.eppopkaaddons.utils.ChatUtils.removeFormatting
import dev.eppopkaaddons.utils.PartyUtils
import dev.eppopkaaddons.utils.location.LocationUtils
import dev.eppopkaaddons.websocket.WebSocketPacket
import net.minecraft.network.chat.Component

class S2CPacketChat(val message: String): WebSocketPacket {
    override fun handle() {
        ChatUtils.chat("§b[WS]§r $message")
        if (! LocationUtils.inDungeon) return
        val text = message.removeFormatting()
        if (PartyUtils.members.none { it.equals(text.substringBefore(":"), ignoreCase = true) }) return
        EventBus.post(ChatMessageEvent(Component.literal("Party > $text")))
    }
}