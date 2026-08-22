package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
import net.xuwu.betterbeyonddimensions.client.SidebarRenderer;
import net.xuwu.betterbeyonddimensions.client.SidebarScreenAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps CreativeModeInventoryScreen from treating sidebar clicks as outside-inventory actions. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin
{
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void bbd$creativeSidebarCharTyped(char codePoint, int modifiers,
                                               CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (this instanceof SidebarScreenAccess host)
        {
            EditBox search = host.bbd$getSearchBox();
            if (search != null && search.visible && search.active && search.isFocused())
            {
                search.charTyped(codePoint, modifiers);
                callbackInfo.setReturnValue(true);
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void bbd$creativeSidebarKeyPressed(int keyCode, int scanCode, int modifiers,
                                                CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (this instanceof SidebarScreenAccess host)
        {
            EditBox search = host.bbd$getSearchBox();
            if (search != null && search.visible && search.active && search.isFocused())
            {
                boolean handled = search.keyPressed(keyCode, scanCode, modifiers);
                if (handled || keyCode != 256)
                {
                    callbackInfo.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void bbd$creativeSidebarQuickMove(Slot slot, int slotId, int mouseButton, ClickType type,
                                               CallbackInfo callbackInfo)
    {
        if (type != ClickType.QUICK_MOVE || !(this instanceof SidebarScreenAccess host)
                || host.bbd$getSearchBox() == null || !ClientStorageState.available()
                || !ClientStorageState.snapshot().shiftPlayerInventory() || slot == null)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && slot.container == minecraft.player.getInventory())
        {
            NetworkHandler.quickMove(slot.index);
            callbackInfo.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bbd$creativeSidebarClick(double mouseX, double mouseY, int button,
                                          CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (this instanceof SidebarScreenAccess host
                && (button == 0 || button == 1)
                && Screen.hasShiftDown()
                && host.bbd$handleCreativePlayerQuickMove(mouseX, mouseY))
        {
            host.bbd$markSidebarMouseRelease();
            callbackInfo.setReturnValue(true);
        }
        else if (this instanceof SidebarScreenAccess host
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
