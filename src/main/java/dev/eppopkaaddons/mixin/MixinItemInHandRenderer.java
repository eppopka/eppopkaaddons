package dev.eppopkaaddons.mixin;

import dev.eppopkaaddons.features.impl.visual.ItemAura;
import dev.eppopkaaddons.features.impl.visual.RotatingItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    private void eppopkaaddons$capture(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack stack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        ItemAura.captureHand(poseStack, hand, stack);
    }

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void eppopkaaddons$rotate(PoseStack poseStack, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        RotatingItem.applyTransform(poseStack, arm == HumanoidArm.LEFT);
    }

    @Inject(method = "renderHandsWithItems", at = @At("TAIL"))
    private void eppopkaaddons$aura(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.player.LocalPlayer player, int light, CallbackInfo ci) {
        ItemAura.drawCaptured();
    }
}
