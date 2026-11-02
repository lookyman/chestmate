package io.github.lookyman.chestmate.click;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.lookyman.chestmate.TestBootstrap;
import io.github.lookyman.chestmate.core.SlotRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClickSimTest {
	private static final ItemStack E = ItemStack.EMPTY;

	/** Slot 0 rejects everything; every other slot is vanilla. */
	private static final SlotRules SLOT0_REJECTS = new SlotRules() {
		@Override
		public boolean mayPlace(int pos, ItemStack s) {
			return pos != 0;
		}

		@Override
		public int maxStackSize(int pos, ItemStack s) {
			return s.getMaxStackSize();
		}
	};

	@BeforeAll
	static void boot() {
		TestBootstrap.init();
	}

	private static ItemStack s(Item item, int n) {
		return new ItemStack(item, n);
	}

	private static void assertStack(ItemStack expected, ItemStack actual) {
		assertTrue(ItemStack.matches(expected, actual), "expected " + expected + " but was " + actual);
	}

	private static ClickSim sim(SlotRules rules, ItemStack carried, ItemStack... slots) {
		return new ClickSim(new ArrayList<>(List.of(slots)), rules, carried);
	}

	@Test
	void pickUpWholeStackWithEmptyCursor() {
		ClickSim sim = sim(SlotRules.vanilla(), E, s(Items.STONE, 40));
		sim.apply(Click.left(0));
		assertStack(E, sim.get(0));
		assertStack(s(Items.STONE, 40), sim.carried());
	}

	@Test
	void placeIntoEmptySlotUpToMax() {
		// Ender pearls stack to 16; a cursor of 20 can only exist via commands, but the rule is min(count, max).
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.ENDER_PEARL, 20), E);
		sim.apply(Click.left(0));
		assertStack(s(Items.ENDER_PEARL, 16), sim.get(0));
		assertStack(s(Items.ENDER_PEARL, 4), sim.carried());
	}

	@Test
	void addWhatFitsToSameKind() {
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.STONE, 30), s(Items.STONE, 50));
		sim.apply(Click.left(0));
		assertStack(s(Items.STONE, 64), sim.get(0));
		assertStack(s(Items.STONE, 16), sim.carried());
	}

	@Test
	void swapDifferentKinds() {
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.DIRT, 3), s(Items.STONE, 5));
		sim.apply(Click.left(0));
		assertStack(s(Items.DIRT, 3), sim.get(0));
		assertStack(s(Items.STONE, 5), sim.carried());
	}

	@Test
	void notPlaceableSameKindPullsIntoCursor() {
		ClickSim sim = sim(SLOT0_REJECTS, s(Items.STONE, 10), s(Items.STONE, 20));
		sim.apply(Click.left(0));
		assertStack(E, sim.get(0));
		assertStack(s(Items.STONE, 30), sim.carried());
	}

	@Test
	void notPlaceableDifferentKindDoesNothing() {
		ClickSim sim = sim(SLOT0_REJECTS, s(Items.DIRT, 10), s(Items.STONE, 20), E);
		sim.apply(Click.left(0));
		assertStack(s(Items.STONE, 20), sim.get(0));
		assertStack(s(Items.DIRT, 10), sim.carried());
		// and an unplaceable empty slot keeps the cursor too
		ClickSim empty = sim(SLOT0_REJECTS, s(Items.DIRT, 10), E);
		empty.apply(Click.left(0));
		assertStack(E, empty.get(0));
		assertStack(s(Items.DIRT, 10), empty.carried());
	}

	@Test
	void notPlaceableSameKindPullsNothingWhenSlotDoesNotFit() {
		// Vanilla tryRemove refuses outright (allowModification is false) when the slot holds more than fits.
		ClickSim sim = sim(SLOT0_REJECTS, s(Items.STONE, 60), s(Items.STONE, 20));
		sim.apply(Click.left(0));
		assertStack(s(Items.STONE, 20), sim.get(0));
		assertStack(s(Items.STONE, 60), sim.carried());
	}

	@Test
	void swapNeedsCursorWithinSlotMax() {
		SlotRules max8 = new SlotRules() {
			@Override
			public boolean mayPlace(int pos, ItemStack s) {
				return true;
			}

			@Override
			public int maxStackSize(int pos, ItemStack s) {
				return Math.min(8, s.getMaxStackSize());
			}
		};
		ClickSim sim = sim(max8, s(Items.DIRT, 10), s(Items.STONE, 5));
		sim.apply(Click.left(0));
		assertStack(s(Items.STONE, 5), sim.get(0));
		assertStack(s(Items.DIRT, 10), sim.carried());
	}

	@Test
	void copiesItsInputsAndSnapshots() {
		List<ItemStack> slots = new ArrayList<>(List.of(s(Items.STONE, 5), E));
		ClickSim sim = new ClickSim(slots, SlotRules.vanilla(), E);
		ClickSim copy = sim.copy();
		sim.apply(Click.left(0));
		assertEquals(5, slots.get(0).getCount());
		assertStack(s(Items.STONE, 5), copy.get(0));
		assertStack(E, copy.carried());
		List<ItemStack> snapshot = sim.snapshot();
		sim.apply(Click.left(1));
		assertStack(E, snapshot.get(1));
	}

	@Test
	void bundleLeftClicksWithItemsInHandAreUnsafe() {
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.STONE, 5), s(Items.BUNDLE, 1), E);
		assertTrue(sim.isUnsafe(Click.left(0)));
		assertFalse(sim.isUnsafe(Click.left(1)));
		assertThrows(IllegalStateException.class, () -> sim.apply(Click.left(0)));

		ClickSim holdingBundle = sim(SlotRules.vanilla(), s(Items.BUNDLE, 1), s(Items.STONE, 5), E);
		assertTrue(holdingBundle.isUnsafe(Click.left(0)));
		assertFalse(holdingBundle.isUnsafe(Click.left(1)));
		holdingBundle.apply(Click.left(1));
		assertStack(s(Items.BUNDLE, 1), holdingBundle.get(1));
		assertStack(E, holdingBundle.carried());

		ClickSim emptyHand = sim(SlotRules.vanilla(), E, s(Items.DYED_BUNDLE.pick(DyeColor.RED), 1));
		assertFalse(emptyHand.isUnsafe(Click.left(0)));
		emptyHand.apply(Click.left(0));
		assertStack(s(Items.DYED_BUNDLE.pick(DyeColor.RED), 1), emptyHand.carried());
	}

	@Test
	void rightClickPlacesOneIntoEmptySlot() {
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.STONE, 5), E);
		sim.apply(Click.right(0));
		assertStack(s(Items.STONE, 1), sim.get(0));
		assertStack(s(Items.STONE, 4), sim.carried());
	}

	@Test
	void rightClickTakesHalfRoundedUp() {
		ClickSim sim = sim(SlotRules.vanilla(), E, s(Items.STONE, 5));
		sim.apply(Click.right(0));
		assertStack(s(Items.STONE, 2), sim.get(0));
		assertStack(s(Items.STONE, 3), sim.carried());
	}

	@Test
	void rightClickAddsOneToSameKind() {
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.STONE, 5), s(Items.STONE, 10));
		sim.apply(Click.right(0));
		assertStack(s(Items.STONE, 11), sim.get(0));
		assertStack(s(Items.STONE, 4), sim.carried());
	}

	@Test
	void rightClickSwapsDifferentKindsIncludingBundles() {
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.DIRT, 3), s(Items.STONE, 5), s(Items.BUNDLE, 1));
		sim.apply(Click.right(0));
		assertStack(s(Items.DIRT, 3), sim.get(0));
		assertStack(s(Items.STONE, 5), sim.carried());
		assertFalse(sim.isUnsafe(Click.right(1)));
		sim.apply(Click.right(1));
		assertStack(s(Items.STONE, 5), sim.get(1));
		assertStack(s(Items.BUNDLE, 1), sim.carried());
		assertFalse(sim.isUnsafe(Click.right(0)));
		sim.apply(Click.right(0));
		assertStack(s(Items.BUNDLE, 1), sim.get(0));
		assertStack(s(Items.DIRT, 3), sim.carried());
	}

	@Test
	void bundleRightClicksTheHooksInterceptAreUnsafe() {
		ClickSim holdingBundle = sim(SlotRules.vanilla(), s(Items.BUNDLE, 1), E);
		assertTrue(holdingBundle.isUnsafe(Click.right(0)));
		ClickSim emptyHand = sim(SlotRules.vanilla(), E, s(Items.BUNDLE, 1));
		assertTrue(emptyHand.isUnsafe(Click.right(0)));
		assertThrows(IllegalStateException.class, () -> emptyHand.apply(Click.right(0)));
	}

	@Test
	void bundleSelectedItemResetIsNotPartOfItsIdentity() {
		ItemStack bundle = s(Items.BUNDLE, 1);
		bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStackTemplate(Items.STONE, 3))));
		BundleItem.toggleSelectedItem(bundle, 0);
		assertEquals(0, BundleItem.getSelectedItemIndex(bundle));
		ClickSim sim = sim(SlotRules.vanilla(), E, bundle);
		sim.apply(Click.left(0));
		// vanilla overrideOtherStackedOnMe calls toggleSelectedItem(self, -1) and then lets the pick-up happen
		assertEquals(-1, BundleItem.getSelectedItemIndex(sim.carried()));
		assertStack(bundle, sim.carried());
	}

	@Test
	void clickRejectsOtherButtons() {
		assertThrows(IllegalArgumentException.class, () -> new Click(0, 2));
	}
}
