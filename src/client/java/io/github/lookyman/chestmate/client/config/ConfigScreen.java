package io.github.lookyman.chestmate.client.config;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Click-speed settings screen built from vanilla widgets. */
public final class ConfigScreen extends Screen {
	static final double[] STEPS = {0.2, 0.25, 1.0 / 3.0, 0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

	private final Screen parent;
	private final ChestmateConfig config = ChestmateConfig.get();

	public ConfigScreen(Screen parent) {
		super(Component.translatable("chestmate.config.title"));
		this.parent = parent;
	}

	static int nearestStep(double clicksPerTick) {
		int best = 0;
		for (int i = 1; i < STEPS.length; i++) {
			if (Math.abs(STEPS[i] - clicksPerTick) < Math.abs(STEPS[best] - clicksPerTick)) {
				best = i;
			}
		}
		return best;
	}

	static Component label(double clicksPerTick) {
		double perSecond = clicksPerTick * 20;
		String text = perSecond == Math.rint(perSecond)
			? Long.toString(Math.round(perSecond))
			: String.format(Locale.ROOT, "%.1f", perSecond);
		return Component.translatable(
			clicksPerTick <= ChestmateConfig.DEFAULT_CLICKS_PER_TICK
				? "chestmate.config.click_speed.safe"
				: "chestmate.config.click_speed",
			text);
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		addRenderableWidget(new SpeedSlider(centerX - 100, this.height / 2 - 20, 200, 20, config));
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
			.bounds(centerX - 100, this.height / 2 + 10, 200, 20)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 50, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		config.save(ChestmateConfig.defaultPath());
		this.minecraft.gui.setScreen(parent);
	}

	private static final class SpeedSlider extends AbstractSliderButton {
		private final ChestmateConfig config;

		SpeedSlider(int x, int y, int width, int height, ChestmateConfig config) {
			super(x, y, width, height, label(config.clicksPerTick),
				nearestStep(config.clicksPerTick) / (double) (STEPS.length - 1));
			this.config = config;
		}

		@Override
		protected void updateMessage() {
			setMessage(label(config.clicksPerTick));
		}

		@Override
		protected void applyValue() {
			int index = (int) Math.round(this.value * (STEPS.length - 1));
			this.value = index / (double) (STEPS.length - 1);
			config.clicksPerTick = STEPS[index];
		}
	}
}
