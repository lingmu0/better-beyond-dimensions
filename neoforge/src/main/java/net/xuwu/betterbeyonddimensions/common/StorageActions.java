package net.xuwu.betterbeyonddimensions.common;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * All mutations are performed here, on the logical server.
 */
public final class StorageActions
{
    public static final int TOGGLE_PLAYER_SHIFT = 0;
    public static final int TOGGLE_CONTAINER_SHIFT = 1;
    public static final int DEPOSIT_CONTAINER = 2;
    public static final int DEPOSIT_PLAYER_INVENTORY = 3;

    private static final String SHIFT_PLAYER_TAG = "better_beyond_dimensions.shift_player_inventory";
    private static final String SHIFT_CONTAINER_TAG = "better_beyond_dimensions.shift_container";

    private StorageActions()
    {
    }

    public static boolean isShiftPlayerInventoryEnabled(Player player)
    {
        return player.getPersistentData().getBoolean(SHIFT_PLAYER_TAG);
    }

    public static boolean isShiftContainerEnabled(Player player)
    {
        return player.getPersistentData().getBoolean(SHIFT_CONTAINER_TAG);
    }

    public static boolean toggle(Player player, int target)
    {
        CompoundTag data = player.getPersistentData();
        if (target == TOGGLE_PLAYER_SHIFT)
        {
            boolean next = !isShiftPlayerInventoryEnabled(player);
            data.putBoolean(SHIFT_PLAYER_TAG, next);
            return next;
        }
        if (target == TOGGLE_CONTAINER_SHIFT)
        {
            boolean next = !isShiftContainerEnabled(player);
            data.putBoolean(SHIFT_CONTAINER_TAG, next);
            return next;
        }
        return false;
    }

    public static void depositPlayerInventory(ServerPlayer player)
    {
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null)
        {
            return;
        }

        Inventory inventory = player.getInventory();
        // Slots 9..35 are the main inventory. The hotbar (0..8) is intentionally excluded.
        for (int index = 9; index < inventory.getContainerSize(); index++)
        {
            depositStack(network, inventory.getItem(index));
        }
        player.containerMenu.broadcastChanges();
    }

    public static void depositContainer(ServerPlayer player)
    {
        depositContainer(player, player.containerMenu);
    }

    public static void depositContainer(ServerPlayer player, AbstractContainerMenu menu)
    {
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null || menu == null)
        {
            return;
        }

        Container playerInventory = player.getInventory();
        for (Slot slot : menu.slots)
        {
            if (slot.container != playerInventory && slot.hasItem())
            {
                depositStack(network, slot.getItem());
            }
        }
        menu.broadcastChanges();
    }

    /**
     * Intercepts a vanilla QUICK_MOVE only when the relevant sidebar option is enabled.
     * Returning true means the vanilla menu must not run its own quick-move algorithm.
     */
    public static boolean routeQuickMove(ServerPlayer player, AbstractContainerMenu menu, int slotId)
    {
        if (menu == null || slotId < 0 || slotId >= menu.slots.size())
        {
            return false;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null)
        {
            return false;
        }

        Slot slot = menu.slots.get(slotId);
        if (!slot.hasItem())
        {
            return false;
        }

        boolean fromPlayerInventory = slot.container == player.getInventory();
        boolean enabled = fromPlayerInventory
                ? isShiftPlayerInventoryEnabled(player)
                : isShiftContainerEnabled(player);
        if (!enabled)
        {
            return false;
        }

        // Even if the network is full, consume this explicit setting rather than silently
        // falling back to a different vanilla transfer destination.
        depositStack(network, slot.getItem());
        menu.broadcastChanges();
        return true;
    }

    public static void withdraw(ServerPlayer player, ItemStack requestedStack, long amount)
    {
        if (requestedStack == null || requestedStack.isEmpty() || amount <= 0L)
        {
            return;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null)
        {
            return;
        }

        ItemStackKey key = new ItemStackKey(requestedStack);
        UnifiedStorage storage = network.getUnifiedStorage();
        KeyAmount extracted = storage.extract(key, Math.min((long) Integer.MAX_VALUE, amount), false, false);
        if (extracted.amount() <= 0L)
        {
            return;
        }

        ItemStack output = key.copyStackWithCount(extracted.amount());
        int inserted = insertIntoPlayerInventory(player, output);
        long rollback = extracted.amount() - inserted;
        if (rollback > 0L)
        {
            storage.insert(key, rollback, false);
        }
        player.containerMenu.broadcastChanges();
    }

    private static int depositStack(DimensionsNet network, ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return 0;
        }

        int original = stack.getCount();
        ItemStackKey key = new ItemStackKey(stack);
        KeyAmount leftover = network.getUnifiedStorage().insert(key, original, false);
        long insertedLong = Math.max(0L, original - leftover.amount());
        int inserted = (int) Math.min((long) original, insertedLong);
        if (inserted > 0)
        {
            stack.shrink(inserted);
        }
        return inserted;
    }

    private static int insertIntoPlayerInventory(ServerPlayer player, ItemStack input)
    {
        if (input.isEmpty())
        {
            return 0;
        }

        Inventory inventory = player.getInventory();
        int remaining = input.getCount();
        int original = remaining;

        // Merge into existing compatible stacks first.
        for (int index = 0; index < inventory.getContainerSize() && remaining > 0; index++)
        {
            ItemStack existing = inventory.getItem(index);
            if (existing.isEmpty() || !sameStoredStack(existing, input))
            {
                continue;
            }
            int limit = Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize());
            int space = Math.max(0, limit - existing.getCount());
            int move = Math.min(space, remaining);
            if (move > 0)
            {
                existing.grow(move);
                remaining -= move;
            }
        }

        // Then use empty slots.
        for (int index = 0; index < inventory.getContainerSize() && remaining > 0; index++)
        {
            if (!inventory.getItem(index).isEmpty())
            {
                continue;
            }
            int limit = Math.min(input.getMaxStackSize(), inventory.getMaxStackSize());
            int move = Math.min(limit, remaining);
            ItemStack placed = input.copy();
            placed.setCount(move);
            inventory.setItem(index, placed);
            remaining -= move;
        }

        if (remaining != original)
        {
            inventory.setChanged();
        }
        return original - remaining;
    }

    private static boolean sameStoredStack(ItemStack first, ItemStack second)
    {
        return new ItemStackKey(first).equals(new ItemStackKey(second));
    }
}
