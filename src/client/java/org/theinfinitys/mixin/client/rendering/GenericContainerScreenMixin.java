package org.theinfinitys.mixin.client.rendering;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin
    extends HandledScreen<GenericContainerScreenHandler> {
  public GenericContainerScreenMixin(
      GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
    super(handler, inventory, title);
  }

  @Final
  @Override
  public void init() {
    super.init();
  }
}
