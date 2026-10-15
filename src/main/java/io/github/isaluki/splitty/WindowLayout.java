package io.github.isaluki.splitty;

import com.mojang.blaze3d.platform.Window;
import io.github.isaluki.splitty.mixin.WindowAccessor;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryStack;

/** Moves and resizes the game window into its half of the screen. Must run on the render thread. */
public final class WindowLayout {
	private WindowLayout() {
	}

	public static void apply(Minecraft minecraft) {
		SplittyConfig config = SplittyConfig.get();
		Role role = config.role();
		if (role == Role.OFF) {
			return;
		}

		Window window = minecraft.getWindow();
		long handle = window.handle();
		if (minecraft.options.fullscreen().get()) {
			minecraft.options.fullscreen().set(false);
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_Rect area = SDL_Rect.malloc(stack);
			int display = pickDisplay(config.monitor);
			if (!SDLVideo.SDL_GetDisplayUsableBounds(display, area) && !SDLVideo.SDL_GetDisplayBounds(display, area)) {
				Splitty.LOGGER.warn("Could not query display {} bounds: {}", display, SDLError.SDL_GetError());
				return;
			}

			boolean firstHalf = (role == Role.HOST) != config.swapSides;
			int x = area.x();
			int y = area.y();
			int width = area.w();
			int height = area.h();
			if (config.leftRight()) {
				int firstWidth = width / 2;
				if (!firstHalf) {
					x += firstWidth;
				}
				width = firstHalf ? firstWidth : width - firstWidth;
			} else {
				int firstHeight = height / 2;
				if (!firstHalf) {
					y += firstHeight;
				}
				height = firstHalf ? firstHeight : height - firstHeight;
			}

			SDLVideo.SDL_SetWindowBordered(handle, !config.borderless);
			if (!config.borderless) {
				// Keep the title bar inside our half of the screen.
				IntBuffer top = stack.mallocInt(1);
				IntBuffer left = stack.mallocInt(1);
				IntBuffer bottom = stack.mallocInt(1);
				IntBuffer right = stack.mallocInt(1);
				if (SDLVideo.SDL_GetWindowBordersSize(handle, top, left, bottom, right)) {
					x += left.get(0);
					y += top.get(0);
					width -= left.get(0) + right.get(0);
					height -= top.get(0) + bottom.get(0);
				}
			}

			WindowAccessor accessor = (WindowAccessor) (Object) window;
			accessor.splitty$setWindowedX(x);
			accessor.splitty$setWindowedY(y);
			// Applies windowed mode using the position above and the given size.
			window.setWindowed(width, height);
			Splitty.LOGGER.info("Placed {} window at {},{} size {}x{} (video driver: {})",
				role.key(), x, y, width, height, SDLVideo.SDL_GetCurrentVideoDriver());
			if ("wayland".equals(SDLVideo.SDL_GetCurrentVideoDriver())) {
				Splitty.LOGGER.warn("Running on native Wayland, which does not let games position their own windows. "
					+ "Set forceX11 to true in config/splitty.json (and allow X11 for Prism if it is a Flatpak), "
					+ "or tile the two windows yourself.");
			}
		}
	}

	private static int pickDisplay(int index) {
		if (index >= 0) {
			IntBuffer displays = SDLVideo.SDL_GetDisplays();
			if (displays != null) {
				try {
					if (index < displays.remaining()) {
						return displays.get(displays.position() + index);
					}
				} finally {
					SDLStdinc.SDL_free(displays);
				}
			}
			Splitty.LOGGER.warn("Display {} does not exist, using the primary display", index);
		}
		return SDLVideo.SDL_GetPrimaryDisplay();
	}
}
