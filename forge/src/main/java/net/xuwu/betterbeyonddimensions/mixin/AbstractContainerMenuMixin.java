package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageMenuAccess;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageSlot;
import net.xuwu.betterbeyonddimensions.common.StorageActions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin implements NetworkStorageMenuAccess
{
    @Shadow
    protected abstract Slot addSlot(Slot slot);

    @Shadow
    @Final
    private NonNullList<ItemStack> lastSlots;

    @Shadow
    @Final
    private NonNullList<ItemStack> remoteSlots;

    @Unique
    private final List<NetworkStorageSlot> bbd$networkSlots = new ArrayList<>();

    @Override
    public List<NetworkStorageSlot> bbd$getNetworkSlots()
    {
        return bbd$networkSlots;
    }

    @Override
    public void bbd$addNetworkSlot(NetworkStorageSlot slot)
    {
        addSlot(slot);
    }

    @Override
    public void bbd$removeNetworkSlot(NetworkStorageSlot slot)
    {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        int index = menu.slots.indexOf(slot);
        if (index < 0)
        {
            return;
        }

        menu.slots.remove(index);
        if (index < lastSlots.size())
        {
            lastSlots.remove(index);
        }
        if (index < remoteSlots.size())
        {
            remoteSlots.remove(index);
        }
    }

    @Override
    public void bbd$ensureNetworkSlots(int x, int y)
    {
        if (bbd$isNetworkSlotExcludedMenu() || !bbd$networkSlots.isEmpty())
        {
            return;
        }

        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        for (int index = 0; index < 40; index++)
        {
            int column = index % 5;
            int row = index / 5;
            NetworkStorageSlot slot = new NetworkStorageSlot(menu, index,
                    x + column * 18,
                    y + row * 18);
            addSlot(slot);
            bbd$networkSlots.add(slot);
        }
    }

    /**
     * The first clientbound slot packet can arrive before the client screen's init() callback.
     * Create the client-side real slots before vanilla setItem()/initializeContents() resolves
     * the packet's slot index.
     */
    @Inject(method = "setItem", at = @At("HEAD"))
    private void bbd$ensureNetworkSlotsBeforeSync(int stateId, int slotId, ItemStack stack,
                                                    CallbackInfo callbackInfo)
    {
        if (!bbd$isNetworkSlotExcludedMenu())
        {
            bbd$ensureNetworkSlots(0, 0);
        }
    }

    @Inject(method = "initializeContents", at = @At("HEAD"))
    private void bbd$ensureNetworkSlotsBeforeContents(int stateId, List<ItemStack> items,
                                                        ItemStack carried, CallbackInfo callbackInfo)
    {
        if (!bbd$isNetworkSlotExcludedMenu())
        {
            bbd$ensureNetworkSlots(0, 0);
        }
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void bbd$interceptNetworkSlot(int slotId, int button, ClickType clickType,
                                           Player player, CallbackInfo callbackInfo)
    {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (player instanceof ServerPlayer serverPlayer
                && slotId >= 0 && slotId < menu.slots.size()
                && menu.slots.get(slotId) instanceof NetworkStorageSlot networkSlot)
        {
            StorageActions.handleSidebarClick(serverPlayer, networkSlot, button, clickType);
            NetworkHandler.sendSnapshot(serverPlayer);
            callbackInfo.cancel();
            return;
        }

        if (clickType == ClickType.QUICK_MOVE && player instanceof ServerPlayer serverPlayer
                && StorageActions.routeQuickMove(serverPlayer, menu, slotId))
        {
            NetworkHandler.sendSnapshot(serverPlayer);
            callbackInfo.cancel();
        }
    }

    /**
     * Vanilla's merge pass in moveItemStackTo does not check Slot#mayPlace before it
     * increases an existing matching stack. A visible network slot would therefore
     * consume a normal container stack even though it is intentionally not a vanilla
     * insertion target. Hide network slots from that vanilla merge scan; sidebar
     * interactions are handled by the server-side sidebar click path instead.
     */
    @Redirect(
            method = "moveItemStackTo",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack bbd$hideNetworkSlotFromVanillaMerge(Slot slot)
    {
        return slot instanceof NetworkStorageSlot ? ItemStack.EMPTY : slot.getItem();
    }

    @Unique
    private boolean bbd$isNetworkSlotExcludedMenu()
    {
        String className = ((Object) this).getClass().getName();
        return className.startsWith("com.wintercogs.beyonddimensions.")
                || className.equals("net.minecraft.client.gui.screens.inventory."
                + "CreativeModeInventoryScreen$ItemPickerMenu");
    }
}
