package io.github.isaluki.splitty;

import java.util.Locale;

public enum Role {
	/** Splitty does nothing; the game behaves normally. */
	OFF,
	/** Player 1: keyboard and mouse. Hosts the world and opens it to LAN. */
	HOST,
	/** Player 2: controller (via Controlify). Joins the host automatically. */
	GUEST;

	public Role next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Role parse(String value, Role fallback) {
		if (value == null) {
			return fallback;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "off", "none", "disabled" -> OFF;
			case "host", "p1", "player1", "keyboard" -> HOST;
			case "guest", "p2", "player2", "controller" -> GUEST;
			default -> fallback;
		};
	}
}
