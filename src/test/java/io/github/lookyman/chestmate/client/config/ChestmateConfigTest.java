package io.github.lookyman.chestmate.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChestmateConfigTest {
	@TempDir
	Path dir;

	private ChestmateConfig loadJson(String json) throws IOException {
		Path file = dir.resolve("chestmate.json");
		Files.writeString(file, json);
		return ChestmateConfig.load(file);
	}

	@Test
	void defaultsWhenMissing() {
		assertEquals(1.0, ChestmateConfig.load(dir.resolve("missing.json")).clicksPerTick);
	}

	@Test
	void clampsOutOfRange() throws IOException {
		assertEquals(10.0, loadJson("{\"clicksPerTick\": 50}").clicksPerTick);
		assertEquals(0.2, loadJson("{\"clicksPerTick\": 0}").clicksPerTick);
		assertEquals(0.2, loadJson("{\"clicksPerTick\": 0.05}").clicksPerTick);
	}

	@Test
	void nonFiniteOrNegativeGivesDefault() throws IOException {
		assertEquals(1.0, loadJson("{\"clicksPerTick\": NaN}").clicksPerTick);
		assertEquals(1.0, loadJson("{\"clicksPerTick\": Infinity}").clicksPerTick);
		assertEquals(1.0, loadJson("{\"clicksPerTick\": -Infinity}").clicksPerTick);
		assertEquals(1.0, loadJson("{\"clicksPerTick\": -3}").clicksPerTick);
		assertEquals(1.0, loadJson("{\"clicksPerTick\": \"abc\"}").clicksPerTick);
	}

	@Test
	void roundTrip() {
		Path file = dir.resolve("out.json");
		ChestmateConfig config = new ChestmateConfig();
		config.clicksPerTick = 3.0;
		config.save(file);
		assertEquals(3.0, ChestmateConfig.load(file).clicksPerTick);
	}

	@Test
	void corruptFileGivesDefaults() throws IOException {
		assertEquals(1.0, loadJson("{not json at all").clicksPerTick);
		assertEquals(1.0, loadJson("").clicksPerTick);
	}
}
