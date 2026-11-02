package io.github.lookyman.chestmate.click;

import net.minecraft.world.item.ItemStack;

/**
 * The open container menu as the {@link ClickExecutor} sees it. The client adapter sends real clicks; test fakes back
 * it with a {@link ClickSim}. {@code get} and {@code carried} may return live stacks: callers must not mutate them.
 */
public interface MenuView {
	int containerId();

	/** Number of menu slots. */
	int slotCount();

	ItemStack get(int menuSlot);

	ItemStack carried();

	boolean isOpen();

	/** Sends a PICKUP click on the menu slot with the click's button. */
	void click(Click click);
}
