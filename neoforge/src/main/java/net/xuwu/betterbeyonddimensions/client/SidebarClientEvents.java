package net.xuwu.betterbeyonddimensions.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.xuwu.betterbeyonddimensions.BetterBeyondDimensions;

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
}
