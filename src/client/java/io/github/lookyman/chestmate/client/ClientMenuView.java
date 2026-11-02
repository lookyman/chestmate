package io.github.lookyman.chestmate.client;

import io.github.lookyman.chestmate.click.Click;
import io.github.lookyman.chestmate.click.MenuView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * The menu of the open container screen. Clicks go through the client game mode, so they are predicted locally (the
 * executor checks that prediction before every click) and sent to the server.
 */
public final class ClientMenuView implements MenuView {
	private final Minecraft minecraft;
	private final AbstractContainerMenu menu;

	public ClientMenuView(Minecraft minecraft, AbstractContainerMenu menu) {
		this.minecraft = minecraft;
		this.menu = menu;
	}

	@Override
	public int containerId() {
		return menu.containerId;
	}

	@Override
	public int slotCount() {
		return menu.slots.size();
	}

	@Override
	public ItemStack get(int menuSlot) {
		return menu.getSlot(menuSlot).getItem();
	}

	@Override
	public ItemStack carried() {
		return menu.getCarried();
	}

	/**
	 * The player's menu is still this one and a screen still shows it. The survival inventory menu (id 0) stays the
	 * player's menu after its screen closes, so the screen check is what stops a run there.
	 */
	@Override
	public boolean isOpen() {
		return minecraft.player != null
			&& minecraft.gameMode != null
			&& minecraft.player.containerMenu.containerId == menu.containerId
			&& minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen
			&& screen.getMenu() == menu;
	}

	@Override
	public void click(Click click) {
		if (minecraft.player == null || minecraft.gameMode == null) {
			return;
		}
		minecraft.gameMode.handleContainerInput(menu.containerId, click.menuSlot(), click.button(), ContainerInput.PICKUP,
			minecraft.player);
	}
}
