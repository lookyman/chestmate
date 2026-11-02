package io.github.lookyman.chestmate.click;

import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.SlotRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Sends a precomputed click plan at a configured rate. Before every click the menu must still match the state the
 * plan expects (clicked slot and cursor); otherwise the run is aborted and any cursor stack is put back.
 *
 * <p>After the plan finishes or is aborted, the executor settles for {@link #SETTLE_TICKS} ticks: a late server
 * correction (hopper, another player, item pickup) can still leave a stack on the cursor, and while the same menu is
 * open it is put back into an empty slot. No new run starts while settling.
 */
public final class ClickExecutor {
	/** Ticks to watch the cursor after the last click; restarted by every put-back. */
	public static final int SETTLE_TICKS = 20;
	/** Ticks to wait after a put-back before another one, so the server's answer to it can arrive. */
	static final int PUT_BACK_COOLDOWN = 5;
	/** Put-backs per settle phase, so a server that keeps answering with a cursor stack cannot loop forever. */
	static final int MAX_PUT_BACKS = 5;

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
	/** Ticks left in the settle phase; positive only while settling (view set, not running). */
	private int settleTicks;
	private int cooldown;
	private int putBacks;

	public boolean start(MenuView view, List<Click> plan, SlotRules menuRules, MenuRegions regions) {
		if (isBusy() || plan.isEmpty() || !view.isOpen() || !view.carried().isEmpty()) {
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

	/** Whether plan clicks are still being sent. */
	public boolean isRunning() {
		return running;
	}

	/** Whether a run is sending clicks or settling; no new run starts until this is false. */
	public boolean isBusy() {
		return running || settleTicks > 0;
	}

	public void tick(double clicksPerTick) {
		if (!running) {
			if (settleTicks > 0) {
				settle();
			}
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
				startSettling();
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
	 * Stops sending the plan because the menu no longer matches. While the menu is still open, a cursor stack is put
	 * back (see {@link #putBack}) and the executor settles; if there is no slot for it, it stays on the cursor and
	 * vanilla returns it to the inventory when the menu closes.
	 */
	private void abort() {
		if (!sameMenuOpen()) {
			clear();
			return;
		}
		putBack();
		startSettling();
	}

	/**
	 * Stops at once without sending any click, e.g. because the screen is closing: vanilla's close handling returns a
	 * cursor stack to the inventory, and a click sent after the close packet could pick a stack up again.
	 */
	public void stop() {
		clear();
	}

	private void startSettling() {
		running = false;
		plan = List.of();
		expected = null;
		next = 0;
		budget = 0;
		settleTicks = SETTLE_TICKS;
		cooldown = 0;
		putBacks = 0;
	}

	/** One settle tick: put back a stack a late correction left on the cursor, then count down. */
	private void settle() {
		if (!sameMenuOpen()) {
			clear();
			return;
		}
		if (cooldown > 0) {
			cooldown--;
		} else if (!view.carried().isEmpty() && putBacks < MAX_PUT_BACKS && putBack()) {
			putBacks++;
			cooldown = PUT_BACK_COOLDOWN;
			settleTicks = SETTLE_TICKS;
		}
		if (--settleTicks <= 0) {
			clear();
		}
	}

	private boolean sameMenuOpen() {
		return view != null && view.isOpen() && view.containerId() == containerId;
	}

	/**
	 * Puts a cursor stack back with a left click on an empty slot that accepts all of it (first the slot it came from,
	 * then the first such slot of the container, then of the main inventory). Returns whether a click was sent.
	 */
	private boolean putBack() {
		if (view.carried().isEmpty()) {
			return false;
		}
		int target = findEmptyTarget(view.carried().copy());
		if (target < 0) {
			return false;
		}
		view.click(Click.left(target));
		return true;
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
		settleTicks = 0;
		cooldown = 0;
		putBacks = 0;
	}
}
