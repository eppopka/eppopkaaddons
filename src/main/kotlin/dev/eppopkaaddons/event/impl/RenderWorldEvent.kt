package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event
import dev.eppopkaaddons.utils.render.world.RenderContext

class RenderWorldEvent(val ctx: RenderContext): Event(cancelable = false)