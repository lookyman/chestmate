package io.github.lookyman.chestmate.client.ui;

import io.github.lookyman.chestmate.client.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A small glyph button placed at a fixed offset from the container screen's top-left corner. {@link #reposition()}
 * runs before every frame, so the button follows the screen when the recipe book shifts it.
 */
public final class AnchoredButton extends Button {
	public static final int SIZE = 11;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int INACTIVE_TEXT_COLOR = 0xFFA0A0A0;

	private final AbstractContainerScreenAccessor anchor;
	private final int offsetX;
	private final int offsetY;

	public AnchoredButton(
		AbstractContainerScreenAccessor anchor, int offsetX, int offsetY, Component glyph, Component tooltip,
		OnPress onPress
	) {
		super(0, 0, SIZE, SIZE, glyph, onPress, narration -> AbstractWidget.wrapDefaultNarrationMessage(tooltip));
		this.anchor = anchor;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		setTooltip(Tooltip.create(tooltip));
		reposition();
	}

	public void reposition() {
		setPosition(anchor.chestmate$getLeftPos() + offsetX, anchor.chestmate$getTopPos() + offsetY);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		extractDefaultSprite(graphics);
		graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2 + 1,
			getY() + (getHeight() - 8) / 2, active ? TEXT_COLOR : INACTIVE_TEXT_COLOR);
	}
}
