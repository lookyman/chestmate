package io.github.lookyman.chestmate.click;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.lookyman.chestmate.TestBootstrap;
import io.github.lookyman.chestmate.core.Counts;
import io.github.lookyman.chestmate.core.Layouts;
import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import io.github.lookyman.chestmate.core.SlotRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClickPlannerTest {
	private static final Comparator<ItemStack> ORDER =
		Comparator.comparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
	private static final ItemStack E = ItemStack.EMPTY;

	@BeforeAll
	static void boot() {
		TestBootstrap.init();
	}

	private static ItemStack s(Item item, int n) {
		return new ItemStack(item, n);
	}

	private static List<Integer> range(int from, int toExclusive) {
		return IntStream.range(from, toExclusive).boxed().toList();
	}

	private static List<ItemStack> empties(int n) {
		List<ItemStack> list = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			list.add(E);
		}
		return list;
	}

	private static List<ItemStack> pick(List<ItemStack> slots, List<Integer> region) {
		return region.stream().map(slots::get).toList();
	}

	private static void assertStacks(List<ItemStack> expected, List<ItemStack> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			assertTrue(ItemStack.matches(expected.get(i), actual.get(i)),
				"position " + i + ": expected " + expected.get(i) + " but was " + actual.get(i));
		}
	}

	/** Applies the plan to a fresh sim, checking every click is safe, then the cursor and the totals. */
	private static ClickSim replay(List<Click> plan, List<ItemStack> slots, SlotRules rules) {
		ClickSim sim = new ClickSim(slots, rules, E);
		for (Click click : plan) {
			assertFalse(sim.isUnsafe(click), "unsafe click " + click);
			sim.apply(click);
		}
		assertTrue(sim.carried().isEmpty(), "cursor not empty: " + sim.carried());
		assertTrue(Counts.same(slots, sim.snapshot()), "item totals changed");
		return sim;
	}

	private static ClickSim organize(Operation op, MenuRegions regions, List<ItemStack> slots, SlotRules rules) {
		List<Integer> region = op == Operation.ORGANIZE_CONTAINER ? regions.container() : regions.main();
		List<Click> plan = ClickPlanner.plan(op, regions, slots, rules, ORDER);
		for (Click click : plan) {
			assertTrue(region.contains(click.menuSlot()), "click outside the region: " + click);
		}
		ClickSim sim = replay(plan, slots, rules);
		List<ItemStack> target = Layouts.organize(pick(slots, region), rules.forRegion(region), ORDER);
		assertStacks(target, pick(sim.snapshot(), region));
		return sim;
	}

	/** A chest menu: {@code rows * 9} container slots, then main 27, then hotbar 9. */
	private static MenuRegions chestRegions(int containerSize) {
		return new MenuRegions(range(0, containerSize), range(containerSize, containerSize + 27));
	}

	@Test
	void organizeSmallChest() {
		List<ItemStack> slots = empties(27 + 36);
		Item[] items = {Items.STONE, Items.DIRT, Items.APPLE, Items.OAK_LOG, Items.ENDER_PEARL, Items.DIAMOND_SWORD};
		for (int i = 0; i < 27; i++) {
			if (i % 4 == 1) {
				continue;
			}
			Item item = items[i % items.length];
			slots.set(i, s(item, 1 + (i * 5) % new ItemStack(item).getMaxStackSize()));
		}
		ClickSim sim = organize(Operation.ORGANIZE_CONTAINER, chestRegions(27), slots, SlotRules.vanilla());
		assertStacks(pick(slots, range(27, 63)), pick(sim.snapshot(), range(27, 63)));
	}

	@Test
	void organizeFullDoubleChestAndFullInventory() {
		Random random = new Random(42);
		Item[] items = {Items.STONE, Items.DIRT, Items.APPLE, Items.OAK_LOG, Items.ENDER_PEARL, Items.DIAMOND_SWORD,
			Items.COBBLESTONE, Items.SAND};
		List<ItemStack> slots = new ArrayList<>();
		for (int i = 0; i < 54 + 36; i++) {
			Item item = items[random.nextInt(items.length)];
			int max = new ItemStack(item).getMaxStackSize();
			ItemStack stack = s(item, random.nextInt(4) == 0 ? max : 1 + random.nextInt(max));
			if (item == Items.DIAMOND_SWORD && random.nextBoolean()) {
				stack.set(DataComponents.CUSTOM_NAME, Component.literal("Bob"));
			}
			slots.add(stack);
		}
		MenuRegions regions = chestRegions(54);
		ClickSim container = organize(Operation.ORGANIZE_CONTAINER, regions, slots, SlotRules.vanilla());
		assertStacks(pick(slots, range(54, 90)), pick(container.snapshot(), range(54, 90)));

		ClickSim inventory = organize(Operation.ORGANIZE_INVENTORY, regions, slots, SlotRules.vanilla());
		assertStacks(pick(slots, range(0, 54)), pick(inventory.snapshot(), range(0, 54)));
		assertStacks(pick(slots, range(81, 90)), pick(inventory.snapshot(), range(81, 90)));
	}

	@Test
	void organizeSameKindPartialBeforeFull() {
		List<ItemStack> slots = new ArrayList<>(List.of(s(Items.STONE, 6), s(Items.STONE, 64)));
		MenuRegions regions = new MenuRegions(List.of(0, 1), List.of());
		ClickSim sim = organize(Operation.ORGANIZE_CONTAINER, regions, slots, SlotRules.vanilla());
		assertStacks(List.of(s(Items.STONE, 64), s(Items.STONE, 6)), sim.snapshot());
	}

	@Test
	void organizeInventoryNeverTouchesHotbar() {
		// InventoryMenu: 0 result, 1-4 crafting, 5-8 armor, 9-35 main, 36-44 hotbar, 45 offhand.
		List<ItemStack> slots = empties(46);
		for (int i = 9; i <= 35; i++) {
			if (i % 3 != 0) {
				slots.set(i, s(i % 2 == 0 ? Items.STONE : Items.DIRT, 1 + i));
			}
		}
		for (int i = 36; i <= 44; i++) {
			slots.set(i, s(Items.STONE, 7));
		}
		slots.set(45, s(Items.DIRT, 3));
		MenuRegions regions = new MenuRegions(List.of(), range(9, 36));
		ClickSim sim = organize(Operation.ORGANIZE_INVENTORY, regions, slots, SlotRules.vanilla());
		assertStacks(pick(slots, range(36, 46)), pick(sim.snapshot(), range(36, 46)));
	}

	@Test
	void organizeKeepsComponentsApart() {
		ItemStack named = s(Items.STONE, 10);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Bob"));
		ItemStack sword = s(Items.DIAMOND_SWORD, 1);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Excalibur"));
		List<ItemStack> slots = new ArrayList<>(List.of(
			named, s(Items.STONE, 10), named.copy(), E, sword, s(Items.DIAMOND_SWORD, 1), s(Items.STONE, 30)));
		MenuRegions regions = new MenuRegions(range(0, 7), List.of());
		ClickSim sim = organize(Operation.ORGANIZE_CONTAINER, regions, slots, SlotRules.vanilla());
		long namedStone = sim.snapshot().stream()
			.filter(x -> x.is(Items.STONE) && x.has(DataComponents.CUSTOM_NAME)).count();
		assertEquals(1, namedStone);
	}

	@Test
	void organizeSmallMaxStacks() {
		List<ItemStack> slots = new ArrayList<>(List.of(
			s(Items.ENDER_PEARL, 10), s(Items.DIAMOND_SWORD, 1), s(Items.ENDER_PEARL, 10), s(Items.IRON_PICKAXE, 1),
			s(Items.ENDER_PEARL, 16), s(Items.DIAMOND_SWORD, 1), s(Items.ENDER_PEARL, 3)));
		MenuRegions regions = new MenuRegions(range(0, 7), List.of());
		ClickSim sim = organize(Operation.ORGANIZE_CONTAINER, regions, slots, SlotRules.vanilla());
		assertStacks(List.of(s(Items.DIAMOND_SWORD, 1), s(Items.DIAMOND_SWORD, 1), s(Items.ENDER_PEARL, 16),
			s(Items.ENDER_PEARL, 16), s(Items.ENDER_PEARL, 7), s(Items.IRON_PICKAXE, 1), E), sim.snapshot());
	}

	@Test
	void organizeWithBundleAndAnEmptySlot() {
		List<ItemStack> slots = new ArrayList<>(List.of(
			s(Items.BUNDLE, 1), s(Items.STONE, 64), s(Items.APPLE, 64), s(Items.STONE, 5), E));
		MenuRegions regions = new MenuRegions(range(0, 5), List.of());
		ClickSim sim = organize(Operation.ORGANIZE_CONTAINER, regions, slots, SlotRules.vanilla());
		assertStacks(List.of(s(Items.APPLE, 64), s(Items.BUNDLE, 1), s(Items.STONE, 64), s(Items.STONE, 5), E),
			sim.snapshot());
	}

	@Test
	void organizeFullRegionWithBundlesSwapsByRightClick() {
		// Double chest and inventory completely full, no empty slot anywhere, bundles out of place.
		Item[] items = {Items.STONE, Items.BUNDLE, Items.APPLE, Items.DIRT, Items.ENDER_PEARL};
		List<ItemStack> slots = new ArrayList<>();
		for (int i = 0; i < 54 + 36; i++) {
			Item item = items[(i * 7 + i / 5) % items.length];
			slots.add(s(item, new ItemStack(item).getMaxStackSize()));
		}
		slots.set(3, s(Items.DYED_BUNDLE.pick(DyeColor.BLUE), 1));
		MenuRegions regions = chestRegions(54);
		for (Operation op : List.of(Operation.ORGANIZE_CONTAINER, Operation.ORGANIZE_INVENTORY)) {
			List<Click> plan = ClickPlanner.plan(op, regions, slots, SlotRules.vanilla(), ORDER);
			assertFalse(plan.isEmpty());
			assertTrue(plan.stream().anyMatch(c -> c.button() == Click.RIGHT));
			ClickSim sim = organize(op, regions, slots, SlotRules.vanilla());
			assertStacks(pick(slots, range(81, 90)), pick(sim.snapshot(), range(81, 90)));
		}
	}

	@Test
	void putInSkipsUnplaceable() {
		// ShulkerBoxMenu: 0-26 shulker, 27-53 main, 54-62 hotbar.
		SlotRules shulkerRules = new SlotRules() {
			@Override
			public boolean mayPlace(int pos, ItemStack s) {
				return pos >= 27 || !s.is(Items.SHULKER_BOX);
			}

			@Override
			public int maxStackSize(int pos, ItemStack s) {
				return s.getMaxStackSize();
			}
		};
		List<ItemStack> slots = empties(63);
		slots.set(0, s(Items.STONE, 40));
		slots.set(27, s(Items.SHULKER_BOX, 1));
		slots.set(28, s(Items.STONE, 50));
		slots.set(30, s(Items.DIRT, 64));
		slots.set(40, s(Items.ENDER_PEARL, 16));
		slots.set(55, s(Items.APPLE, 5));
		MenuRegions regions = new MenuRegions(range(0, 27), range(27, 54));
		List<Click> plan = ClickPlanner.plan(Operation.PUT_IN, regions, slots, shulkerRules, ORDER);
		assertTrue(plan.stream().noneMatch(c -> c.menuSlot() == 27), "shulker box was clicked");
		ClickSim sim = replay(plan, slots, shulkerRules);
		List<ItemStack> after = sim.snapshot();
		assertStacks(List.of(s(Items.STONE, 64), s(Items.STONE, 26), s(Items.DIRT, 64), s(Items.ENDER_PEARL, 16)),
			after.subList(0, 4));
		assertStacks(List.of(s(Items.SHULKER_BOX, 1)), after.subList(27, 28));
		assertEquals(1, pick(after, range(27, 54)).stream().filter(x -> !x.isEmpty()).count());
		assertStacks(pick(slots, range(54, 63)), pick(after, range(54, 63)));
	}

	@Test
	void takeOutNeverIntoHotbar() {
		// Double chest: 0-53 container, 54-80 main, 81-89 hotbar.
		List<ItemStack> slots = empties(90);
		for (int i = 0; i < 30; i++) {
			slots.set(i, s(Items.STONE, 64));
		}
		for (int i = 54; i < 81 - 3; i++) {
			slots.set(i, s(Items.DIRT, 64));
		}
		MenuRegions regions = chestRegions(54);
		List<Click> plan = ClickPlanner.plan(Operation.TAKE_OUT, regions, slots, SlotRules.vanilla(), ORDER);
		ClickSim sim = replay(plan, slots, SlotRules.vanilla());
		List<ItemStack> after = sim.snapshot();
		assertEquals(27, pick(after, range(0, 54)).stream().filter(x -> !x.isEmpty()).count());
		assertTrue(pick(after, range(54, 81)).stream().allMatch(x -> !x.isEmpty()));
		assertTrue(pick(after, range(81, 90)).stream().allMatch(ItemStack::isEmpty));
		assertStacks(List.of(s(Items.STONE, 64), s(Items.STONE, 64), s(Items.STONE, 64)), after.subList(78, 81));
	}

	@Test
	void transferMatchesLayouts() {
		List<ItemStack> slots = empties(27 + 36);
		slots.set(2, s(Items.STONE, 20));
		slots.set(5, s(Items.BUNDLE, 1));
		slots.set(27, s(Items.STONE, 60));
		slots.set(29, s(Items.ENDER_PEARL, 9));
		slots.set(33, s(Items.DIRT, 1));
		MenuRegions regions = chestRegions(27);
		for (Operation op : List.of(Operation.PUT_IN, Operation.TAKE_OUT)) {
			List<Integer> from = op == Operation.PUT_IN ? regions.main() : regions.container();
			List<Integer> to = op == Operation.PUT_IN ? regions.container() : regions.main();
			List<Click> plan = ClickPlanner.plan(op, regions, slots, SlotRules.vanilla(), ORDER);
			ClickSim sim = replay(plan, slots, SlotRules.vanilla());
			Layouts.Transfer expected = Layouts.transfer(pick(slots, from), pick(slots, to), SlotRules.vanilla().forRegion(to));
			assertStacks(expected.from(), pick(sim.snapshot(), from));
			assertStacks(expected.to(), pick(sim.snapshot(), to));
		}
	}

	@Test
	void randomLayoutsReachTheTarget() {
		Item[] items = {Items.STONE, Items.DIRT, Items.APPLE, Items.ENDER_PEARL, Items.DIAMOND_SWORD, Items.BUNDLE,
			Items.SHULKER_BOX};
		SlotRules noShulkerInContainer = new SlotRules() {
			@Override
			public boolean mayPlace(int pos, ItemStack s) {
				return pos >= 27 || !s.is(Items.SHULKER_BOX);
			}

			@Override
			public int maxStackSize(int pos, ItemStack s) {
				return s.getMaxStackSize();
			}
		};
		for (int seed = 0; seed < 300; seed++) {
			Random random = new Random(seed);
			List<ItemStack> slots = empties(27 + 36);
			for (int i = 0; i < 63; i++) {
				if (random.nextInt(10) < 3) {
					continue;
				}
				Item item = items[random.nextInt(items.length)];
				if (item == Items.SHULKER_BOX && i < 27) {
					item = Items.SAND;
				}
				ItemStack stack = s(item, 1 + random.nextInt(new ItemStack(item).getMaxStackSize()));
				if (random.nextInt(8) == 0) {
					stack.set(DataComponents.CUSTOM_NAME, Component.literal("n"));
				}
				slots.set(i, stack);
			}
			MenuRegions regions = chestRegions(27);
			for (Operation op : Operation.values()) {
				List<Click> plan = ClickPlanner.plan(op, regions, slots, noShulkerInContainer, ORDER);
				ClickSim sim = replay(plan, slots, noShulkerInContainer);
				assertStacks(pick(slots, range(54, 63)), pick(sim.snapshot(), range(54, 63)));
				if (op == Operation.ORGANIZE_CONTAINER || op == Operation.ORGANIZE_INVENTORY) {
					List<Integer> region = op == Operation.ORGANIZE_CONTAINER ? regions.container() : regions.main();
					List<ItemStack> target = Layouts.organize(pick(slots, region),
						noShulkerInContainer.forRegion(region), ORDER);
					assertStacks(target, pick(sim.snapshot(), region));
				} else {
					List<Integer> from = op == Operation.PUT_IN ? regions.main() : regions.container();
					List<Integer> to = op == Operation.PUT_IN ? regions.container() : regions.main();
					Layouts.Transfer expected = Layouts.transfer(pick(slots, from), pick(slots, to),
						noShulkerInContainer.forRegion(to));
					assertStacks(expected.from(), pick(sim.snapshot(), from));
					assertStacks(expected.to(), pick(sim.snapshot(), to));
				}
			}
		}
	}

	@Test
	void alreadyOrganizedPlansNothing() {
		List<ItemStack> slots = empties(27 + 36);
		slots.set(0, s(Items.APPLE, 64));
		slots.set(1, s(Items.STONE, 64));
		slots.set(2, s(Items.STONE, 6));
		MenuRegions regions = chestRegions(27);
		assertEquals(List.of(), ClickPlanner.plan(Operation.ORGANIZE_CONTAINER, regions, slots, SlotRules.vanilla(), ORDER));
		// nothing to move either way
		List<ItemStack> noMain = empties(27 + 36);
		assertEquals(List.of(), ClickPlanner.plan(Operation.PUT_IN, regions, noMain, SlotRules.vanilla(), ORDER));
	}
}
