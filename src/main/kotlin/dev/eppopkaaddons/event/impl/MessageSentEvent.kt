package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event

class MessageSentEvent(var message: String): Event(cancelable = true)