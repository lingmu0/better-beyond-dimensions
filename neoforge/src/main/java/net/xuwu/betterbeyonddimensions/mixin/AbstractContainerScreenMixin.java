package net.xuwu.betterbeyonddimensions.mixin;

import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
import net.xuwu.betterbeyonddimensions.client.SidebarRenderer;
import net.xuwu.betterbeyonddimensions.client.SidebarScreenAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the sidebar to vanilla container screens, excluding Beyond Dimensions' own network screens. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> implements SidebarScreenAccess
{
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;
    @Shadow protected T menu;

    @Unique private EditBox bbd$searchBox;
    @Unique private Button bbd$playerShiftButton;
    @Unique private Button bbd$containerShiftButton;
    @Unique private Button bbd$depositContainerButton;
    @Unique private Button bbd$depositPlayerButton;
    @Unique private net.xuwu.betterbeyonddimensions.client.SidebarScrollWidget bbd$scrollWidget;

    @Inject(method = "init", at = @At("TAIL"))
    private void bbd$initSidebar(CallbackInfo callbackInfo)
    {
        if (bbd$isBeyondDimensionsScreen())
        {
            ClientStorageState.clear();
            return;
        }

        int x = bbd$getSidebarX();
        int y = bbd$getSidebarY();
        int buttonWidth = (SidebarRenderer.WIDTH - 8 - 3) / 4;
        int buttonGap = 1;

        bbd$searchBox = bbd$addRenderableWidget(new EditBox(
                Minecraft.getInstance().font,
                x + 4,
                y + 4,
                SidebarRenderer.WIDTH - 8,
                Minecraft.getInstance().font.lineHeight + 5,
                Component.translatable("wintercogs.beyonddimensions.dimensionsguisearch")
        ));
        bbd$searchBox.setMaxLength(200);
        bbd$searchBox.setBordered(true);
        bbd$searchBox.setVisible(true);
        bbd$searchBox.setTextColor(16777215);
        bbd$searchBox.setTooltip(Tooltip.create(Component.translatable("tooltip.editbox.beyonddimensions.search")));
        bbd$searchBox.setResponder(text -> {
            bbd$searchBox.setSuggestion(text.isEmpty()
                    ? Component.translatable("wintercogs.beyonddimensions.dimensionsguisearch").getString()
                    : null);
            CommonConfigRuntime.uiSearch = text;
            ClientStorageState.resetScroll();
        });
        bbd$searchBox.setSuggestion(CommonConfigRuntime.uiSearch.isEmpty()
                ? Component.translatable("wintercogs.beyonddimensions.dimensionsguisearch").getString()
                : null);
        bbd$searchBox.setValue(CommonConfigRuntime.uiSearch);

        bbd$playerShiftButton = bbd$addRenderableWidget(Button.builder(Component.literal("人×"), button -> NetworkHandler.togglePlayerShift())
                .bounds(x + 3, y + 33, buttonWidth, 14).build());
        bbd$playerShiftButton.setTooltip(Tooltip.create(Component.translatable("better_beyond_dimensions.tooltip.shift_player")));
        bbd$containerShiftButton = bbd$addRenderableWidget(Button.builder(Component.literal("箱×"), button -> NetworkHandler.toggleContainerShift())
                .bounds(x + 3 + buttonWidth + buttonGap, y + 33, buttonWidth, 14).build());
        bbd$containerShiftButton.setTooltip(Tooltip.create(Component.translatable("better_beyond_dimensions.tooltip.shift_container")));
        bbd$depositContainerButton = bbd$addRenderableWidget(Button.builder(Component.literal("存箱"), button -> NetworkHandler.depositContainer())
                .bounds(x + 3 + (buttonWidth + buttonGap) * 2, y + 33, buttonWidth, 14).build());
        bbd$depositContainerButton.setTooltip(Tooltip.create(Component.translatable("better_beyond_dimensions.tooltip.deposit_container")));
        bbd$depositPlayerButton = bbd$addRenderableWidget(Button.builder(Component.literal("存包"), button -> NetworkHandler.depositPlayerInventory())
                .bounds(x + 3 + (buttonWidth + buttonGap) * 3, y + 33, buttonWidth, 14).build());
        bbd$depositPlayerButton.setTooltip(Tooltip.create(Component.translatable("better_beyond_dimensions.tooltip.deposit_player")));
        bbd$scrollWidget = bbd$addRenderableWidget(new net.xuwu.betterbeyonddimensions.client.SidebarScrollWidget(
                this,
                x + 7,
                y + SidebarRenderer.getGridTop(),
                SidebarRenderer.WIDTH - 14,
                Math.max(18, SidebarRenderer.getPanelHeight() - SidebarRenderer.getGridTop() - 7)
        ));

        bbd$setWidgetsVisible(false);
        ClientStorageState.clear();
        NetworkHandler.requestSnapshot();
    }

    @Unique
    private <W extends net.minecraft.client.gui.components.events.GuiEventListener
            & net.minecraft.client.gui.components.Renderable
            & net.minecraft.client.gui.narration.NarratableEntry> W bbd$addRenderableWidget(W widget)
    {
        ScreenWidgetAccessor screen = (ScreenWidgetAccessor) (Object) this;
        screen.bbd$getChildren().add(widget);
        screen.bbd$getRenderables().add(widget);
        screen.bbd$getNarratables().add(widget);
        return widget;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void bbd$renderSidebar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callbackInfo)
    {
        if (!bbd$isBeyondDimensionsScreen() && bbd$searchBox != null)
        {
            SidebarRenderer.render(this, graphics, mouseX, mouseY, partialTick);
            SidebarRenderer.renderTooltip(this, graphics, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bbd$sidebarClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (!bbd$isBeyondDimensionsScreen() && bbd$searchBox != null
                && SidebarRenderer.handleClick(this, mouseX, mouseY, button))
        {
            callbackInfo.setReturnValue(true);
        }
    }

    @Unique
    private boolean bbd$isBeyondDimensionsScreen()
    {
        return this.getClass().getName().startsWith("com.wintercogs.beyonddimensions.");
    }

    @Unique
    private void bbd$setWidgetsVisible(boolean visible)
    {
        if (bbd$searchBox == null)
        {
            return;
        }
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
    public net.minecraft.world.item.ItemStack bbd$getCarried()
    {
        return menu.getCarried();
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
        return Math.max(SidebarRenderer.getPanelHeight(), imageHeight);
    }
}
