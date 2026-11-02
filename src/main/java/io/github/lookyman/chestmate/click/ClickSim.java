package io.github.lookyman.chestmate.click;

import io.github.lookyman.chestmate.core.SlotRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;

/**
 * A mutable model of a menu under PICKUP clicks (left and right button), mirroring 26.3
 * {@code AbstractContainerMenu.doClick} with {@code Slot.safeInsert} / {@code Slot.tryRemove}.
 * Assumes {@code Slot.mayPickup} is true, as it is for chest, shulker box and main inventory slots.
 * Clicks that an item's stacked-on override (bundles) would intercept are not modelled: they are
 * {@link #isUnsafe unsafe} and {@link #apply} refuses them.
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
	 * Whether {@code BundleItem.overrideStackedOnOther} (cursor) or {@code overrideOtherStackedOnMe} (slot) would
	 * intercept this click instead of the plain PICKUP logic:
	 * left with a bundle on the cursor over a non-empty slot, left with items on the cursor over a bundle,
	 * right with a bundle on the cursor over an empty slot, right with an empty cursor over a bundle.
	 */
	public boolean isUnsafe(Click c) {
		ItemStack clicked = slots.get(c.menuSlot());
		if (c.button() == Click.LEFT) {
			return !carried.isEmpty() && !clicked.isEmpty() && (hasOverride(carried) || hasOverride(clicked));
		}
		return hasOverride(carried) && clicked.isEmpty() || carried.isEmpty() && hasOverride(clicked);
	}

	public void apply(Click c) {
		if (isUnsafe(c)) {
			throw new IllegalStateException("unsafe click " + c + " with " + carried + " on " + slots.get(c.menuSlot()));
		}
		boolean left = c.button() == Click.LEFT;
		int pos = c.menuSlot();
		ItemStack clicked = slots.get(pos);
		// overrideOtherStackedOnMe's fall-through paths reset the bundle's selected item (not part of its identity).
		if (clicked.getItem() instanceof BundleItem && carried.isEmpty() == left) {
			BundleItem.toggleSelectedItem(clicked, -1);
		}
		if (clicked.isEmpty()) {
			if (!carried.isEmpty()) {
				carried = safeInsert(pos, carried, left ? carried.getCount() : 1);
			}
		} else if (carried.isEmpty()) {
			// tryRemove(amount, Integer.MAX_VALUE): left takes the whole stack, right takes half rounded up.
			int amount = left ? clicked.getCount() : (clicked.getCount() + 1) / 2;
			carried = clicked.split(amount);
			if (clicked.isEmpty()) {
				slots.set(pos, ItemStack.EMPTY);
			}
		} else if (rules.mayPlace(pos, carried)) {
			if (ItemStack.isSameItemSameComponents(clicked, carried)) {
				carried = safeInsert(pos, carried, left ? carried.getCount() : 1);
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

	/** Mirrors {@code Slot.safeInsert(input, amount)}; returns what is left on the cursor. */
	private ItemStack safeInsert(int pos, ItemStack input, int amount) {
		if (input.isEmpty() || !rules.mayPlace(pos, input)) {
			return input;
		}
		ItemStack slotStack = slots.get(pos);
		int transferable = Math.min(Math.min(amount, input.getCount()),
			rules.maxStackSize(pos, input) - slotStack.getCount());
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
