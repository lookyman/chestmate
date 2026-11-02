package io.github.lookyman.chestmate.click;

import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.SlotRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Sends a precomputed click plan at a configured rate. Before every click the menu must still match the state the
 * plan expects (clicked slot and cursor); otherwise the run is aborted and any cursor stack is put back.
 */
public final class ClickExecutor {
	private MenuView view;
	private List<Click> plan = List.of();
	private int next;
	private int containerId;
	private ClickSim expected;
	private SlotRules rules;
	private MenuRegions regions;
	private double budget;
	/** Menu slot the current cursor stack was picked up from, or -1. */
	private int source = -1;
	private boolean running;

	public boolean start(MenuView view, List<Click> plan, SlotRules menuRules, MenuRegions regions) {
		if (running || plan.isEmpty() || !view.isOpen() || !view.carried().isEmpty()) {
			return false;
		}
		List<ItemStack> slots = new ArrayList<>(view.slotCount());
		for (int i = 0; i < view.slotCount(); i++) {
			slots.add(view.get(i).copy());
		}
		this.expected = new ClickSim(slots, menuRules, ItemStack.EMPTY);
		this.view = view;
		this.plan = List.copyOf(plan);
		this.rules = menuRules;
		this.regions = regions;
		this.containerId = view.containerId();
		this.next = 0;
		this.budget = 0;
		this.source = -1;
		this.running = true;
		return true;
	}

	public boolean isRunning() {
		return running;
	}

	public void tick(double clicksPerTick) {
		if (!running) {
			return;
		}
		budget += clicksPerTick;
		while (running && budget >= 1.0) {
			budget -= 1.0;
			if (!sendNext()) {
				abort();
				return;
			}
			if (next >= plan.size()) {
				clear();
			}
		}
	}

	/** Sends the next click if the menu still matches the expectation; false means the run must be aborted. */
	private boolean sendNext() {
		if (!view.isOpen() || view.containerId() != containerId) {
			return false;
		}
		Click click = plan.get(next);
		int slot = click.menuSlot();
		if (slot < 0 || slot >= view.slotCount() || expected.isUnsafe(click)) {
			return false;
		}
		if (!ItemStack.matches(expected.get(slot), view.get(slot)) || !ItemStack.matches(expected.carried(), view.carried())) {
			return false;
		}
		if (expected.carried().isEmpty() && !expected.get(slot).isEmpty()) {
			source = slot;
		}
		view.click(click);
		expected.apply(click);
		if (expected.carried().isEmpty()) {
			source = -1;
		}
		next++;
		return true;
	}

	/**
	 * Stops the run. A cursor stack is put back with a left click on an empty slot that accepts it (first the slot it
	 * came from, then the first such slot of either region); if there is none it stays on the cursor, and vanilla returns
	 * it to the inventory when the menu closes.
	 */
	public void abort() {
		if (running && view.isOpen() && view.containerId() == containerId && !view.carried().isEmpty()) {
			ItemStack held = view.carried().copy();
			int target = findEmptyTarget(held);
			if (target >= 0) {
				view.click(Click.left(target));
			}
		}
		clear();
	}

	private int findEmptyTarget(ItemStack held) {
		if (accepts(source, held)) {
			return source;
		}
		for (List<Integer> region : List.of(regions.container(), regions.main())) {
			for (int slot : region) {
				if (accepts(slot, held)) {
					return slot;
				}
			}
		}
		return -1;
	}

	/** Whether a left click would put the whole {@code held} stack into the (empty) slot. */
	private boolean accepts(int slot, ItemStack held) {
		return slot >= 0 && slot < view.slotCount() && view.get(slot).isEmpty()
			&& rules.mayPlace(slot, held) && rules.maxStackSize(slot, held) >= held.getCount();
	}

	private void clear() {
		running = false;
		view = null;
		plan = List.of();
		expected = null;
		next = 0;
		budget = 0;
		source = -1;
	}
}
