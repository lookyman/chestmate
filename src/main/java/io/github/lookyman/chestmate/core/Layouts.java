package io.github.lookyman.chestmate.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Pure functions deciding what a region looks like after Organize / Put in / Take out. */
public final class Layouts {
	private Layouts() {
	}

	public record Transfer(List<ItemStack> from, List<ItemStack> to) {
	}

	/**
	 * Merges equal stacks into full stacks plus at most one partial per kind, sorts them with {@code order}
	 * (stable; full stacks before the partial of one kind) and packs them from position 0.
	 * {@code rules} must be region-position rules. Never mutates the input. If the merged result would
	 * somehow need more slots than the region has, the input is returned unchanged (as copies).
	 */
	public static List<ItemStack> organize(List<ItemStack> region, SlotRules rules, Comparator<ItemStack> order) {
		List<ItemStack> kinds = new ArrayList<>();
		List<Integer> totals = new ArrayList<>();
		for (ItemStack stack : region) {
			if (stack.isEmpty()) {
				continue;
			}
			int found = -1;
			for (int i = 0; i < kinds.size(); i++) {
				if (ItemStack.isSameItemSameComponents(kinds.get(i), stack)) {
					found = i;
					break;
				}
			}
			if (found < 0) {
				kinds.add(stack.copyWithCount(1));
				totals.add(stack.getCount());
			} else {
				totals.set(found, totals.get(found) + stack.getCount());
			}
		}

		List<ItemStack> chunks = new ArrayList<>();
		for (int k = 0; k < kinds.size(); k++) {
			ItemStack kind = kinds.get(k);
			int max = Integer.MAX_VALUE;
			for (int pos = 0; pos < region.size(); pos++) {
				max = Math.min(max, rules.maxStackSize(pos, kind));
			}
			max = Math.max(1, max);
			int left = totals.get(k);
			while (left > 0) {
				int n = Math.min(left, max);
				chunks.add(kind.copyWithCount(n));
				left -= n;
			}
		}
		if (chunks.size() > region.size()) {
			return copyAll(region);
		}
		chunks.sort(order);

		List<ItemStack> result = new ArrayList<>(region.size());
		result.addAll(chunks);
		while (result.size() < region.size()) {
			result.add(ItemStack.EMPTY);
		}
		return result;
	}

	/**
	 * Moves as much of {@code from} into {@code to} as fits: per from position in order, tops up partial
	 * same-kind stacks first, then uses empty positions, while {@code toRules} accepts the stack.
	 */
	public static Transfer transfer(List<ItemStack> from, List<ItemStack> to, SlotRules toRules) {
		List<ItemStack> newFrom = copyAll(from);
		List<ItemStack> newTo = copyAll(to);
		for (ItemStack source : newFrom) {
			if (source.isEmpty()) {
				continue;
			}
			for (int j = 0; j < newTo.size() && !source.isEmpty(); j++) {
				ItemStack target = newTo.get(j);
				if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, source)
					|| !toRules.mayPlace(j, source)) {
					continue;
				}
				int moved = Math.min(toRules.maxStackSize(j, source) - target.getCount(), source.getCount());
				if (moved > 0) {
					target.grow(moved);
					source.shrink(moved);
				}
			}
			for (int j = 0; j < newTo.size() && !source.isEmpty(); j++) {
				if (!newTo.get(j).isEmpty() || !toRules.mayPlace(j, source)) {
					continue;
				}
				int moved = Math.min(toRules.maxStackSize(j, source), source.getCount());
				if (moved > 0) {
					newTo.set(j, source.copyWithCount(moved));
					source.shrink(moved);
				}
			}
		}
		for (int i = 0; i < newFrom.size(); i++) {
			if (newFrom.get(i).isEmpty()) {
				newFrom.set(i, ItemStack.EMPTY);
			}
		}
		return new Transfer(newFrom, newTo);
	}

	private static List<ItemStack> copyAll(List<ItemStack> stacks) {
		List<ItemStack> copy = new ArrayList<>(stacks.size());
		for (ItemStack stack : stacks) {
			copy.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
		return copy;
	}
}
