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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
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

		/** A server correction that changes only the cursor. */
		void setCarried(ItemStack stack) {
			sim = new ClickSim(sim.snapshot(), rules, stack);
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

	/**
	 * A client in front of a server that receives every click {@code lag} ticks late. Like vanilla, the client predicts
	 * a click at once and reports its predicted state with it; the server applies it to its own (possibly diverged)
	 * state, then sends a correction for every slot (and the cursor) that differs from what it believes the client
	 * shows, or the full state when the click was made against an outdated state id. Corrections arrive at once.
	 */
	private static final class LaggyView implements MenuView {
		private record Pending(int due, Click click, int stateId, List<ItemStack> slots, ItemStack carried) {
		}

		final SlotRules rules = SlotRules.vanilla();
		final int lag;
		final Deque<Pending> inFlight = new ArrayDeque<>();
		final List<Click> sent = new ArrayList<>();
		ClickSim server;
		ClickSim client;
		List<ItemStack> remote;
		ItemStack remoteCarried = ItemStack.EMPTY;
		int serverState;
		int clientState;
		int now;

		LaggyView(List<ItemStack> slots, int lag) {
			this.lag = lag;
			server = new ClickSim(slots, rules, ItemStack.EMPTY);
			client = server.copy();
			remote = server.snapshot();
		}

		/** A hopper (or another player) changes a slot on the server only; the client hears of it on the next tick. */
		void serverInsert(int slot, ItemStack stack) {
			List<ItemStack> state = server.snapshot();
			state.set(slot, stack);
			server = new ClickSim(state, rules, server.carried());
		}

		/** One server tick: apply the clicks that arrived, then send corrections. */
		void serverTick() {
			now++;
			boolean fullResync = false;
			while (!inFlight.isEmpty() && inFlight.peek().due() <= now) {
				Pending p = inFlight.poll();
				if (!server.isUnsafe(p.click())) {
					server.apply(p.click());
				}
				remote = p.slots();
				remoteCarried = p.carried();
				fullResync |= p.stateId() != serverState;
			}
			List<ItemStack> clientSlots = client.snapshot();
			ItemStack clientCarried = client.carried().copy();
			List<ItemStack> serverSlots = server.snapshot();
			boolean changed = false;
			for (int i = 0; i < serverSlots.size(); i++) {
				if (fullResync || !ItemStack.matches(serverSlots.get(i), remote.get(i))) {
					clientSlots.set(i, serverSlots.get(i).copy());
					changed = true;
				}
			}
			if (fullResync || !ItemStack.matches(server.carried(), remoteCarried)) {
				clientCarried = server.carried().copy();
				changed = true;
			}
			if (changed) {
				serverState++;
				clientState = serverState;
				client = new ClickSim(clientSlots, rules, clientCarried);
				remote = server.snapshot();
				remoteCarried = server.carried().copy();
			}
		}

		void drain() {
			while (!inFlight.isEmpty()) {
				serverTick();
			}
			serverTick();
		}

		List<ItemStack> serverTotal() {
			List<ItemStack> all = server.snapshot();
			all.add(server.carried().copy());
			return all;
		}

		@Override
		public int containerId() {
			return 7;
		}

		@Override
		public int slotCount() {
			return client.snapshot().size();
		}

		@Override
		public ItemStack get(int menuSlot) {
			return client.get(menuSlot);
		}

		@Override
		public ItemStack carried() {
			return client.carried();
		}

		@Override
		public boolean isOpen() {
			return true;
		}

		@Override
		public void click(Click click) {
			sent.add(click);
			if (!client.isUnsafe(click)) {
				client.apply(click);
			}
			inFlight.add(new Pending(now + lag, click, clientState, client.snapshot(), client.carried().copy()));
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

	@Test
	void settlePutsBackAStackALateServerCorrectionLeavesOnTheCursor() {
		LaggyView view = new LaggyView(slots(s(Items.STONE, 3), E, E), 2);
		ClickExecutor ex = new ClickExecutor();
		// move the stone from slot 0 to slot 2; a hopper fills slot 2 on the server after the last click was sent
		assertTrue(ex.start(view, List.of(Click.left(0), Click.left(2)), view.rules, regions(3, 3)));
		for (int t = 1; t <= 60; t++) {
			if (t == 3) {
				view.serverInsert(2, s(Items.DIRT, 1));
			}
			view.serverTick();
			ex.tick(1.0);
		}
		view.drain();

		assertTrue(view.server.carried().isEmpty(), "server cursor " + view.server.carried());
		assertTrue(view.client.carried().isEmpty());
		assertTrue(Counts.same(slots(s(Items.STONE, 3), s(Items.DIRT, 1)), view.serverTotal()));
	}

	@Test
	void organizeOnALaggingDivergedServerConservesItemsAndEmptiesTheCursor() {
		Item[] items = {Items.STONE, Items.DIRT, Items.APPLE, Items.OAK_LOG};
		List<ItemStack> start = new ArrayList<>();
		for (int i = 0; i < 27 + 36; i++) {
			start.add(i < 27 && i % 3 != 1 ? s(items[i % items.length], 1 + (i * 7) % 40) : E);
		}
		MenuRegions regions = regions(27, 63);
		List<Click> plan = ClickPlanner.plan(Operation.ORGANIZE_CONTAINER, regions, start, SlotRules.vanilla(), ORDER);
		for (int lag = 1; lag <= 4; lag++) {
			for (int hopperTick = 1; hopperTick <= 30; hopperTick += 3) {
				for (int hopperSlot = 0; hopperSlot < 27; hopperSlot += 4) {
					LaggyView view = new LaggyView(start, lag);
					ClickExecutor ex = new ClickExecutor();
					assertTrue(ex.start(view, plan, view.rules, regions));
					ItemStack inserted = s(Items.GOLD_INGOT, 1);
					for (int t = 1; t <= 400; t++) {
						if (t == hopperTick) {
							ItemStack there = view.server.get(hopperSlot);
							if (there.isEmpty()) {
								view.serverInsert(hopperSlot, inserted.copy());
							} else {
								inserted = ItemStack.EMPTY;
							}
						}
						view.serverTick();
						ex.tick(1.0);
					}
					view.drain();
					String where = "lag " + lag + ", hopper at tick " + hopperTick + " into slot " + hopperSlot;
					List<ItemStack> before = new ArrayList<>(start);
					before.add(inserted);
					assertTrue(view.server.carried().isEmpty(), where + ": server cursor " + view.server.carried());
					assertTrue(Counts.same(before, view.serverTotal()), where);
				}
			}
		}
	}

	@Test
	void closingMidRunWithACursorStackSendsNoClick() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E, E), E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, List.of(Click.left(0), Click.left(2)), view.rules, regions(3, 3));
		ex.tick(1.0);
		assertFalse(view.carried().isEmpty());
		view.open = false;
		ex.tick(1.0);
		assertFalse(ex.isBusy());
		assertEquals(List.of(Click.left(0)), view.sent);
	}

	@Test
	void stopSendsNoClickEvenWhileTheMenuStillLooksOpen() {
		// the inventory menu (id 0) stays the player's menu while its screen is being removed
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E, E), E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, List.of(Click.left(0), Click.left(2)), view.rules, regions(3, 3));
		ex.tick(1.0);
		ex.stop();
		for (int i = 0; i < ClickExecutor.SETTLE_TICKS + 5; i++) {
			ex.tick(1.0);
		}
		assertFalse(ex.isBusy());
		assertEquals(List.of(Click.left(0)), view.sent);
		assertEquals(3, view.carried().getCount());
	}

	@Test
	void stopEndsTheSettlePhaseWithoutClicking() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, List.of(Click.left(0), Click.left(1)), view.rules, regions(1, 2));
		ex.tick(2.0);
		assertTrue(ex.isBusy());
		view.setCarried(s(Items.DIRT, 1));
		ex.stop();
		ex.tick(1.0);
		assertFalse(ex.isBusy());
		assertEquals(2, view.sent.size());
	}

	@Test
	void abortsOnACursorOnlyMismatchAndPutsTheCursorBack() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E, E), E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, List.of(Click.left(0), Click.left(2)), view.rules, regions(3, 3));
		ex.tick(1.0);
		// slots still match; only the cursor was corrected (the server had one stone less)
		view.setCarried(s(Items.STONE, 2));
		ex.tick(1.0);
		assertFalse(ex.isRunning());
		assertEquals(List.of(Click.left(0), Click.left(0)), view.sent);
		assertTrue(view.carried().isEmpty());
		assertEquals(2, view.get(0).getCount());
	}

	@Test
	void toleratesABundleSelectedIndexThatDiffersFromTheExpectation() {
		ItemStack bundle = s(Items.BUNDLE, 1);
		bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStackTemplate(Items.STONE, 3))));
		FakeView view = new FakeView(slots(bundle, E), E);
		ClickExecutor ex = new ClickExecutor();
		assertTrue(ex.start(view, List.of(Click.left(0), Click.left(1)), view.rules, regions(1, 2)));
		BundleItem.toggleSelectedItem(view.get(0), 0);
		ex.tick(1.0);
		assertEquals(-1, BundleItem.getSelectedItemIndex(view.carried()));
		BundleItem.toggleSelectedItem(view.carried(), 0);
		ex.tick(1.0);
		assertEquals(List.of(Click.left(0), Click.left(1)), view.sent);
		assertTrue(view.carried().isEmpty());
		assertTrue(ItemStack.matches(bundle, view.get(1)));
	}

	@Test
	void settlingBlocksNewRunsUntilItEnds() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, List.of(Click.left(0), Click.left(1)), view.rules, regions(1, 2));
		ex.tick(2.0);
		assertFalse(ex.isRunning());
		assertTrue(ex.isBusy());
		assertFalse(ex.start(view, List.of(Click.left(1), Click.left(0)), view.rules, regions(1, 2)));
		for (int i = 0; i < ClickExecutor.SETTLE_TICKS; i++) {
			ex.tick(1.0);
		}
		assertFalse(ex.isBusy());
		assertTrue(ex.start(view, List.of(Click.left(1), Click.left(0)), view.rules, regions(1, 2)));
	}

	@Test
	void settleEndsWhenTheMenuCloses() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, List.of(Click.left(0), Click.left(1)), view.rules, regions(1, 2));
		ex.tick(2.0);
		view.setCarried(s(Items.DIRT, 1));
		view.open = false;
		ex.tick(1.0);
		assertFalse(ex.isBusy());
		assertEquals(2, view.sent.size());
	}

	@Test
	void settlePutsALateCursorStackIntoAnEmptySlot() {
		FakeView view = new FakeView(slots(s(Items.STONE, 3), E), E);
		ClickExecutor ex = new ClickExecutor();
		ex.start(view, List.of(Click.left(0), Click.left(1)), view.rules, regions(1, 2));
		ex.tick(2.0);
		view.setCarried(s(Items.DIRT, 1));
		ex.tick(1.0);
		assertEquals(Click.left(0), view.sent.get(2));
		assertTrue(view.carried().isEmpty());
		assertTrue(ItemStack.matches(s(Items.DIRT, 1), view.get(0)));
	}
}
