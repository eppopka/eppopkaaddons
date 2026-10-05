package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event

abstract class WebSocketEvent: Event(cancelable = false) {
    class Payload(val message: String): WebSocketEvent()
    object Connect: WebSocketEvent()
    object Disconnect: WebSocketEvent()
}