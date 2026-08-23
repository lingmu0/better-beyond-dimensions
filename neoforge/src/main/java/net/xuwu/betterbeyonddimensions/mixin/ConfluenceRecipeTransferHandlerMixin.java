package net.xuwu.betterbeyonddimensions.mixin;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageMenuAccess;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageSlot;
import net.xuwu.betterbeyonddimensions.common.StorageEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Makes Confluence's custom JEI handler see the complete network, not only the current page. */
@Pseudo
@Mixin(targets = "org.confluence.mod.integration.jei.EitherRecipe4xHelper$TransferHandler", remap = false)
public abstract class ConfluenceRecipeTransferHandlerMixin
{
    @Unique
    private final List<NetworkStorageSlot> bbd$temporaryNetworkSlots = new ArrayList<>();

    @Unique
    private AbstractContainerMenu bbd$temporaryNetworkMenu;

    @Inject(
            method = "transferRecipe(Lorg/confluence/lib/common/menu/EitherAmountContainerMenu4x;"
                    + "Lnet/minecraft/world/item/crafting/RecipeHolder;"
                    + "Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;"
                    + "Lnet/minecraft/world/entity/player/Player;ZZ)"
                    + "Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void bbd$addAllNetworkSources(@Coerce Object rawMenu,
                                           RecipeHolder<?> recipe,
                                           IRecipeSlotsView recipeSlots,
                                           Player player,
                                           boolean maxTransfer,
                                           boolean doTransfer,
                                           CallbackInfoReturnable<IRecipeTransferError> callbackInfo)
    {
        bbd$removeTemporaryNetworkSlots();
        if (!(rawMenu instanceof AbstractContainerMenu menu)
                || !ClientStorageState.available() || ClientStorageState.isSidebarHidden()
                || !(menu instanceof NetworkStorageMenuAccess access))
        {
            return;
        }

        bbd$temporaryNetworkMenu = menu;

        Set<ItemStackKey> representedKeys = new HashSet<>();
        for (NetworkStorageSlot slot : access.bbd$getNetworkSlots())
        {
            if (slot.isActive() && slot.hasItem() && slot.getKey() != null)
            {
                representedKeys.add(slot.getKey());
            }
        }

        List<StorageEntry> entries = ClientStorageState.entries();
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
            access.bbd$addNetworkSlot(slot);
            bbd$temporaryNetworkSlots.add(slot);
        }
    }

    @Inject(
            method = "transferRecipe(Lorg/confluence/lib/common/menu/EitherAmountContainerMenu4x;"
                    + "Lnet/minecraft/world/item/crafting/RecipeHolder;"
                    + "Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;"
                    + "Lnet/minecraft/world/entity/player/Player;ZZ)"
                    + "Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void bbd$removeAllNetworkSources(CallbackInfoReturnable<IRecipeTransferError> callbackInfo)
    {
        bbd$removeTemporaryNetworkSlots();
    }

    @Unique
    private void bbd$removeTemporaryNetworkSlots()
    {
        if (bbd$temporaryNetworkMenu == null)
        {
            return;
        }

        // Remove by identity.  The handler may have re-ordered its slot list while examining
        // it, so relying on a contiguous range is unsafe.
        if (bbd$temporaryNetworkMenu instanceof NetworkStorageMenuAccess access)
        {
            for (NetworkStorageSlot slot : bbd$temporaryNetworkSlots)
            {
                access.bbd$removeNetworkSlot(slot);
            }
        }
        bbd$temporaryNetworkSlots.clear();
        bbd$temporaryNetworkMenu = null;
    }
}
