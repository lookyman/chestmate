package io.github.lookyman.chestmate.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

	@Test
	void resolveRunsNothingWhenNoShortcutMatches() {
		assertEquals(Optional.empty(), Shortcuts.resolve(Set.<String>of()::contains, 0, CHEST));
	}

	@Test
	void resolveOrganizeUsesTheHoveredRegion() {
		assertEquals(Optional.of(Operation.ORGANIZE_INVENTORY),
			Shortcuts.resolve(Set.of(Shortcuts.ORGANIZE)::contains, 30, CHEST));
		assertEquals(Optional.of(Operation.ORGANIZE_CONTAINER),
			Shortcuts.resolve(Set.of(Shortcuts.ORGANIZE)::contains, Shortcuts.NO_SLOT, CHEST));
	}

	@Test
	void resolvePutInAndTakeOutOnAContainerScreen() {
		assertEquals(Optional.of(Operation.PUT_IN), Shortcuts.resolve(Set.of(Shortcuts.PUT_IN)::contains, 0, CHEST));
		assertEquals(Optional.of(Operation.TAKE_OUT), Shortcuts.resolve(Set.of(Shortcuts.TAKE_OUT)::contains, 0, CHEST));
	}

	@Test
	void resolveLeavesPutInAndTakeOutToVanillaWithoutAContainer() {
		// e.g. Put in bound to Q: on the inventory screen Q must still drop the hovered item
		assertEquals(Optional.empty(), Shortcuts.resolve(Set.of(Shortcuts.PUT_IN)::contains, 9, INVENTORY));
		assertEquals(Optional.empty(), Shortcuts.resolve(Set.of(Shortcuts.TAKE_OUT)::contains, 9, INVENTORY));
	}

	@Test
	void resolvePrefersOrganizeWhenSeveralShortcutsShareAnInput() {
		assertEquals(Optional.of(Operation.ORGANIZE_CONTAINER),
			Shortcuts.resolve(Set.of(Shortcuts.ORGANIZE, Shortcuts.PUT_IN, Shortcuts.TAKE_OUT)::contains, 0, CHEST));
		assertEquals(Optional.of(Operation.PUT_IN),
			Shortcuts.resolve(Set.of(Shortcuts.PUT_IN, Shortcuts.TAKE_OUT)::contains, 0, CHEST));
	}
}
