package io.github.isaluki.splitty;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Splitty implements ClientModInitializer {
	public static final String MOD_ID = "splitty";
	public static final Logger LOGGER = LoggerFactory.getLogger("Splitty");

	private static final HostController HOST = new HostController();
	private static final GuestController GUEST = new GuestController();
	private static final ControlifyCompat CONTROLIFY = new ControlifyCompat();

	public static Role role() {
		return SplittyConfig.get().role();
	}

	public static boolean active() {
		return role() != Role.OFF;
	}

	public static boolean isGuest() {
		return role() == Role.GUEST;
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Splitty role: {}", role().key());

		ClientLifecycleEvents.CLIENT_STARTED.register(WindowLayout::apply);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> HOST.endSession());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			switch (role()) {
				case HOST -> HOST.tick(client);
				case GUEST -> {
					GUEST.tick(client);
					CONTROLIFY.tick();
				}
				case OFF -> HOST.endSession();
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen titleScreen) {
				addTitleButtons(client, titleScreen);
			}
		});
	}

	private static void addTitleButtons(Minecraft minecraft, TitleScreen screen) {
		SplittyConfig config = SplittyConfig.get();
		Button roleButton = Button.builder(roleLabel(), button -> {
				config.role = config.role().next().key();
				config.save();
				button.setMessage(roleLabel());
				WindowLayout.apply(minecraft);
				minecraft.gui.setScreen(new TitleScreen());
			})
			.bounds(4, 4, 160, 20)
			.tooltip(Tooltip.create(Component.translatable("splitty.button.role.tooltip")))
			.build();
		roleButton.active = !config.roleForcedByJvmArg();
		Screens.getWidgets(screen).add(roleButton);

		if (config.role() == Role.GUEST) {
			Button joinButton = Button.builder(Component.translatable("splitty.button.join"), button -> {
					if (!GUEST.joinNow(minecraft, screen)) {
						button.setMessage(Component.translatable("splitty.button.join.no_host"));
					}
				})
				.bounds(4, 28, 160, 20)
				.build();
			Screens.getWidgets(screen).add(joinButton);
		}
	}

	private static Component roleLabel() {
		return Component.translatable("splitty.button.role", Component.translatable("splitty.role." + role().key()));
	}
}
