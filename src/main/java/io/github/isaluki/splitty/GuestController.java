package io.github.isaluki.splitty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Player 2: waits on the title screen until player 1's world is open, then joins it. */
public final class GuestController {
	private static final int POLL_TICKS = 20;

	private String lastAttemptedSession;
	private int ticks;

	public void tick(Minecraft minecraft) {
		if (!SplittyConfig.get().autoJoin || minecraft.level != null) {
			return;
		}
		if (!(minecraft.gui.screen() instanceof TitleScreen titleScreen) || ticks++ % POLL_TICKS != 0) {
			return;
		}
		SessionFile.readFresh().ifPresent(session -> {
			// Only try each host session once, so a failed join doesn't loop forever.
			if (!session.id().equals(lastAttemptedSession)) {
				join(minecraft, titleScreen, session);
			}
		});
	}

	/** Joins the host if it is running. Returns false if no host was found. */
	public boolean joinNow(Minecraft minecraft, Screen parent) {
		return SessionFile.readFresh().map(session -> {
			join(minecraft, parent, session);
			return true;
		}).orElse(false);
	}

	private void join(Minecraft minecraft, Screen parent, SessionFile.Session session) {
		lastAttemptedSession = session.id();
		String host = "127.0.0.1";
		Splitty.LOGGER.info("Joining player 1's world '{}' on port {}", session.world(), session.port());
		ServerData data = new ServerData(session.world() == null ? "Splitty" : session.world(), host + ":" + session.port(), ServerData.Type.LAN);
		ConnectScreen.startConnecting(parent, minecraft, new ServerAddress(host, session.port()), data, false, null);
	}
}
