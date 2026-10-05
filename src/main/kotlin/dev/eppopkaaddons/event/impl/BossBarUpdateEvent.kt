package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event
import net.minecraft.network.chat.Component

class BossBarUpdateEvent(val name: Component, val progress: Float): Event(false)