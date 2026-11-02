package io.github.lookyman.chestmate.click;

import io.github.lookyman.chestmate.core.SlotRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;

/**
 * A mutable model of a menu under left-button PICKUP clicks, mirroring 26.3
 * {@code AbstractContainerMenu.doClick} with {@code Slot.safeInsert} / {@code Slot.tryRemove}.
 * Assumes {@code Slot.mayPickup} is true, as it is for chest, shulker box and main inventory slots.
 * Clicks that would run an item's stacked-on override (bundles) are not modelled: they are unsafe and
 * {@link #apply} refuses them.
 */
public final class ClickSim {
	private final List<ItemStack> slots;
	private final SlotRules rules;
	private ItemStack carried;

	public ClickSim(List<ItemStack> menuSlots, SlotRules menuRules, ItemStack carried) {
		this.slots = new ArrayList<>(menuSlots.size());
		for (ItemStack stack : menuSlots) {
			this.slots.add(copy(stack));
		}
		this.rules = menuRules;
		this.carried = copy(carried);
	}

	/**
	 * Whether vanilla might run {@code overrideStackedOnOther} / {@code overrideOtherStackedOnMe} for this click
	 * instead of the plain PICKUP logic. With an empty cursor the override only toggles a bundle's selected item
	 * (not part of its identity) and falls through; a bundle held over an empty slot falls through too
	 * ({@code BundleItem.overrideStackedOnOther} only acts on PRIMARY when the slot is not empty). With items in hand
	 * over a non-empty slot it may insert into or extract from a bundle.
	 */
	public boolean isUnsafe(Click c) {
		ItemStack clicked = slots.get(c.menuSlot());
		return !carried.isEmpty() && !clicked.isEmpty() && (hasOverride(carried) || hasOverride(clicked));
	}

	public void apply(Click c) {
		if (isUnsafe(c)) {
			throw new IllegalStateException("unsafe click " + c + " with " + carried + " on " + slots.get(c.menuSlot()));
		}
		int pos = c.menuSlot();
		ItemStack clicked = slots.get(pos);
		if (clicked.isEmpty()) {
			if (!carried.isEmpty()) {
				carried = safeInsert(pos, carried);
			}
		} else if (carried.isEmpty()) {
			// tryRemove(count, Integer.MAX_VALUE): the whole stack.
			carried = clicked;
			slots.set(pos, ItemStack.EMPTY);
		} else if (rules.mayPlace(pos, carried)) {
			if (ItemStack.isSameItemSameComponents(clicked, carried)) {
				carried = safeInsert(pos, carried);
			} else if (carried.getCount() <= rules.maxStackSize(pos, carried)) {
				slots.set(pos, carried);
				carried = clicked;
			}
		} else if (ItemStack.isSameItemSameComponents(clicked, carried)) {
			int maxAmount = carried.getMaxStackSize() - carried.getCount();
			// tryRemove refuses when !allowModification (= !mayPlace(slot item)) and maxAmount < slot count.
			if (!rules.mayPlace(pos, clicked) && maxAmount < clicked.getCount()) {
				return;
			}
			int amount = Math.min(clicked.getCount(), maxAmount);
			if (amount > 0) {
				clicked.shrink(amount);
				carried.grow(amount);
				if (clicked.isEmpty()) {
					slots.set(pos, ItemStack.EMPTY);
				}
			}
		}
	}

	public ItemStack get(int menuSlot) {
		return slots.get(menuSlot);
	}

	public ItemStack carried() {
		return carried;
	}

	public List<ItemStack> snapshot() {
		List<ItemStack> copy = new ArrayList<>(slots.size());
		for (ItemStack stack : slots) {
			copy.add(copy(stack));
		}
		return copy;
	}

	public ClickSim copy() {
		return new ClickSim(slots, rules, carried);
	}

	/** Mirrors {@code Slot.safeInsert(stack, stack.getCount())}; returns what is left on the cursor. */
	private ItemStack safeInsert(int pos, ItemStack input) {
		if (input.isEmpty() || !rules.mayPlace(pos, input)) {
			return input;
		}
		ItemStack slotStack = slots.get(pos);
		int transferable = Math.min(input.getCount(), rules.maxStackSize(pos, input) - slotStack.getCount());
		if (transferable <= 0) {
			return input;
		}
		if (slotStack.isEmpty()) {
			slots.set(pos, input.split(transferable));
		} else if (ItemStack.isSameItemSameComponents(slotStack, input)) {
			input.shrink(transferable);
			slotStack.grow(transferable);
		}
		return input.isEmpty() ? ItemStack.EMPTY : input;
	}

	/** Items whose class overrides the stacked-on hooks (in 26.3 only {@link BundleItem}) or carry bundle contents. */
	static boolean hasOverride(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof BundleItem || stack.has(DataComponents.BUNDLE_CONTENTS));
	}

	private static ItemStack copy(ItemStack stack) {
		return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
	}
}
