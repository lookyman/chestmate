package io.github.lookyman.chestmate.mixin;

import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets {@code CreativeOrder} run a tab's generator without touching vanilla's global tab contents. */
@Mixin(CreativeModeTab.class)
public interface CreativeModeTabAccessor {
	@Accessor("displayItemsGenerator")
	CreativeModeTab.DisplayItemsGenerator chestmate$getDisplayItemsGenerator();
}
