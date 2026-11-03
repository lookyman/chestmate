package io.github.lookyman.chestmate.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.lookyman.chestmate.Chestmate;
import io.github.lookyman.chestmate.client.Dispatcher;
import io.github.lookyman.chestmate.core.MenuRegions;
import io.github.lookyman.chestmate.core.Operation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/** One unbound-by-default shortcut per button, active on the screens that show that button. */
public final class Shortcuts {
	static final String ORGANIZE_CONTAINER = "key.chestmate.organize_container";
	static final String ORGANIZE_INVENTORY = "key.chestmate.organize_inventory";
	static final String PUT_IN = "key.chestmate.put_in";
	static final String TAKE_OUT = "key.chestmate.take_out";
	/** In conflict-resolution order: the first matching one wins. */
	static final List<String> KEY_NAMES = List.of(ORGANIZE_CONTAINER, ORGANIZE_INVENTORY, PUT_IN, TAKE_OUT);
	/** The label key vanilla derives from the category id {@code chestmate:main}. */
	static final String CATEGORY_LABEL_KEY = "key.category.chestmate.main";

	private static final Map<String, Operation> OPERATIONS = Map.of(
		ORGANIZE_CONTAINER, Operation.ORGANIZE_CONTAINER,
		ORGANIZE_INVENTORY, Operation.ORGANIZE_INVENTORY,
		PUT_IN, Operation.PUT_IN,
		TAKE_OUT, Operation.TAKE_OUT);
	private static final Map<String, KeyMapping> MAPPINGS = new HashMap<>();

	private Shortcuts() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Chestmate.id("main"));
		for (String name : KEY_NAMES) {
			MAPPINGS.put(name, KeyMappingHelper.registerKeyMapping(unbound(name, category)));
		}
	}

	private static KeyMapping unbound(String name, KeyMapping.Category category) {
		return new KeyMapping(name, InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), category);
	}

	/**
	 * The operation of the first matching shortcut (in {@link #KEY_NAMES} order) on a screen with these regions, or
	 * empty if none matches or the matched one has no button there; then the input is left to vanilla.
	 */
	static Optional<Operation> resolve(Predicate<String> matchesShortcut, MenuRegions regions) {
		for (String name : KEY_NAMES) {
			if (matchesShortcut.test(name)) {
				Operation op = OPERATIONS.get(name);
				return op.supportedBy(regions) ? Optional.of(op) : Optional.empty();
			}
		}
		return Optional.empty();
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
		Optional<Operation> op = resolve(name -> matches.test(MAPPINGS.get(name)), regions.get());
		op.ifPresent(Dispatcher::run);
		return op.isPresent();
	}
}
