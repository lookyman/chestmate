package io.github.lookyman.chestmate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BootstrapTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.init();
	}

	@Test
	void itemsAreRegistered() {
		assertEquals(64, new ItemStack(Items.STONE).getMaxStackSize());
		assertEquals(16, new ItemStack(Items.ENDER_PEARL).getMaxStackSize());
	}
}
