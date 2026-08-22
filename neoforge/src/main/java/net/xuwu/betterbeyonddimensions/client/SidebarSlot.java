package net.xuwu.betterbeyonddimensions.client;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A client-side Slot for one visible cell in the sidebar.
 *
 * <p>The storage itself remains server authoritative.  The Slot deliberately exposes the
 * current network stack to screen integrations (JEI, hover handling, etc.), while mutations
 * are routed through the same sidebar click packets as Beyond Dimensions' storage slots.</p>
 */
public final class SidebarSlot extends Slot
{
    private static final Container EMPTY_CONTAINER = new SimpleContainer(0);

    private final Object owner;
    private final int visualIndex;
    private ItemStackKey key;
    private ItemStack renderStack = ItemStack.EMPTY;
    private long amount;
    private int storageIndex = -1;
    private boolean active;

    public SidebarSlot(Object owner, int visualIndex, int x, int y)
    {
        super(EMPTY_CONTAINER, 0, x, y);
        this.owner = owner;
        this.visualIndex = visualIndex;
    }

    public void update(int storageIndex, ClientStorageView.Entry entry, boolean active)
    {
        clear();
        this.active = active;
        if (!active || entry == null || entry.key() == null || entry.amount() <= 0L)
        {
            return;
        }

        this.storageIndex = storageIndex;
        this.key = entry.key();
        this.amount = entry.amount();
        this.renderStack = this.key.getRenderStack().copy();
    }

    public void clear()
    {
        this.storageIndex = -1;
        this.key = null;
        this.renderStack = ItemStack.EMPTY;
        this.amount = 0L;
        this.active = false;
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

    @Override
    public ItemStack getItem()
    {
        if (!hasItem())
        {
            return ItemStack.EMPTY;
        }

        ItemStack stack = renderStack.copy();
        stack.setCount((int) Math.min((long) stack.getMaxStackSize(), amount));
        return stack;
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
        // Sidebar mutations are server-authoritative and go through NetworkHandler.
    }

    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack)
    {
        // Sidebar mutations are server-authoritative and go through NetworkHandler.
    }

    @Override
    public ItemStack remove(int amount)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public void setChanged()
    {
        // The snapshot update is the change notification for this client-side Slot.
    }

    @Override
    public boolean isActive()
    {
        return active;
    }

    @Override
    public boolean isSameInventory(Slot other)
    {
        return other instanceof SidebarSlot sidebarSlot && sidebarSlot.owner == owner;
    }
}
