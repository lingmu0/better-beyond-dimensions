package net.xuwu.betterbeyonddimensions.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Invisible event widget that gives the sidebar its own scrollable viewport. */
public final class SidebarScrollWidget extends AbstractWidget
{
    private final SidebarScreenAccess host;

    public SidebarScrollWidget(SidebarScreenAccess host, int x, int y, int width, int height)
    {
        super(x, y, width, height, Component.empty());
        this.host = host;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        // The visible grid is painted by SidebarRenderer after the container screen.
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration)
    {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        return SidebarRenderer.handleScroll(host, mouseX, mouseY, scrollY);
    }
}
