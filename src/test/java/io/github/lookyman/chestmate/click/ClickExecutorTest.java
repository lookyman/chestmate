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
import java.util.stream.IntStream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClickExecutorTest {
	private static final ItemStack E = ItemStack.EMPTY;
	private static final Comparator<ItemStack> ORDER =
		Comparator.comparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString());

	@BeforeAll
	static void boot() {
		TestBootstrap.init();
	}

	/** A menu backed by a ClickSim; "hopper" injections replace the underlying state directly. */
	private static final class FakeView implements MenuView {
		ClickSim sim;
		final SlotRules rules = SlotRules.vanilla();
		final List<Click> sent = new ArrayList<>();
		boolean open = true;
		int id = 7;

		FakeView(List<ItemStack> slots, ItemStack carried) {
			sim = new ClickSim(slots, rules, carried);
		}

		void hopperInsert(int slot, ItemStack stack) {
			List<ItemStack> state = sim.snapshot();
			state.set(slot, stack);
			sim = new ClickSim(state, rules, sim.carried());
		}

		@Override
		public int containerId() {
			return id;
		}

		@Override
		public int slotCount() {
			return sim.snapshot().size();
		}

		@Override
		public ItemStack get(int menuSlot) {
			return sim.get(menuSlot);
		}

		@Override
		public ItemStack carried() {
			return sim.carried();
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		@Override
		public void click(Click click) {
			sent.add(click);
			sim.apply(click);
		}
	}

	private static List<ItemStack> slots(ItemStack... stacks) {
		return new ArrayList<>(List.of(stacks));
	}

	private static MenuRegions regions(int container, int total) {
		return new MenuRegions(IntStream.range(0, container).boxed().toList(),
			IntStream.range(container, total).boxed().toList());
	}

	private static ItemStack s(Item item, int n) {
		return new ItemStack(item, n);
	}

	/** Ten harmless clicks: pick up and put back the stone in slot 0, five times. */
	private static List<Click> tenClicks() {
		List<Click> plan = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			plan.add(Click.left(0));
			plan.add(Click.left(0));
		}
		return plan;
	}

	@Test
	void paces20PerSecondByDefault() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), E);
		ClickExecutor ex = new ClickExecutor();
		assertTrue(ex.start(view, tenClicks(), view.rules, regions(1, 2)));
		for (int i = 0; i < 5; i++) {
			ex.tick(1.0);
		}
		assertEquals(5, view.sent.size());
		assertTrue(ex.isRunning());
	}

	@Test
	void fractionalSpeed() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, tenClicks(), view.rules, regions(1, 2));
		for (int i = 0; i < 4; i++) {
			ex.tick(0.2);
		}
		assertEquals(0, view.sent.size());
		ex.tick(0.2);
		assertEquals(1, view.sent.size());
	}

	@Test
	void refusesWithCursorStack() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), s(Items.STONE, 1));
		ClickExecutor ex = new ClickExecutor();
		assertFalse(ex.start(view, tenClicks(), view.rules, regions(1, 2)));
		ex.tick(1.0);
		assertTrue(view.sent.isEmpty());
		assertFalse(ex.isRunning());
	}

	@Test
	void abortsWhenSlotChangesMidRun() {
		List<ItemStack> start = slots(s(Items.STONE, 3), s(Items.GRAVEL, 2), E);
		FakeView view = new FakeView(start, E);
		// pick stone, park it in slot 2, pick gravel (cursor held), put it in slot 0
		List<Click> plan = List.of(Click.left(0), Click.left(2), Click.left(1), Click.left(0));
		ClickExecutor ex = new ClickExecutor();
		assertTrue(ex.start(view, plan, view.rules, regions(3, 3)));
		ex.tick(1.0);
		ex.tick(1.0);
		ex.tick(1.0);
		assertEquals(3, view.sent.size());
		assertFalse(view.carried().isEmpty());
		view.hopperInsert(0, s(Items.DIRT, 1));
		ex.tick(1.0);

		assertFalse(ex.isRunning());
		assertEquals(List.of(Click.left(0), Click.left(2), Click.left(1), Click.left(1)), view.sent);
		assertTrue(view.carried().isEmpty());
		List<ItemStack> expected = new ArrayList<>(start);
		expected.add(s(Items.DIRT, 1));
		assertTrue(Counts.same(expected, view.sim.snapshot()));
	}

	@Test
	void abortUsesAnotherEmptySlotWhenSourceIsTaken() {
		List<ItemStack> start = slots(s(Items.STONE, 3), E, E);
		FakeView view = new FakeView(start, E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, List.of(Click.left(0), Click.left(2)), view.rules, regions(3, 3));
		ex.tick(1.0);
		view.hopperInsert(0, s(Items.APPLE, 1));
		view.hopperInsert(2, s(Items.DIRT, 1));
		ex.tick(1.0);
		assertFalse(ex.isRunning());
		assertTrue(view.carried().isEmpty());
		assertEquals(2, view.sent.size());
		assertEquals(Click.left(1), view.sent.get(1));
	}

	@Test
	void abortWithNoEmptySlotLeavesCursorAlone() {
		List<ItemStack> start = slots(s(Items.STONE, 3), s(Items.DIRT, 1));
		FakeView view = new FakeView(start, E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, List.of(Click.left(0), Click.left(0)), view.rules, regions(1, 2));
		ex.tick(1.0);
		view.hopperInsert(0, s(Items.APPLE, 1));
		ex.tick(1.0);
		assertFalse(ex.isRunning());
		assertEquals(1, view.sent.size());
		assertEquals(3, view.carried().getCount());
	}

	@Test
	void abortsWhenClosed() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, tenClicks(), view.rules, regions(1, 2));
		view.open = false;
		ex.tick(1.0);
		assertFalse(ex.isRunning());
		assertTrue(view.sent.isEmpty());
	}

	@Test
	void abortsWhenContainerIdChanges() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, tenClicks(), view.rules, regions(1, 2));
		view.id = 8;
		ex.tick(1.0);
		assertFalse(ex.isRunning());
		assertTrue(view.sent.isEmpty());
	}

	@Test
	void ignoresSecondStart() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), E);
		ClickExecutor ex = new ClickExecutor();
		assertTrue(ex.start(view, tenClicks(), view.rules, regions(1, 2)));
		assertFalse(ex.start(view, tenClicks(), view.rules, regions(1, 2)));
	}

	@Test
	void emptyPlanDoesNotStart() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), E);
		assertFalse(new ClickExecutor().start(view, List.of(), view.rules, regions(1, 2)));
	}

	@Test
	void completesPlan() {
		List<ItemStack> start = new ArrayList<>();
		Item[] items = {Items.STONE, Items.DIRT, Items.APPLE, Items.OAK_LOG};
		for (int i = 0; i < 27 + 36; i++) {
			start.add(i < 27 && i % 3 != 1 ? s(items[i % items.length], 1 + (i * 7) % 40) : E);
		}
		MenuRegions regions = regions(27, 63);
		FakeView view = new FakeView(start, E);
		List<Click> plan = ClickPlanner.plan(Operation.ORGANIZE_CONTAINER, regions, start, view.rules, ORDER);
		assertFalse(plan.isEmpty());
		ClickExecutor ex = new ClickExecutor();
		assertTrue(ex.start(view, plan, view.rules, regions));
		int ticks = 0;
		while (ex.isRunning() && ticks++ < 10_000) {
			ex.tick(2.5);
		}
		assertFalse(ex.isRunning());
		assertEquals(plan, view.sent);
		assertTrue(view.carried().isEmpty());
		List<ItemStack> after = view.sim.snapshot();
		List<ItemStack> target = Layouts.organize(
			regions.container().stream().map(start::get).toList(), view.rules.forRegion(regions.container()), ORDER);
		for (int i = 0; i < 27; i++) {
			assertTrue(ItemStack.matches(target.get(i), after.get(i)), "slot " + i);
		}
	}
}
