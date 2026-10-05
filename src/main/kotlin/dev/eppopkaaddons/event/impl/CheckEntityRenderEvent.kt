package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event
import net.minecraft.world.entity.Entity

class CheckEntityRenderEvent(val entity: Entity): Event(cancelable = true)