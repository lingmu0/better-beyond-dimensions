package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.xuwu.betterbeyonddimensions.client.SidebarRenderer;
import net.xuwu.betterbeyonddimensions.client.SidebarScreenAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps CreativeModeInventoryScreen from treating sidebar clicks as outside-inventory actions. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin
{
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bbd$creativeSidebarClick(double mouseX, double mouseY, int button,
                                          CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (this instanceof SidebarScreenAccess host
                && host.bbd$getSearchBox() != null
                && SidebarRenderer.handleMouseClick(host, mouseX, mouseY, button))
        {
            host.bbd$markSidebarMouseRelease();
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void bbd$creativeSidebarRelease(double mouseX, double mouseY, int button,
                                             CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (this instanceof SidebarScreenAccess host && host.bbd$consumeSidebarMouseRelease())
        {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void bbd$creativeSidebarScroll(double mouseX, double mouseY, double scrollAmount,
                                            CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (this instanceof SidebarScreenAccess host
                && host.bbd$getSearchBox() != null
                && SidebarRenderer.handleScroll(host, mouseX, mouseY, scrollAmount))
        {
            callbackInfo.setReturnValue(true);
        }
    }
}
