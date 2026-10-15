package io.github.isaluki.splitty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Per-instance settings, stored in {@code config/splitty.json}.
 * The role can also be forced with the JVM argument {@code -Dsplitty.role=host|guest|off}.
 */
public final class SplittyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("splitty.json");
	private static SplittyConfig instance;

	/** "off", "host" (keyboard + mouse, player 1) or "guest" (controller, player 2). */
	public String role = "off";
	/** "top_bottom" or "left_right". */
	public String layout = "top_bottom";
	/** Put the guest in the first half (top/left) instead of the host. */
	public boolean swapSides = false;
	/** Index into the connected displays; -1 means the primary display. */
	public int monitor = -1;
	/** Remove the window title bar so the two halves fit together exactly. */
	public boolean borderless = true;
	/** Ask SDL to use X11/XWayland so windows can be positioned (Wayland does not allow it). */
	public boolean forceX11 = true;

	/** Host: open single-player worlds to LAN as soon as they load. */
	public boolean autoOpenLan = true;
	/** Host: let accounts that are not signed in to Microsoft (offline accounts) join. */
	public boolean allowOfflineGuests = true;
	/** Host: LAN port, 0 picks a free one. */
	public int lanPort = 0;
	/** Host: give the guest access to cheats/commands. */
	public boolean allowGuestCommands = false;

	/** Guest: join the host from the title screen automatically. */
	public boolean autoJoin = true;
	/** Guest: ignore real keyboard/mouse input while in a world so player 1's input never leaks in. */
	public boolean blockKeyboardMouseInWorld = true;

	/** Where the host announces its LAN port. Empty uses the system temp directory. */
	public String sessionFile = "";

	public static SplittyConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public Role role() {
		return Role.parse(System.getProperty("splitty.role"), Role.parse(role, Role.OFF));
	}

	public boolean roleForcedByJvmArg() {
		return System.getProperty("splitty.role") != null;
	}

	public boolean leftRight() {
		return "left_right".equalsIgnoreCase(layout) || "vertical".equalsIgnoreCase(layout);
	}

	public Path sessionPath() {
		if (sessionFile != null && !sessionFile.isBlank()) {
			return Path.of(sessionFile);
		}
		return Path.of(System.getProperty("java.io.tmpdir"), "splitty-" + System.getProperty("user.name", "player") + ".json");
	}

	private static SplittyConfig load() {
		SplittyConfig config = null;
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				config = GSON.fromJson(reader, SplittyConfig.class);
			} catch (IOException | JsonParseException e) {
				Splitty.LOGGER.error("Could not read {}, using defaults", PATH, e);
			}
		}
		if (config == null) {
			config = new SplittyConfig();
		}
		// Write back so new options show up in the file.
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			Splitty.LOGGER.error("Could not save {}", PATH, e);
		}
	}
}
