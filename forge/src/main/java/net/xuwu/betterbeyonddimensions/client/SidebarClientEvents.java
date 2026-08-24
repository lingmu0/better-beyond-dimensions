package net.xuwu.betterbeyonddimensions.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
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

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

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
