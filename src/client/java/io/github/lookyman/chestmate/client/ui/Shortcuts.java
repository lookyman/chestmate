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
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
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

	/**
	 * The operation the first matching shortcut (Organize, then Put in, then Take out) runs on a screen with these
	 * regions, or empty if none matches or the matched one doesn't apply there; then the input is left to vanilla.
	 */
	static Optional<Operation> resolve(Predicate<String> matchesShortcut, int hoveredMenuSlot, MenuRegions regions) {
		Operation op;
		if (matchesShortcut.test(ORGANIZE)) {
			op = organizeTarget(hoveredMenuSlot, regions);
		} else if (matchesShortcut.test(PUT_IN)) {
			op = Operation.PUT_IN;
		} else if (matchesShortcut.test(TAKE_OUT)) {
			op = Operation.TAKE_OUT;
		} else {
			return Optional.empty();
		}
		return op.supportedBy(regions) ? Optional.of(op) : Optional.empty();
	}

	/**
	 * Called after every init of a supported screen. The shortcuts run before vanilla's input handling and consume the
	 * input, so a key vanilla also uses in container screens (offhand swap, throw, pick block) does only one thing.
	 */
	public static void attach(Screen screen) {
		if (!ScreenButtons.supports(screen) || !(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return;
		}
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
			if (s.isInputCaptured()) {
				return true;
			}
			if (trigger(containerScreen, mapping -> mapping.matches(event))) {
				return false;
			}
			if (actsOnSlots(mapping -> mapping.matches(event))) {
				Dispatcher.playerInput();
			}
			return true;
		});
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
			if (trigger(containerScreen, mapping -> mapping.matchesMouse(event))) {
				return false;
			}
			if (!onChestmateButton(s, event.x(), event.y())) {
				Dispatcher.playerInput();
			}
			return true;
		});
	}

	/** Vanilla keys that move stacks in a container screen: hotbar swaps, offhand swap, drop, pick block. */
	private static boolean actsOnSlots(Predicate<KeyMapping> matches) {
		Options options = Minecraft.getInstance().options;
		if (matches.test(options.keySwapOffhand) || matches.test(options.keyDrop) || matches.test(options.keyPickItem)) {
			return true;
		}
		for (KeyMapping hotbar : options.keyHotbarSlots) {
			if (matches.test(hotbar)) {
				return true;
			}
		}
		return false;
	}

	/** A press on our own button is not slot input; while a run settles, the button press is ignored as busy. */
	private static boolean onChestmateButton(Screen screen, double x, double y) {
		for (var widget : Screens.getWidgets(screen)) {
			if (widget instanceof AnchoredButton button && button.isMouseOver(x, y)) {
				return true;
			}
		}
		return false;
	}

	/** Runs the operation of the shortcut that matches; true (consume the input) if one ran. */
	private static boolean trigger(AbstractContainerScreen<?> screen, Predicate<KeyMapping> matches) {
		Optional<MenuRegions> regions = MenuRegions.of(screen.getMenu());
		if (regions.isEmpty()) {
			return false;
		}
		Slot hovered = ((AbstractContainerScreenAccessor) screen).chestmate$getHoveredSlot();
		Optional<Operation> op = resolve(name -> matches.test(mapping(name)), hovered == null ? NO_SLOT : hovered.index,
			regions.get());
		op.ifPresent(Dispatcher::run);
		return op.isPresent();
	}

	private static KeyMapping mapping(String name) {
		return switch (name) {
			case ORGANIZE -> organize;
			case PUT_IN -> putIn;
			case TAKE_OUT -> takeOut;
			default -> throw new IllegalArgumentException(name);
		};
	}
}
