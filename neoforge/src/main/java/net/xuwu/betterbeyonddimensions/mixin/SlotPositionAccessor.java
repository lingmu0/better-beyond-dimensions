package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Client-side accessor used to keep real sidebar slot hitboxes aligned while dragging. */
@Mixin(Slot.class)
public interface SlotPositionAccessor
{
    @Mutable
    @Accessor("x")
    void bbd$setX(int x);

    @Mutable
    @Accessor("y")
    void bbd$setY(int y);
}
