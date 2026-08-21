package net.xuwu.betterbeyonddimensions.common;

import net.minecraft.world.item.ItemStack;

/** One item key and its long-sized amount in the dimension network. */
public record StorageEntry(ItemStack stack, long amount)
{
    public StorageEntry
    {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        if (!stack.isEmpty())
        {
            stack.setCount(1);
        }
        amount = Math.max(0L, amount);
    }
}
