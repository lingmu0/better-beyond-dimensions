package net.xuwu.betterbeyonddimensions.mixin;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.client.ClientStorageView;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
import net.xuwu.betterbeyonddimensions.client.SidebarRenderer;
import net.xuwu.betterbeyonddimensions.client.SidebarPositionStore;
import net.xuwu.betterbeyonddimensions.client.SidebarScreenAccess;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageMenuAccess;
import net.xuwu.betterbeyonddimensions.common.NetworkStorageSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

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
    @Unique private Button bbd$sidebarToggleButton;
    @Unique private net.xuwu.betterbeyonddimensions.client.SidebarScrollWidget bbd$scrollWidget;
    @Unique private boolean bbd$consumeSidebarMouseRelease;
    @Unique private final List<NetworkStorageSlot> bbd$sidebarSlots = new ArrayList<>();
    @Unique private List<ItemStackKey> bbd$lastSidebarView = List.of();
    @Unique private int bbd$sidebarX;
    @Unique private int bbd$sidebarY;
    @Unique private String bbd$sidebarPositionKey = "";
    @Unique private boolean bbd$sidebarDragging;
    @Unique private double bbd$sidebarDragStartMouseX;
    @Unique private double bbd$sidebarDragStartMouseY;
    @Unique private int bbd$sidebarDragOffsetX;
    @Unique private int bbd$sidebarDragOffsetY;

    @Inject(method = "init", at = @At("TAIL"))
    private void bbd$initSidebar(CallbackInfo callbackInfo)
    {
        if (bbd$isSidebarExcludedScreen())
        {
            return;
        }

        bbd$initializeSidebarPosition();
        bbd$rebuildSidebarSlots();

        int x = bbd$getSidebarX();
        int y = bbd$getSidebarY();
        int buttonGap = 3;
        int buttonWidth = (SidebarRenderer.WIDTH - 7 - buttonGap) / 2;
        int firstButtonX = x + 3;
        int secondButtonX = firstButtonX + buttonWidth + buttonGap;
        int firstButtonY = y + 20;
        int secondButtonY = firstButtonY + 15;
        int searchHeight = Minecraft.getInstance().font.lineHeight + 5;

        bbd$searchBox = bbd$addRenderableWidget(new EditBox(
                Minecraft.getInstance().font,
                x + SidebarRenderer.SEARCH_LEFT,
                y + 4,
                SidebarRenderer.getSearchWidth(),
                searchHeight,
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
                .bounds(firstButtonX, firstButtonY, buttonWidth, 14).build());
        bbd$playerShiftButton.setTooltip(Tooltip.create(Component.translatable("better_beyond_dimensions.tooltip.shift_player")));
        bbd$containerShiftButton = bbd$addRenderableWidget(Button.builder(Component.literal("箱×"), button -> NetworkHandler.toggleContainerShift())
                .bounds(secondButtonX, firstButtonY, buttonWidth, 14).build());
        bbd$containerShiftButton.setTooltip(Tooltip.create(Component.translatable("better_beyond_dimensions.tooltip.shift_container")));
        bbd$depositContainerButton = bbd$addRenderableWidget(Button.builder(Component.literal("存箱"), button -> NetworkHandler.depositContainer())
                .bounds(firstButtonX, secondButtonY, buttonWidth, 14).build());
        bbd$depositContainerButton.setTooltip(Tooltip.create(Component.translatable("better_beyond_dimensions.tooltip.deposit_container")));
        bbd$depositPlayerButton = bbd$addRenderableWidget(Button.builder(Component.literal("存包"), button -> NetworkHandler.depositPlayerInventory())
                .bounds(secondButtonX, secondButtonY, buttonWidth, 14).build());
        bbd$depositPlayerButton.setTooltip(Tooltip.create(Component.translatable("better_beyond_dimensions.tooltip.deposit_player")));
        bbd$sidebarToggleButton = bbd$addRenderableWidget(Button.builder(Component.literal("×"), button -> {
                })
                .bounds(SidebarRenderer.getToggleX(x), y + 4,
                        SidebarRenderer.TOGGLE_WIDTH, searchHeight).build());
        bbd$sidebarToggleButton.setTooltip(Tooltip.create(Component.translatable("better_beyond_dimensions.tooltip.hide_sidebar")));
        bbd$scrollWidget = bbd$addRenderableWidget(new net.xuwu.betterbeyonddimensions.client.SidebarScrollWidget(
                this,
                x + 7,
                y + SidebarRenderer.getGridTop(),
                SidebarRenderer.WIDTH - 14,
                Math.max(18, SidebarRenderer.getPanelHeight() - SidebarRenderer.getGridTop() - 7)
        ));

        bbd$setWidgetsVisible(false);
        bbd$sidebarToggleButton.visible = false;
        bbd$sidebarToggleButton.active = false;
        NetworkHandler.requestSnapshot();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void bbd$prepareSidebarSlots(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo callbackInfo)
    {
        if (!bbd$isSidebarExcludedScreen() && bbd$searchBox != null)
        {
            bbd$rebuildSidebarSlots();
            SidebarRenderer.prepareSlots(this);
        }
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void bbd$skipNativeSidebarSlot(GuiGraphics graphics, Slot slot, CallbackInfo callbackInfo)
    {
        if (slot instanceof NetworkStorageSlot)
        {
            callbackInfo.cancel();
        }
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
        if (!bbd$isSidebarExcludedScreen() && bbd$searchBox != null)
        {
            bbd$rebuildSidebarSlots();
            SidebarRenderer.render(this, graphics, mouseX, mouseY, partialTick);
            SidebarRenderer.renderTooltip(this, graphics, mouseX, mouseY);
        }
    }

    /** Use the exact typed-stack tooltip path used by Beyond Dimensions' own screens. */
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void bbd$renderStorageTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                          CallbackInfo callbackInfo)
    {
        if (!bbd$isSidebarExcludedScreen() && bbd$searchBox != null
                && SidebarRenderer.renderStorageTooltip(this, graphics, mouseX, mouseY))
        {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bbd$sidebarClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (!bbd$isSidebarExcludedScreen() && bbd$searchBox != null
                && SidebarRenderer.handleMouseClick(this, mouseX, mouseY, button))
        {
            bbd$markSidebarMouseRelease();
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void bbd$sidebarDrag(double mouseX, double mouseY, int button, double dragX, double dragY,
                                 CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (!bbd$isSidebarExcludedScreen() && bbd$searchBox != null
                && SidebarRenderer.handleMouseDrag(this, mouseX, mouseY, button))
        {
            callbackInfo.setReturnValue(true);
        }
    }

    /** Map the sidebar's screen coordinates to its real menu slot for vanilla hit-testing. */
    @Inject(method = "findSlot", at = @At("RETURN"), cancellable = true)
    private void bbd$findSidebarSlot(double mouseX, double mouseY,
                                     CallbackInfoReturnable<Slot> callbackInfo)
    {
        if (!bbd$isSidebarExcludedScreen() && bbd$searchBox != null)
        {
            NetworkStorageSlot slot = SidebarRenderer.findSlotAt(this, mouseX, mouseY);
            if (slot != null)
            {
                callbackInfo.setReturnValue(slot);
            }
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void bbd$networkSlotClicked(Slot slot, int slotId, int button, ClickType clickType,
                                         CallbackInfo callbackInfo)
    {
        if (!bbd$isSidebarExcludedScreen() && slot instanceof NetworkStorageSlot)
        {
            // Never let middle-click/clone mutate a sidebar item; the wheel belongs to paging.
            if (button != 2 && clickType != ClickType.CLONE)
            {
                NetworkHandler.clickSidebarSlot(slotId, button, clickType);
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void bbd$sidebarRelease(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (!bbd$isSidebarExcludedScreen() && bbd$searchBox != null
                && SidebarRenderer.handleMouseRelease(this, mouseX, mouseY, button))
        {
            callbackInfo.setReturnValue(true);
        }
    }

    @Unique
    private boolean bbd$isSidebarExcludedScreen()
    {
        return bbd$isBeyondScreen() || bbd$isCreativeScreen();
    }

    @Unique
    private boolean bbd$isBeyondScreen()
    {
        return this.getClass().getName().startsWith("com.wintercogs.beyonddimensions.");
    }

    @Unique
    private boolean bbd$isCreativeScreen()
    {
        return (Object) this instanceof CreativeModeInventoryScreen;
    }

    @Unique
    private void bbd$initializeSidebarPosition()
    {
        bbd$sidebarPositionKey = this.getClass().getName() + "|" + menu.getClass().getName()
                + "|" + imageWidth + "x" + imageHeight;
        SidebarPositionStore.Position saved = SidebarPositionStore.get(bbd$sidebarPositionKey).orElse(null);
        int x = saved == null ? bbd$defaultSidebarX() : leftPos + saved.offsetX();
        int y = saved == null ? topPos + 1 : topPos + saved.offsetY();
        bbd$sidebarX = bbd$clampSidebarX(x);
        bbd$sidebarY = bbd$clampSidebarY(y);
    }

    @Unique
    private int bbd$defaultSidebarX()
    {
        int margin = 4;
        int gap = 2;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int leftCandidate = leftPos - SidebarRenderer.WIDTH - gap;
        int rightCandidate = leftPos + imageWidth + gap;
        boolean fitsLeft = leftCandidate >= margin;
        boolean fitsRight = rightCandidate + SidebarRenderer.WIDTH <= screenWidth - margin;
        if (fitsLeft)
        {
            return leftCandidate;
        }
        if (fitsRight)
        {
            return rightCandidate;
        }

        int leftSpace = leftPos - margin;
        int rightSpace = screenWidth - margin - (leftPos + imageWidth);
        return rightSpace > leftSpace ? rightCandidate : leftCandidate;
    }

    @Unique
    private int bbd$clampSidebarX(int x)
    {
        int max = Math.max(4, Minecraft.getInstance().getWindow().getGuiScaledWidth()
                - SidebarRenderer.WIDTH - 4);
        return Math.max(4, Math.min(max, x));
    }

    @Unique
    private int bbd$clampSidebarY(int y)
    {
        int max = Math.max(4, Minecraft.getInstance().getWindow().getGuiScaledHeight()
                - SidebarRenderer.getPanelHeight() - 4);
        return Math.max(4, Math.min(max, y));
    }

    @Unique
    private void bbd$setSidebarPosition(int x, int y)
    {
        bbd$sidebarX = bbd$clampSidebarX(x);
        bbd$sidebarY = bbd$clampSidebarY(y);
        bbd$layoutSidebarWidgets();
        bbd$positionSidebarSlots();
    }

    @Unique
    private void bbd$layoutSidebarWidgets()
    {
        if (bbd$searchBox == null)
        {
            return;
        }

        int x = bbd$sidebarX;
        int y = bbd$sidebarY;
        int buttonGap = 3;
        int buttonWidth = (SidebarRenderer.WIDTH - 7 - buttonGap) / 2;
        int firstButtonX = x + 3;
        int secondButtonX = firstButtonX + buttonWidth + buttonGap;
        int firstButtonY = y + 20;
        int secondButtonY = firstButtonY + 15;
        bbd$searchBox.setX(x + SidebarRenderer.SEARCH_LEFT);
        bbd$searchBox.setY(y + 4);
        bbd$playerShiftButton.setX(firstButtonX);
        bbd$playerShiftButton.setY(firstButtonY);
        bbd$containerShiftButton.setX(secondButtonX);
        bbd$containerShiftButton.setY(firstButtonY);
        bbd$depositContainerButton.setX(firstButtonX);
        bbd$depositContainerButton.setY(secondButtonY);
        bbd$depositPlayerButton.setX(secondButtonX);
        bbd$depositPlayerButton.setY(secondButtonY);
        bbd$sidebarToggleButton.setX(SidebarRenderer.getToggleX(x));
        bbd$sidebarToggleButton.setY(y + 4);
        bbd$scrollWidget.setX(x + 7);
        bbd$scrollWidget.setY(y + SidebarRenderer.getGridTop());
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
        bbd$scrollWidget.visible = visible;
        bbd$scrollWidget.active = visible;
    }

    @Override
    public void bbd$toggleSidebarVisibility()
    {
        boolean hidden = !ClientStorageState.isSidebarHidden();
        ClientStorageState.setSidebarHidden(hidden);
        NetworkHandler.setSidebarHidden(hidden);
        if (hidden && bbd$searchBox != null)
        {
            ((net.minecraft.client.gui.screens.Screen) (Object) this).setFocused(null);
            bbd$searchBox.setFocused(false);
        }
    }

    @Override public EditBox bbd$getSearchBox() { return bbd$searchBox; }
    @Override public Button bbd$getPlayerShiftButton() { return bbd$playerShiftButton; }
    @Override public Button bbd$getContainerShiftButton() { return bbd$containerShiftButton; }
    @Override public Button bbd$getDepositContainerButton() { return bbd$depositContainerButton; }
    @Override public Button bbd$getDepositPlayerButton() { return bbd$depositPlayerButton; }
    @Override public Button bbd$getSidebarToggleButton() { return bbd$sidebarToggleButton; }
    @Override public net.minecraft.world.item.ItemStack bbd$getCarried() { return menu.getCarried(); }
    @Override public void bbd$markSidebarMouseRelease() { bbd$consumeSidebarMouseRelease = true; }
    @Override public boolean bbd$consumeSidebarMouseRelease()
    {
        boolean consume = bbd$consumeSidebarMouseRelease;
        bbd$consumeSidebarMouseRelease = false;
        return consume;
    }
    @Override public boolean bbd$isSidebarHidden() { return ClientStorageState.isSidebarHidden(); }
    @Override public boolean bbd$isSidebarDragging() { return bbd$sidebarDragging; }

    @Override
    public void bbd$beginSidebarDrag(double mouseX, double mouseY)
    {
        bbd$sidebarDragging = true;
        bbd$sidebarDragStartMouseX = mouseX;
        bbd$sidebarDragStartMouseY = mouseY;
        bbd$sidebarDragOffsetX = (int) Math.floor(mouseX) - bbd$sidebarX;
        bbd$sidebarDragOffsetY = (int) Math.floor(mouseY) - bbd$sidebarY;
    }

    @Override
    public void bbd$dragSidebarTo(double mouseX, double mouseY)
    {
        if (!bbd$sidebarDragging)
        {
            return;
        }
        bbd$setSidebarPosition(
                (int) Math.floor(mouseX) - bbd$sidebarDragOffsetX,
                (int) Math.floor(mouseY) - bbd$sidebarDragOffsetY
        );
    }

    @Override
    public boolean bbd$endSidebarDrag(double mouseX, double mouseY)
    {
        if (!bbd$sidebarDragging)
        {
            return false;
        }

        bbd$dragSidebarTo(mouseX, mouseY);
        bbd$sidebarDragging = false;
        double deltaX = mouseX - bbd$sidebarDragStartMouseX;
        double deltaY = mouseY - bbd$sidebarDragStartMouseY;
        boolean moved = deltaX * deltaX + deltaY * deltaY >= 9.0D;
        if (moved)
        {
            SidebarPositionStore.save(bbd$sidebarPositionKey,
                    bbd$sidebarX - leftPos, bbd$sidebarY - topPos);
        }
        return moved;
    }

    @Override public int bbd$getSidebarX() { return bbd$sidebarX; }
    @Override public int bbd$getSidebarY() { return bbd$sidebarY; }
    @Override public int bbd$getSidebarHeight() { return Math.max(SidebarRenderer.getPanelHeight(), imageHeight); }
    @Override public List<NetworkStorageSlot> bbd$getSidebarSlots() { return bbd$sidebarSlots; }

    @Override
    public void bbd$rebuildSidebarSlots()
    {
        if (menu == null || bbd$isSidebarExcludedScreen())
        {
            return;
        }

        int slotBaseX = bbd$getSidebarX() + 8 - leftPos;
        int slotBaseY = bbd$getSidebarY() + SidebarRenderer.getGridTop() + 1 - topPos;
        NetworkStorageMenuAccess access = (NetworkStorageMenuAccess) (Object) menu;
        access.bbd$ensureNetworkSlots(slotBaseX, slotBaseY);
        bbd$sidebarSlots.clear();
        bbd$sidebarSlots.addAll(access.bbd$getNetworkSlots());
        bbd$positionSidebarSlots();
    }

    @Unique
    private void bbd$positionSidebarSlots()
    {
        int slotBaseX = bbd$getSidebarX() + 8 - leftPos;
        int slotBaseY = bbd$getSidebarY() + SidebarRenderer.getGridTop() + 1 - topPos;
        for (int index = 0; index < bbd$sidebarSlots.size(); index++)
        {
            SlotPositionAccessor accessor = (SlotPositionAccessor) (Object) bbd$sidebarSlots.get(index);
            accessor.bbd$setX(slotBaseX + index % SidebarRenderer.SLOT_COLUMNS * 18);
            accessor.bbd$setY(slotBaseY + index / SidebarRenderer.SLOT_COLUMNS * 18);
        }
    }

    @Override
    public void bbd$updateSidebarSlots(List<ClientStorageView.Entry> entries)
    {
        int firstEntry = ClientStorageState.scrollRow() * SidebarRenderer.SLOT_COLUMNS;
        int rows = SidebarRenderer.getVisibleRows();
        for (int index = 0; index < bbd$sidebarSlots.size(); index++)
        {
            int row = index / SidebarRenderer.SLOT_COLUMNS;
            int entryIndex = firstEntry + index;
            boolean active = ClientStorageState.available() && row < rows;
            NetworkStorageSlot slot = bbd$sidebarSlots.get(index);
            ClientStorageView.Entry entry = entryIndex < entries.size() ? entries.get(entryIndex) : null;
            if (entry == null)
            {
                slot.clear();
            }
            else
            {
                slot.update(entryIndex, entry.key(), entry.amount(), active);
            }
        }

        List<ItemStackKey> viewKeys = new ArrayList<>(bbd$sidebarSlots.size());
        List<net.minecraft.world.item.ItemStack> viewStacks = new ArrayList<>(bbd$sidebarSlots.size());
        for (NetworkStorageSlot slot : bbd$sidebarSlots)
        {
            ItemStackKey key = slot.getKey();
            viewKeys.add(key == null ? ItemStackKey.EMPTY : key);
            viewStacks.add(key == null ? net.minecraft.world.item.ItemStack.EMPTY : key.copyStack());
        }
        if (!viewKeys.equals(bbd$lastSidebarView))
        {
            bbd$lastSidebarView = List.copyOf(viewKeys);
            NetworkHandler.updateSidebarView(viewStacks);
        }
    }
}
