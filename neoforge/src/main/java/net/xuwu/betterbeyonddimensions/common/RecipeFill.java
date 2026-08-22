package net.xuwu.betterbeyonddimensions.common;

import net.minecraft.world.item.ItemStack;

/** One client-planned ingredient fill for a real crafting slot. */
public record RecipeFill(int slotId, ItemStack stack, int amount)
{
    public RecipeFill
    {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        amount = Math.max(0, amount);
    }
}
