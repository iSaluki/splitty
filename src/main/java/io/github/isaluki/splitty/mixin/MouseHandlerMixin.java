package io.github.isaluki.splitty.mixin;

import io.github.isaluki.splitty.Splitty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player 2's window is normally not focused, and vanilla refuses to "grab" the mouse for an
 * unfocused window. Without a grab, holding attack (mining) doesn't work. For the guest we track
 * the grabbed state without touching the real cursor, which belongs to player 1.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private boolean mouseGrabbed;

	@Shadow
	private boolean ignoreFirstMove;

	@Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
	private void splitty$virtualGrab(CallbackInfo ci) {
		if (!Splitty.isGuest()) {
			return;
		}
		ci.cancel();
		if (!this.mouseGrabbed) {
			this.mouseGrabbed = true;
			this.minecraft.gui.setScreen(null);
			this.minecraft.missTime = 10000;
			this.ignoreFirstMove = true;
		}
	}

	@Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
	private void splitty$virtualRelease(CallbackInfo ci) {
		if (Splitty.isGuest()) {
			ci.cancel();
			this.mouseGrabbed = false;
		}
	}
}
