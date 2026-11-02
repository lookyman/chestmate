package io.github.lookyman.chestmate;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

/** Bootstraps Minecraft registries once per JVM so tests can use items and components. */
public final class TestBootstrap {
	private static boolean done;

	private TestBootstrap() {
	}

	public static synchronized void init() {
		if (done) {
			return;
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// Since 26.x item components are bound lazily, as the data pack reload would do.
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
			.forEach(pending -> pending.apply());
		done = true;
	}
}
