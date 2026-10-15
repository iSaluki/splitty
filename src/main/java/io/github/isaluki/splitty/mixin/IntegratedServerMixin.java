package io.github.isaluki.splitty.mixin;

import io.github.isaluki.splitty.Role;
import io.github.isaluki.splitty.Splitty;
import io.github.isaluki.splitty.SplittyConfig;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
	/**
	 * Player 2 usually uses an offline account (one Microsoft account can't be signed in twice),
	 * so the LAN world must not require Microsoft authentication.
	 */
	@Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At("HEAD"))
	private void splitty$allowOfflineGuests(MinecraftServer.MultiplayerScope scope, int port, CallbackInfoReturnable<Boolean> cir) {
		if (Splitty.role() == Role.HOST && SplittyConfig.get().allowOfflineGuests) {
			((MinecraftServer) (Object) this).setUsesAuthentication(false);
		}
	}
}
