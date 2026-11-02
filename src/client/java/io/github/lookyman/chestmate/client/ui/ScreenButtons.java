package io.github.lookyman.chestmate.client.ui;

import io.github.lookyman.chestmate.client.Dispatcher;
import io.github.lookyman.chestmate.client.mixin.AbstractContainerScreenAccessor;
import io.github.lookyman.chestmate.core.Operation;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;

/** Adds the Chestmate icon buttons to chest, barrel, shulker box and survival inventory screens. */
public final class ScreenButtons {
	/** Gap between the buttons' right edge and the screen image's right edge. */
	private static final int RIGHT_MARGIN = 7;
	private static final int CONTAINER_TITLE_ROW_Y = 4;
	/** The strip of the survival inventory just above the 27 main slots. */
	private static final int INVENTORY_STRIP_Y = 70;

	/** A button's operation and its offset from the screen image's top-left corner. */
	public record ButtonSpec(Operation op, int dx, int dy) {
	}

	private ScreenButtons() {
	}

	/** Chest / barrel / shulker box: organize in the title row; put in, take out, organize in the inventory row. */
	static List<ButtonSpec> containerLayout(int imageWidth, int inventoryLabelY) {
		int right = imageWidth - RIGHT_MARGIN - AnchoredButton.SIZE;
		int inventoryRowY = inventoryLabelY - 2;
		return List.of(
			new ButtonSpec(Operation.ORGANIZE_CONTAINER, right, CONTAINER_TITLE_ROW_Y),
			new ButtonSpec(Operation.ORGANIZE_INVENTORY, right, inventoryRowY),
			new ButtonSpec(Operation.TAKE_OUT, right - AnchoredButton.SIZE - 1, inventoryRowY),
			new ButtonSpec(Operation.PUT_IN, right - 2 * AnchoredButton.SIZE - 2, inventoryRowY));
	}

	/** Survival inventory: organize inventory only. */
	static List<ButtonSpec> inventoryLayout(int imageWidth) {
		return List.of(new ButtonSpec(Operation.ORGANIZE_INVENTORY, imageWidth - RIGHT_MARGIN - AnchoredButton.SIZE,
			INVENTORY_STRIP_Y));
	}

	static String glyph(Operation op) {
		return switch (op) {
			case ORGANIZE_CONTAINER, ORGANIZE_INVENTORY -> "⇅";
			case PUT_IN -> "▲";
			case TAKE_OUT -> "▼";
		};
	}

	static String tooltipKey(Operation op) {
		return switch (op) {
			case ORGANIZE_CONTAINER -> "chestmate.button.organize_container";
			case ORGANIZE_INVENTORY -> "chestmate.button.organize_inventory";
			case PUT_IN -> "chestmate.button.put_in";
			case TAKE_OUT -> "chestmate.button.take_out";
		};
	}

	/** Whether Chestmate adds buttons and shortcuts to this screen. */
	public static boolean supports(Screen screen) {
		return screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen
			|| (screen instanceof InventoryScreen && !(screen instanceof CreativeModeInventoryScreen));
	}

	/** Called after every init of a screen (Fabric resets per-screen events and widgets on init). */
	public static void attach(Screen screen) {
		if (!supports(screen) || !(screen instanceof AbstractContainerScreen<?>)) {
			return;
		}
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		List<ButtonSpec> layout = screen instanceof InventoryScreen
			? inventoryLayout(accessor.chestmate$getImageWidth())
			: containerLayout(accessor.chestmate$getImageWidth(), accessor.chestmate$getInventoryLabelY());
		List<AnchoredButton> buttons = new ArrayList<>(layout.size());
		for (ButtonSpec spec : layout) {
			AnchoredButton button = new AnchoredButton(accessor, spec.dx(), spec.dy(),
				Component.literal(glyph(spec.op())), Component.translatable(tooltipKey(spec.op())),
				pressed -> Dispatcher.run(spec.op()));
			buttons.add(button);
			Screens.getWidgets(screen).add(button);
		}
		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, a) -> {
			for (AnchoredButton button : buttons) {
				button.reposition();
			}
		});
	}
}
