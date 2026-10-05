package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.EppopkaAddons
import dev.eppopkaaddons.event.Event

abstract class NoammDebugFlagEvent(val flag: String): Event(false) {
    class Add(flag: String): NoammDebugFlagEvent(flag)
    class Remove(flag: String): NoammDebugFlagEvent(flag)

    override fun cancel() {
        EppopkaAddons.debugFlags.remove(flag)
    }
}