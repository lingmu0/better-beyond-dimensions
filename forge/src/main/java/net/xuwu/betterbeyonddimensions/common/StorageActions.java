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
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

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

    /**
     * Fills real crafting slots for JEI's recipe-transfer button.  The client only sends the
     * selected ingredient variants and target slot ids; all item movement is performed here on
     * the logical server, using the network before the player's inventory.
     */
    public static void fillRecipe(ServerPlayer player, List<RecipeFill> fills)
    {
        if (player == null || player.containerMenu == null || fills == null || fills.isEmpty())
        {
            return;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        UnifiedStorage storage = network == null ? null : network.getUnifiedStorage();
        List<Container> changedContainers = new java.util.ArrayList<>();

        for (RecipeFill fill : fills)
        {
            if (fill == null || fill.slotId() < 0 || fill.slotId() >= player.containerMenu.slots.size())
            {
                continue;
            }

            Slot target = player.containerMenu.slots.get(fill.slotId());
            if (!(target.container instanceof CraftingContainer))
            {
                continue;
            }

            ItemStack desired = fill.stack() == null ? ItemStack.EMPTY : fill.stack().copy();
            ItemStack current = target.getItem().copy();

            if (desired.isEmpty() || fill.amount() <= 0)
            {
                if (!current.isEmpty() && canInsertIntoPlayerInventory(player, current))
                {
                    insertIntoPlayerInventory(player, current);
                    target.set(ItemStack.EMPTY);
                    addChangedContainer(changedContainers, target.container);
                }
                continue;
            }

            desired.setCount(1);
            if (!target.mayPlace(desired))
            {
                continue;
            }

            if (!current.isEmpty() && !sameStoredStack(current, desired))
            {
                if (!canInsertIntoPlayerInventory(player, current))
                {
                    continue;
                }
                insertIntoPlayerInventory(player, current);
                target.set(ItemStack.EMPTY);
                current = ItemStack.EMPTY;
                addChangedContainer(changedContainers, target.container);
            }

            int limit = Math.min(target.getMaxStackSize(desired), desired.getMaxStackSize());
            int requested = Math.min(Math.max(1, fill.amount()), Math.max(1, limit));
            int currentCount = current.isEmpty() ? 0 : current.getCount();
            int missing = Math.max(0, requested - currentCount);
            if (missing <= 0)
            {
                continue;
            }

            int inserted = 0;
            if (storage != null)
            {
                ItemStackKey key = new ItemStackKey(desired);
                KeyAmount extracted = storage.extract(key, missing, false, false);
                if (extracted.amount() > 0L)
                {
                    inserted = (int) Math.min((long) missing, extracted.amount());
                }
            }

            if (inserted < missing)
            {
                inserted += removeFromPlayerInventory(player, desired, missing - inserted);
            }

            if (inserted > 0)
            {
                ItemStack result = current.isEmpty() ? desired.copy() : current.copy();
                result.setCount(currentCount + inserted);
                target.set(result);
                addChangedContainer(changedContainers, target.container);
            }
        }

        for (Container container : changedContainers)
        {
            player.containerMenu.slotsChanged(container);
        }
        player.containerMenu.broadcastChanges();
    }

    /**
     * Handles the overlay storage slot using the same basic left/right rules as Beyond Dimensions.
     * The server menu's carried stack is authoritative, so the cursor can continue into vanilla
     * container slots after taking an item from the sidebar.
     */
    public static void clickSidebar(ServerPlayer player, ItemStack requestedStack, int button)
    {
        if (button != 0 && button != 1)
        {
            return;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null || player.containerMenu == null)
        {
            return;
        }

        UnifiedStorage storage = network.getUnifiedStorage();
        ItemStack requested = requestedStack == null ? ItemStack.EMPTY : requestedStack.copy();
        if (!requested.isEmpty())
        {
            requested.setCount(1);
        }

        ItemStackKey clickedKey = requested.isEmpty() ? null : new ItemStackKey(requested);
        KeyAmount clicked = clickedKey == null ? null : storage.getStackByKey(clickedKey);
        if (clicked != null && clicked.isEmpty())
        {
            clickedKey = null;
            clicked = null;
        }

        ItemStack carried = player.containerMenu.getCarried().copy();
        if (carried.isEmpty())
        {
            if (clickedKey == null || clicked == null)
            {
                return;
            }

            long maxStack = Math.max(1L, clickedKey.getVanillaMaxStackSize());
            long requestedAmount = Math.min(clicked.amount(), maxStack);
            long amount = button == 0 ? requestedAmount : (requestedAmount + 1L) / 2L;
            KeyAmount extracted = storage.extract(clickedKey, amount, false, false);
            if (extracted.key() instanceof ItemStackKey itemKey && extracted.amount() > 0L)
            {
                player.containerMenu.setCarried(itemKey.copyStackWithCount(extracted.amount()));
            }
        }
        else
        {
            ItemStackKey carriedKey = new ItemStackKey(carried);
            // The sidebar is a storage view, not a two-slot swap surface.  Match the native
            // Beyond Dimensions storage interaction: a carried stack is always inserted into
            // the network, even when the clicked cell contains a different item.
            insertCarried(storage, player, carried, carriedKey, button);
        }

        player.containerMenu.broadcastChanges();
    }

    private static void insertCarried(UnifiedStorage storage, ServerPlayer player, ItemStack carried,
                                      ItemStackKey carriedKey, int button)
    {
        long requestedAmount = button == 0 ? carried.getCount() : 1L;
        KeyAmount remaining = storage.insert(carriedKey, requestedAmount, false);
        long inserted = Math.max(0L, Math.min(requestedAmount, requestedAmount - remaining.amount()));
        if (inserted <= 0L)
        {
            return;
        }

        long newCount = carried.getCount() - inserted;
        if (newCount <= 0L)
        {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        else
        {
            ItemStack newCarried = carried.copy();
            newCarried.setCount((int) newCount);
            player.containerMenu.setCarried(newCarried);
        }
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

    private static int removeFromPlayerInventory(ServerPlayer player, ItemStack requested, int amount)
    {
        if (requested == null || requested.isEmpty() || amount <= 0)
        {
            return 0;
        }

        Inventory inventory = player.getInventory();
        int remaining = amount;
        for (int index = 0; index < inventory.getContainerSize() && remaining > 0; index++)
        {
            ItemStack existing = inventory.getItem(index);
            if (existing.isEmpty() || !sameStoredStack(existing, requested))
            {
                continue;
            }

            int move = Math.min(existing.getCount(), remaining);
            existing.shrink(move);
            remaining -= move;
        }

        if (remaining != amount)
        {
            inventory.setChanged();
        }
        return amount - remaining;
    }

    private static boolean canInsertIntoPlayerInventory(ServerPlayer player, ItemStack input)
    {
        if (input == null || input.isEmpty())
        {
            return true;
        }

        Inventory inventory = player.getInventory();
        int remaining = input.getCount();
        for (int index = 0; index < inventory.getContainerSize() && remaining > 0; index++)
        {
            ItemStack existing = inventory.getItem(index);
            if (existing.isEmpty() || !sameStoredStack(existing, input))
            {
                continue;
            }
            int limit = Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize());
            remaining -= Math.min(Math.max(0, limit - existing.getCount()), remaining);
        }

        for (int index = 0; index < inventory.getContainerSize() && remaining > 0; index++)
        {
            if (inventory.getItem(index).isEmpty())
            {
                remaining -= Math.min(input.getMaxStackSize(), remaining);
            }
        }
        return remaining <= 0;
    }

    private static void addChangedContainer(List<Container> containers, Container container)
    {
        if (container != null && !containers.contains(container))
        {
            containers.add(container);
        }
    }

    private static boolean sameStoredStack(ItemStack first, ItemStack second)
    {
        return new ItemStackKey(first).equals(new ItemStackKey(second));
    }
}
