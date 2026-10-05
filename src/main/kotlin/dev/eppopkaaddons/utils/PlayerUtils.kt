package dev.eppopkaaddons.utils

import dev.eppopkaaddons.features.Shortcuts
import dev.eppopkaaddons.mixin.IKeyMapping
import dev.eppopkaaddons.utils.ChatUtils.formattedText
import dev.eppopkaaddons.utils.ChatUtils.modMessage
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/** Player helpers used by the retained EppopkaAddons modules. */
object PlayerUtils : Shortcuts {
    fun rightClick() {
        val key = mc.options.keyUse
        key.isDown = true
        (key as IKeyMapping).clickCount += 1
        key.isDown = false
    }

    fun getSelectionBlock(): BlockPos? {
        val hit = mc.hitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        return (hit as BlockHitResult).blockPos
    }

    fun getHotbarSlot(index: Int): ItemStack? {
        if (!Inventory.isHotbarSlot(index)) return null
        return player.inventory.getItem(index)
    }

    fun findHotbarSlot(predicate: (ItemStack) -> Boolean): Int? = (0..8).firstOrNull { index ->
        val stack = getHotbarSlot(index) ?: return@firstOrNull false
        !stack.isEmpty && predicate(stack)
    }

    fun swapToSlot(slot: Int) {
        if (!Inventory.isHotbarSlot(slot) || player.inventory.selectedSlot == slot) return
        modMessage("swapped to hotbar Slot $slot (${player.inventory.getSlot(slot)?.get()?.hoverName?.formattedText}&r)")
        player.inventory.selectedSlot = slot
    }
}
