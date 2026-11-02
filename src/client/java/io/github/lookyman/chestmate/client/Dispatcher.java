package io.github.lookyman.chestmate.client;

import com.mojang.logging.LogUtils;
import io.github.lookyman.chestmate.click.Click;
import io.github.lookyman.chestmate.click.ClickExecutor;
import io.github.lookyman.chestmate.click.ClickPlanner;
import io.github.lookyman.chestmate.client.config.ChestmateConfig;
import io.github.lookyman.chestmate.core.CreativeOrder;
import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import io.github.lookyman.chestmate.core.SlotRules;
import io.github.lookyman.chestmate.net.OperationRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** Runs an operation on the open screen: instantly on the server when it has Chestmate, otherwise by paced clicks. */
public final class Dispatcher {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ClickExecutor EXECUTOR = new ClickExecutor();

	private Dispatcher() {
	}

	public static void run(Operation op) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (minecraft.player == null || level == null
			|| !(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen)
			|| screen instanceof CreativeModeInventoryScreen) {
			return;
		}
		AbstractContainerMenu menu = screen.getMenu();
		if (menu != minecraft.player.containerMenu) {
			return;
		}
		Optional<MenuRegions> found = MenuRegions.of(menu);
		if (found.isEmpty() || !op.supportedBy(found.get()) || !menu.getCarried().isEmpty() || EXECUTOR.isRunning()) {
			return;
		}
		MenuRegions regions = found.get();
		if (ClientPlayNetworking.canSend(OperationRequest.TYPE)) {
			ClientPlayNetworking.send(new OperationRequest(menu.containerId, op));
			return;
		}
		List<ItemStack> snapshot = new ArrayList<>(menu.slots.size());
		for (Slot slot : menu.slots) {
			snapshot.add(slot.getItem().copy());
		}
		SlotRules rules = SlotRules.of(menu);
		List<Click> plan;
		try {
			plan = ClickPlanner.plan(op, regions, snapshot, rules,
				CreativeOrder.forWorld(level.enabledFeatures(), level.registryAccess()));
		} catch (IllegalStateException e) {
			LOGGER.warn("Chestmate could not plan {}; doing nothing", op, e);
			return;
		}
		EXECUTOR.start(new ClientMenuView(minecraft, menu), plan, rules, regions);
	}

	/** Called every client tick: sends this tick's share of clicks. */
	public static void tick() {
		EXECUTOR.tick(ChestmateConfig.get().clicksPerTick);
	}

	/** Stops a click run, e.g. because its screen closed. */
	public static void abort() {
		EXECUTOR.abort();
	}
}
