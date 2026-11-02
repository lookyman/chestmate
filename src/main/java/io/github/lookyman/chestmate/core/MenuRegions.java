package io.github.lookyman.chestmate.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;

/** Menu slot indices (in menu order) of the storage container and the player's 27 main slots. */
public record MenuRegions(List<Integer> container, List<Integer> main) {
	private static final int MAIN_FIRST = 9;
	private static final int MAIN_LAST = 35;

	public MenuRegions {
		container = List.copyOf(container);
		main = List.copyOf(main);
	}

	public static Optional<MenuRegions> of(AbstractContainerMenu menu) {
		if (!(menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof InventoryMenu)) {
			return Optional.empty();
		}
		return Optional.of(classify(menu.slots, c -> c instanceof Inventory, !(menu instanceof InventoryMenu)));
	}

	public static MenuRegions classify(List<Slot> slots, Predicate<Container> isPlayerInventory, boolean hasContainer) {
		List<Integer> container = new ArrayList<>();
		List<Integer> main = new ArrayList<>();
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			if (isPlayerInventory.test(slot.container)) {
				int index = slot.getContainerSlot();
				if (index >= MAIN_FIRST && index <= MAIN_LAST) {
					main.add(i);
				}
			} else if (hasContainer) {
				container.add(i);
			}
		}
		return new MenuRegions(container, main);
	}
}
