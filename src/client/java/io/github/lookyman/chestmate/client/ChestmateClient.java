package io.github.lookyman.chestmate.client;

import io.github.lookyman.chestmate.client.config.ChestmateConfig;
import io.github.lookyman.chestmate.client.ui.ScreenButtons;
import io.github.lookyman.chestmate.client.ui.Shortcuts;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

public class ChestmateClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ChestmateConfig.get();
		Shortcuts.register();
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (ScreenButtons.supports(screen)) {
				ScreenButtons.attach(screen);
				Shortcuts.attach(screen);
				ScreenEvents.remove(screen).register(removed -> Dispatcher.abort());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> Dispatcher.tick());
	}
}
