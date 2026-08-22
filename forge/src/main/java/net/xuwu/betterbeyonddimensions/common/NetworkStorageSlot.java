package net.xuwu.betterbeyonddimensions.common;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A real menu slot whose contents are backed by the Beyond Dimensions network.
 * The long amount and mutations are handled by the server storage actions.
 */
public final class NetworkStorageSlot extends Slot
{
    private static final Container EMPTY_CONTAINER = new SimpleContainer(0);

    private final AbstractContainerMenu owner;
    private final int visualIndex;
    private ItemStackKey key;
    private ItemStack renderStack = ItemStack.EMPTY;
    private long amount;
    private int storageIndex = -1;
    private boolean active;

    public NetworkStorageSlot(AbstractContainerMenu owner, int visualIndex, int x, int y)
    {
        super(EMPTY_CONTAINER, 0, x, y);
        this.owner = owner;
        this.visualIndex = visualIndex;
    }

    public void update(int storageIndex, ItemStackKey key, long amount, boolean active)
    {
        this.storageIndex = -1;
        this.key = null;
        this.renderStack = ItemStack.EMPTY;
        this.amount = 0L;
        this.active = active;

        if (!active || key == null || key.isEmpty() || amount <= 0L)
        {
            return;
        }

        this.storageIndex = storageIndex;
        this.key = key;
        this.amount = amount;
        this.renderStack = key.getRenderStack().copy();
    }

    public void clear()
    {
        update(-1, null, 0L, false);
    }

    public int getVisualIndex()
    {
        return visualIndex;
    }

    public int getStorageIndex()
    {
        return storageIndex;
    }

    public ItemStackKey getKey()
    {
        return key;
    }

    public long getStoredAmount()
    {
        return amount;
    }

    public ItemStack copyViewStack()
    {
        return key == null ? ItemStack.EMPTY : key.copyStack();
    }

    @Override
    public ItemStack getItem()
    {
        if (!hasItem())
        {
            return ItemStack.EMPTY;
        }

        long count = Math.min(amount, Math.max(1L, key.getVanillaMaxStackSize()));
        return key.copyStackWithCount(count);
    }

    @Override
    public boolean hasItem()
    {
        return active && storageIndex >= 0 && amount > 0L && !renderStack.isEmpty();
    }

    @Override
    public boolean mayPlace(ItemStack stack)
    {
        return active && stack != null && !stack.isEmpty();
    }

    @Override
    public boolean mayPickup(Player player)
    {
        return active && hasItem();
    }

    @Override
    public int getMaxStackSize()
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaxStackSize(ItemStack stack)
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public void set(ItemStack stack)
    {
        // Mutations go through StorageActions.
    }

    @Override
    public ItemStack remove(int amount)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public void setChanged()
    {
        // Storage mutations explicitly broadcast the menu and snapshot.
    }

    @Override
    public int getContainerSlot()
    {
        return visualIndex;
    }

    @Override
    public boolean isActive()
    {
        return active;
    }

    @Override
    public boolean isSameInventory(Slot other)
    {
        return other instanceof NetworkStorageSlot networkSlot && networkSlot.owner == owner;
    }
}
