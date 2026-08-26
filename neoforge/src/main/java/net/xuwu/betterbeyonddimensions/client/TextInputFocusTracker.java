package net.xuwu.betterbeyonddimensions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.lang.ref.WeakReference;

/**
 * Tracks EditBox focus even when a UI overlay keeps its text field outside the
 * current Screen's normal focus tree.
 */
public final class TextInputFocusTracker
{
    private static WeakReference<EditBox> focusedInput;
    private static WeakReference<Screen> focusedScreen;

    private TextInputFocusTracker()
    {
    }

    public static void onFocusChanged(EditBox input, boolean focused)
    {
        if (focused)
        {
            Screen screen = Minecraft.getInstance().screen;
            if (screen == null)
            {
                clear();
                return;
            }

            focusedInput = new WeakReference<>(input);
            focusedScreen = new WeakReference<>(screen);
        }
        else if (focusedInput != null && focusedInput.get() == input)
        {
            clear();
        }
    }

    public static boolean isTextInputFocused(Screen screen)
    {
        if (screen.getFocused() instanceof EditBox)
        {
            return true;
        }

        EditBox input = focusedInput == null ? null : focusedInput.get();
        Screen focusedOn = focusedScreen == null ? null : focusedScreen.get();
        if (input == null || focusedOn != screen)
        {
            return false;
        }

        if (!input.active || !input.visible || !input.isFocused())
        {
            clear();
            return false;
        }

        return true;
    }

    private static void clear()
    {
        focusedInput = null;
        focusedScreen = null;
    }
}
