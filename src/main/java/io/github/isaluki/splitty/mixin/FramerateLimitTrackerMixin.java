package io.github.isaluki.splitty.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import io.github.isaluki.splitty.Splitty;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	/**
	 * Vanilla drops to 30/10 FPS when it sees no keyboard/mouse input for a minute ("AFK"),
	 * which would hit the controller player. Skip that throttle while in a world.
	 */
	@Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
	private void splitty$noAfkThrottle(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
		if (Splitty.active() && this.minecraft.level != null && !this.minecraft.getWindow().isIconified()) {
			cir.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
		}
	}
}
