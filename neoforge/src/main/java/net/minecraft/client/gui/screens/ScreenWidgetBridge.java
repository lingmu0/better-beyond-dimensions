package net.minecraft.client.gui.screens;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

/** Small package bridge for Screen's protected widget registration method. */
public final class ScreenWidgetBridge
{
    private ScreenWidgetBridge()
    {
    }

    public static <T extends GuiEventListener & Renderable & NarratableEntry> T add(Screen screen, T widget)
    {
        return screen.addRenderableWidget(widget);
    }
}
