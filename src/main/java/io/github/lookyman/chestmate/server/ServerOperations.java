package io.github.lookyman.chestmate.server;

import io.github.lookyman.chestmate.core.Counts;
import io.github.lookyman.chestmate.core.Layouts;
import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import io.github.lookyman.chestmate.core.SlotRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Authoritative, instant application of an {@link Operation} to a menu. Pure over the menu: no player needed. */
public final class ServerOperations {
	public enum Result { APPLIED, NOTHING_TO_DO, REJECTED }

	private ServerOperations() {
	}

	/** Mirrors vanilla's click handler: spectators and dead or dying players may not touch containers. */
	public static boolean mayAct(boolean spectator, boolean deadOrDying, boolean menuStillValid) {
		return !spectator && !deadOrDying && menuStillValid;
	}

	public static Result apply(AbstractContainerMenu menu, int requestContainerId, Operation op, ItemStack carried,
		Comparator<ItemStack> order) {
		Optional<MenuRegions> regions = MenuRegions.of(menu);
		if (regions.isEmpty()) {
			return Result.REJECTED;
		}
		return apply(menu, regions.get(), requestContainerId, op, carried, order);
	}

	static Result apply(AbstractContainerMenu menu, MenuRegions regions, int requestContainerId, Operation op,
		ItemStack carried, Comparator<ItemStack> order) {
		if (requestContainerId != menu.containerId || !op.supportedBy(regions) || !carried.isEmpty()) {
			return Result.REJECTED;
		}
		SlotRules rules = SlotRules.of(menu);
		List<Integer> container = regions.container();
		List<Integer> main = regions.main();

		List<Integer> firstRegion;
		List<Integer> secondRegion;
		List<ItemStack> firstNew;
		List<ItemStack> secondNew;
		switch (op) {
			case ORGANIZE_CONTAINER -> {
				firstRegion = container;
				secondRegion = List.of();
				firstNew = Layouts.organize(read(menu, container), rules.forRegion(container), order);
				secondNew = List.of();
			}
			case ORGANIZE_INVENTORY -> {
				firstRegion = main;
				secondRegion = List.of();
				firstNew = Layouts.organize(read(menu, main), rules.forRegion(main), order);
				secondNew = List.of();
			}
			case PUT_IN -> {
				firstRegion = main;
				secondRegion = container;
				Layouts.Transfer t = Layouts.transfer(read(menu, main), read(menu, container), rules.forRegion(container));
				firstNew = t.from();
				secondNew = t.to();
			}
			case TAKE_OUT -> {
				firstRegion = container;
				secondRegion = main;
				Layouts.Transfer t = Layouts.transfer(read(menu, container), read(menu, main), rules.forRegion(main));
				firstNew = t.from();
				secondNew = t.to();
			}
			default -> {
				return Result.REJECTED;
			}
		}

		List<ItemStack> firstOld = read(menu, firstRegion);
		List<ItemStack> secondOld = read(menu, secondRegion);
		if (!Counts.sameAll(List.of(firstOld, secondOld), List.of(firstNew, secondNew))) {
			return Result.REJECTED;
		}
		if (identical(firstOld, firstNew) && identical(secondOld, secondNew)) {
			return Result.NOTHING_TO_DO;
		}
		write(menu, firstRegion, firstNew);
		write(menu, secondRegion, secondNew);
		return Result.APPLIED;
	}

	private static List<ItemStack> read(AbstractContainerMenu menu, List<Integer> region) {
		List<ItemStack> stacks = new ArrayList<>(region.size());
		for (int index : region) {
			stacks.add(menu.getSlot(index).getItem().copy());
		}
		return stacks;
	}

	private static void write(AbstractContainerMenu menu, List<Integer> region, List<ItemStack> stacks) {
		for (int i = 0; i < region.size(); i++) {
			menu.getSlot(region.get(i)).set(stacks.get(i).copy());
		}
	}

	private static boolean identical(List<ItemStack> a, List<ItemStack> b) {
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.matches(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}
}
