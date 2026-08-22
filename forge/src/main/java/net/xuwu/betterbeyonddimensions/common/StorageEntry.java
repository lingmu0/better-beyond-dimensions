package net.xuwu.betterbeyonddimensions.common;

import net.minecraft.world.item.ItemStack;

/** One item key, amount, and the UI timestamps used by Beyond Dimensions sorting. */
public record StorageEntry(ItemStack stack, long amount, long insertedTime, long modifiedTime)
{
    public StorageEntry(ItemStack stack, long amount)
    {
        this(stack, amount, 0L, 0L);
    }

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
