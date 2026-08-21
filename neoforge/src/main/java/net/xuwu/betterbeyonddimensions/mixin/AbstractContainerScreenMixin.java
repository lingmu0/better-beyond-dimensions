package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ScreenWidgetBridge;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
import net.xuwu.betterbeyonddimensions.client.SidebarRenderer;
import net.xuwu.betterbeyonddimensions.client.SidebarScreenAccess;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the sidebar to every vanilla AbstractContainerScreen. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> implements SidebarScreenAccess
{
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;

    @Unique private EditBox bbd$searchBox;
    @Unique private Button bbd$playerShiftButton;
    @Unique private Button bbd$containerShiftButton;
    @Unique private Button bbd$depositContainerButton;
    @Unique private Button bbd$depositPlayerButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void bbd$initSidebar(CallbackInfo callbackInfo)
    {
        int x = bbd$getSidebarX();
        int y = bbd$getSidebarY();
        int buttonWidth = (SidebarRenderer.WIDTH - 14) / 2;

        bbd$searchBox = bbd$addRenderableWidget(new EditBox(
                Minecraft.getInstance().font,
                x + 6,
                y + 38,
                SidebarRenderer.WIDTH - 12,
                18,
                Component.literal("搜索维度网络")
        ));
        bbd$searchBox.setMaxLength(80);
        bbd$searchBox.setHint(Component.literal("搜索物品"));

        bbd$playerShiftButton = bbd$addRenderableWidget(Button.builder(Component.literal("玩家移入:关"), button -> NetworkHandler.togglePlayerShift())
                .bounds(x + 6, y + 58, buttonWidth, 18).build());
        bbd$containerShiftButton = bbd$addRenderableWidget(Button.builder(Component.literal("容器移入:关"), button -> NetworkHandler.toggleContainerShift())
                .bounds(x + 8 + buttonWidth, y + 58, buttonWidth, 18).build());
        bbd$depositContainerButton = bbd$addRenderableWidget(Button.builder(Component.literal("存入容器"), button -> NetworkHandler.depositContainer())
                .bounds(x + 6, y + 80, buttonWidth, 18).build());
        bbd$depositPlayerButton = bbd$addRenderableWidget(Button.builder(Component.literal("存入背包"), button -> NetworkHandler.depositPlayerInventory())
                .bounds(x + 8 + buttonWidth, y + 80, buttonWidth, 18).build());

        bbd$setWidgetsVisible(false);
        ClientStorageState.clear();
        NetworkHandler.requestSnapshot();
    }

    @Unique
    private <W extends net.minecraft.client.gui.components.events.GuiEventListener
            & net.minecraft.client.gui.components.Renderable
            & net.minecraft.client.gui.narration.NarratableEntry> W bbd$addRenderableWidget(W widget)
    {
        return ScreenWidgetBridge.add((Screen) (Object) this, widget);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void bbd$renderSidebar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo)
    {
        if (bbd$searchBox != null)
        {
            SidebarRenderer.render(this, graphics, mouseX, mouseY, partialTick);
            SidebarRenderer.renderTooltip(this, graphics, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bbd$sidebarClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (bbd$searchBox != null && SidebarRenderer.handleClick(this, mouseX, mouseY, button))
        {
            callbackInfo.setReturnValue(true);
        }
    }

    @Unique
    private void bbd$setWidgetsVisible(boolean visible)
    {
        bbd$searchBox.visible = visible;
        bbd$searchBox.active = visible;
        bbd$playerShiftButton.visible = visible;
        bbd$playerShiftButton.active = visible;
        bbd$containerShiftButton.visible = visible;
        bbd$containerShiftButton.active = visible;
        bbd$depositContainerButton.visible = visible;
        bbd$depositContainerButton.active = visible;
        bbd$depositPlayerButton.visible = visible;
        bbd$depositPlayerButton.active = visible;
    }

    @Override
    public EditBox bbd$getSearchBox()
    {
        return bbd$searchBox;
    }

    @Override
    public Button bbd$getPlayerShiftButton()
    {
        return bbd$playerShiftButton;
    }

    @Override
    public Button bbd$getContainerShiftButton()
    {
        return bbd$containerShiftButton;
    }

    @Override
    public Button bbd$getDepositContainerButton()
    {
        return bbd$depositContainerButton;
    }

    @Override
    public Button bbd$getDepositPlayerButton()
    {
        return bbd$depositPlayerButton;
    }

    @Override
    public int bbd$getSidebarX()
    {
        return Math.max(4, leftPos - SidebarRenderer.WIDTH - 6);
    }

    @Override
    public int bbd$getSidebarY()
    {
        return Math.max(4, topPos - 1);
    }

    @Override
    public int bbd$getSidebarHeight()
    {
        return Math.max(210, imageHeight);
    }
}
