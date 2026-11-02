package io.github.lookyman.chestmate.core;

/** The four actions Chestmate offers. */
public enum Operation {
	ORGANIZE_CONTAINER(true),
	ORGANIZE_INVENTORY(false),
	PUT_IN(true),
	TAKE_OUT(true);

	private final boolean needsContainer;

	Operation(boolean needsContainer) {
		this.needsContainer = needsContainer;
	}

	public boolean supportedBy(MenuRegions regions) {
		return !needsContainer || !regions.container().isEmpty();
	}
}
