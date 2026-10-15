package io.github.isaluki.splitty;

import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;

/** Player 1: opens the single-player world to LAN and tells the guest which port to use. */
public final class HostController {
	private static final int HEARTBEAT_TICKS = 40;

	private IntegratedServer publishAttempted;
	private String sessionId;
	private int ticks;
	private boolean warnedAboutControlify;

	public void tick(Minecraft minecraft) {
		SplittyConfig config = SplittyConfig.get();
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.player == null) {
			endSession();
			publishAttempted = null;
			return;
		}

		if (!warnedAboutControlify && FabricLoader.getInstance().isModLoaded("controlify")) {
			warnedAboutControlify = true;
			chat(minecraft, Component.translatable("splitty.host.controlify_warning").withStyle(ChatFormatting.YELLOW));
		}

		if (!server.isPublished() && config.autoOpenLan && publishAttempted != server) {
			publishAttempted = server;
			int port = config.lanPort > 0 ? config.lanPort : HttpUtil.getAvailablePort();
			if (server.publishServer(MinecraftServer.MultiplayerScope.LAN, config.allowGuestCommands, port)) {
				chat(minecraft, Component.translatable("splitty.host.published", port).withStyle(ChatFormatting.GREEN));
				minecraft.updateTitle();
			} else {
				chat(minecraft, Component.translatable("splitty.host.publish_failed", port).withStyle(ChatFormatting.RED));
			}
		}

		if (server.isPublished()) {
			if (sessionId == null) {
				sessionId = UUID.randomUUID().toString();
				ticks = 0;
			}
			if (ticks++ % HEARTBEAT_TICKS == 0) {
				SessionFile.write(new SessionFile.Session(sessionId, server.getPort(), System.currentTimeMillis(),
					server.getWorldData().getLevelName()));
			}
		} else {
			endSession();
		}
	}

	public void endSession() {
		if (sessionId != null) {
			SessionFile.clear(sessionId);
			sessionId = null;
		}
	}

	private static void chat(Minecraft minecraft, Component message) {
		minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("[Splitty] ").withStyle(ChatFormatting.AQUA).append(message));
	}
}
