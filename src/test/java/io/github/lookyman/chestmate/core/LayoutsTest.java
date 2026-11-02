package io.github.lookyman.chestmate.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.lookyman.chestmate.TestBootstrap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LayoutsTest {
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

	private static void assertStacks(List<ItemStack> expected, List<ItemStack> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			assertTrue(ItemStack.matches(expected.get(i), actual.get(i)),
				"position " + i + ": expected " + expected.get(i) + " but was " + actual.get(i));
		}
	}

	@Test
	void organizeMergesPartials() {
		List<ItemStack> in = List.of(s(Items.STONE, 10), s(Items.DIRT, 5), s(Items.STONE, 60), E);
		List<ItemStack> out = Layouts.organize(in, SlotRules.vanilla(), ORDER);
		assertStacks(List.of(s(Items.DIRT, 5), s(Items.STONE, 64), s(Items.STONE, 6), E), out);
		assertEquals(10, in.get(0).getCount());
	}

	@Test
	void organizeKeepsComponentsApart() {
		ItemStack named = s(Items.DIAMOND_SWORD, 1);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Bob"));
		ItemStack named2 = named.copy();
		List<ItemStack> in = List.of(named, s(Items.DIAMOND_SWORD, 1), named2, E);
		List<ItemStack> out = Layouts.organize(in, SlotRules.vanilla(), ORDER);
		assertEquals(3, out.stream().filter(x -> !x.isEmpty()).count());
		assertTrue(Counts.same(in, out));
	}

	@Test
	void organizeRespectsMaxStack() {
		List<ItemStack> out = Layouts.organize(
			List.of(s(Items.ENDER_PEARL, 10), s(Items.ENDER_PEARL, 10)), SlotRules.vanilla(), ORDER);
		assertStacks(List.of(s(Items.ENDER_PEARL, 16), s(Items.ENDER_PEARL, 4)), out);
	}

	@Test
	void organizeIsIdempotent() {
		Item[] items = {Items.STONE, Items.DIRT, Items.ENDER_PEARL, Items.DIAMOND_SWORD, Items.APPLE, Items.OAK_LOG};
		List<ItemStack> in = new ArrayList<>();
		for (int i = 0; i < 54; i++) {
			in.add(s(items[i % items.length], 1 + (i * 7) % 16));
		}
		List<ItemStack> once = Layouts.organize(in, SlotRules.vanilla(), ORDER);
		assertStacks(once, Layouts.organize(once, SlotRules.vanilla(), ORDER));
		assertTrue(Counts.same(in, once));
	}

	@Test
	void transferTopsUpBeforeEmpty() {
		Layouts.Transfer t = Layouts.transfer(List.of(s(Items.STONE, 20)),
			List.of(E, s(Items.STONE, 50)), SlotRules.vanilla());
		assertStacks(List.of(s(Items.STONE, 6), s(Items.STONE, 64)), t.to());
		assertStacks(List.of(E), t.from());
	}

	@Test
	void transferLeavesWhatDoesNotFit() {
		Layouts.Transfer t = Layouts.transfer(List.of(s(Items.STONE, 5)),
			List.of(s(Items.DIRT, 64), s(Items.DIRT, 64)), SlotRules.vanilla());
		assertStacks(List.of(s(Items.STONE, 5)), t.from());
		assertStacks(List.of(s(Items.DIRT, 64), s(Items.DIRT, 64)), t.to());
	}

	@Test
	void transferHonorsMayPlace() {
		SlotRules noShulker = new SlotRules() {
			@Override
			public boolean mayPlace(int pos, ItemStack st) {
				return !st.is(Items.SHULKER_BOX);
			}

			@Override
			public int maxStackSize(int pos, ItemStack st) {
				return st.getMaxStackSize();
			}
		};
		Layouts.Transfer t = Layouts.transfer(List.of(s(Items.SHULKER_BOX, 1), s(Items.STONE, 3)),
			List.of(E, E), noShulker);
		assertStacks(List.of(s(Items.SHULKER_BOX, 1), E), t.from());
		assertStacks(List.of(s(Items.STONE, 3), E), t.to());
	}

	@Test
	void countsDetectLoss() {
		assertFalse(Counts.same(List.of(s(Items.STONE, 5)), List.of(s(Items.STONE, 4))));
		assertTrue(Counts.sameAll(List.of(List.of(s(Items.STONE, 5)), List.of(E)),
			List.of(List.of(s(Items.STONE, 2)), List.of(s(Items.STONE, 3)))));
	}
}
