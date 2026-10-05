package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event

class MouseClickEvent(val button: Int, val action: Int, val modifiers: Int): Event(true)