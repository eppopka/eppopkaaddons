package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event
import net.minecraft.network.protocol.Packet

abstract class PacketEvent(val packet: Packet<*>): Event(cancelable = true) {
    class Sent(packet: Packet<*>): PacketEvent(packet)
    class Received(packet: Packet<*>): PacketEvent(packet)
}