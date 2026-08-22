package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Client-only access to the menu Slot list, so JEI can see the sidebar cells. */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor
{
    @Invoker("addSlot")
    Slot bbd$addSlot(Slot slot);
}
