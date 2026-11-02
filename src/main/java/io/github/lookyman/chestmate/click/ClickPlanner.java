package io.github.lookyman.chestmate.click;

import io.github.lookyman.chestmate.core.Counts;
import io.github.lookyman.chestmate.core.Layouts;
import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import io.github.lookyman.chestmate.core.SlotRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Plans the left-button PICKUP clicks that reach the {@link Layouts} target using only the cursor, for servers
 * without Chestmate. Never shift-clicks, never clicks outside the operation's regions, never plans a click
 * {@link ClickSim#isUnsafe unsafe} (bundles): if the target can't be reached without one, the plan is empty.
 */
public final class ClickPlanner {
	private ClickPlanner() {
	}

	/** Thrown internally when the target can't be reached without an unsafe click. */
	private static final class Unreachable extends Exception {
		Unreachable() {
			super(null, null, false, false);
		}
	}

	/** A sim plus the clicks applied to it. */
	private static final class Recorder {
		final ClickSim sim;
		final List<Click> clicks = new ArrayList<>();

		Recorder(ClickSim sim) {
			this.sim = sim;
		}

		void click(int menuSlot) throws Unreachable {
			Click c = new Click(menuSlot);
			if (sim.isUnsafe(c)) {
				throw new Unreachable();
			}
			sim.apply(c);
			clicks.add(c);
		}

		ItemStack get(int menuSlot) {
			return sim.get(menuSlot);
		}
	}

	/**
	 * The clicks that perform {@code op}; empty when there is nothing to do or the target is unreachable without an
	 * unsafe click. Precondition: the cursor is empty. Throws {@link IllegalStateException} if the planned clicks
	 * don't reproduce the target exactly (a bug; callers treat it as "do nothing").
	 */
	public static List<Click> plan(
		Operation op, MenuRegions regions, List<ItemStack> menuSlots, SlotRules menuRules, Comparator<ItemStack> order
	) {
		if (!op.supportedBy(regions)) {
			return List.of();
		}
		Recorder rec = new Recorder(new ClickSim(menuSlots, menuRules, ItemStack.EMPTY));
		List<Integer> region;
		List<ItemStack> regionTarget;
		List<Integer> otherRegion = null;
		List<ItemStack> otherTarget = null;
		try {
			switch (op) {
				case ORGANIZE_CONTAINER, ORGANIZE_INVENTORY -> {
					region = op == Operation.ORGANIZE_CONTAINER ? regions.container() : regions.main();
					regionTarget = Layouts.organize(pick(menuSlots, region), menuRules.forRegion(region), order);
					organize(rec, region, regionTarget, menuRules);
				}
				case PUT_IN, TAKE_OUT -> {
					List<Integer> from = op == Operation.PUT_IN ? regions.main() : regions.container();
					List<Integer> to = op == Operation.PUT_IN ? regions.container() : regions.main();
					Layouts.Transfer target = Layouts.transfer(pick(menuSlots, from), pick(menuSlots, to),
						menuRules.forRegion(to));
					transfer(rec, from, to, menuRules);
					region = from;
					regionTarget = target.from();
					otherRegion = to;
					otherTarget = target.to();
				}
				default -> throw new IllegalArgumentException("unknown operation " + op);
			}
		} catch (Unreachable e) {
			return List.of();
		}

		List<Click> clicks = List.copyOf(rec.clicks);
		verify(clicks, menuSlots, menuRules, region, regionTarget, otherRegion, otherTarget);
		return clicks;
	}

	/** Merge partials with the cursor, then permute the stacks into place. */
	private static void organize(Recorder rec, List<Integer> region, List<ItemStack> target, SlotRules rules)
		throws Unreachable {
		merge(rec, region, rules);
		permute(rec, region, target);
	}

	/** Until every kind has at most one non-full stack: pick up the last non-full one, top up earlier ones. */
	private static void merge(Recorder rec, List<Integer> region, SlotRules rules) throws Unreachable {
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int a = 0; a < region.size() && !changed; a++) {
				ItemStack kind = rec.get(region.get(a));
				if (kind.isEmpty() || kind.getCount() >= chunkMax(kind, region, rules)) {
					continue;
				}
				int max = chunkMax(kind, region, rules);
				List<Integer> partials = new ArrayList<>();
				for (int b = a; b < region.size(); b++) {
					ItemStack other = rec.get(region.get(b));
					if (!other.isEmpty() && other.getCount() < max && ItemStack.isSameItemSameComponents(kind, other)) {
						partials.add(region.get(b));
					}
				}
				if (partials.size() < 2) {
					continue;
				}
				int source = partials.getLast();
				rec.click(source);
				for (int k = 0; k < partials.size() - 1 && !rec.sim.carried().isEmpty(); k++) {
					rec.click(partials.get(k));
				}
				if (!rec.sim.carried().isEmpty()) {
					rec.click(source);
				}
				changed = true;
			}
		}
	}

	/** Same chunk size {@link Layouts#organize} uses: the minimum max stack size over the region. */
	private static int chunkMax(ItemStack kind, List<Integer> region, SlotRules rules) {
		int max = Integer.MAX_VALUE;
		for (int pos : region) {
			max = Math.min(max, rules.maxStackSize(pos, kind));
		}
		return Math.max(1, max);
	}

	/** Puts {@code target[i]} into place for each {@code i}, holding at most one stack on the cursor. */
	private static void permute(Recorder rec, List<Integer> region, List<ItemStack> target) throws Unreachable {
		for (int i = 0; i < region.size(); i++) {
			int slotI = region.get(i);
			ItemStack want = target.get(i);
			if (sameStack(rec.get(slotI), want)) {
				continue;
			}
			int j = -1;
			for (int k = i + 1; k < region.size(); k++) {
				if (sameStack(rec.get(region.get(k)), want)) {
					j = k;
					break;
				}
			}
			if (j < 0) {
				throw new IllegalStateException("organize: no stack " + want + " left for region position " + i);
			}
			int slotJ = region.get(j);
			ItemStack have = rec.get(slotI);

			if (want.isEmpty()) {
				// Move the stack at i into the empty slot j.
				rec.click(slotI);
				rec.click(slotJ);
			} else if (!have.isEmpty() && (ClickSim.hasOverride(have) || ClickSim.hasOverride(want))) {
				// Bundles may only be dropped into empty slots: first evict i into an empty slot of the region.
				int e = firstEmpty(rec, region, i + 1);
				if (e < 0) {
					throw new Unreachable();
				}
				rec.click(slotI);
				rec.click(e);
				rec.click(slotJ);
				rec.click(slotI);
			} else if (!have.isEmpty() && ItemStack.isSameItemSameComponents(have, want)
				&& have.getCount() > want.getCount()) {
				// i holds the full stack and the partial is wanted: hold the full, top up j, drop the rest in i.
				rec.click(slotI);
				rec.click(slotJ);
				rec.click(slotI);
			} else {
				// Places into empty, swaps with another kind, or tops up i leaving the old partial on the cursor.
				rec.click(slotJ);
				rec.click(slotI);
				if (!rec.sim.carried().isEmpty()) {
					rec.click(slotJ);
				}
			}
			if (!rec.sim.carried().isEmpty() || !sameStack(rec.get(slotI), want)) {
				throw new IllegalStateException("organize: position " + i + " is " + rec.get(slotI) + ", wanted " + want);
			}
		}
	}

	private static int firstEmpty(Recorder rec, List<Integer> region, int fromIndex) {
		for (int k = fromIndex; k < region.size(); k++) {
			if (rec.get(region.get(k)).isEmpty()) {
				return region.get(k);
			}
		}
		return -1;
	}

	/**
	 * Per source slot in order, mirroring {@link Layouts#transfer}'s allocation: pick it up, top up same-kind
	 * destination stacks, then fill empty destination slots, and put any rest back into the source.
	 */
	private static void transfer(Recorder rec, List<Integer> from, List<Integer> to, SlotRules rules)
		throws Unreachable {
		for (int source : from) {
			ItemStack stack = rec.get(source);
			if (stack.isEmpty()) {
				continue;
			}
			List<Integer> destinations = new ArrayList<>();
			int left = stack.getCount();
			for (int j = 0; j < to.size() && left > 0; j++) {
				int dest = to.get(j);
				ItemStack there = rec.get(dest);
				if (there.isEmpty() || !ItemStack.isSameItemSameComponents(there, stack) || !rules.mayPlace(dest, stack)) {
					continue;
				}
				int room = Math.min(rules.maxStackSize(dest, stack) - there.getCount(), left);
				if (room > 0) {
					destinations.add(dest);
					left -= room;
				}
			}
			for (int j = 0; j < to.size() && left > 0; j++) {
				int dest = to.get(j);
				if (!rec.get(dest).isEmpty() || !rules.mayPlace(dest, stack)) {
					continue;
				}
				int room = Math.min(rules.maxStackSize(dest, stack), left);
				if (room > 0) {
					destinations.add(dest);
					left -= room;
				}
			}
			if (destinations.isEmpty()) {
				continue;
			}
			rec.click(source);
			for (int dest : destinations) {
				rec.click(dest);
			}
			if (!rec.sim.carried().isEmpty()) {
				rec.click(source);
			}
		}
	}

	/** Replays {@code clicks} on a fresh sim and checks the cursor, the targets, untouched slots and totals. */
	private static void verify(
		List<Click> clicks, List<ItemStack> menuSlots, SlotRules rules,
		List<Integer> region, List<ItemStack> regionTarget, List<Integer> otherRegion, List<ItemStack> otherTarget
	) {
		ClickSim sim = new ClickSim(menuSlots, rules, ItemStack.EMPTY);
		for (Click c : clicks) {
			if (!region.contains(c.menuSlot()) && (otherRegion == null || !otherRegion.contains(c.menuSlot()))) {
				throw new IllegalStateException("planned click outside the regions: " + c);
			}
			sim.apply(c);
		}
		if (!sim.carried().isEmpty()) {
			throw new IllegalStateException("plan leaves " + sim.carried() + " on the cursor");
		}
		List<ItemStack> after = sim.snapshot();
		List<ItemStack> expected = new ArrayList<>(menuSlots);
		for (int k = 0; k < region.size(); k++) {
			expected.set(region.get(k), regionTarget.get(k));
		}
		if (otherRegion != null) {
			for (int k = 0; k < otherRegion.size(); k++) {
				expected.set(otherRegion.get(k), otherTarget.get(k));
			}
		}
		for (int pos = 0; pos < after.size(); pos++) {
			if (!sameStack(after.get(pos), expected.get(pos))) {
				throw new IllegalStateException(
					"plan leaves slot " + pos + " as " + after.get(pos) + ", expected " + expected.get(pos));
			}
		}
		if (!Counts.same(menuSlots, after)) {
			throw new IllegalStateException("plan changes item totals");
		}
	}

	/** Same item, components and count, or both empty. */
	private static boolean sameStack(ItemStack a, ItemStack b) {
		return ItemStack.matches(a, b);
	}

	private static List<ItemStack> pick(List<ItemStack> slots, List<Integer> region) {
		List<ItemStack> picked = new ArrayList<>(region.size());
		for (int pos : region) {
			picked.add(slots.get(pos));
		}
		return picked;
	}
}
