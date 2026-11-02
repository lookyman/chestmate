package io.github.lookyman.chestmate.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.lookyman.chestmate.TestBootstrap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreativeOrderTest {
	@BeforeAll
	static void boot() {
		TestBootstrap.init();
	}

	private static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> p) {
		ItemStack s = new ItemStack(Items.POTION);
		s.set(DataComponents.POTION_CONTENTS, new PotionContents(p));
		return s;
	}

	private static List<Object> items(List<ItemStack> l) {
		return l.stream().map(s -> (Object) s.getItem()).toList();
	}

	@Test
	void followsListOrder() {
		var cmp = CreativeOrder.comparator(List.of(new ItemStack(Items.DIRT), new ItemStack(Items.STONE), new ItemStack(Items.DIAMOND)));
		List<ItemStack> in = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.STONE), new ItemStack(Items.DIRT)));
		in.sort(cmp);
		assertEquals(List.of(Items.DIRT, Items.STONE, Items.DIAMOND), items(in));
	}

	@Test
	void unknownItemsLast() {
		var cmp = CreativeOrder.comparator(List.of(new ItemStack(Items.DIRT), new ItemStack(Items.STONE)));
		List<ItemStack> in = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.STONE), new ItemStack(Items.DIRT)));
		in.sort(cmp);
		assertEquals(List.of(Items.DIRT, Items.STONE, Items.DIAMOND), items(in));
	}

	@Test
	void exactVariantBeatsItemIndex() {
		ItemStack healing = potion(Potions.HEALING);
		ItemStack swiftness = potion(Potions.SWIFTNESS);
		var cmp = CreativeOrder.comparator(List.of(healing, swiftness));
		assertTrue(cmp.compare(swiftness, healing) > 0);
		assertTrue(cmp.compare(healing, swiftness) < 0);
	}

	@Test
	void deterministicTies() {
		ItemStack a = new ItemStack(Items.DIAMOND_SWORD);
		a.set(DataComponents.CUSTOM_NAME, Component.literal("A"));
		ItemStack b = new ItemStack(Items.DIAMOND_SWORD);
		b.set(DataComponents.CUSTOM_NAME, Component.literal("B"));
		var cmp = CreativeOrder.comparator(List.of(new ItemStack(Items.DIAMOND_SWORD)));
		List<ItemStack> one = new ArrayList<>(List.of(a, b));
		List<ItemStack> two = new ArrayList<>(List.of(b, a));
		one.sort(cmp);
		two.sort(cmp);
		assertEquals(one.get(0).getHoverName().getString(), two.get(0).getHoverName().getString());
		assertEquals(one.get(1).getHoverName().getString(), two.get(1).getHoverName().getString());
		Collections.reverse(one);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void bindDirectly(ResourceManager resources, Registry registry) {
		MappedRegistry mapped = (MappedRegistry) registry;
		mapped.bindTags(TagLoader.loadTagsForRegistry(resources, mapped.key(), TagLoader.ElementLookup.fromWritableRegistry(mapped)));
		mapped.freeze();
	}

	/** The data-driven registries the creative tab generators read (dimensions etc. are not needed and not loadable here). */
	private static List<RegistryDataLoader.RegistryData<?>> tabRegistries() {
		Set<ResourceKey<?>> wanted = Set.of(Registries.BANNER_PATTERN, Registries.ENCHANTMENT, Registries.INSTRUMENT,
			Registries.PAINTING_VARIANT, Registries.JUKEBOX_SONG, Registries.TRIM_MATERIAL, Registries.TRIM_PATTERN, Registries.DAMAGE_TYPE);
		return RegistryDataLoader.WORLD_REGISTRIES.stream().filter(data -> wanted.contains(data.key())).toList();
	}

	/** Loads the vanilla data pack (registries and tags) the way a server reload does. */
	private static HolderLookup.Provider vanillaDataLookup() {
		var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
		RegistryAccess.Frozen builtIn = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		List<Registry.PendingTags<?>> builtInTags = new ArrayList<>();
		builtIn.registries().forEach(entry -> {
			try {
				builtInTags.addAll(TagLoader.loadTagsForExistingRegistries(resources, new RegistryAccess.ImmutableRegistryAccess(List.of(entry.value()))));
			} catch (IllegalStateException unfrozen) {
				// registries with intrusive holders (items, blocks...) are bound directly, as at server start
				bindDirectly(resources, entry.value());
			}
		});
		builtInTags.forEach(Registry.PendingTags::apply);
		RegistryAccess.Frozen world = RegistryDataLoader.load(
			resources, TagLoader.buildUpdatedLookups(builtIn, builtInTags), tabRegistries(), Runnable::run).join();
		TagLoader.loadTagsForExistingRegistries(resources, world).forEach(Registry.PendingTags::apply);
		return world;
	}

	@Test
	void vanillaWorldOrder() {
		var cmp = CreativeOrder.forWorld(FeatureFlags.DEFAULT_FLAGS, false, vanillaDataLookup());
		assertTrue(cmp.compare(new ItemStack(Items.STONE), new ItemStack(Items.DIAMOND)) < 0);
		assertTrue(cmp.compare(new ItemStack(Items.COMMAND_BLOCK), new ItemStack(Items.DIAMOND)) > 0);
		assertTrue(cmp.compare(new ItemStack(Items.COMMAND_BLOCK), new ItemStack(Items.DIRT)) > 0);
	}
}
