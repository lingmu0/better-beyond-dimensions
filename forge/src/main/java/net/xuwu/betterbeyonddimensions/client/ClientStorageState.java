package net.xuwu.betterbeyonddimensions.client;

import net.xuwu.betterbeyonddimensions.common.StorageEntry;
import net.xuwu.betterbeyonddimensions.common.StorageSnapshot;

import java.util.List;

/** Client copy of the last server-authoritative snapshot. */
public final class ClientStorageState
{
    private static volatile StorageSnapshot snapshot = StorageSnapshot.unavailable(false, false, false);
    private static int scrollRow;

    private ClientStorageState()
    {
    }

    public static void apply(StorageSnapshot next)
    {
        snapshot = next == null ? StorageSnapshot.unavailable(false, false, false) : next;
        scrollRow = 0;
    }

    public static void clear()
    {
        snapshot = StorageSnapshot.unavailable(false, false, false);
        scrollRow = 0;
    }

    public static StorageSnapshot snapshot()
    {
        return snapshot;
    }

    public static boolean available()
    {
        return snapshot.available();
    }

    public static boolean isSidebarHidden()
    {
        return snapshot.sidebarHidden();
    }

    /** Applies the button immediately while the server-authoritative snapshot is in flight. */
    public static void setSidebarHidden(boolean hidden)
    {
        StorageSnapshot current = snapshot;
        snapshot = new StorageSnapshot(
                current.available(),
                current.networkName(),
                current.shiftPlayerInventory(),
                current.shiftContainer(),
                hidden,
                current.entries()
        );
    }

    public static List<StorageEntry> entries()
    {
        return snapshot.entries();
    }

    public static int scrollRow()
    {
        return scrollRow;
    }

    public static void setScrollRow(int row)
    {
        scrollRow = Math.max(0, row);
    }

    public static void resetScroll()
    {
        scrollRow = 0;
    }

}
