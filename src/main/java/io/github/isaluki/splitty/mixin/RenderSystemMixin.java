package io.github.isaluki.splitty.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.isaluki.splitty.Splitty;
import io.github.isaluki.splitty.SplittyConfig;
import net.minecraft.util.Util;
import org.lwjgl.sdl.SDLHints;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
public class RenderSystemMixin {
	/**
	 * Wayland doesn't let applications place their own windows, so prefer X11 (XWayland) before
	 * SDL starts. Falls back to Wayland if X11 isn't available. An explicit SDL_VIDEO_DRIVER wins.
	 */
	@Inject(method = "initBackendSystem", at = @At("HEAD"))
	private static void splitty$preferX11(CallbackInfoReturnable<?> cir) {
		SplittyConfig config = SplittyConfig.get();
		if (Util.getPlatform() == Util.OS.LINUX && config.forceX11 && Splitty.active() && System.getenv("SDL_VIDEO_DRIVER") == null) {
			SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_DRIVER, "x11,wayland");
		}
	}
}
