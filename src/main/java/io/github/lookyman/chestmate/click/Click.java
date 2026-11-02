package io.github.lookyman.chestmate.click;

/**
 * A {@code ContainerInput.PICKUP} click on menu slot {@code menuSlot} with {@code button} {@link #LEFT} (0,
 * {@code ClickAction.PRIMARY}) or {@link #RIGHT} (1, {@code ClickAction.SECONDARY}).
 */
public record Click(int menuSlot, int button) {
	public static final int LEFT = 0;
	public static final int RIGHT = 1;

	public Click {
		if (button != LEFT && button != RIGHT) {
			throw new IllegalArgumentException("not a PICKUP button: " + button);
		}
	}

	public static Click left(int menuSlot) {
		return new Click(menuSlot, LEFT);
	}

	public static Click right(int menuSlot) {
		return new Click(menuSlot, RIGHT);
	}
}
