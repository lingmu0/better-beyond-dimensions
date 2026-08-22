package net.xuwu.betterbeyonddimensions.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Accessors implemented by the container-screen mixin. */
public interface SidebarScreenAccess
{
    EditBox bbd$getSearchBox();

    Button bbd$getPlayerShiftButton();

    Button bbd$getContainerShiftButton();

    Button bbd$getDepositContainerButton();

    Button bbd$getDepositPlayerButton();

    Button bbd$getSidebarToggleButton();

    ItemStack bbd$getCarried();

    void bbd$markSidebarMouseRelease();

    boolean bbd$consumeSidebarMouseRelease();

    boolean bbd$isSidebarHidden();

    int bbd$getSidebarX();

    int bbd$getSidebarY();

    int bbd$getSidebarHeight();

    List<SidebarSlot> bbd$getSidebarSlots();

    void bbd$rebuildSidebarSlots();

    void bbd$updateSidebarSlots(List<ClientStorageView.Entry> entries);

    boolean bbd$handleCreativePlayerQuickMove(double mouseX, double mouseY);
}
