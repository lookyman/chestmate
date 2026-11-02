package io.github.lookyman.chestmate;

import io.github.lookyman.chestmate.core.CreativeOrder;
import io.github.lookyman.chestmate.net.OperationRequest;
import io.github.lookyman.chestmate.server.ServerOperations;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class Chestmate implements ModInitializer {
	public static final String MOD_ID = "chestmate";
	/**
	 * Server-only test switch, not a player setting: with {@code -Dchestmate.disableServer=true} the server in this JVM
	 * (dedicated or integrated) registers no request receiver, so clients see a server without Chestmate and use the
	 * click path. The payload type stays registered, so a client launched with the switch can still send to a server
	 * that has Chestmate.
	 */
	public static final String DISABLE_SERVER_PROPERTY = "chestmate.disableServer";

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(OperationRequest.TYPE, OperationRequest.STREAM_CODEC);
		if (Boolean.getBoolean(DISABLE_SERVER_PROPERTY)) {
			return;
		}
		ServerPlayNetworking.registerGlobalReceiver(OperationRequest.TYPE,
			(request, context) -> handle(context.player(), request));
	}

	/** Runs on the server thread. Never sends anything to the client: the normal slot sync does that. */
	private static void handle(ServerPlayer player, OperationRequest request) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!ServerOperations.mayAct(player.isSpectator(), player.isDeadOrDying(), menu.stillValid(player))) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerOperations.Result result = ServerOperations.apply(menu, request.containerId(), request.operation(),
			menu.getCarried(),
			CreativeOrder.forWorld(player.level().enabledFeatures(), server.registryAccess()));
		if (result == ServerOperations.Result.APPLIED) {
			menu.broadcastChanges();
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
