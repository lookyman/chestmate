package io.github.lookyman.chestmate.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ShortcutsTest {
	// 3-row chest: container 0..26, main 27..53, hotbar 54..62
	private static final MenuRegions CHEST = new MenuRegions(
		IntStream.range(0, 27).boxed().toList(), IntStream.range(27, 54).boxed().toList());
	// survival inventory: main 9..35, hotbar 36..44, no container
	private static final MenuRegions INVENTORY = new MenuRegions(List.of(), IntStream.range(9, 36).boxed().toList());

	@Test
	void organizeHoveredContainerSlot() {
		assertEquals(Operation.ORGANIZE_CONTAINER, Shortcuts.organizeTarget(0, CHEST));
		assertEquals(Operation.ORGANIZE_CONTAINER, Shortcuts.organizeTarget(26, CHEST));
	}

	@Test
	void organizeHoveredMainSlot() {
		assertEquals(Operation.ORGANIZE_INVENTORY, Shortcuts.organizeTarget(27, CHEST));
		assertEquals(Operation.ORGANIZE_INVENTORY, Shortcuts.organizeTarget(53, CHEST));
		assertEquals(Operation.ORGANIZE_INVENTORY, Shortcuts.organizeTarget(9, INVENTORY));
	}

	@Test
	void noHoveredStorageSlotPrefersTheContainer() {
		assertEquals(Operation.ORGANIZE_CONTAINER, Shortcuts.organizeTarget(Shortcuts.NO_SLOT, CHEST));
		assertEquals(Operation.ORGANIZE_CONTAINER, Shortcuts.organizeTarget(60, CHEST)); // hotbar
	}

	@Test
	void noHoveredStorageSlotWithoutContainerOrganizesInventory() {
		assertEquals(Operation.ORGANIZE_INVENTORY, Shortcuts.organizeTarget(Shortcuts.NO_SLOT, INVENTORY));
		assertEquals(Operation.ORGANIZE_INVENTORY, Shortcuts.organizeTarget(0, INVENTORY)); // crafting result
		assertEquals(Operation.ORGANIZE_INVENTORY, Shortcuts.organizeTarget(40, INVENTORY)); // hotbar
	}
}
