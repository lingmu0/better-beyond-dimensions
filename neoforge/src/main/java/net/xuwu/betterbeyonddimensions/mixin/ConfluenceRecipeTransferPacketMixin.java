package net.xuwu.betterbeyonddimensions.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.common.NetworkStorage;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageMenuAccess;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageSlot;
import net.xuwu.betterbeyonddimensions.common.StorageEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets Confluence's custom JEI transfer packet extract from the actual source Slot.
 *
 * <p>Confluence scans the whole menu on the client, so it can see the sidebar. Its server
 * packet then discards that Slot and calls {@link Inventory#removeItem(int, int)} using the
 * source slot index. That works for player inventory slots, but maps a NetworkStorageSlot's
 * visual index back onto the player inventory. Delegating through Slot.remove keeps the
 * original behavior for vanilla slots and uses the sidebar's network-backed extraction for
 * network slots.</p>
 */
@Pseudo
@Mixin(targets = "org.confluence.mod.integration.jei.RecipeTransferPacketC2S", remap = false)
public abstract class ConfluenceRecipeTransferPacketMixin
{
    @Unique
    private final List<NetworkStorageSlot> bbd$temporaryNetworkSources = new ArrayList<>();

    @Unique
    private final List<NetworkStorageSlot> bbd$transactionalNetworkSources = new ArrayList<>();

    @Inject(
            method = "work",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/confluence/lib/common/menu/EitherAmountContainerMenu4x;"
                            + "clearContainerNoUpdate(Lnet/minecraft/world/entity/player/Player;)V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void bbd$addPageIndependentSources(ServerPlayer player, CallbackInfo callbackInfo)
    {
        bbd$removePageIndependentSources(player);
        if (player == null || player.containerMenu == null
                || !(player.containerMenu instanceof NetworkStorageMenuAccess access))
        {
            return;
        }

        List<StorageEntry> entries = NetworkStorage.snapshot(player).entries();
        if (entries.isEmpty())
        {
            return;
        }

        Set<ItemStackKey> representedKeys = new HashSet<>();
        for (NetworkStorageSlot slot : access.bbd$getNetworkSlots())
        {
            // A current-page slot can be selected before any temporary hidden-page slot.
            // Suppress its snapshot too, otherwise the server broadcasts the temporary
            // server-only slot indexes to a client whose menu is shorter.
            slot.beginRecipeTransfer(player);
            bbd$transactionalNetworkSources.add(slot);
            if (slot.isActive() && slot.hasItem() && slot.getKey() != null)
            {
                representedKeys.add(slot.getKey());
            }
        }

        AbstractContainerMenu menu = player.containerMenu;
        List<NetworkStorageSlot> added = new ArrayList<>();
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++)
        {
            StorageEntry entry = entries.get(entryIndex);
            if (entry == null || entry.amount() <= 0L || entry.stack().isEmpty())
            {
                continue;
            }

            ItemStackKey key = new ItemStackKey(entry.stack());
            if (!representedKeys.add(key))
            {
                continue;
            }

            NetworkStorageSlot slot = new NetworkStorageSlot(menu, entryIndex, 0, 0);
            slot.update(entryIndex, key, entry.amount(), true);
            slot.bindPlayer(player);
            // Confluence removes ingredients by calling Slot.remove(). Keep the whole
            // operation transactional so that the slot does not send a sidebar snapshot
            // while this temporary server-only slot is still in the menu.
            slot.beginRecipeTransfer(player);
            bbd$transactionalNetworkSources.add(slot);
            access.bbd$addNetworkSlot(slot);
            added.add(slot);
        }

        if (!added.isEmpty())
        {
            bbd$temporaryNetworkSources.addAll(added);
        }
    }

    @Inject(
            method = "work",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/confluence/lib/common/menu/EitherAmountContainerMenu4x;"
                            + "broadcastChanges()V",
                    shift = At.Shift.BEFORE,
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private void bbd$removeSourcesBeforeBroadcast(ServerPlayer player, CallbackInfo callbackInfo)
    {
        bbd$removePageIndependentSources(player);
    }

    @Inject(method = "work", at = @At("RETURN"), remap = false, require = 0)
    private void bbd$removeSourcesOnReturn(ServerPlayer player, CallbackInfo callbackInfo)
    {
        bbd$removePageIndependentSources(player);
    }

    @Unique
    private void bbd$removePageIndependentSources(ServerPlayer player)
    {
        if (player == null || (bbd$temporaryNetworkSources.isEmpty()
                && bbd$transactionalNetworkSources.isEmpty()))
        {
            return;
        }

        boolean changed = false;
        for (NetworkStorageSlot slot : bbd$transactionalNetworkSources)
        {
            changed |= slot.endRecipeTransfer();
        }

        if (player.containerMenu instanceof NetworkStorageMenuAccess access)
        {
            for (NetworkStorageSlot slot : bbd$temporaryNetworkSources)
            {
                access.bbd$removeNetworkSlot(slot);
            }
        }
        bbd$temporaryNetworkSources.clear();
        bbd$transactionalNetworkSources.clear();
        // Send the final sidebar state only after the temporary slots have been removed;
        // otherwise refreshSidebarSlots() broadcasts a slot index the client does not have.
        if (changed && player.containerMenu != null)
        {
            NetworkHandler.sendSnapshot(player);
        }
    }

    @WrapOperation(
            method = {"lambda$work$2", "lambda$work$3"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;removeItem(II)"
                            + "Lnet/minecraft/world/item/ItemStack;"
            ),
            require = 0
    )
    private static ItemStack bbd$removeFromSourceSlot(Inventory inventory, int slotIndex,
                                                       int amount, Operation<ItemStack> original,
                                                       @Local Slot slot)
    {
        if (slot instanceof NetworkStorageSlot)
        {
            return slot.remove(amount);
        }
        return original.call(inventory, slotIndex, amount);
    }
}
