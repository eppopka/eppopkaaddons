package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event
import net.minecraft.world.entity.Entity

class EntityUnloadEvent(val entity: Entity): Event(false)