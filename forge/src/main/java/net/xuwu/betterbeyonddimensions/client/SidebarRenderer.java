package net.xuwu.betterbeyonddimensions.client;

import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.common.StorageSnapshot;

import java.util.List;

/** Renders a compact Beyond Dimensions-style storage view beside vanilla container screens. */
public final class SidebarRenderer
{
    public static final int SLOT_COLUMNS = 5;
    public static final int WIDTH = SLOT_COLUMNS * 18 + 14;

    private static final int GRID_TOP = CommonTextures.TOP_BASE_COMMON_HEIGHT
            + CommonTextures.COMMON_CONNECTION_HEIGHT
            + 18;
    public static final int MAX_VISIBLE_ROWS = 8;

    private static final ClientStorageView STORAGE_VIEW = new ClientStorageView();

    private static StorageSnapshot lastSnapshot;
    private static String lastSearch = "";
    private static ButtonState lastPrimarySort;
    private static ButtonState lastSecondarySort;
    private static ButtonState lastReverse;

    private SidebarRenderer()
    {
    }

    public static int getPanelHeight()
    {
        return GRID_TOP + visibleRows() * CommonTextures.COMMON_SLOTS_HEIGHT
                + CommonTextures.BOTTOM_BASE_COMMON_HEIGHT;
    }

    public static int getGridTop()
    {
        return GRID_TOP;
    }

    public static int getVisibleRows()
    {
        return visibleRows();
    }

    /** Updates the real menu Slots before vanilla starts rendering its container slots. */
    public static void prepareSlots(SidebarScreenAccess host)
    {
        if (!ClientStorageState.available() || host.bbd$isSidebarHidden())
        {
            host.bbd$updateSidebarSlots(List.of());
            return;
        }

        List<ClientStorageView.Entry> entries = entries(host);
        StorageSnapshot snapshot = ClientStorageState.snapshot();
        syncScrollState(snapshot, host.bbd$getSearchBox().getValue(), entries.size());
        host.bbd$updateSidebarSlots(entries);
    }

    public static void render(SidebarScreenAccess host, GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        StorageSnapshot snapshot = ClientStorageState.snapshot();
        if (!snapshot.available())
        {
            host.bbd$updateSidebarSlots(List.of());
            setWidgetsVisible(host, false);
            host.bbd$getSidebarToggleButton().visible = false;
            host.bbd$getSidebarToggleButton().active = false;
            return;
        }

        syncWidgets(host);
        if (host.bbd$isSidebarHidden())
        {
            host.bbd$updateSidebarSlots(List.of());
            renderWidgets(host, graphics, mouseX, mouseY, partialTick);
            return;
        }

        List<ClientStorageView.Entry> entries = entries(host);
        int rows = visibleRows();
        syncScrollState(snapshot, host.bbd$getSearchBox().getValue(), entries.size());
        host.bbd$updateSidebarSlots(entries);

        int x = host.bbd$getSidebarX();
        int y = host.bbd$getSidebarY();
        Font font = Minecraft.getInstance().font;

        drawBackground(graphics, x, y, rows);
        String networkName = snapshot.networkName().isEmpty() ? "超越维度" : snapshot.networkName();
        graphics.drawString(font, trim(font, networkName, 50), x + 5, y + 7, 0xFF404040, false);

        int firstEntry = ClientStorageState.scrollRow() * SLOT_COLUMNS;
        for (int row = 0; row < rows; row++)
        {
            for (int col = 0; col < SLOT_COLUMNS; col++)
            {
                int entryIndex = firstEntry + row * SLOT_COLUMNS + col;
                int slotX = x + 8 + col * 18;
                int slotY = y + GRID_TOP + row * 18 + 1;
                if (isHovered(x, y, mouseX, mouseY, row, col))
                {
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);
                }
                SidebarSlot slot = host.bbd$getSidebarSlots().get(row * SLOT_COLUMNS + col);
                if (entryIndex < 0 || entryIndex >= entries.size() || !slot.hasItem())
                {
                    continue;
                }

                slot.getKey().getRender().render(graphics, slot.getKey(), slotX, slotY);
                slot.getKey().getRender().renderAmount(graphics, slot.getStoredAmount(), slotX, slotY);
            }
        }

        renderWidgets(host, graphics, mouseX, mouseY, partialTick);
    }

    public static boolean handleClick(SidebarScreenAccess host, double mouseX, double mouseY, int button)
    {
        if (!ClientStorageState.available() || host.bbd$isSidebarHidden() || (button != 0 && button != 1))
        {
            return false;
        }

        List<ClientStorageView.Entry> entries = entries(host);
        host.bbd$updateSidebarSlots(entries);
        SidebarSlot slot = slotAt(host, mouseX, mouseY);
        if (slot == null)
        {
            return false;
        }

        ItemStack stack = slot.getItem();
        if (Screen.hasShiftDown() && !stack.isEmpty())
        {
            long amount = Math.min(slot.getStoredAmount(), Math.max(1, stack.getMaxStackSize()));
            NetworkHandler.withdraw(stack, (int) amount);
        }
        else
        {
            NetworkHandler.clickSidebar(stack, button, host.bbd$getCarried());
        }
        return true;
    }

    public static boolean handleMouseClick(SidebarScreenAccess host, double mouseX, double mouseY, int button)
    {
        EditBox search = host.bbd$getSearchBox();
        if (search == null)
        {
            return false;
        }

        if (search.visible && search.active && search.isMouseOver(mouseX, mouseY))
        {
            if (button == 0 || button == 1)
            {
                Screen screen = (Screen) (Object) host;
                screen.setFocused(search);
                search.setFocused(true);
                if (button == 0)
                {
                    search.mouseClicked(mouseX, mouseY, button);
                }
                else
                {
                    search.setValue("");
                }
                return true;
            }
            return false;
        }

        if (search.isFocused())
        {
            ((Screen) (Object) host).setFocused(null);
            search.setFocused(false);
        }
        return handleClick(host, mouseX, mouseY, button);
    }

    public static boolean handleScroll(SidebarScreenAccess host, double mouseX, double mouseY, double scrollAmount)
    {
        if (!ClientStorageState.available() || host.bbd$isSidebarHidden() || scrollAmount == 0.0D)
        {
            return false;
        }

        int x = host.bbd$getSidebarX();
        int y = host.bbd$getSidebarY();
        int rows = visibleRows();
        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y + GRID_TOP
                || mouseY >= y + GRID_TOP + rows * CommonTextures.COMMON_SLOTS_HEIGHT)
        {
            return false;
        }

        List<ClientStorageView.Entry> entries = entries(host);
        int totalRows = (entries.size() + SLOT_COLUMNS - 1) / SLOT_COLUMNS;
        int maxScroll = Math.max(0, totalRows - rows);
        int direction = scrollAmount > 0.0D ? -1 : 1;
        ClientStorageState.setScrollRow(Math.max(0, Math.min(maxScroll, ClientStorageState.scrollRow() + direction)));
        return true;
    }

    public static void renderTooltip(SidebarScreenAccess host, GuiGraphics graphics, int mouseX, int mouseY)
    {
        renderButtonTooltip(host, mouseX, mouseY);
        if (!ClientStorageState.available() || host.bbd$isSidebarHidden() || !host.bbd$getCarried().isEmpty())
        {
            return;
        }

        List<ClientStorageView.Entry> entries = entries(host);
        host.bbd$updateSidebarSlots(entries);
        SidebarSlot slot = slotAt(host, mouseX, mouseY);
        if (slot != null && slot.hasItem() && slot.getKey() != null)
        {
            slot.getKey().getRender().renderTooltip(
                    graphics,
                    Minecraft.getInstance().font,
                    slot.getKey(),
                    slot.getStoredAmount(),
                    mouseX,
                    mouseY
            );
        }
    }

    private static void renderButtonTooltip(SidebarScreenAccess host, int mouseX, int mouseY)
    {
        Button[] buttons = {
                host.bbd$getPlayerShiftButton(),
                host.bbd$getContainerShiftButton(),
                host.bbd$getDepositContainerButton(),
                host.bbd$getDepositPlayerButton(),
                host.bbd$getSidebarToggleButton()
        };
        for (Button button : buttons)
        {
            if (button.visible && button.isMouseOver(mouseX, mouseY) && button.getTooltip() != null)
            {
                ((Screen) (Object) host).setTooltipForNextRenderPass(
                        button.getTooltip().toCharSequence(Minecraft.getInstance()));
                return;
            }
        }
    }

    private static void drawBackground(GuiGraphics graphics, int x, int y, int rows)
    {
        blitCropped(graphics, CommonTextures.TOP_BASE_COMMON, x, y,
                CommonTextures.TOP_BASE_COMMON_HEIGHT, CommonTextures.TOP_BASE_COMMON_WIDTH,
                CommonTextures.TOP_BASE_COMMON_HEIGHT);
        blitCropped(graphics, CommonTextures.COMMON_CONNECTION, x, y + CommonTextures.TOP_BASE_COMMON_HEIGHT,
                CommonTextures.COMMON_CONNECTION_HEIGHT, CommonTextures.COMMON_CONNECTION_WIDTH,
                CommonTextures.COMMON_CONNECTION_HEIGHT);

        int controlsTop = y + CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.COMMON_CONNECTION_HEIGHT;
        fillPanelRegion(graphics, x, controlsTop, y + GRID_TOP);

        for (int row = 0; row < rows; row++)
        {
            int rowY = y + GRID_TOP + row * CommonTextures.COMMON_SLOTS_HEIGHT;
            blitSlotRow(graphics, x, rowY);
        }

        int bottomY = y + GRID_TOP + rows * CommonTextures.COMMON_SLOTS_HEIGHT;
        blitCropped(graphics, CommonTextures.BOTTOM_BASE_COMMON, x, bottomY,
                CommonTextures.BOTTOM_BASE_COMMON_HEIGHT, CommonTextures.BOTTOM_BASE_COMMON_WIDTH,
                CommonTextures.BOTTOM_BASE_COMMON_HEIGHT);
    }

    private static void blitCropped(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                    int height, int textureWidth, int textureHeight)
    {
        int edgeWidth = Math.min(2, WIDTH);
        int bodyWidth = WIDTH - edgeWidth;
        graphics.blit(texture, x, y, 0, 0, bodyWidth, height, textureWidth, textureHeight);
        graphics.blit(texture, x + bodyWidth, y, textureWidth - edgeWidth, 0,
                edgeWidth, height, textureWidth, textureHeight);
    }

    private static void blitSlotRow(GuiGraphics graphics, int x, int y)
    {
        // COMMON_SLOTS is a nine-slot row: 7px left border, nine 18px cells, 7px right border.
        // Rebuild the row so the five visible cells do not expose a partial sixth cell.
        int borderWidth = 7;
        int cellsWidth = SLOT_COLUMNS * 18;
        int textureWidth = CommonTextures.COMMON_SLOTS_WIDTH;
        int textureHeight = CommonTextures.COMMON_SLOTS_HEIGHT;
        graphics.blit(CommonTextures.COMMON_SLOTS, x, y, 0, 0,
                borderWidth, textureHeight, textureWidth, textureHeight);
        graphics.blit(CommonTextures.COMMON_SLOTS, x + borderWidth, y, borderWidth, 0,
                cellsWidth, textureHeight, textureWidth, textureHeight);
        graphics.blit(CommonTextures.COMMON_SLOTS, x + borderWidth + cellsWidth, y,
                textureWidth - borderWidth, 0, borderWidth, textureHeight, textureWidth, textureHeight);
    }

    private static void fillPanelRegion(GuiGraphics graphics, int x, int top, int bottom)
    {
        if (bottom <= top)
        {
            return;
        }

        // Keep the same frame as COMMON_CONNECTION while the buttons occupy this region.
        graphics.fill(x + 2, top, x + WIDTH - 2, bottom, 0xFFC6C6C6);
        graphics.fill(x, top, x + 1, bottom, 0xFF000000);
        graphics.fill(x + 1, top, x + 2, bottom, 0xFFF1F1F1);
        graphics.fill(x + WIDTH - 2, top, x + WIDTH - 1, bottom, 0xFFF1F1F1);
        graphics.fill(x + WIDTH - 1, top, x + WIDTH, bottom, 0xFF000000);
    }

    private static void syncWidgets(SidebarScreenAccess host)
    {
        boolean visible = !host.bbd$isSidebarHidden();
        setWidgetsVisible(host, visible);
        Button toggle = host.bbd$getSidebarToggleButton();
        toggle.visible = true;
        toggle.active = true;
        toggle.setMessage(Component.literal(host.bbd$isSidebarHidden() ? "+" : "×"));
        toggle.setTooltip(Tooltip.create(Component.translatable(host.bbd$isSidebarHidden()
                ? "better_beyond_dimensions.tooltip.show_sidebar"
                : "better_beyond_dimensions.tooltip.hide_sidebar")));
        StorageSnapshot snapshot = ClientStorageState.snapshot();
        boolean player = snapshot.shiftPlayerInventory();
        boolean container = snapshot.shiftContainer();
        host.bbd$getPlayerShiftButton().setMessage(Component.translatable(
                "better_beyond_dimensions.button.shift_player", player ? "✓" : "×"));
        host.bbd$getContainerShiftButton().setMessage(Component.translatable(
                "better_beyond_dimensions.button.shift_container", container ? "✓" : "×"));
        host.bbd$getDepositContainerButton().setMessage(Component.translatable("better_beyond_dimensions.button.deposit_container"));
        host.bbd$getDepositPlayerButton().setMessage(Component.translatable("better_beyond_dimensions.button.deposit_player"));
    }

    private static void setWidgetsVisible(SidebarScreenAccess host, boolean visible)
    {
        EditBox search = host.bbd$getSearchBox();
        Button player = host.bbd$getPlayerShiftButton();
        Button container = host.bbd$getContainerShiftButton();
        Button depositContainer = host.bbd$getDepositContainerButton();
        Button depositPlayer = host.bbd$getDepositPlayerButton();
        search.visible = visible;
        search.active = visible;
        player.visible = visible;
        player.active = visible;
        container.visible = visible;
        container.active = visible;
        depositContainer.visible = visible;
        depositContainer.active = visible;
        depositPlayer.visible = visible;
        depositPlayer.active = visible;
    }

    private static void renderWidgets(SidebarScreenAccess host, GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        host.bbd$getSidebarToggleButton().render(graphics, mouseX, mouseY, partialTick);
        if (host.bbd$isSidebarHidden())
        {
            return;
        }
        host.bbd$getSearchBox().render(graphics, mouseX, mouseY, partialTick);
        host.bbd$getPlayerShiftButton().render(graphics, mouseX, mouseY, partialTick);
        host.bbd$getContainerShiftButton().render(graphics, mouseX, mouseY, partialTick);
        host.bbd$getDepositContainerButton().render(graphics, mouseX, mouseY, partialTick);
        host.bbd$getDepositPlayerButton().render(graphics, mouseX, mouseY, partialTick);
    }

    private static List<ClientStorageView.Entry> entries(SidebarScreenAccess host)
    {
        return STORAGE_VIEW.entries(ClientStorageState.snapshot(), host.bbd$getSearchBox().getValue());
    }

    private static int cellIndexAt(SidebarScreenAccess host, double mouseX, double mouseY)
    {
        int x = host.bbd$getSidebarX();
        int y = host.bbd$getSidebarY();
        int rows = visibleRows();
        if (mouseX < x + 7 || mouseX >= x + WIDTH - 7
                || mouseY < y + GRID_TOP || mouseY >= y + GRID_TOP + rows * 18)
        {
            return -1;
        }

        int col = (int) ((mouseX - (x + 7)) / 18.0D);
        int row = (int) ((mouseY - (y + GRID_TOP)) / 18.0D);
        if (col < 0 || col >= SLOT_COLUMNS || row < 0 || row >= rows)
        {
            return -1;
        }

        return (ClientStorageState.scrollRow() + row) * SLOT_COLUMNS + col;
    }

    private static SidebarSlot slotAt(SidebarScreenAccess host, double mouseX, double mouseY)
    {
        int storageIndex = cellIndexAt(host, mouseX, mouseY);
        if (storageIndex < 0)
        {
            return null;
        }

        int visualIndex = storageIndex - ClientStorageState.scrollRow() * SLOT_COLUMNS;
        List<SidebarSlot> slots = host.bbd$getSidebarSlots();
        if (visualIndex < 0 || visualIndex >= slots.size())
        {
            return null;
        }
        SidebarSlot slot = slots.get(visualIndex);
        return slot.isActive() ? slot : null;
    }

    private static boolean isHovered(int x, int y, double mouseX, double mouseY, int row, int col)
    {
        int slotX = x + 8 + col * 18;
        int slotY = y + GRID_TOP + row * 18 + 1;
        return mouseX >= slotX - 1 && mouseX < slotX + 17
                && mouseY >= slotY - 1 && mouseY < slotY + 17;
    }

    private static int visibleRows()
    {
        return Math.max(2, Math.min(MAX_VISIBLE_ROWS, CommonConfigRuntime.uiPageNum));
    }

    private static void syncScrollState(StorageSnapshot snapshot, String search, int entryCount)
    {
        ButtonState primary = CommonConfigRuntime.uiSortButton;
        ButtonState secondary = CommonConfigRuntime.uiSecondSortButton;
        ButtonState reverse = CommonConfigRuntime.uiReverseButton;
        if (snapshot != lastSnapshot || !search.equals(lastSearch)
                || primary != lastPrimarySort || secondary != lastSecondarySort || reverse != lastReverse)
        {
            ClientStorageState.resetScroll();
            lastSnapshot = snapshot;
            lastSearch = search;
            lastPrimarySort = primary;
            lastSecondarySort = secondary;
            lastReverse = reverse;
        }

        int maxScroll = Math.max(0, (entryCount + SLOT_COLUMNS - 1) / SLOT_COLUMNS - visibleRows());
        ClientStorageState.setScrollRow(Math.min(ClientStorageState.scrollRow(), maxScroll));
    }

    private static String trim(Font font, String value, int maxWidth)
    {
        if (font.width(value) <= maxWidth)
        {
            return value;
        }
        String shortened = value;
        while (shortened.length() > 1 && font.width(shortened + "…") > maxWidth)
        {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }
}
