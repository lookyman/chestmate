package io.github.lookyman.chestmate.core;

import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** What a slot accepts. {@code pos} is a menu slot index or, for region rules, an index into the region. */
public interface SlotRules {
	boolean mayPlace(int pos, ItemStack s);

	int maxStackSize(int pos, ItemStack s);

	static SlotRules vanilla() {
		return new SlotRules() {
			@Override
			public boolean mayPlace(int pos, ItemStack s) {
				return true;
			}

			@Override
			public int maxStackSize(int pos, ItemStack s) {
				return s.getMaxStackSize();
			}
		};
	}

	static SlotRules of(AbstractContainerMenu menu) {
		return new SlotRules() {
			@Override
			public boolean mayPlace(int pos, ItemStack s) {
				return menu.getSlot(pos).mayPlace(s);
			}

			@Override
			public int maxStackSize(int pos, ItemStack s) {
				return menu.getSlot(pos).getMaxStackSize(s);
			}
		};
	}

	/** Rules whose {@code pos} indexes into {@code region}, mapped to {@code region.get(pos)}. */
	default SlotRules forRegion(List<Integer> region) {
		SlotRules outer = this;
		return new SlotRules() {
			@Override
			public boolean mayPlace(int pos, ItemStack s) {
				return outer.mayPlace(region.get(pos), s);
			}

			@Override
			public int maxStackSize(int pos, ItemStack s) {
				return outer.maxStackSize(region.get(pos), s);
			}
		};
	}
}
