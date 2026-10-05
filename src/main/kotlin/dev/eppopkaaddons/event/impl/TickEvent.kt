package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event

abstract class TickEvent: Event(false) {
    object Start: TickEvent()
    object End: TickEvent()
    object Server: TickEvent()
}