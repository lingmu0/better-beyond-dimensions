package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Prevents CreativeModeInventoryScreen from wrapping sidebar-only slots as inventory slots. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventorySlotFilterMixin
{
    @Unique
    private final List<Slot> bbd$temporarilyRemovedNetworkSlots = new ArrayList<>();

    @Inject(method = "selectTab", at = @At("HEAD"))
    private void bbd$hideNetworkSlotsWhileBuildingTab(CreativeModeTab tab, CallbackInfo callbackInfo)
    {
        bbd$restoreNetworkSlots();
        if (Minecraft.getInstance().player == null)
        {
            return;
        }

        Iterator<Slot> iterator = Minecraft.getInstance().player.inventoryMenu.slots.iterator();
        while (iterator.hasNext())
        {
            Slot slot = iterator.next();
            if (slot instanceof NetworkStorageSlot)
            {
                bbd$temporarilyRemovedNetworkSlots.add(slot);
                iterator.remove();
            }
        }
    }

    @Inject(method = "selectTab", at = @At("RETURN"))
    private void bbd$restoreNetworkSlotsAfterBuildingTab(CreativeModeTab tab, CallbackInfo callbackInfo)
    {
        bbd$restoreNetworkSlots();
    }

    @Unique
    private void bbd$restoreNetworkSlots()
    {
        if (bbd$temporarilyRemovedNetworkSlots.isEmpty() || Minecraft.getInstance().player == null)
        {
            return;
        }
        Minecraft.getInstance().player.inventoryMenu.slots.addAll(bbd$temporarilyRemovedNetworkSlots);
        bbd$temporarilyRemovedNetworkSlots.clear();
    }
}
