package io.github.isaluki.splitty.mixin;

import io.github.isaluki.splitty.Splitty;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	/** Only one window can have focus, so never pause just because the other player's window has it. */
	@Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
	private void splitty$neverPauseOnFocusLoss(CallbackInfo ci) {
		if (Splitty.active()) {
			ci.cancel();
		}
	}
}
