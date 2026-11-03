package io.github.lookyman.chestmate.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the container screen's layout. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int chestmate$getLeftPos();

	@Accessor("topPos")
	int chestmate$getTopPos();

	@Accessor("imageWidth")
	int chestmate$getImageWidth();

	@Accessor("imageHeight")
	int chestmate$getImageHeight();

	@Accessor("inventoryLabelY")
	int chestmate$getInventoryLabelY();
}
