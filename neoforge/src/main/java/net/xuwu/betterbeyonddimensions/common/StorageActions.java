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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * All mutations are performed here, on the logical server.
 */
public final class StorageActions
{
    private static final int MAX_RECIPE_TRANSFER_SETS = 4096;

    public static final int TOGGLE_PLAYER_SHIFT = 0;
    public static final int TOGGLE_CONTAINER_SHIFT = 1;
    public static final int DEPOSIT_CONTAINER = 2;
    public static final int DEPOSIT_PLAYER_INVENTORY = 3;
    public static final int HIDE_SIDEBAR = 4;
    public static final int SHOW_SIDEBAR = 5;

    private static final String SHIFT_PLAYER_TAG = "better_beyond_dimensions.shift_player_inventory";
    private static final String SHIFT_CONTAINER_TAG = "better_beyond_dimensions.shift_container";
    private static final String SIDEBAR_HIDDEN_TAG = "better_beyond_dimensions.sidebar_hidden";

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

    public static void setShiftSettings(Player player, boolean shiftPlayer, boolean shiftContainer)
    {
        if (player == null)
        {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putBoolean(SHIFT_PLAYER_TAG, shiftPlayer);
        data.putBoolean(SHIFT_CONTAINER_TAG, shiftContainer);
    }

    public static boolean isSidebarHidden(Player player)
    {
        return player.getPersistentData().getBoolean(SIDEBAR_HIDDEN_TAG);
    }

    public static void setSidebarHidden(Player player, boolean hidden)
    {
        player.getPersistentData().putBoolean(SIDEBAR_HIDDEN_TAG, hidden);
        if (hidden && player instanceof ServerPlayer serverPlayer)
        {
            clearSidebarSlots(serverPlayer);
        }
    }

    public static boolean toggle(Player player, int target)
    {
        if (target == HIDE_SIDEBAR || target == SHOW_SIDEBAR)
        {
            boolean hidden = target == HIDE_SIDEBAR;
            setSidebarHidden(player, hidden);
            return hidden;
        }
        if (isSidebarHidden(player))
        {
            return false;
        }

        if (target == TOGGLE_PLAYER_SHIFT)
        {
            boolean next = !isShiftPlayerInventoryEnabled(player);
            setShiftSettings(player, next, isShiftContainerEnabled(player));
            return next;
        }
        if (target == TOGGLE_CONTAINER_SHIFT)
        {
            boolean next = !isShiftContainerEnabled(player);
            setShiftSettings(player, isShiftPlayerInventoryEnabled(player), next);
            return next;
        }
        return false;
    }

    public static void depositPlayerInventory(ServerPlayer player)
    {
        if (isSidebarHidden(player))
        {
            return;
        }
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null)
        {
            return;
        }

        Inventory inventory = player.getInventory();
        // Slots 9..35 are the main inventory. The hotbar (0..8), armor (36..39), and
        // offhand (40) are intentionally excluded.
        for (int index = 9; index < 36; index++)
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
        if (isSidebarHidden(player))
        {
            return;
        }
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null || menu == null)
        {
            return;
        }

        Container playerInventory = player.getInventory();
        List<Slot> inputSlots = new ArrayList<>();
        List<Slot> normalSlots = new ArrayList<>();
        List<Slot> unknownSlots = new ArrayList<>();
        List<Slot> outputSlots = new ArrayList<>();
        for (Slot slot : menu.slots)
        {
            if (slot instanceof NetworkStorageSlot
                    || slot.container == playerInventory
                    || !slot.hasItem())
            {
                continue;
            }

            switch (classifyDepositSlot(slot))
            {
                case INPUT -> inputSlots.add(slot);
                case NORMAL -> normalSlots.add(slot);
                case UNKNOWN -> unknownSlots.add(slot);
                case OUTPUT -> outputSlots.add(slot);
            }
        }

        // Keep recipe ingredients ahead of ordinary storage slots and outputs. Removing an
        // ingredient first also lets crafting menus invalidate their result before the output
        // pass reaches it.
        for (List<Slot> slots : List.of(inputSlots, normalSlots, unknownSlots))
        {
            for (Slot slot : slots)
            {
                transferSlotToNetwork(player, menu, slot, network);
            }
        }
        for (Slot slot : outputSlots)
        {
            if (slot instanceof ResultSlot resultSlot)
            {
                quickCraftResultToNetwork(player, menu, resultSlot, network);
            }
            else
            {
                quickTransferOutputToNetwork(player, menu, slot, network);
            }
        }
        menu.broadcastChanges();
    }

    private static DepositSlotKind classifyDepositSlot(Slot slot)
    {
        if (slot instanceof ResultSlot || !slot.mayPlace(slot.getItem()))
        {
            return DepositSlotKind.OUTPUT;
        }
        if (slot.container instanceof CraftingContainer)
        {
            return DepositSlotKind.INPUT;
        }
        if (slot.getClass() == Slot.class)
        {
            return DepositSlotKind.NORMAL;
        }
        return DepositSlotKind.UNKNOWN;
    }

    private enum DepositSlotKind
    {
        INPUT,
        NORMAL,
        UNKNOWN,
        OUTPUT
    }

    /**
     * Intercepts a vanilla QUICK_MOVE only when the relevant sidebar option is enabled.
     * Returning true means the vanilla menu must not run its own quick-move algorithm.
     */
    public static boolean routeQuickMove(ServerPlayer player, AbstractContainerMenu menu, int slotId)
    {
        if (isSidebarHidden(player)
                || (player.isCreative() && menu == player.inventoryMenu)
                || menu == null || slotId < 0 || slotId >= menu.slots.size())
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

        if (slot instanceof ResultSlot resultSlot)
        {
            quickCraftResultToNetwork(player, menu, resultSlot, network);
            return true;
        }

        // Even if the network is full, consume this explicit setting rather than silently
        // falling back to a different vanilla transfer destination.
        if (!slot.mayPlace(slot.getItem()))
        {
            quickTransferOutputToNetwork(player, menu, slot, network);
        }
        else
        {
            transferSlotToNetwork(player, menu, slot, network);
        }
        menu.broadcastChanges();
        return true;
    }

    /** Mirrors vanilla quick-move repetition for non-ResultSlot machine/mod output slots. */
    private static void quickTransferOutputToNetwork(ServerPlayer player, AbstractContainerMenu menu,
                                                     Slot slot, DimensionsNet network)
    {
        if (slot == null || !slot.hasItem())
        {
            return;
        }

        ItemStack initialOutput = slot.getItem().copy();
        for (int iteration = 0; iteration < 4096 && slot.hasItem(); iteration++)
        {
            if (!sameStoredStack(initialOutput, slot.getItem())
                    || transferSlotToNetwork(player, menu, slot, network) <= 0)
            {
                break;
            }
        }
    }

    /**
     * Removes a stack through the Slot API so vanilla and modded output slots receive their
     * normal onTake callbacks (furnace XP, recipe bookkeeping, machine side effects, etc.).
     */
    private static int transferSlotToNetwork(ServerPlayer player, AbstractContainerMenu menu,
                                             Slot slot, DimensionsNet network)
    {
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player))
        {
            return 0;
        }

        ItemStack source = slot.getItem().copy();
        int requested = source.getCount();
        ItemStackKey key = new ItemStackKey(source);
        UnifiedStorage storage = network.getUnifiedStorage();
        KeyAmount remaining = storage.insert(key, requested, false);
        int inserted = (int) Math.min((long) requested,
                Math.max(0L, requested - remaining.amount()));
        if (inserted <= 0)
        {
            return 0;
        }

        // Output slots must be taken atomically. Partially taking a multi-item recipe result
        // could consume the full recipe while only storing part of its output.
        if (!slot.mayPlace(source) && inserted != requested)
        {
            storage.extract(key, inserted, false, false);
            return 0;
        }

        ItemStack taken = slot.remove(inserted);
        int takenCount = taken.isEmpty() ? 0 : Math.min(inserted, taken.getCount());
        if (takenCount < inserted)
        {
            storage.extract(key, inserted - takenCount, false, false);
        }
        if (takenCount <= 0)
        {
            return 0;
        }

        if (taken.getCount() != takenCount)
        {
            taken.setCount(takenCount);
        }
        slot.onTake(player, taken);
        slot.setChanged();
        // TransientCraftingContainer#setChanged is empty in both target versions.
        menu.slotsChanged(slot.container);
        return takenCount;
    }

    /**
     * Moves complete crafting results into the network and then lets ResultSlot perform
     * the vanilla take bookkeeping. Calling onTake is essential: it consumes ingredients,
     * handles recipe remainders, fires crafted hooks, and refreshes the next result.
     */
    private static void quickCraftResultToNetwork(ServerPlayer player, AbstractContainerMenu menu,
                                                  ResultSlot resultSlot, DimensionsNet network)
    {
        UnifiedStorage storage = network.getUnifiedStorage();
        for (int iteration = 0; iteration < 4096 && resultSlot.hasItem(); iteration++)
        {
            if (!resultSlot.mayPickup(player))
            {
                break;
            }

            ItemStack output = resultSlot.getItem().copy();
            int outputCount = output.getCount();
            if (outputCount <= 0)
            {
                break;
            }

            ItemStackKey key = new ItemStackKey(output);
            KeyAmount remaining = storage.insert(key, outputCount, false);
            long inserted = Math.max(0L, outputCount - remaining.amount());
            if (inserted != outputCount)
            {
                if (inserted > 0L)
                {
                    storage.extract(key, inserted, false, false);
                }
                break;
            }

            ItemStack taken = resultSlot.remove(outputCount);
            if (taken.isEmpty())
            {
                storage.extract(key, outputCount, false, false);
                break;
            }
            resultSlot.onTake(player, taken);
        }
        menu.broadcastChanges();
    }

    /** Validates and applies a client-side sidebar view to the server's real menu slots. */
    public static void updateSidebarView(ServerPlayer player, List<ItemStack> viewStacks)
    {
        if (player == null || player.containerMenu == null
                || !(player.containerMenu instanceof NetworkStorageMenuAccess access))
        {
            return;
        }

        if (isSidebarHidden(player))
        {
            clearSidebarSlots(player);
            return;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        UnifiedStorage storage = network == null ? null : network.getUnifiedStorage();
        List<NetworkStorageSlot> slots = access.bbd$getNetworkSlots();
        for (int index = 0; index < slots.size(); index++)
        {
            ItemStack requested = viewStacks != null && index < viewStacks.size()
                    ? viewStacks.get(index) : ItemStack.EMPTY;
            if (storage == null || requested == null || requested.isEmpty())
            {
                slots.get(index).clear();
                continue;
            }

            ItemStackKey key = new ItemStackKey(requested);
            KeyAmount stored = storage.getStackByKey(key);
            if (stored == null || stored.isEmpty() || !(stored.key() instanceof ItemStackKey storedKey))
            {
                slots.get(index).clear();
                continue;
            }

            slots.get(index).update(index, storedKey, stored.amount(), true);
        }
        player.containerMenu.broadcastChanges();
    }

    /** Refreshes the server's visible slot contents after a storage mutation. */
    public static void refreshSidebarSlots(ServerPlayer player)
    {
        if (player == null || player.containerMenu == null
                || !(player.containerMenu instanceof NetworkStorageMenuAccess access))
        {
            return;
        }

        DimensionsNet network = isSidebarHidden(player) ? null : DimensionsNet.getNetFromPlayer(player);
        UnifiedStorage storage = network == null ? null : network.getUnifiedStorage();
        for (NetworkStorageSlot slot : access.bbd$getNetworkSlots())
        {
            ItemStackKey key = slot.getKey();
            if (storage == null || key == null || !slot.isActive())
            {
                if (slot.hasItem())
                {
                    slot.clear();
                }
                continue;
            }

            KeyAmount stored = storage.getStackByKey(key);
            if (stored == null || stored.isEmpty() || !(stored.key() instanceof ItemStackKey storedKey))
            {
                slot.clear();
            }
            else
            {
                slot.update(slot.getStorageIndex(), storedKey, stored.amount(), true);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    /** Handles both the custom native-style click packet and vanilla fallback menu clicks. */
    public static void handleSidebarClick(ServerPlayer player, int slotId, int button, ClickType clickType)
    {
        if (player == null || isSidebarHidden(player) || player.containerMenu == null
                || slotId < 0 || slotId >= player.containerMenu.slots.size()
                || !(player.containerMenu.slots.get(slotId) instanceof NetworkStorageSlot slot))
        {
            return;
        }

        switch (clickType)
        {
            case PICKUP -> clickSidebar(player, slot, button);
            case QUICK_MOVE -> quickMoveSidebar(player, slot);
            case THROW -> throwFromSidebar(player, slot, button);
            case PICKUP_ALL -> pickupAllFromSidebar(player, slot);
            case SWAP -> swapHotbarWithSidebar(player, slot, button);
            case QUICK_CRAFT, CLONE -> {
                // Drag distribution and creative clone are intentionally not allowed to invoke
                // Slot.set on a storage view. A normal click remains fully interactive.
            }
        }
        player.containerMenu.broadcastChanges();
    }

    public static void handleSidebarClick(ServerPlayer player, NetworkStorageSlot slot, int button,
                                          ClickType clickType)
    {
        if (player == null || isSidebarHidden(player) || player.containerMenu == null || slot == null)
        {
            return;
        }

        switch (clickType)
        {
            case PICKUP -> clickSidebar(player, slot, button);
            case QUICK_MOVE -> quickMoveSidebar(player, slot);
            case THROW -> throwFromSidebar(player, slot, button);
            case PICKUP_ALL -> pickupAllFromSidebar(player, slot);
            case SWAP -> swapHotbarWithSidebar(player, slot, button);
            case QUICK_CRAFT, CLONE -> {
            }
        }
        player.containerMenu.broadcastChanges();
    }

    /** Shift-clicking a sidebar cell transfers exactly one vanilla-sized group to the player. */
    public static void quickMoveSidebar(ServerPlayer player, NetworkStorageSlot slot)
    {
        if (player == null || isSidebarHidden(player) || slot == null || slot.getKey() == null)
        {
            return;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null)
        {
            return;
        }

        ItemStackKey key = slot.getKey();
        long group = Math.min(slot.getStoredAmount(), Math.max(1L, key.getVanillaMaxStackSize()));
        KeyAmount extracted = network.getUnifiedStorage().extract(key, group, false, false);
        if (!(extracted.key() instanceof ItemStackKey extractedKey) || extracted.amount() <= 0L)
        {
            return;
        }

        ItemStack output = extractedKey.copyStackWithCount(extracted.amount());
        int inserted = insertIntoPlayerInventory(player, output);
        long rollback = extracted.amount() - inserted;
        if (rollback > 0L)
        {
            network.getUnifiedStorage().insert(extractedKey, rollback, false);
        }
    }

    private static void throwFromSidebar(ServerPlayer player, NetworkStorageSlot slot, int button)
    {
        if (slot == null || slot.getKey() == null)
        {
            return;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null)
        {
            return;
        }

        long amount = button == 1
                ? Math.min(slot.getStoredAmount(), Math.max(1L, slot.getKey().getVanillaMaxStackSize()))
                : 1L;
        KeyAmount extracted = network.getUnifiedStorage().extract(slot.getKey(), amount, false, false);
        if (extracted.key() instanceof ItemStackKey key && extracted.amount() > 0L)
        {
            player.drop(key.copyStackWithCount(extracted.amount()), true);
        }
    }

    private static void pickupAllFromSidebar(ServerPlayer player, NetworkStorageSlot slot)
    {
        ItemStack carried = player.containerMenu.getCarried().copy();
        if (carried.isEmpty())
        {
            return;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null)
        {
            return;
        }

        ItemStackKey key = new ItemStackKey(carried);
        long space = Math.max(0L, carried.getMaxStackSize() - (long) carried.getCount());
        if (space <= 0L)
        {
            return;
        }

        KeyAmount extracted = network.getUnifiedStorage().extract(key, space, false, false);
        if (extracted.amount() > 0L)
        {
            carried.grow((int) Math.min((long) Integer.MAX_VALUE, extracted.amount()));
            player.containerMenu.setCarried(carried);
        }
    }

    private static void swapHotbarWithSidebar(ServerPlayer player, NetworkStorageSlot slot, int button)
    {
        if (button < 0 || button >= 9 || slot == null || slot.getKey() == null)
        {
            return;
        }

        ItemStack hotbar = player.getInventory().getItem(button).copy();
        if (!hotbar.isEmpty())
        {
            ItemStackKey key = new ItemStackKey(hotbar);
            KeyAmount remaining = DimensionsNet.getNetFromPlayer(player).getUnifiedStorage()
                    .insert(key, hotbar.getCount(), false);
            long inserted = Math.max(0L, hotbar.getCount() - remaining.amount());
            if (inserted > 0L)
            {
                hotbar.shrink((int) inserted);
                player.getInventory().setItem(button, hotbar);
            }
        }
        else
        {
            quickMoveSidebar(player, slot);
        }
    }

    public static void withdraw(ServerPlayer player, ItemStack requestedStack, long amount)
    {
        if (isSidebarHidden(player) || requestedStack == null || requestedStack.isEmpty() || amount <= 0L)
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

    /** Extracts a JEI ingredient from a real sidebar slot on the logical server. */
    public static ItemStack takeRecipeTransferIngredient(Player player, NetworkStorageSlot slot,
                                                         int requestedAmount)
    {
        if (!(player instanceof ServerPlayer serverPlayer) || slot == null || requestedAmount <= 0
                || isSidebarHidden(player) || slot.getKey() == null
                || player.containerMenu == null || !player.containerMenu.slots.contains(slot))
        {
            return ItemStack.EMPTY;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(serverPlayer);
        if (network == null)
        {
            return ItemStack.EMPTY;
        }

        ItemStackKey key = slot.getKey();
        long requested = Math.min((long) requestedAmount, slot.getStoredAmount());
        KeyAmount extracted = network.getUnifiedStorage().extract(key, requested, false, false);
        if (extracted.amount() <= 0L)
        {
            return ItemStack.EMPTY;
        }
        return key.copyStackWithCount(extracted.amount());
    }

    /** Restores an extraction when JEI rolls back an incomplete transfer set. */
    public static long restoreRecipeTransferIngredient(Player player, NetworkStorageSlot slot,
                                                       long amount)
    {
        if (!(player instanceof ServerPlayer serverPlayer) || slot == null || amount <= 0L
                || slot.getKey() == null || player.containerMenu == null
                || !player.containerMenu.slots.contains(slot))
        {
            return 0L;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(serverPlayer);
        if (network == null)
        {
            return 0L;
        }

        KeyAmount remaining = network.getUnifiedStorage().insert(slot.getKey(), amount, false);
        return Math.max(0L, amount - remaining.amount());
    }

    /**
     * Fills real recipe input slots for JEI's recipe-transfer button.  The client only sends the
     * selected ingredient variants and target slot ids; all item movement is performed here on
     * the logical server, using the network before the player's inventory.
     */
    public static void fillRecipe(ServerPlayer player, List<RecipeFill> fills)
    {
        fillRecipe(player, fills, false, false);
    }

    public static void fillRecipe(ServerPlayer player, List<RecipeFill> fills,
                                  boolean maxTransfer, boolean requireCompleteSets)
    {
        if (player == null || player.containerMenu == null || fills == null || fills.isEmpty())
        {
            return;
        }

        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        UnifiedStorage storage = isSidebarHidden(player) || network == null
                ? null : network.getUnifiedStorage();
        List<RecipeTarget> targets = new ArrayList<>();
        Container playerInventory = player.getInventory();

        // Build and validate the target list before changing either the network or the menu.
        // JEI normally sends one operation per recipe slot; de-duplicating here also makes the
        // packet safe for handlers that describe the same target more than once.
        for (RecipeFill fill : fills)
        {
            if (fill == null || fill.slotId() < 0 || fill.slotId() >= player.containerMenu.slots.size())
            {
                continue;
            }

            ItemStack desired = fill.stack() == null ? ItemStack.EMPTY : fill.stack().copy();
            if (desired.isEmpty() || fill.amount() <= 0)
            {
                continue;
            }

            Slot target = player.containerMenu.slots.get(fill.slotId());
            desired.setCount(1);
            if (target instanceof NetworkStorageSlot
                    || target instanceof ResultSlot
                    || target.container == playerInventory
                    || !target.mayPlace(desired))
            {
                continue;
            }

            boolean alreadyAdded = false;
            for (RecipeTarget existing : targets)
            {
                if (existing.slot() == target)
                {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded)
            {
                targets.add(new RecipeTarget(target, desired,
                        Math.min(64, Math.max(1, fill.amount()))));
            }
        }

        if (targets.isEmpty())
        {
            return;
        }

        List<Container> changedContainers = new ArrayList<>();

        // If a handler changes an already occupied recipe slot to another ingredient, return
        // the old stack to the player's inventory before filling the new recipe. This mirrors
        // JEI's normal shuffle step and avoids silently deleting the old ingredient.
        for (RecipeTarget target : targets)
        {
            ItemStack current = target.slot().getItem();
            if (!current.isEmpty() && !sameStoredStack(current, target.desired()))
            {
                if (!canInsertIntoPlayerInventory(player, current))
                {
                    return;
                }
                if (insertIntoPlayerInventory(player, current) < current.getCount())
                {
                    return;
                }
                target.slot().set(ItemStack.EMPTY);
                addChangedContainer(changedContainers, target.slot().container);
            }
        }

        int iterations = maxTransfer ? MAX_RECIPE_TRANSFER_SETS : 1;
        for (int iteration = 0; iteration < iterations; iteration++)
        {
            List<RecipeAcquisition> acquired = new ArrayList<>();
            java.util.Map<Slot, ItemStack> originalTargets = new java.util.LinkedHashMap<>();
            boolean complete = true;
            boolean progressed = false;

            for (RecipeTarget target : targets)
            {
                Slot slot = target.slot();
                ItemStack desired = target.desired();
                int limit = Math.min(slot.getMaxStackSize(desired), desired.getMaxStackSize());
                limit = Math.max(1, limit);
                ItemStack current = slot.getItem();
                if (!current.isEmpty() && !sameStoredStack(current, desired))
                {
                    complete = false;
                    continue;
                }
                int currentCount = current.isEmpty() ? 0 : current.getCount();
                int requiredCount = maxTransfer
                        ? iteration + 1 : target.requestedAmount();
                requiredCount = Math.min(limit, Math.max(1, requiredCount));
                if (currentCount >= requiredCount)
                {
                    continue;
                }

                ItemStackKey key = new ItemStackKey(desired);
                boolean fromNetwork = false;
                if (storage != null)
                {
                    KeyAmount extracted = storage.extract(key, 1L, false, false);
                    fromNetwork = extracted.amount() > 0L;
                }

                if (!fromNetwork && removeFromPlayerInventory(player, desired, 1) <= 0)
                {
                    complete = false;
                    continue;
                }

                originalTargets.putIfAbsent(slot, current.copy());
                ItemStack result = current.isEmpty() ? desired.copy() : current.copy();
                result.setCount(current.isEmpty() ? 1 : current.getCount() + 1);
                slot.set(result);
                addChangedContainer(changedContainers, slot.container);
                acquired.add(new RecipeAcquisition(key, fromNetwork));
                progressed = true;
            }

            if (!complete && (!maxTransfer || requireCompleteSets))
            {
                for (java.util.Map.Entry<Slot, ItemStack> original : originalTargets.entrySet())
                {
                    original.getKey().set(original.getValue());
                }
                rollbackRecipeAcquisitions(player, storage, acquired);
                break;
            }

            if (!progressed || !complete)
            {
                break;
            }
        }

        for (Container container : changedContainers)
        {
            player.containerMenu.slotsChanged(container);
        }
        player.containerMenu.broadcastChanges();
    }

    private static void rollbackRecipeAcquisitions(ServerPlayer player, UnifiedStorage storage,
                                                   List<RecipeAcquisition> acquired)
    {
        for (RecipeAcquisition acquisition : acquired)
        {
            if (acquisition.fromNetwork())
            {
                if (storage != null)
                {
                    storage.insert(acquisition.key(), 1L, false);
                }
            }
            else
            {
                ItemStack restored = acquisition.key().copyStackWithCount(1);
                if (!player.getInventory().add(restored) && !restored.isEmpty())
                {
                    player.drop(restored, false);
                }
            }
        }
    }

    private record RecipeTarget(Slot slot, ItemStack desired, int requestedAmount)
    {
    }

    private record RecipeAcquisition(ItemStackKey key, boolean fromNetwork)
    {
    }

    /**
     * Handles the overlay storage slot using the same basic left/right rules as Beyond Dimensions.
     * The server menu's carried stack is authoritative, so the cursor can continue into vanilla
     * container slots after taking an item from the sidebar.
     */
    public static void clickSidebar(ServerPlayer player, NetworkStorageSlot slot, int button)
    {
        if (isSidebarHidden(player) || slot == null)
        {
            return;
        }
        ItemStack requested = slot.getKey() == null
                ? ItemStack.EMPTY
                : slot.getKey().copyStackWithCount(1);
        clickSidebar(player, requested, button);
    }

    public static void clickSidebar(ServerPlayer player, ItemStack requestedStack, int button)
    {
        if (isSidebarHidden(player) || (button != 0 && button != 1))
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

    private static void clearSidebarSlots(ServerPlayer player)
    {
        if (player == null || player.containerMenu == null
                || !(player.containerMenu instanceof NetworkStorageMenuAccess access))
        {
            return;
        }
        for (NetworkStorageSlot slot : access.bbd$getNetworkSlots())
        {
            slot.clear();
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
