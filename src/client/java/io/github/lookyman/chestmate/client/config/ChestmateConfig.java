package io.github.lookyman.chestmate.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Client-only settings, stored as JSON in the config directory. */
public final class ChestmateConfig {
	public static final double MIN_CLICKS_PER_TICK = 0.2;
	public static final double MAX_CLICKS_PER_TICK = 10.0;
	public static final double DEFAULT_CLICKS_PER_TICK = 1.0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ChestmateConfig instance;

	public double clicksPerTick = DEFAULT_CLICKS_PER_TICK;

	/** Never returns NaN, infinity or a non-positive value: invalid becomes the default, finite is clamped. */
	static double sanitize(double value) {
		if (!Double.isFinite(value) || value < 0) {
			return DEFAULT_CLICKS_PER_TICK;
		}
		return Math.max(MIN_CLICKS_PER_TICK, Math.min(MAX_CLICKS_PER_TICK, value));
	}

	public static ChestmateConfig load(Path file) {
		ChestmateConfig config = new ChestmateConfig();
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			if (root.isJsonObject()) {
				JsonObject object = root.getAsJsonObject();
				if (object.has("clicksPerTick")) {
					config.clicksPerTick = sanitize(object.get("clicksPerTick").getAsDouble());
				}
			}
		} catch (IOException | RuntimeException e) {
			config.clicksPerTick = DEFAULT_CLICKS_PER_TICK;
		}
		return config;
	}

	public void save(Path file) {
		JsonObject object = new JsonObject();
		object.addProperty("clicksPerTick", sanitize(clicksPerTick));
		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			Files.writeString(file, GSON.toJson(object));
		} catch (IOException e) {
			// A failed save only loses the setting for the next launch.
		}
	}

	public static synchronized ChestmateConfig get() {
		if (instance == null) {
			instance = load(defaultPath());
		}
		return instance;
	}

	public static Path defaultPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("chestmate.json");
	}
}
