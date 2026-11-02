package io.github.lookyman.chestmate.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/** Per (item + components) totals, used to assert a layout change loses and duplicates nothing. */
public final class Counts {
	private Counts() {
	}

	/** Item + components identity of a stack, ignoring its count. */
	public static final class Key {
		private final ItemStack proto;

		private Key(ItemStack stack) {
			this.proto = stack.copyWithCount(1);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key other && ItemStack.isSameItemSameComponents(proto, other.proto);
		}

		@Override
		public int hashCode() {
			return ItemStack.hashItemAndComponents(proto);
		}

		@Override
		public String toString() {
			return proto.toString();
		}
	}

	@SafeVarargs
	public static Map<Key, Integer> of(List<ItemStack>... lists) {
		Map<Key, Integer> counts = new HashMap<>();
		for (List<ItemStack> list : lists) {
			for (ItemStack stack : list) {
				if (!stack.isEmpty()) {
					counts.merge(new Key(stack), stack.getCount(), Integer::sum);
				}
			}
		}
		return counts;
	}

	public static boolean same(List<ItemStack> before, List<ItemStack> after) {
		return of(before).equals(of(after));
	}

	@SuppressWarnings("unchecked")
	public static boolean sameAll(List<List<ItemStack>> before, List<List<ItemStack>> after) {
		return of(before.toArray(new List[0])).equals(of(after.toArray(new List[0])));
	}
}
