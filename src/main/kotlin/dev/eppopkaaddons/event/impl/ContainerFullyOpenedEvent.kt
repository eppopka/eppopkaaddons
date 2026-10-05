package dev.eppopkaaddons.event.impl

import dev.eppopkaaddons.event.Event
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

class ContainerFullyOpenedEvent(
    val title: Component,
    val windowId: Int,
    val slotCount: Int,
    val items: HashMap<Int, ItemStack>
): Event(false)