package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageMenuAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the same real sidebar slots to every server-side vanilla menu. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin
{
    @Inject(method = "initMenu", at = @At("TAIL"))
    private void bbd$addNetworkSlots(AbstractContainerMenu menu, CallbackInfo callbackInfo)
    {
        if (menu == null || menu.getClass().getName().startsWith("com.wintercogs.beyonddimensions."))
        {
            return;
        }

        NetworkStorageMenuAccess access = (NetworkStorageMenuAccess) menu;
        access.bbd$ensureNetworkSlots(0, 0);
        ServerPlayer player = (ServerPlayer) (Object) this;
        access.bbd$getNetworkSlots().forEach(slot -> slot.bindPlayer(player));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void bbd$flushNetworkStorageSync(CallbackInfo callbackInfo)
    {
        NetworkHandler.tick((ServerPlayer) (Object) this);
    }
}
