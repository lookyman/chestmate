package io.github.lookyman.chestmate.core;

import io.github.lookyman.chestmate.TestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuRegionsTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.init();
	}

	private static List<Integer> range(int from, int toInclusive) {
		return IntStream.rangeClosed(from, toInclusive).boxed().toList();
	}

	@Test
	void chestLayout() {
		Container storage = new SimpleContainer(27);
		Container player = new SimpleContainer(41);
		List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < 27; i++) {
			slots.add(new Slot(storage, i, 0, 0));
		}
		for (int i = 9; i < 36; i++) {
			slots.add(new Slot(player, i, 0, 0));
		}
		for (int i = 0; i < 9; i++) {
			slots.add(new Slot(player, i, 0, 0));
		}

		MenuRegions regions = MenuRegions.classify(slots, c -> c == player, true);

		assertEquals(range(0, 26), regions.container());
		assertEquals(range(27, 53), regions.main());
	}

	@Test
	void inventoryMenuLayout() {
		Container crafting = new SimpleContainer(5);
		Container player = new SimpleContainer(41);
		List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			slots.add(new Slot(crafting, i, 0, 0));
		}
		for (int i = 36; i < 40; i++) {
			slots.add(new Slot(player, i, 0, 0));
		}
		for (int i = 9; i < 36; i++) {
			slots.add(new Slot(player, i, 0, 0));
		}
		for (int i = 0; i < 9; i++) {
			slots.add(new Slot(player, i, 0, 0));
		}
		slots.add(new Slot(player, 40, 0, 0));

		MenuRegions regions = MenuRegions.classify(slots, c -> c == player, false);

		assertTrue(regions.container().isEmpty());
		assertEquals(range(9, 35), regions.main());
	}

	@Test
	void organizeContainerUnsupportedOnInventory() {
		MenuRegions inventory = new MenuRegions(List.of(), range(9, 35));
		assertFalse(Operation.ORGANIZE_CONTAINER.supportedBy(inventory));
		assertTrue(Operation.ORGANIZE_INVENTORY.supportedBy(inventory));
	}
}
