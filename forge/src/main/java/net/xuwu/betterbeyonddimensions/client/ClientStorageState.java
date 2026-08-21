package net.xuwu.betterbeyonddimensions.client;

import net.xuwu.betterbeyonddimensions.common.StorageEntry;
import net.xuwu.betterbeyonddimensions.common.StorageSnapshot;

import java.util.List;

/** Client copy of the last server-authoritative snapshot. */
public final class ClientStorageState
{
    private static volatile StorageSnapshot snapshot = StorageSnapshot.unavailable(false, false);

    private ClientStorageState()
    {
    }

    public static void apply(StorageSnapshot next)
    {
        snapshot = next == null ? StorageSnapshot.unavailable(false, false) : next;
    }

    public static void clear()
    {
        snapshot = StorageSnapshot.unavailable(false, false);
    }

    public static StorageSnapshot snapshot()
    {
        return snapshot;
    }

    public static boolean available()
    {
        return snapshot.available();
    }

    public static List<StorageEntry> entries()
    {
        return snapshot.entries();
    }
}
