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
	/** Test switch: pretend the server lacks the mod, to exercise the client-only path. */
	public static final String DISABLE_SERVER_PROPERTY = "chestmate.disableServer";

	@Override
	public void onInitialize() {
		if (Boolean.getBoolean(DISABLE_SERVER_PROPERTY)) {
			return;
		}
		PayloadTypeRegistry.serverboundPlay().register(OperationRequest.TYPE, OperationRequest.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OperationRequest.TYPE,
			(request, context) -> handle(context.player(), request));
	}

	/** Runs on the server thread. Never sends anything to the client: the normal slot sync does that. */
	private static void handle(ServerPlayer player, OperationRequest request) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!menu.stillValid(player)) {
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
