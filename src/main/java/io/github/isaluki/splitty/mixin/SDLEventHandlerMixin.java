package io.github.isaluki.splitty.mixin;

import com.mojang.blaze3d.platform.SDLEventHandler;
import io.github.isaluki.splitty.Splitty;
import io.github.isaluki.splitty.SplittyConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player 2 plays with a controller only. While they are in a world, drop real keyboard and mouse
 * events so a stray click or key press in their half of the screen can't move them.
 */
@Mixin(SDLEventHandler.class)
public class SDLEventHandlerMixin {
	@Inject(
		method = {
			"handleKeyEvent",
			"handleTextInputEvent",
			"handleTextEditingEvent",
			"handleMouseMotionEvent",
			"handleMouseButtonEvent",
			"handleMouseWheelEvent"
		},
		at = @At("HEAD"),
		cancellable = true
	)
	private void splitty$blockGuestKeyboardMouse(SDL_Event event, CallbackInfo ci) {
		if (Splitty.isGuest() && SplittyConfig.get().blockKeyboardMouseInWorld && Minecraft.getInstance().level != null) {
			ci.cancel();
		}
	}
}
