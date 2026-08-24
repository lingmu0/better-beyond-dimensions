package net.xuwu.betterbeyonddimensions.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.Event;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * Fired after a container screen's sidebar widgets are created and before the sidebar is shown.
 * Listeners may customize only this screen instance; the server-backed menu is not replaced.
 */
public final class SidebarDisplayEvent extends Event
{
    public enum ButtonId
    {
        PLAYER_SHIFT,
        CONTAINER_SHIFT,
        DEPOSIT_CONTAINER,
        DEPOSIT_PLAYER,
        SIDEBAR_TOGGLE
    }

    private final Screen screen;
    private final EnumSet<ButtonId> disabledButtons = EnumSet.noneOf(ButtonId.class);
    private final EnumMap<ButtonId, Component> buttonMessages = new EnumMap<>(ButtonId.class);
    private final EnumMap<ButtonId, Component> buttonTooltips = new EnumMap<>(ButtonId.class);
    private boolean sidebarEnabled = true;

    public SidebarDisplayEvent(Screen screen)
    {
        this.screen = screen;
    }

    public Screen getScreen()
    {
        return screen;
    }

    public boolean isSidebarEnabled()
    {
        return sidebarEnabled;
    }

    public void setSidebarEnabled(boolean enabled)
    {
        sidebarEnabled = enabled;
    }

    public void disableSidebar()
    {
        sidebarEnabled = false;
    }

    public void disableButton(ButtonId button)
    {
        if (button != null)
        {
            disabledButtons.add(button);
        }
    }

    public void enableButton(ButtonId button)
    {
        if (button != null)
        {
            disabledButtons.remove(button);
        }
    }

    public boolean isButtonEnabled(ButtonId button)
    {
        return button == null || !disabledButtons.contains(button);
    }

    public void setButtonMessage(ButtonId button, Component message)
    {
        if (button == null)
        {
            return;
        }
        if (message == null)
        {
            buttonMessages.remove(button);
        }
        else
        {
            buttonMessages.put(button, message);
        }
    }

    public Component getButtonMessage(ButtonId button)
    {
        return buttonMessages.get(button);
    }

    public void setButtonTooltip(ButtonId button, Component tooltip)
    {
        if (button == null)
        {
            return;
        }
        if (tooltip == null)
        {
            buttonTooltips.remove(button);
        }
        else
        {
            buttonTooltips.put(button, tooltip);
        }
    }

    public Component getButtonTooltip(ButtonId button)
    {
        return buttonTooltips.get(button);
    }
}
