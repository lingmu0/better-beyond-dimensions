package net.xuwu.betterbeyonddimensions.common;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.xuwu.betterbeyonddimensions.NetworkHandler;

import java.util.Optional;

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
    private ServerPlayer owningPlayer;
    private Player recipeTransferPlayer;
    private long pendingRecipeExtraction;
    private boolean recipeTransferChanged;

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

    /** Binds the logical server owner so transfer handlers that call Slot.remove can extract. */
    public void bindPlayer(ServerPlayer player)
    {
        this.owningPlayer = player;
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
        // Sidebar insertion is handled by its authoritative click packet. Returning false keeps
        // host menus with custom merge logic from treating this appended slot as ordinary storage.
        return false;
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
        // JEI restores a source slot with set(originalStack) when a complete recipe set cannot
        // be assembled. Return everything extracted during that transaction to the network.
        if (recipeTransferPlayer != null && pendingRecipeExtraction > 0L && key != null
                && stack != null && !stack.isEmpty()
                && key.equals(new ItemStackKey(stack)))
        {
            long restored = StorageActions.restoreRecipeTransferIngredient(
                    recipeTransferPlayer, this, pendingRecipeExtraction);
            amount += restored;
            pendingRecipeExtraction = Math.max(0L, pendingRecipeExtraction - restored);
        }
    }

    @Override
    public ItemStack remove(int requestedAmount)
    {
        return extractForTransfer(owningPlayer, requestedAmount);
    }

    @Override
    public Optional<ItemStack> tryRemove(int minimumAmount, int maximumAmount, Player player)
    {
        int requested = Math.max(0, Math.min(minimumAmount, maximumAmount));
        if (requested <= 0 || !mayPickup(player))
        {
            return Optional.empty();
        }

        ItemStack extracted = extractForTransfer(player, requested);
        return extracted.isEmpty() ? Optional.empty() : Optional.of(extracted);
    }

    @Override
    public ItemStack safeTake(int minimumAmount, int maximumAmount, Player player)
    {
        Optional<ItemStack> extracted = tryRemove(minimumAmount, maximumAmount, player);
        if (extracted.isPresent())
        {
            onTake(player, extracted.get());
        }
        return extracted.orElse(ItemStack.EMPTY);
    }

    private ItemStack extractForTransfer(Player player, int requestedAmount)
    {
        if (player == null || requestedAmount <= 0)
        {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = StorageActions.takeRecipeTransferIngredient(
                player, this, Math.max(0, requestedAmount));
        if (extracted.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        int extractedCount = extracted.getCount();
        amount = Math.max(0L, amount - extractedCount);
        if (recipeTransferPlayer == player)
        {
            pendingRecipeExtraction += extractedCount;
            recipeTransferChanged = true;
        }
        else if (player instanceof ServerPlayer serverPlayer)
        {
            // Custom JEI handlers often use remove()/tryRemove() instead of safeTake().
            // Keep the sidebar snapshot authoritative after those direct slot mutations.
            NetworkHandler.sendSnapshot(serverPlayer);
        }
        return extracted;
    }

    @Override
    public ItemStack safeInsert(ItemStack stack)
    {
        return stack;
    }

    @Override
    public ItemStack safeInsert(ItemStack stack, int maximumAmount)
    {
        return stack;
    }

    @Override
    public boolean allowModification(Player player)
    {
        return active && hasItem();
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

    public void beginRecipeTransfer(Player player)
    {
        recipeTransferPlayer = player;
        pendingRecipeExtraction = 0L;
        recipeTransferChanged = false;
    }

    public boolean endRecipeTransfer()
    {
        boolean changed = recipeTransferChanged;
        recipeTransferPlayer = null;
        pendingRecipeExtraction = 0L;
        recipeTransferChanged = false;
        return changed;
    }
}
