package io.github.lookyman.chestmate;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class Chestmate implements ModInitializer {
	public static final String MOD_ID = "chestmate";

	@Override
	public void onInitialize() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
