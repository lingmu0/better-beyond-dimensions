package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Makes native JEI server transfers transactional when a network slot is a source. */
@Pseudo
@Mixin(targets = "mezz.jei.common.transfer.BasicRecipeTransferHandlerServer", remap = false)
public abstract class JeiRecipeTransferServerMixin
{
    @Inject(method = "setItems", at = @At("HEAD"), remap = false)
    private static void bbd$beginNetworkTransfer(Player player, List<?> operations,
                                                 List<Slot> recipeSlots, List<Slot> inventorySlots,
                                                 boolean maxTransfer, boolean requireCompleteSets,
                                                 CallbackInfo callbackInfo)
    {
        for (Slot slot : inventorySlots)
        {
            if (slot instanceof NetworkStorageSlot networkSlot)
            {
                networkSlot.beginRecipeTransfer(player);
            }
        }
    }

    @Inject(method = "setItems", at = @At("RETURN"), remap = false)
    private static void bbd$finishNetworkTransfer(Player player, List<?> operations,
                                                  List<Slot> recipeSlots, List<Slot> inventorySlots,
                                                  boolean maxTransfer, boolean requireCompleteSets,
                                                  CallbackInfo callbackInfo)
    {
        boolean changed = false;
        for (Slot slot : inventorySlots)
        {
            if (slot instanceof NetworkStorageSlot networkSlot)
            {
                changed |= networkSlot.endRecipeTransfer();
            }
        }
        if (changed && player instanceof ServerPlayer serverPlayer)
        {
            NetworkHandler.sendSnapshot(serverPlayer);
        }
    }
}
