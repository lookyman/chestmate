package io.github.lookyman.chestmate.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.lookyman.chestmate.TestBootstrap;
import io.github.lookyman.chestmate.click.Click;
import io.github.lookyman.chestmate.click.ClickPlanner;
import io.github.lookyman.chestmate.click.ClickSim;
import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import io.github.lookyman.chestmate.core.SlotRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The click path (planner replayed on a ClickSim) and the server path end in the same slots for every operation. */
class ClickServerParityTest {
	private static final Comparator<ItemStack> ORDER =
		Comparator.comparingInt(s -> BuiltInRegistries.ITEM.getId(s.getItem()));
	private static final int CONTAINER = 27;
	private static final int MENU_SIZE = CONTAINER + 36;

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.init();
	}

	/** Chest-like or shulker-like menu: 27 container slots, 27 main slots, 9 hotbar slots. */
	private static final class TestMenu extends AbstractContainerMenu {
		final Container storage = new SimpleContainer(CONTAINER);
		final Container player = new SimpleContainer(41);

		TestMenu(boolean shulker, List<ItemStack> contents) {
			super(null, 3);
			for (int i = 0; i < CONTAINER; i++) {
				addSlot(shulker ? new ShulkerBoxSlot(storage, i, 0, 0) : new Slot(storage, i, 0, 0));
			}
			for (int i = 9; i < 36; i++) {
				addSlot(new Slot(player, i, 0, 0));
			}
			for (int i = 0; i < 9; i++) {
				addSlot(new Slot(player, i, 0, 0));
			}
			for (int i = 0; i < contents.size(); i++) {
				getSlot(i).set(contents.get(i).copy());
			}
		}

		MenuRegions regions() {
			return MenuRegions.classify(slots, c -> c == player, true);
		}

		List<ItemStack> snapshot() {
			List<ItemStack> out = new ArrayList<>();
			for (Slot slot : slots) {
				out.add(slot.getItem().copy());
			}
			return out;
		}

		@Override
		public ItemStack quickMoveStack(Player p, int i) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player p) {
			return true;
		}
	}

	private static ItemStack s(Item item, int n) {
		return new ItemStack(item, n);
	}

	private static ItemStack fullBundle() {
		ItemStack bundle = s(Items.BUNDLE, 1);
		bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStackTemplate(Items.STONE, 3))));
		return bundle;
	}

	/** A layout with partial stacks, unstackables, bundles and shulker boxes in both regions and some free slots. */
	private static List<ItemStack> layout(long seed, boolean shulker) {
		Random random = new Random(seed);
		ItemStack[] kinds = {s(Items.STONE, 0), s(Items.DIRT, 0), s(Items.ENDER_PEARL, 0), s(Items.DIAMOND_SWORD, 0),
			s(Items.BUNDLE, 0), fullBundle(), s(Items.DYED_BUNDLE.pick(DyeColor.BLUE), 0), s(Items.SHULKER_BOX, 0),
			s(Items.APPLE, 0)};
		List<ItemStack> slots = new ArrayList<>();
		for (int i = 0; i < MENU_SIZE; i++) {
			if (random.nextInt(3) == 0) {
				slots.add(ItemStack.EMPTY);
				continue;
			}
			ItemStack kind = kinds[random.nextInt(kinds.length)];
			if (shulker && i < CONTAINER && kind.is(Items.SHULKER_BOX)) {
				kind = kinds[0];
			}
			slots.add(kind.copyWithCount(1 + random.nextInt(kind.getMaxStackSize())));
		}
		return slots;
	}

	/** Returns whether the operation changed anything, so the test cannot pass vacuously. */
	private static boolean assertSameResult(boolean shulker, List<ItemStack> start, Operation op, String where) {
		TestMenu serverMenu = new TestMenu(shulker, start);
		ServerOperations.Result result =
			ServerOperations.apply(serverMenu, serverMenu.regions(), serverMenu.containerId, op, ItemStack.EMPTY, ORDER);
		List<ItemStack> serverSlots = serverMenu.snapshot();

		TestMenu clientMenu = new TestMenu(shulker, start);
		SlotRules rules = SlotRules.of(clientMenu);
		List<Click> plan = ClickPlanner.plan(op, clientMenu.regions(), clientMenu.snapshot(), rules, ORDER);
		ClickSim sim = new ClickSim(clientMenu.snapshot(), rules, ItemStack.EMPTY);
		plan.forEach(sim::apply);

		assertTrue(sim.carried().isEmpty(), where + ": cursor " + sim.carried());
		for (int i = 0; i < MENU_SIZE; i++) {
			assertTrue(ItemStack.matches(serverSlots.get(i), sim.get(i)),
				where + " slot " + i + ": server " + serverSlots.get(i) + ", clicks " + sim.get(i));
		}
		return result == ServerOperations.Result.APPLIED;
	}

	@Test
	void clickPathAndServerPathAgree() {
		int applied = 0;
		for (boolean shulker : new boolean[] {false, true}) {
			for (long seed = 1; seed <= 25; seed++) {
				List<ItemStack> start = layout(seed, shulker);
				for (Operation op : Operation.values()) {
					applied += assertSameResult(shulker, start, op, (shulker ? "shulker" : "chest") + " seed " + seed + " " + op) ? 1 : 0;
				}
			}
		}
		assertTrue(applied >= 150, "only " + applied + " of 200 cases changed anything");
	}
}
