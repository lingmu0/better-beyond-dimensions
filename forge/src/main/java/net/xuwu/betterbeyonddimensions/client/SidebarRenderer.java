package net.xuwu.betterbeyonddimensions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.common.StorageEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws and handles the lightweight overlay shared by every container screen. */
public final class SidebarRenderer
{
    public static final int WIDTH = 164;
    private static final int HEIGHT = 210;
    private static final int LIST_TOP = 104;
    private static final int ROW_HEIGHT = 22;

    private SidebarRenderer()
    {
    }

    public static void render(SidebarScreenAccess host, GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        if (!ClientStorageState.available())
        {
            setWidgetsVisible(host, false);
            return;
        }

        syncWidgets(host);
        int x = host.bbd$getSidebarX();
        int y = host.bbd$getSidebarY();
        int height = host.bbd$getSidebarHeight();
        Font font = Minecraft.getInstance().font;

        graphics.fill(x, y, x + WIDTH, y + height, 0xE9101520);
        graphics.fill(x, y, x + WIDTH, y + 1, 0xFF6C8DB7);
        graphics.fill(x, y + height - 1, x + WIDTH, y + height, 0xFF26354D);
        graphics.drawString(font, Component.literal("超越维度"), x + 6, y + 5, 0xFFFFFF, true);

        String networkName = ClientStorageState.snapshot().networkName();
        graphics.drawString(font, trim(font, networkName, WIDTH - 12), x + 6, y + 17, 0xFF9BB9E6, false);

        List<StorageEntry> entries = visibleEntries(host.bbd$getSearchBox());
        int rows = Math.max(1, (height - LIST_TOP - 5) / ROW_HEIGHT);
        for (int index = 0; index < Math.min(rows, entries.size()); index++)
        {
            StorageEntry entry = entries.get(index);
            int rowY = y + LIST_TOP + index * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered)
            {
                graphics.fill(x + 2, rowY, x + WIDTH - 2, rowY + ROW_HEIGHT - 1, 0xFF304B70);
            }

            ItemStack stack = entry.stack();
            graphics.renderItem(stack, x + 5, rowY + 2);
            graphics.drawString(font, trim(font, stack.getHoverName().getString(), WIDTH - 38), x + 27, rowY + 3, 0xF2F2F2, false);
            graphics.drawString(font, formatAmount(entry.amount()), x + 27, rowY + 12, 0xFFB7C8E8, false);
        }

        renderWidgets(host, graphics, mouseX, mouseY, partialTick);
    }

    public static boolean handleClick(SidebarScreenAccess host, double mouseX, double mouseY, int button)
    {
        if (!ClientStorageState.available())
        {
            return false;
        }

        int x = host.bbd$getSidebarX();
        int y = host.bbd$getSidebarY();
        int height = host.bbd$getSidebarHeight();
        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y + LIST_TOP || mouseY >= y + height)
        {
            return false;
        }

        int index = (int) ((mouseY - y - LIST_TOP) / ROW_HEIGHT);
        List<StorageEntry> entries = visibleEntries(host.bbd$getSearchBox());
        int rows = Math.max(1, (height - LIST_TOP - 5) / ROW_HEIGHT);
        if (index < 0 || index >= rows || index >= entries.size())
        {
            return false;
        }

        StorageEntry entry = entries.get(index);
        // Left click extracts one vanilla stack; right click extracts one item.
        NetworkHandler.withdraw(entry.stack(), button == 1 ? 1 : 64);
        return true;
    }

    public static void renderTooltip(SidebarScreenAccess host, GuiGraphics graphics, int mouseX, int mouseY)
    {
        if (!ClientStorageState.available())
        {
            return;
        }
        int x = host.bbd$getSidebarX();
        int y = host.bbd$getSidebarY();
        int height = host.bbd$getSidebarHeight();
        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y + LIST_TOP || mouseY >= y + height)
        {
            return;
        }
        int index = (int) ((mouseY - y - LIST_TOP) / ROW_HEIGHT);
        List<StorageEntry> entries = visibleEntries(host.bbd$getSearchBox());
        int rows = Math.max(1, (height - LIST_TOP - 5) / ROW_HEIGHT);
        if (index < 0 || index >= rows || index >= entries.size())
        {
            return;
        }
        graphics.renderTooltip(Minecraft.getInstance().font, entries.get(index).stack(), mouseX, mouseY);
    }

    private static void syncWidgets(SidebarScreenAccess host)
    {
        setWidgetsVisible(host, true);
        boolean player = ClientStorageState.snapshot().shiftPlayerInventory();
        boolean container = ClientStorageState.snapshot().shiftContainer();
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
        if (!ClientStorageState.available())
        {
            return;
        }
        host.bbd$getSearchBox().render(graphics, mouseX, mouseY, partialTick);
        host.bbd$getPlayerShiftButton().render(graphics, mouseX, mouseY, partialTick);
        host.bbd$getContainerShiftButton().render(graphics, mouseX, mouseY, partialTick);
        host.bbd$getDepositContainerButton().render(graphics, mouseX, mouseY, partialTick);
        host.bbd$getDepositPlayerButton().render(graphics, mouseX, mouseY, partialTick);
    }

    private static List<StorageEntry> visibleEntries(EditBox searchBox)
    {
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty())
        {
            return ClientStorageState.entries();
        }

        List<StorageEntry> result = new ArrayList<>();
        for (StorageEntry entry : ClientStorageState.entries())
        {
            String name = entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.stack().getItem());
            String registryName = id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
            if (name.contains(query) || registryName.contains(query))
            {
                result.add(entry);
            }
        }
        return result;
    }

    private static String formatAmount(long amount)
    {
        if (amount >= 1_000_000_000L)
        {
            return String.format(Locale.ROOT, "%.1fG", amount / 1_000_000_000.0D);
        }
        if (amount >= 1_000_000L)
        {
            return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0D);
        }
        if (amount >= 1_000L)
        {
            return String.format(Locale.ROOT, "%.1fk", amount / 1_000.0D);
        }
        return Long.toString(amount);
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
