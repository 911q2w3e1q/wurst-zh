package com.github.bunnyi116.bedrockminer.mixin;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        BedrockMiner.CONTEXT.setTicks(BedrockMiner.CONTEXT.getTicks() + 1);
    }
}