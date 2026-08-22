package net.xuwu.betterbeyonddimensions.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.xuwu.betterbeyonddimensions.BetterBeyondDimensions;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
import net.xuwu.betterbeyonddimensions.client.SidebarRenderer;
import net.xuwu.betterbeyonddimensions.client.SidebarScreenAccess;

import java.util.List;

/** Keeps JEI's overlay and ingredient lookup aware of the real sidebar Slot area. */
@JeiPlugin
public final class BetterBeyondDimensionsJeiPlugin implements IModPlugin
{
    @Override
    public ResourceLocation getPluginUid()
    {
        return BetterBeyondDimensions.id("jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration)
    {
        registration.addGenericGuiContainerHandler(AbstractContainerScreen.class,
                new IGuiContainerHandler<AbstractContainerScreen<?>>()
                {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen)
                    {
                        if (!(screen instanceof SidebarScreenAccess access)
                                || access.bbd$getSearchBox() == null
                                || access.bbd$isSidebarHidden()
                                || !ClientStorageState.available())
                        {
                            return List.of();
                        }

                        return List.of(new Rect2i(
                                access.bbd$getSidebarX(),
                                access.bbd$getSidebarY(),
                                SidebarRenderer.WIDTH,
                                SidebarRenderer.getPanelHeight()
                        ));
                    }
        });
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration)
    {
        registration.addRecipeTransferHandler(
                new SidebarRecipeTransferHandler<CraftingMenu, CraftingRecipe>(
                        CraftingMenu.class, RecipeTypes.CRAFTING, registration.getTransferHelper()),
                RecipeTypes.CRAFTING);
        registration.addRecipeTransferHandler(
                new SidebarRecipeTransferHandler<InventoryMenu, CraftingRecipe>(
                        InventoryMenu.class, RecipeTypes.CRAFTING, registration.getTransferHelper()),
                RecipeTypes.CRAFTING);
    }
}
