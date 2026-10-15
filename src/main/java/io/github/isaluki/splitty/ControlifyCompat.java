package io.github.isaluki.splitty;

import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Turns on Controlify's "Out of Focus Input" for player 2. Their window almost never has focus,
 * so without it the controller does nothing. Uses reflection so Controlify stays optional.
 */
public final class ControlifyCompat {
	private static final int CHECK_INTERVAL_TICKS = 100;

	private boolean disabled = !FabricLoader.getInstance().isModLoaded("controlify");
	private int ticks;

	public void tick() {
		if (disabled || ticks++ % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		try {
			Object controlify = Class.forName("dev.isxander.controlify.Controlify").getMethod("instance").invoke(null);
			Object config = controlify.getClass().getMethod("config").invoke(controlify);
			Object settings = config.getClass().getMethod("getSettings").invoke(config);
			Object global = settings.getClass().getMethod("globalSettings").invoke(settings);
			if (global == null) {
				return; // Controlify hasn't loaded its config yet.
			}
			Field field = global.getClass().getField("outOfFocusInput");
			if (!field.getBoolean(global)) {
				field.setBoolean(global, true);
				Splitty.LOGGER.info("Enabled Controlify's Out of Focus Input for player 2");
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			disabled = true;
			Splitty.LOGGER.warn("Could not enable Controlify's Out of Focus Input; turn it on in Controlify's settings", e);
		}
	}
}
