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

/**
 * Moves and resizes the game window into its half of the screen, then keeps checking for a while
 * that the window manager actually did it. Must run on the render thread.
 */
public final class WindowLayout {
	/** How often to compare the real window with the target. */
	private static final int CHECK_INTERVAL_TICKS = 20;
	/** How many checks to make after placing the window (20 ticks each, so about 20 seconds). */
	private static final int CHECKS = 20;
	private static final int MAX_ATTEMPTS = 5;
	private static final int TOLERANCE_PX = 8;

	private static Target target;
	private static int checksLeft;
	private static int attempts;
	private static int ticks;

	private record Target(int x, int y, int width, int height) {
	}

	private WindowLayout() {
	}

	public static void apply(Minecraft minecraft) {
		if (Splitty.role() == Role.OFF) {
			target = null;
			return;
		}
		target = computeTarget(minecraft);
		if (target == null) {
			return;
		}
		attempts = 0;
		checksLeft = CHECKS;
		ticks = 0;
		place(minecraft, target);
	}

	/** Re-applies the layout if the window manager changed or ignored it shortly after placing. */
	public static void tick(Minecraft minecraft) {
		if (target == null || checksLeft <= 0 || ++ticks % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		checksLeft--;
		long handle = minecraft.getWindow().handle();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x = stack.mallocInt(1);
			IntBuffer y = stack.mallocInt(1);
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			if (!SDLVideo.SDL_GetWindowPosition(handle, x, y) || !SDLVideo.SDL_GetWindowSize(handle, w, h)) {
				return;
			}
			long flags = SDLVideo.SDL_GetWindowFlags(handle);
			boolean maximized = (flags & SDLVideo.SDL_WINDOW_MAXIMIZED) != 0;
			boolean fullscreen = (flags & SDLVideo.SDL_WINDOW_FULLSCREEN) != 0;
			boolean sizeOk = Math.abs(w.get(0) - target.width()) <= TOLERANCE_PX && Math.abs(h.get(0) - target.height()) <= TOLERANCE_PX;
			boolean positionOk = Math.abs(x.get(0) - target.x()) <= TOLERANCE_PX && Math.abs(y.get(0) - target.y()) <= TOLERANCE_PX;
			// Wayland never reports or allows a position, so only the size can be checked there.
			if (isWayland()) {
				positionOk = true;
			}
			if (sizeOk && positionOk && !maximized && !fullscreen) {
				return;
			}

			String actual = String.format("%d,%d %dx%d%s%s", x.get(0), y.get(0), w.get(0), h.get(0),
				maximized ? " maximized" : "", fullscreen ? " fullscreen" : "");
			if (attempts >= MAX_ATTEMPTS) {
				Splitty.LOGGER.error("The window manager keeps the window at {} instead of {},{} {}x{}. "
						+ "Turn off \"Launch maximized\" / \"Start Minecraft maximized\" in Prism's settings and make sure your "
						+ "desktop doesn't force Minecraft windows to be maximized or fullscreen.",
					actual, target.x(), target.y(), target.width(), target.height());
				checksLeft = 0;
				return;
			}
			Splitty.LOGGER.warn("Window is at {} instead of {},{} {}x{}; placing it again",
				actual, target.x(), target.y(), target.width(), target.height());
			place(minecraft, target);
		}
	}

	private static Target computeTarget(Minecraft minecraft) {
		SplittyConfig config = SplittyConfig.get();
		long handle = minecraft.getWindow().handle();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_Rect area = SDL_Rect.malloc(stack);
			int display = pickDisplay(config.monitor);
			if (!SDLVideo.SDL_GetDisplayUsableBounds(display, area) && !SDLVideo.SDL_GetDisplayBounds(display, area)) {
				Splitty.LOGGER.warn("Could not query display {} bounds: {}", display, SDLError.SDL_GetError());
				return null;
			}
			Splitty.LOGGER.info("Display {}: usable area {},{} {}x{}, content scale {}, video driver {}",
				display, area.x(), area.y(), area.w(), area.h(), SDLVideo.SDL_GetDisplayContentScale(display),
				SDLVideo.SDL_GetCurrentVideoDriver());

			boolean firstHalf = (config.role() == Role.HOST) != config.swapSides;
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

			if (!config.borderless) {
				// Keep the title bar inside our half of the screen.
				SDLVideo.SDL_SetWindowBordered(handle, true);
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
			return new Target(x, y, width, height);
		}
	}

	private static void place(Minecraft minecraft, Target target) {
		attempts++;
		SplittyConfig config = SplittyConfig.get();
		Window window = minecraft.getWindow();
		long handle = window.handle();
		if (minecraft.options.fullscreen().get()) {
			minecraft.options.fullscreen().set(false);
		}

		// A maximized window ignores resize requests, so un-maximize it first. This happens when
		// Prism starts the game "maximized", or when the desktop auto-maximizes a large new window.
		long flags = SDLVideo.SDL_GetWindowFlags(handle);
		if ((flags & (SDLVideo.SDL_WINDOW_MAXIMIZED | SDLVideo.SDL_WINDOW_FULLSCREEN)) != 0) {
			SDLVideo.SDL_SetWindowFullscreen(handle, false);
			SDLVideo.SDL_RestoreWindow(handle);
			SDLVideo.SDL_SyncWindow(handle);
		}

		SDLVideo.SDL_SetWindowBordered(handle, !config.borderless);
		WindowAccessor accessor = (WindowAccessor) (Object) window;
		accessor.splitty$setWindowedX(target.x());
		accessor.splitty$setWindowedY(target.y());
		// Applies windowed mode using the position above and the given size.
		window.setWindowed(target.width(), target.height());
		SDLVideo.SDL_SyncWindow(handle);

		Splitty.LOGGER.info("Placed {} window at {},{} size {}x{} (attempt {})",
			config.role().key(), target.x(), target.y(), target.width(), target.height(), attempts);
		if (attempts == 1 && isWayland()) {
			Splitty.LOGGER.warn("Running on native Wayland, which does not let games position their own windows. "
				+ "Set forceX11 to true in config/splitty.json (and allow X11 for Prism if it is a Flatpak), "
				+ "or tile the two windows yourself.");
		}
	}

	private static boolean isWayland() {
		return "wayland".equals(SDLVideo.SDL_GetCurrentVideoDriver());
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
