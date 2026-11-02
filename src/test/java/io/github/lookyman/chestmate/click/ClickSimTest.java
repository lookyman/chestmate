package io.github.lookyman.chestmate.click;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.lookyman.chestmate.TestBootstrap;
import io.github.lookyman.chestmate.core.SlotRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
		sim.apply(new Click(0));
		assertStack(E, sim.get(0));
		assertStack(s(Items.STONE, 40), sim.carried());
	}

	@Test
	void placeIntoEmptySlotUpToMax() {
		// Ender pearls stack to 16; a cursor of 20 can only exist via commands, but the rule is min(count, max).
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.ENDER_PEARL, 20), E);
		sim.apply(new Click(0));
		assertStack(s(Items.ENDER_PEARL, 16), sim.get(0));
		assertStack(s(Items.ENDER_PEARL, 4), sim.carried());
	}

	@Test
	void addWhatFitsToSameKind() {
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.STONE, 30), s(Items.STONE, 50));
		sim.apply(new Click(0));
		assertStack(s(Items.STONE, 64), sim.get(0));
		assertStack(s(Items.STONE, 16), sim.carried());
	}

	@Test
	void swapDifferentKinds() {
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.DIRT, 3), s(Items.STONE, 5));
		sim.apply(new Click(0));
		assertStack(s(Items.DIRT, 3), sim.get(0));
		assertStack(s(Items.STONE, 5), sim.carried());
	}

	@Test
	void notPlaceableSameKindPullsIntoCursor() {
		ClickSim sim = sim(SLOT0_REJECTS, s(Items.STONE, 10), s(Items.STONE, 20));
		sim.apply(new Click(0));
		assertStack(E, sim.get(0));
		assertStack(s(Items.STONE, 30), sim.carried());
	}

	@Test
	void notPlaceableDifferentKindDoesNothing() {
		ClickSim sim = sim(SLOT0_REJECTS, s(Items.DIRT, 10), s(Items.STONE, 20), E);
		sim.apply(new Click(0));
		assertStack(s(Items.STONE, 20), sim.get(0));
		assertStack(s(Items.DIRT, 10), sim.carried());
		// and an unplaceable empty slot keeps the cursor too
		ClickSim empty = sim(SLOT0_REJECTS, s(Items.DIRT, 10), E);
		empty.apply(new Click(0));
		assertStack(E, empty.get(0));
		assertStack(s(Items.DIRT, 10), empty.carried());
	}

	@Test
	void notPlaceableSameKindPullsNothingWhenSlotDoesNotFit() {
		// Vanilla tryRemove refuses outright (allowModification is false) when the slot holds more than fits.
		ClickSim sim = sim(SLOT0_REJECTS, s(Items.STONE, 60), s(Items.STONE, 20));
		sim.apply(new Click(0));
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
		sim.apply(new Click(0));
		assertStack(s(Items.STONE, 5), sim.get(0));
		assertStack(s(Items.DIRT, 10), sim.carried());
	}

	@Test
	void copiesItsInputsAndSnapshots() {
		List<ItemStack> slots = new ArrayList<>(List.of(s(Items.STONE, 5), E));
		ClickSim sim = new ClickSim(slots, SlotRules.vanilla(), E);
		ClickSim copy = sim.copy();
		sim.apply(new Click(0));
		assertEquals(5, slots.get(0).getCount());
		assertStack(s(Items.STONE, 5), copy.get(0));
		assertStack(E, copy.carried());
		List<ItemStack> snapshot = sim.snapshot();
		sim.apply(new Click(1));
		assertStack(E, snapshot.get(1));
	}

	@Test
	void bundleClicksWithItemsInHandAreUnsafe() {
		ClickSim sim = sim(SlotRules.vanilla(), s(Items.STONE, 5), s(Items.BUNDLE, 1), E);
		assertTrue(sim.isUnsafe(new Click(0)));
		assertFalse(sim.isUnsafe(new Click(1)));
		assertThrows(IllegalStateException.class, () -> sim.apply(new Click(0)));

		ClickSim holdingBundle = sim(SlotRules.vanilla(), s(Items.BUNDLE, 1), s(Items.STONE, 5), E);
		assertTrue(holdingBundle.isUnsafe(new Click(0)));
		assertFalse(holdingBundle.isUnsafe(new Click(1)));
		holdingBundle.apply(new Click(1));
		assertStack(s(Items.BUNDLE, 1), holdingBundle.get(1));
		assertStack(E, holdingBundle.carried());

		ClickSim emptyHand = sim(SlotRules.vanilla(), E, s(Items.DYED_BUNDLE.pick(DyeColor.RED), 1));
		assertFalse(emptyHand.isUnsafe(new Click(0)));
		emptyHand.apply(new Click(0));
		assertStack(s(Items.DYED_BUNDLE.pick(DyeColor.RED), 1), emptyHand.carried());
	}
}
