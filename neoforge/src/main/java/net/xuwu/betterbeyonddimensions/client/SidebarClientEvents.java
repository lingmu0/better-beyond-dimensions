package net.xuwu.betterbeyonddimensions.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.xuwu.betterbeyonddimensions.BetterBeyondDimensions;
import net.xuwu.betterbeyonddimensions.NetworkHandler;

/** Consumes sidebar wheel input before inventory-wheel transfer mods can handle it. */
@EventBusSubscriber(modid = BetterBeyondDimensions.MODID, value = Dist.CLIENT)
public final class SidebarClientEvents
{
    private SidebarClientEvents()
    {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event)
    {
        if (event.getScreen() instanceof SidebarScreenAccess host
                && host.bbd$getSearchBox() != null
                && SidebarRenderer.handleScroll(
                        host, event.getMouseX(), event.getMouseY(), event.getScrollDeltaY()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof SidebarScreenAccess host)
                || host.bbd$getSearchBox() == null
                || !host.bbd$isSidebarEnabled()
                || host.bbd$isSidebarHidden()
                || !ClientStorageState.available()
                || host.bbd$getSearchBox().isFocused())
        {
            drainKeyPresses();
            return;
        }

        while (SidebarKeyMappings.DEPOSIT_CONTAINER.consumeClick())
        {
            if (host.bbd$isSidebarButtonEnabled(SidebarDisplayEvent.ButtonId.DEPOSIT_CONTAINER))
            {
                NetworkHandler.depositContainer();
            }
        }
        while (SidebarKeyMappings.DEPOSIT_PLAYER.consumeClick())
        {
            if (host.bbd$isSidebarButtonEnabled(SidebarDisplayEvent.ButtonId.DEPOSIT_PLAYER))
            {
                NetworkHandler.depositPlayerInventory();
            }
        }
    }

    private static void drainKeyPresses()
    {
        while (SidebarKeyMappings.DEPOSIT_CONTAINER.consumeClick())
        {
        }
        while (SidebarKeyMappings.DEPOSIT_PLAYER.consumeClick())
        {
        }
    }
}
