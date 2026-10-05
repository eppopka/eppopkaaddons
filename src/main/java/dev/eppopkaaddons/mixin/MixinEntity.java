package dev.eppopkaaddons.mixin;

import dev.eppopkaaddons.interfaces.IGlowingEntity;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.awt.Color;

@Mixin(Entity.class)
public abstract class MixinEntity implements IGlowingEntity {
    @Unique private Color eppopkaaddons$glowColor = Color.WHITE;
    @Unique private boolean eppopkaaddons$glowForced = false;

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void eppopkaaddons$teamColor(CallbackInfoReturnable<Integer> cir) {
        if (noammaddons$isGlowing()) cir.setReturnValue(noammaddons$glowColor().getRGB());
    }

    @Override public Color noammaddons$glowColor() { return eppopkaaddons$glowColor; }
    @Override public void noammaddons$glowColor(@NonNull Color color) { eppopkaaddons$glowColor = color; }
    @Override public boolean noammaddons$isGlowing() { return eppopkaaddons$glowForced; }
    @Override public void noammaddons$isGlowing(boolean value) { eppopkaaddons$glowForced = value; }
}
