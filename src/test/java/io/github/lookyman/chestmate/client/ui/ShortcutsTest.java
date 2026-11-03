package io.github.lookyman.chestmate.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ShortcutsTest {
	// 3-row chest: container 0..26, main 27..53, hotbar 54..62
	private static final MenuRegions CHEST = new MenuRegions(
		IntStream.range(0, 27).boxed().toList(), IntStream.range(27, 54).boxed().toList());
	// survival inventory: main 9..35, hotbar 36..44, no container
	private static final MenuRegions INVENTORY = new MenuRegions(List.of(), IntStream.range(9, 36).boxed().toList());

	@Test
	void resolveRunsNothingWhenNoShortcutMatches() {
		assertEquals(Optional.empty(), Shortcuts.resolve(Set.<String>of()::contains, CHEST));
	}

	@Test
	void eachKeyMapsToExactlyOneOperationOnAContainerScreen() {
		assertEquals(Optional.of(Operation.ORGANIZE_CONTAINER),
			Shortcuts.resolve(Set.of(Shortcuts.ORGANIZE_CONTAINER)::contains, CHEST));
		assertEquals(Optional.of(Operation.ORGANIZE_INVENTORY),
			Shortcuts.resolve(Set.of(Shortcuts.ORGANIZE_INVENTORY)::contains, CHEST));
		assertEquals(Optional.of(Operation.PUT_IN), Shortcuts.resolve(Set.of(Shortcuts.PUT_IN)::contains, CHEST));
		assertEquals(Optional.of(Operation.TAKE_OUT), Shortcuts.resolve(Set.of(Shortcuts.TAKE_OUT)::contains, CHEST));
	}

	@Test
	void organizeInventoryAlsoWorksOnTheInventoryScreen() {
		assertEquals(Optional.of(Operation.ORGANIZE_INVENTORY),
			Shortcuts.resolve(Set.of(Shortcuts.ORGANIZE_INVENTORY)::contains, INVENTORY));
	}

	@Test
	void containerOnlyKeysRunNothingButAreStillConsumedOnTheInventoryScreen() {
		// e.g. Put in bound to Q: on the inventory screen Q must neither drop the item nor run anything
		for (String key : List.of(Shortcuts.ORGANIZE_CONTAINER, Shortcuts.PUT_IN, Shortcuts.TAKE_OUT)) {
			assertEquals(Optional.empty(), Shortcuts.resolve(Set.of(key)::contains, INVENTORY));
			assertTrue(Shortcuts.isBound(Set.of(key)::contains));
		}
	}

	@Test
	void unboundInputIsNotConsumed() {
		assertFalse(Shortcuts.isBound(Set.<String>of()::contains));
	}

	@Test
	void resolveFirstMatchWinsInDeclaredOrder() {
		assertEquals(Optional.of(Operation.ORGANIZE_CONTAINER), Shortcuts.resolve(
			Set.of(Shortcuts.ORGANIZE_CONTAINER, Shortcuts.ORGANIZE_INVENTORY, Shortcuts.PUT_IN, Shortcuts.TAKE_OUT)::contains,
			CHEST));
		assertEquals(Optional.of(Operation.ORGANIZE_INVENTORY), Shortcuts.resolve(
			Set.of(Shortcuts.ORGANIZE_INVENTORY, Shortcuts.PUT_IN, Shortcuts.TAKE_OUT)::contains, CHEST));
		assertEquals(Optional.of(Operation.PUT_IN),
			Shortcuts.resolve(Set.of(Shortcuts.PUT_IN, Shortcuts.TAKE_OUT)::contains, CHEST));
	}

	@Test
	void aKeySharedByOrganizeContainerAndInventoryRunsWhicheverAppliesOnTheScreen() {
		Predicate<String> both = Set.of(Shortcuts.ORGANIZE_CONTAINER, Shortcuts.ORGANIZE_INVENTORY)::contains;
		assertEquals(Optional.of(Operation.ORGANIZE_INVENTORY), Shortcuts.resolve(both, INVENTORY));
		assertEquals(Optional.of(Operation.ORGANIZE_CONTAINER), Shortcuts.resolve(both, CHEST));
	}
}
