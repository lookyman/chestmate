package io.github.lookyman.chestmate.server;

import io.github.lookyman.chestmate.TestBootstrap;
import io.github.lookyman.chestmate.core.Layouts;
import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import io.github.lookyman.chestmate.core.SlotRules;
import io.github.lookyman.chestmate.net.OperationRequest;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerOperationsTest {
	private static final Comparator<ItemStack> ORDER =
		Comparator.comparingInt(s -> BuiltInRegistries.ITEM.getId(s.getItem()));

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.init();
	}

	/** 27 container slots, 27 main slots and 9 hotbar slots backed by plain containers. */
	private static final class TestMenu extends AbstractContainerMenu {
		final Container storage = new SimpleContainer(27);
		final Container player = new SimpleContainer(41);

		TestMenu(int id) {
			super(null, id);
			for (int i = 0; i < 27; i++) {
				addSlot(new Slot(storage, i, 0, 0));
			}
			for (int i = 9; i < 36; i++) {
				addSlot(new Slot(player, i, 0, 0));
			}
			for (int i = 0; i < 9; i++) {
				addSlot(new Slot(player, i, 0, 0));
			}
		}

		MenuRegions regions() {
			return MenuRegions.classify(slots, c -> c == player, true);
		}

		@Override
		public ItemStack quickMoveStack(Player p, int i) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player p) {
			return true;
		}
	}

	private static List<ItemStack> contents(TestMenu menu, List<Integer> region) {
		List<ItemStack> out = new ArrayList<>();
		for (int i : region) {
			out.add(menu.getSlot(i).getItem().copy());
		}
		return out;
	}

	private static TestMenu scattered() {
		TestMenu menu = new TestMenu(3);
		menu.storage.setItem(5, new ItemStack(Items.DIRT, 10));
		menu.storage.setItem(9, new ItemStack(Items.STONE, 20));
		menu.storage.setItem(20, new ItemStack(Items.DIRT, 5));
		menu.player.setItem(10, new ItemStack(Items.COBBLESTONE, 7));
		menu.player.setItem(30, new ItemStack(Items.APPLE, 3));
		menu.player.setItem(2, new ItemStack(Items.DIAMOND, 4));
		return menu;
	}

	@Test
	void rejectsWrongContainerId() {
		TestMenu menu = scattered();
		assertEquals(ServerOperations.Result.REJECTED,
			ServerOperations.apply(menu, menu.regions(), 99, Operation.ORGANIZE_CONTAINER, ItemStack.EMPTY, ORDER));
		assertEquals(10, menu.storage.getItem(5).getCount());
	}

	@Test
	void rejectsWithCarriedStack() {
		TestMenu menu = scattered();
		assertEquals(ServerOperations.Result.REJECTED, ServerOperations.apply(menu, menu.regions(), 3,
			Operation.ORGANIZE_CONTAINER, new ItemStack(Items.DIRT), ORDER));
		assertEquals(10, menu.storage.getItem(5).getCount());
		assertTrue(menu.storage.getItem(0).isEmpty());
	}

	@Test
	void rejectsUnsupportedMenu() {
		AbstractContainerMenu bare = new AbstractContainerMenu(null, 4) {
			@Override
			public ItemStack quickMoveStack(Player p, int i) {
				return ItemStack.EMPTY;
			}

			@Override
			public boolean stillValid(Player p) {
				return true;
			}
		};
		assertEquals(ServerOperations.Result.REJECTED,
			ServerOperations.apply(bare, 4, Operation.ORGANIZE_CONTAINER, ItemStack.EMPTY, ORDER));
	}

	@Test
	void rejectsContainerOperationWithoutContainer() {
		TestMenu menu = scattered();
		MenuRegions noContainer = new MenuRegions(List.of(), menu.regions().main());
		assertEquals(ServerOperations.Result.REJECTED,
			ServerOperations.apply(menu, noContainer, 3, Operation.PUT_IN, ItemStack.EMPTY, ORDER));
	}

	@Test
	void organizeContainerApplies() {
		TestMenu menu = scattered();
		MenuRegions regions = menu.regions();
		List<ItemStack> before = contents(menu, regions.container());
		List<ItemStack> mainBefore = contents(menu, regions.main());
		List<ItemStack> hotbarBefore = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			hotbarBefore.add(menu.player.getItem(i).copy());
		}
		List<ItemStack> expected = Layouts.organize(before, SlotRules.of(menu).forRegion(regions.container()), ORDER);

		assertEquals(ServerOperations.Result.APPLIED,
			ServerOperations.apply(menu, regions, 3, Operation.ORGANIZE_CONTAINER, ItemStack.EMPTY, ORDER));

		List<ItemStack> after = contents(menu, regions.container());
		for (int i = 0; i < expected.size(); i++) {
			assertTrue(ItemStack.matches(expected.get(i), after.get(i)), "slot " + i);
		}
		List<ItemStack> mainAfter = contents(menu, regions.main());
		for (int i = 0; i < mainBefore.size(); i++) {
			assertTrue(ItemStack.matches(mainBefore.get(i), mainAfter.get(i)));
		}
		for (int i = 0; i < 9; i++) {
			assertTrue(ItemStack.matches(hotbarBefore.get(i), menu.player.getItem(i)));
		}
	}

	@Test
	void organizeTwiceIsNothingToDo() {
		TestMenu menu = scattered();
		MenuRegions regions = menu.regions();
		ServerOperations.apply(menu, regions, 3, Operation.ORGANIZE_CONTAINER, ItemStack.EMPTY, ORDER);
		assertEquals(ServerOperations.Result.NOTHING_TO_DO,
			ServerOperations.apply(menu, regions, 3, Operation.ORGANIZE_CONTAINER, ItemStack.EMPTY, ORDER));
	}

	@Test
	void putInMovesMainToContainerAndLeavesHotbar() {
		TestMenu menu = scattered();
		MenuRegions regions = menu.regions();
		assertEquals(ServerOperations.Result.APPLIED,
			ServerOperations.apply(menu, regions, 3, Operation.PUT_IN, ItemStack.EMPTY, ORDER));
		for (int i : regions.main()) {
			assertTrue(menu.getSlot(i).getItem().isEmpty());
		}
		assertEquals(4, menu.player.getItem(2).getCount());
		int cobble = 0;
		for (int i : regions.container()) {
			if (menu.getSlot(i).getItem().is(Items.COBBLESTONE)) {
				cobble += menu.getSlot(i).getItem().getCount();
			}
		}
		assertEquals(7, cobble);
	}

	@Test
	void takeOutRespectsHotbar() {
		TestMenu menu = scattered();
		MenuRegions regions = menu.regions();
		assertEquals(ServerOperations.Result.APPLIED,
			ServerOperations.apply(menu, regions, 3, Operation.TAKE_OUT, ItemStack.EMPTY, ORDER));
		for (int i = 0; i < 9; i++) {
			if (i == 2) {
				assertEquals(4, menu.player.getItem(2).getCount());
			} else {
				assertTrue(menu.player.getItem(i).isEmpty(), "hotbar " + i);
			}
		}
		assertTrue(menu.storage.isEmpty());
		int total = 0;
		for (int i : regions.main()) {
			total += menu.getSlot(i).getItem().getCount();
		}
		assertEquals(7 + 3 + 10 + 20 + 5, total);
	}

	@Test
	void codecRoundTrip() {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
		OperationRequest.STREAM_CODEC.encode(buf, new OperationRequest(7, Operation.PUT_IN));
		assertEquals(new OperationRequest(7, Operation.PUT_IN), OperationRequest.STREAM_CODEC.decode(buf));
	}

	@Test
	void unknownOrdinalFailsDecoding() {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
		VarInt.write(buf, 7);
		VarInt.write(buf, 99);
		assertThrows(RuntimeException.class, () -> OperationRequest.STREAM_CODEC.decode(buf));
	}
}
