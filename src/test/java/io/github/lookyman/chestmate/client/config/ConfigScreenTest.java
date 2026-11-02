package io.github.lookyman.chestmate.client.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class ConfigScreenTest {
	private static final int LAST = ConfigScreen.STEPS.length - 1;

	@Test
	void nearestStepSnapsToTheClosestStep() {
		assertEquals(0, ConfigScreen.nearestStep(0.2));
		assertEquals(0, ConfigScreen.nearestStep(0.01));
		assertEquals(4, ConfigScreen.nearestStep(1.0));
		assertEquals(4, ConfigScreen.nearestStep(1.4));
		assertEquals(5, ConfigScreen.nearestStep(1.6));
		assertEquals(LAST, ConfigScreen.nearestStep(10.0));
		assertEquals(LAST, ConfigScreen.nearestStep(50.0));
	}

	@Test
	void stepIndexFromSliderValue() {
		assertEquals(0, ConfigScreen.stepIndex(0.0));
		assertEquals(LAST, ConfigScreen.stepIndex(1.0));
		assertEquals(4, ConfigScreen.stepIndex(ConfigScreen.sliderValue(4)));
		assertEquals(4, ConfigScreen.stepIndex(ConfigScreen.sliderValue(4) + 0.01));
	}

	@Test
	void keyboardMovesOneStepAndStopsAtTheEnds() {
		assertEquals(5, ConfigScreen.moveStep(4, 1));
		assertEquals(3, ConfigScreen.moveStep(4, -1));
		assertEquals(0, ConfigScreen.moveStep(0, -1));
		assertEquals(LAST, ConfigScreen.moveStep(LAST, 1));
	}

	@Test
	void everyStepIsReachableByKeyboardFromTheStart() {
		int index = 0;
		double[] visited = new double[ConfigScreen.STEPS.length];
		visited[0] = ConfigScreen.STEPS[0];
		for (int i = 1; i <= LAST; i++) {
			index = ConfigScreen.stepIndex(ConfigScreen.sliderValue(ConfigScreen.moveStep(index, 1)));
			visited[i] = ConfigScreen.STEPS[index];
		}
		assertArrayEquals(ConfigScreen.STEPS, visited);
	}

	@Test
	void labelShowsClicksPerSecond() {
		assertLabel("chestmate.config.click_speed.safe", "4", ConfigScreen.label(0.2));
		assertLabel("chestmate.config.click_speed.safe", "6.7", ConfigScreen.label(1.0 / 3.0));
		assertLabel("chestmate.config.click_speed.safe", "20", ConfigScreen.label(1.0));
		assertLabel("chestmate.config.click_speed", "200", ConfigScreen.label(10.0));
	}

	@Test
	void sliderLabelShowsTheSnappedValue() {
		assertLabel("chestmate.config.click_speed", "40", ConfigScreen.sliderLabel(ConfigScreen.sliderValue(5) + 0.02));
		assertLabel("chestmate.config.click_speed.safe", "20", ConfigScreen.snappedLabel(1.2));
	}

	private static void assertLabel(String key, String perSecond, Component label) {
		TranslatableContents contents = (TranslatableContents) label.getContents();
		assertEquals(key, contents.getKey());
		assertEquals(perSecond, contents.getArgs()[0]);
	}
}
