package io.github.lookyman.chestmate.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.lookyman.chestmate.client.ui.ScreenButtons.ButtonSpec;
import io.github.lookyman.chestmate.core.Operation;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenButtonsTest {
	@Test
	void chestLayoutPutsOrganizeInTitleRowAndThreeButtonsInInventoryRow() {
		// 3-row chest: imageWidth 176, imageHeight 168, inventoryLabelY = 168 - 94 = 74
		assertEquals(List.of(
			new ButtonSpec(Operation.ORGANIZE_CONTAINER, 158, 4),
			new ButtonSpec(Operation.ORGANIZE_INVENTORY, 158, 72),
			new ButtonSpec(Operation.TAKE_OUT, 146, 72),
			new ButtonSpec(Operation.PUT_IN, 134, 72)
		), ScreenButtons.containerLayout(176, 74));
	}

	@Test
	void containerLayoutFollowsInventoryLabelRow() {
		// 6-row chest: imageHeight 222, inventoryLabelY 128
		List<ButtonSpec> layout = ScreenButtons.containerLayout(176, 128);
		assertEquals(new ButtonSpec(Operation.ORGANIZE_CONTAINER, 158, 4), layout.get(0));
		for (ButtonSpec spec : layout.subList(1, layout.size())) {
			assertEquals(126, spec.dy());
		}
	}

	@Test
	void inventoryRowReadsPutInTakeOutOrganizeLeftToRightWithoutOverlap() {
		List<ButtonSpec> row = ScreenButtons.containerLayout(176, 74).subList(1, 4);
		ButtonSpec organize = row.get(0);
		ButtonSpec takeOut = row.get(1);
		ButtonSpec putIn = row.get(2);
		assertTrue(putIn.dx() + AnchoredButton.SIZE < takeOut.dx());
		assertTrue(takeOut.dx() + AnchoredButton.SIZE < organize.dx());
		assertEquals(176 - 7, organize.dx() + AnchoredButton.SIZE);
	}

	@Test
	void inventoryScreenGetsOnlyOrganizeInventory() {
		assertEquals(List.of(new ButtonSpec(Operation.ORGANIZE_INVENTORY, 158, 70)), ScreenButtons.inventoryLayout(176));
	}

	@Test
	void glyphsAndTooltipKeys() {
		assertEquals("⇅", ScreenButtons.glyph(Operation.ORGANIZE_CONTAINER));
		assertEquals("⇅", ScreenButtons.glyph(Operation.ORGANIZE_INVENTORY));
		assertEquals("▲", ScreenButtons.glyph(Operation.PUT_IN));
		assertEquals("▼", ScreenButtons.glyph(Operation.TAKE_OUT));
		assertEquals("chestmate.button.organize_container", ScreenButtons.tooltipKey(Operation.ORGANIZE_CONTAINER));
		assertEquals("chestmate.button.organize_inventory", ScreenButtons.tooltipKey(Operation.ORGANIZE_INVENTORY));
		assertEquals("chestmate.button.put_in", ScreenButtons.tooltipKey(Operation.PUT_IN));
		assertEquals("chestmate.button.take_out", ScreenButtons.tooltipKey(Operation.TAKE_OUT));
	}

	@Test
	void everyButtonAndShortcutKeyIsTranslated() throws Exception {
		JsonObject lang;
		try (Reader reader = new InputStreamReader(
			ScreenButtonsTest.class.getResourceAsStream("/assets/chestmate/lang/en_us.json"), StandardCharsets.UTF_8)) {
			lang = JsonParser.parseReader(reader).getAsJsonObject();
		}
		for (Operation op : Operation.values()) {
			assertTrue(lang.has(ScreenButtons.tooltipKey(op)), op.name());
		}
		for (String key : Shortcuts.KEY_NAMES) {
			assertTrue(lang.has(key), key);
		}
		assertTrue(lang.has(Shortcuts.CATEGORY_LABEL_KEY));
		assertEquals("Chestmate", lang.get(Shortcuts.CATEGORY_LABEL_KEY).getAsString());
	}
}
