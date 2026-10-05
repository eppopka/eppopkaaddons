package dev.eppopkaaddons.mixin;

import dev.eppopkaaddons.event.EventBus;
import dev.eppopkaaddons.event.impl.CheckEntityGlowEvent;
import dev.eppopkaaddons.interfaces.IGlowingEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @ModifyExpressionValue(method = "shouldEntityAppearGlowing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isCurrentlyGlowing()Z"))
    private boolean eppopkaaddons$glow(boolean original, Entity entity) {
        var event = new CheckEntityGlowEvent(entity);
        if (EventBus.post(event)) return false;
        var glow = (IGlowingEntity) entity;
        glow.noammaddons$isGlowing(event.getShouldGlow());
        glow.noammaddons$glowColor(event.getColor());
        return original || glow.noammaddons$isGlowing();
    }
}
