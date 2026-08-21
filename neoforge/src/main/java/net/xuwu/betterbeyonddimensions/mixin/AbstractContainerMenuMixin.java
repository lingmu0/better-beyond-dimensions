package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.common.StorageActions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes vanilla shift-clicks into the dimension network when enabled. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin
{
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void bbd$interceptQuickMove(int slotId, int button, ClickType clickType, Player player, CallbackInfo callbackInfo)
    {
        if (clickType == ClickType.QUICK_MOVE && player instanceof ServerPlayer serverPlayer)
        {
            if (StorageActions.routeQuickMove(serverPlayer, (AbstractContainerMenu) (Object) this, slotId))
            {
                NetworkHandler.sendSnapshot(serverPlayer);
                callbackInfo.cancel();
            }
        }
    }
}
