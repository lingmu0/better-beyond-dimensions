package net.xuwu.betterbeyonddimensions.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.xuwu.betterbeyonddimensions.BetterBeyondDimensions;
import net.xuwu.betterbeyonddimensions.NetworkHandler;

/** Forge 1.20.1 exposes scrolling through an interface default, so intercept its screen event. */
@Mod.EventBusSubscriber(
        modid = BetterBeyondDimensions.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class SidebarClientEvents
{
    private SidebarClientEvents()
    {
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event)
    {
        // Packet sequence numbers and the storage view belong to one server connection.
        // Clear them before the first snapshot of a new world/server is accepted.
        ClientStorageState.clear();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientStorageState.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event)
    {
        if (event.getScreen() instanceof SidebarScreenAccess host
                && host.bbd$getSearchBox() != null
                && SidebarRenderer.handleScroll(
                        host, event.getMouseX(), event.getMouseY(), event.getScrollDelta()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event)
    {
        if (!(event.getScreen() instanceof SidebarScreenAccess host)
                || host.bbd$getSearchBox() == null
                || !host.bbd$isSidebarEnabled()
                || host.bbd$isSidebarHidden()
                || !ClientStorageState.available()
                // Do not consume the deposit shortcuts while any text input on the
                // current screen is focused, including search boxes supplied by other mods.
                || TextInputFocusTracker.isTextInputFocused(event.getScreen()))
        {
            return;
        }

        if (SidebarKeyMappings.DEPOSIT_CONTAINER.matches(event.getKeyCode(), event.getScanCode())
                && host.bbd$isSidebarButtonEnabled(SidebarDisplayEvent.ButtonId.DEPOSIT_CONTAINER))
        {
            NetworkHandler.depositContainer();
            event.setCanceled(true);
            return;
        }
        if (SidebarKeyMappings.DEPOSIT_PLAYER.matches(event.getKeyCode(), event.getScanCode())
                && host.bbd$isSidebarButtonEnabled(SidebarDisplayEvent.ButtonId.DEPOSIT_PLAYER))
        {
            NetworkHandler.depositPlayerInventory();
            event.setCanceled(true);
        }
    }
}
