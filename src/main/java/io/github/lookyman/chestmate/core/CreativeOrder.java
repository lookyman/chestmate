package io.github.lookyman.chestmate.core;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Sort order of the creative Search tab, shared by the client (click path) and the server (instant path). */
public final class CreativeOrder {
	private static final Hash.Strategy<ItemStack> VARIANT = new Hash.Strategy<>() {
		@Override
		public int hashCode(ItemStack o) {
			return o == null ? 0 : ItemStack.hashItemAndComponents(o);
		}

		@Override
		public boolean equals(ItemStack a, ItemStack b) {
			if (a == null || b == null) {
				return a == b;
			}
			return ItemStack.isSameItemSameComponents(a, b);
		}
	};

	private record CacheKey(FeatureFlagSet flags, boolean hasPermissions, int registriesIdentity) {
	}

	private static final Map<CacheKey, Comparator<ItemStack>> CACHE = new ConcurrentHashMap<>();

	private static final int MAX_CACHED = 8;

	private CreativeOrder() {
	}

	/** Pure core: orders by position in the given list; see {@link #forWorld}. */
	public static Comparator<ItemStack> comparator(List<ItemStack> searchTabItems) {
		Object2IntOpenCustomHashMap<ItemStack> exact = new Object2IntOpenCustomHashMap<>(VARIANT);
		exact.defaultReturnValue(-1);
		Map<Item, Integer> firstOfItem = new java.util.HashMap<>();
		int index = 0;
		for (ItemStack entry : searchTabItems) {
			ItemStack key = entry.copyWithCount(1);
			if (!exact.containsKey(key)) {
				exact.put(key, index);
			}
			firstOfItem.putIfAbsent(entry.getItem(), index);
			index++;
		}
		return Comparator.<ItemStack>comparingInt(stack -> primaryIndex(exact, firstOfItem, stack))
			.thenComparing(CreativeOrder::tieBreak);
	}

	private static int primaryIndex(Object2IntOpenCustomHashMap<ItemStack> exact, Map<Item, Integer> firstOfItem, ItemStack stack) {
		int variant = exact.getInt(stack);
		if (variant >= 0) {
			return variant;
		}
		return firstOfItem.getOrDefault(stack.getItem(), Integer.MAX_VALUE);
	}

	/** Placeholder tie-break: components patch text, then registry id, then count descending. */
	private static int tieBreak(ItemStack a, ItemStack b) {
		int c = a.getComponentsPatch().toString().compareTo(b.getComponentsPatch().toString());
		if (c != 0) {
			return c;
		}
		c = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(a.getItem())).toString()
			.compareTo(Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(b.getItem())).toString());
		if (c != 0) {
			return c;
		}
		return Integer.compare(b.getCount(), a.getCount());
	}

	/**
	 * Builds the order from the vanilla Search tab for the given flags and permission.
	 * Callers pass the player's {@code canUseGameMasterBlocks()} so client and server agree.
	 */
	public static Comparator<ItemStack> forWorld(FeatureFlagSet flags, boolean hasPermissions, HolderLookup.Provider registries) {
		CacheKey key = new CacheKey(flags, hasPermissions, System.identityHashCode(registries));
		Comparator<ItemStack> cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		synchronized (CACHE) {
			cached = CACHE.get(key);
			if (cached == null) {
				CreativeModeTabs.tryRebuildTabContents(flags, hasPermissions, registries);
				cached = comparator(new ArrayList<>(CreativeModeTabs.searchTab().getSearchTabDisplayItems()));
				if (CACHE.size() >= MAX_CACHED) {
					CACHE.clear(); // registries change per connection; keep the cache from growing
				}
				CACHE.put(key, cached);
			}
			return cached;
		}
	}
}
