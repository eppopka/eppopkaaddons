package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event
import dev.eppopkaaddons.utils.ChatUtils.formattedText
import dev.eppopkaaddons.utils.ChatUtils.unformattedText
import net.minecraft.network.chat.Component

class ActionBarMessageEvent(val component: Component): Event(cancelable = true) {
    val formattedText by lazy { component.formattedText }
    val unformattedText by lazy { component.unformattedText }

    var message = formattedText
}