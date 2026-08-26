package net.xuwu.betterbeyonddimensions.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(ScreenEvent.Render.Post event)
    {
        if (!(event.getScreen() instanceof SidebarScreenAccess host)
                || host.bbd$getSearchBox() == null
                || !host.bbd$isSidebarEnabled())
        {
            return;
        }

        host.bbd$rebuildSidebarSlots();
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(0.0D, 0.0D, SidebarRenderer.RENDER_Z_OFFSET);
        try
        {
            SidebarRenderer.render(
                    host,
                    event.getGuiGraphics(),
                    event.getMouseX(),
                    event.getMouseY(),
                    Minecraft.getInstance().getFrameTime()
            );
            SidebarRenderer.renderPostTooltip(
                    host,
                    event.getGuiGraphics(),
                    event.getMouseX(),
                    event.getMouseY()
            );
        }
        finally
        {
            event.getGuiGraphics().pose().popPose();
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
