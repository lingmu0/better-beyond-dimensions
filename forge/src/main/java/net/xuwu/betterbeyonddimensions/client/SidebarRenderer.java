package net.xuwu.betterbeyonddimensions.client;

import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.client.gui.CommonTextures;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.common.StorageSnapshot;

import java.util.List;

/** Renders a compact Beyond Dimensions-style storage view beside vanilla container screens. */
public final class SidebarRenderer
{
    public static final int WIDTH = CommonTextures.COMMON_SLOTS_WIDTH;

    private static final int GRID_TOP = CommonTextures.TOP_BASE_COMMON_HEIGHT
            + CommonTextures.COMMON_CONNECTION_HEIGHT
            + 26;
    private static final int SLOT_COLUMNS = 9;
    private static final int MAX_VISIBLE_ROWS = 8;

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

    public static void render(SidebarScreenAccess host, GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        StorageSnapshot snapshot = ClientStorageState.snapshot();
        if (!snapshot.available())
        {
            setWidgetsVisible(host, false);
            return;
        }

        syncWidgets(host);
        List<ClientStorageView.Entry> entries = entries(host);
        int rows = visibleRows();
        syncScrollState(snapshot, host.bbd$getSearchBox().getValue(), entries.size());

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
                if (entryIndex < 0 || entryIndex >= entries.size())
                {
                    continue;
                }

                ClientStorageView.Entry entry = entries.get(entryIndex);
                int slotX = x + 8 + col * 18;
                int slotY = y + GRID_TOP + row * 18 + 1;
                if (isHovered(x, y, mouseX, mouseY, row, col))
                {
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);
                }

                ItemStack stack = entry.key().getRenderStack();
                graphics.renderFakeItem(stack, slotX, slotY);
                graphics.renderItemDecorations(font, stack, slotX, slotY, "");
                entry.key().getRender().renderAmount(graphics, entry.amount(), slotX, slotY);
            }
        }

        renderWidgets(host, graphics, mouseX, mouseY, partialTick);
    }

    public static boolean handleClick(SidebarScreenAccess host, double mouseX, double mouseY, int button)
    {
        if (!ClientStorageState.available() || (button != 0 && button != 1))
        {
            return false;
        }

        List<ClientStorageView.Entry> entries = entries(host);
        int index = entryIndexAt(host, mouseX, mouseY, entries.size());
        if (index < 0)
        {
            return false;
        }

        ClientStorageView.Entry entry = entries.get(index);
        int stackAmount = Math.max(1, entry.key().getRenderStack().getMaxStackSize());
        int amount = button == 0 ? stackAmount : Math.max(1, (stackAmount + 1) / 2);
        NetworkHandler.withdraw(entry.key().getRenderStack(), amount);
        return true;
    }

    public static boolean handleScroll(SidebarScreenAccess host, double mouseX, double mouseY, double scrollAmount)
    {
        if (!ClientStorageState.available() || scrollAmount == 0.0D)
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
        if (!ClientStorageState.available())
        {
            return;
        }

        List<ClientStorageView.Entry> entries = entries(host);
        int index = entryIndexAt(host, mouseX, mouseY, entries.size());
        if (index >= 0)
        {
            ClientStorageView.Entry entry = entries.get(index);
            entry.key().getRender().renderTooltip(
                    graphics,
                    Minecraft.getInstance().font,
                    entry.key(),
                    entry.amount(),
                    mouseX,
                    mouseY
            );
        }
    }

    private static void drawBackground(GuiGraphics graphics, int x, int y, int rows)
    {
        graphics.blit(CommonTextures.TOP_BASE_COMMON, x, y, 0, 0,
                CommonTextures.TOP_BASE_COMMON_WIDTH, CommonTextures.TOP_BASE_COMMON_HEIGHT,
                CommonTextures.TOP_BASE_COMMON_WIDTH, CommonTextures.TOP_BASE_COMMON_HEIGHT);
        graphics.blit(CommonTextures.COMMON_CONNECTION, x, y + CommonTextures.TOP_BASE_COMMON_HEIGHT, 0, 0,
                CommonTextures.COMMON_CONNECTION_WIDTH, CommonTextures.COMMON_CONNECTION_HEIGHT,
                CommonTextures.COMMON_CONNECTION_WIDTH, CommonTextures.COMMON_CONNECTION_HEIGHT);

        int controlsTop = y + CommonTextures.TOP_BASE_COMMON_HEIGHT + CommonTextures.COMMON_CONNECTION_HEIGHT;
        graphics.fill(x, controlsTop, x + WIDTH, y + GRID_TOP, 0xFFC6C6C6);

        for (int row = 0; row < rows; row++)
        {
            int rowY = y + GRID_TOP + row * CommonTextures.COMMON_SLOTS_HEIGHT;
            graphics.blit(CommonTextures.COMMON_SLOTS, x, rowY, 0, 0,
                    CommonTextures.COMMON_SLOTS_WIDTH, CommonTextures.COMMON_SLOTS_HEIGHT,
                    CommonTextures.COMMON_SLOTS_WIDTH, CommonTextures.COMMON_SLOTS_HEIGHT);
        }

        int bottomY = y + GRID_TOP + rows * CommonTextures.COMMON_SLOTS_HEIGHT;
        graphics.blit(CommonTextures.BOTTOM_BASE_COMMON, x, bottomY, 0, 0,
                CommonTextures.BOTTOM_BASE_COMMON_WIDTH, CommonTextures.BOTTOM_BASE_COMMON_HEIGHT,
                CommonTextures.BOTTOM_BASE_COMMON_WIDTH, CommonTextures.BOTTOM_BASE_COMMON_HEIGHT);
    }

    private static void syncWidgets(SidebarScreenAccess host)
    {
        setWidgetsVisible(host, true);
        StorageSnapshot snapshot = ClientStorageState.snapshot();
        boolean player = snapshot.shiftPlayerInventory();
        boolean container = snapshot.shiftContainer();
        host.bbd$getPlayerShiftButton().setMessage(Component.literal("玩家移入:" + (player ? "开" : "关")));
        host.bbd$getContainerShiftButton().setMessage(Component.literal("容器移入:" + (container ? "开" : "关")));
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

    private static int entryIndexAt(SidebarScreenAccess host, double mouseX, double mouseY, int entryCount)
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

        int index = (ClientStorageState.scrollRow() + row) * SLOT_COLUMNS + col;
        return index >= 0 && index < entryCount ? index : -1;
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
