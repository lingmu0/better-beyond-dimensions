package net.xuwu.betterbeyonddimensions.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

/** Accessors implemented by the container-screen mixin. */
public interface SidebarScreenAccess
{
    EditBox bbd$getSearchBox();

    Button bbd$getPlayerShiftButton();

    Button bbd$getContainerShiftButton();

    Button bbd$getDepositContainerButton();

    Button bbd$getDepositPlayerButton();

    int bbd$getSidebarX();

    int bbd$getSidebarY();

    int bbd$getSidebarHeight();
}
