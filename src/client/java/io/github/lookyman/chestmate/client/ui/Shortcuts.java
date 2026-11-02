package io.github.lookyman.chestmate.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.lookyman.chestmate.Chestmate;
import io.github.lookyman.chestmate.client.Dispatcher;
import io.github.lookyman.chestmate.client.mixin.AbstractContainerScreenAccessor;
import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** The three unbound-by-default shortcuts, active on the screens that have Chestmate buttons. */
public final class Shortcuts {
	static final int NO_SLOT = -1;
	static final String ORGANIZE = "key.chestmate.organize";
	static final String PUT_IN = "key.chestmate.put_in";
	static final String TAKE_OUT = "key.chestmate.take_out";
	static final List<String> KEY_NAMES = List.of(ORGANIZE, PUT_IN, TAKE_OUT);
	/** The label key vanilla derives from the category id {@code chestmate:main}. */
	static final String CATEGORY_LABEL_KEY = "key.category.chestmate.main";

	private static KeyMapping organize;
	private static KeyMapping putIn;
	private static KeyMapping takeOut;

	private Shortcuts() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Chestmate.id("main"));
		organize = KeyMappingHelper.registerKeyMapping(unbound(ORGANIZE, category));
		putIn = KeyMappingHelper.registerKeyMapping(unbound(PUT_IN, category));
		takeOut = KeyMappingHelper.registerKeyMapping(unbound(TAKE_OUT, category));
	}

	private static KeyMapping unbound(String name, KeyMapping.Category category) {
		return new KeyMapping(name, InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), category);
	}

	/**
	 * Organize acts on the region under the mouse; with no storage slot hovered it prefers the container, if the
	 * screen has one.
	 */
	static Operation organizeTarget(int hoveredMenuSlot, MenuRegions regions) {
		if (regions.container().contains(hoveredMenuSlot)) {
			return Operation.ORGANIZE_CONTAINER;
		}
		if (regions.main().contains(hoveredMenuSlot)) {
			return Operation.ORGANIZE_INVENTORY;
		}
		return regions.container().isEmpty() ? Operation.ORGANIZE_INVENTORY : Operation.ORGANIZE_CONTAINER;
	}

	/** Called after every init of a supported screen. */
	public static void attach(Screen screen) {
		if (!ScreenButtons.supports(screen) || !(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return;
		}
		ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
			if (!s.isInputCaptured()) {
				trigger(containerScreen, mapping -> mapping.matches(event));
			}
		});
		ScreenMouseEvents.afterMouseClick(screen).register((s, event, consumed) ->
			trigger(containerScreen, mapping -> mapping.matchesMouse(event)) || consumed);
	}

	/** Runs the first shortcut that matches; true if one did. */
	private static boolean trigger(AbstractContainerScreen<?> screen, Predicate<KeyMapping> matches) {
		Optional<MenuRegions> regions = MenuRegions.of(screen.getMenu());
		if (regions.isEmpty()) {
			return false;
		}
		if (matches.test(organize)) {
			Slot hovered = ((AbstractContainerScreenAccessor) screen).chestmate$getHoveredSlot();
			Dispatcher.run(organizeTarget(hovered == null ? NO_SLOT : hovered.index, regions.get()));
			return true;
		}
		if (matches.test(putIn)) {
			Dispatcher.run(Operation.PUT_IN);
			return true;
		}
		if (matches.test(takeOut)) {
			Dispatcher.run(Operation.TAKE_OUT);
			return true;
		}
		return false;
	}
}
