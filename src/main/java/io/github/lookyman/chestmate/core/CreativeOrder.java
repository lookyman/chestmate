package io.github.lookyman.chestmate.core;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.lookyman.chestmate.mixin.CreativeModeTabAccessor;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
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

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final AtomicBoolean WARNED = new AtomicBoolean();
	private static final int MAX_CACHED = 4;

	private record Cached(FeatureFlagSet flags, HolderLookup.Provider registries, Comparator<ItemStack> order) {
	}

	private static final List<Cached> CACHE = new ArrayList<>();

	private CreativeOrder() {
	}

	/**
	 * Pure core: orders by position in the given list. Ties are broken by a canonical, JVM-independent
	 * rendering of the stack's component patch, then registry id, then count descending.
	 */
	public static Comparator<ItemStack> comparator(List<ItemStack> searchTabItems, HolderLookup.Provider registries) {
		Object2IntOpenCustomHashMap<ItemStack> exact = new Object2IntOpenCustomHashMap<>(VARIANT);
		exact.defaultReturnValue(-1);
		Map<Item, Integer> firstOfItem = new HashMap<>();
		int index = 0;
		for (ItemStack entry : searchTabItems) {
			ItemStack key = entry.copyWithCount(1);
			if (!exact.containsKey(key)) {
				exact.put(key, index);
			}
			firstOfItem.putIfAbsent(entry.getItem(), index);
			index++;
		}
		RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
		return Comparator.<ItemStack>comparingInt(stack -> primaryIndex(exact, firstOfItem, stack))
			.thenComparing((a, b) -> tieBreak(ops, a, b));
	}

	private static int primaryIndex(Object2IntOpenCustomHashMap<ItemStack> exact, Map<Item, Integer> firstOfItem, ItemStack stack) {
		int variant = exact.getInt(stack);
		if (variant >= 0) {
			return variant;
		}
		return firstOfItem.getOrDefault(stack.getItem(), Integer.MAX_VALUE);
	}

	/** Placeholder tie-break (spec); kept in one place. */
	private static int tieBreak(RegistryOps<JsonElement> ops, ItemStack a, ItemStack b) {
		int c = patchKey(ops, a).compareTo(patchKey(ops, b));
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
	 * Not cached: stacks are mutable, so a key must reflect the stack as it is now. Only reached
	 * for stacks that tie on their list position and carry component changes.
	 */
	private static String patchKey(RegistryOps<JsonElement> ops, ItemStack stack) {
		DataComponentPatch patch = stack.getComponentsPatch();
		if (patch.isEmpty()) {
			return "";
		}
		var encoded = DataComponentPatch.CODEC.encodeStart(ops, patch).result();
		if (encoded.isEmpty()) {
			if (WARNED.compareAndSet(false, true)) {
				LOGGER.warn("Chestmate: could not encode a component patch for sorting; such stacks tie on components");
			}
			return "";
		}
		StringBuilder out = new StringBuilder();
		canonical(encoded.get(), out);
		return out.toString();
	}

	/** Writes JSON with object keys sorted recursively, so equal data gives equal text on every JVM. */
	private static void canonical(JsonElement json, StringBuilder out) {
		if (json instanceof JsonObject object) {
			out.append('{');
			boolean first = true;
			for (String key : new TreeSet<>(object.keySet())) {
				if (!first) {
					out.append(',');
				}
				first = false;
				out.append(new com.google.gson.JsonPrimitive(key)).append(':');
				canonical(object.get(key), out);
			}
			out.append('}');
		} else if (json instanceof JsonArray array) {
			out.append('[');
			for (int i = 0; i < array.size(); i++) {
				if (i > 0) {
					out.append(',');
				}
				canonical(array.get(i), out);
			}
			out.append(']');
		} else {
			out.append(json);
		}
	}

	/**
	 * Builds the order from the vanilla Search tab contents for the given flags, computed privately:
	 * vanilla's global tab state is never touched. Operator-only items are never included, so every
	 * player gets the same order on the client and on the server.
	 */
	public static Comparator<ItemStack> forWorld(FeatureFlagSet flags, HolderLookup.Provider registries) {
		synchronized (CACHE) {
			for (Cached cached : CACHE) {
				if (cached.registries() == registries && cached.flags().equals(flags)) {
					return cached.order();
				}
			}
			Comparator<ItemStack> order = comparator(searchTabItems(flags, registries), registries);
			if (CACHE.size() >= MAX_CACHED) {
				CACHE.removeFirst();
			}
			CACHE.add(new Cached(flags, registries, order));
			return order;
		}
	}

	/** The search tab's list, in vanilla order: category tabs in registry order, deduped by item + components. */
	static List<ItemStack> searchTabItems(FeatureFlagSet flags, HolderLookup.Provider registries) {
		CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(flags, false, registries);
		Set<ItemStack> result = ItemStackLinkedSet.createTypeAndComponentsSet();
		CreativeModeTab.Output output = (stack, visibility) -> {
			if (visibility != CreativeModeTab.TabVisibility.PARENT_TAB_ONLY && stack.getItem().isEnabled(flags)) {
				result.add(stack);
			}
		};
		for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
			if (tab.getType() == CreativeModeTab.Type.CATEGORY) {
				((CreativeModeTabAccessor) tab).chestmate$getDisplayItemsGenerator().accept(parameters, output);
			}
		}
		return new ArrayList<>(result);
	}
}
