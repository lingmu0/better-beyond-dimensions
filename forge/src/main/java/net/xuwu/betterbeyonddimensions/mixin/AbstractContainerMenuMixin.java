package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageMenuAccess;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageSlot;
import net.xuwu.betterbeyonddimensions.common.StorageActions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin implements NetworkStorageMenuAccess
{
    @Shadow
    protected abstract Slot addSlot(Slot slot);

    @Unique
    private final List<NetworkStorageSlot> bbd$networkSlots = new ArrayList<>();

    @Override
    public List<NetworkStorageSlot> bbd$getNetworkSlots()
    {
        return bbd$networkSlots;
    }

    @Override
    public void bbd$ensureNetworkSlots(int x, int y)
    {
        if (!bbd$networkSlots.isEmpty())
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
}
