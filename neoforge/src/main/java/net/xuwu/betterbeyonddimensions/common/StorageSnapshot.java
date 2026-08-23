package net.xuwu.betterbeyonddimensions.common;

import java.util.List;

/** Immutable server-to-client state for the sidebar. */
public record StorageSnapshot(
        boolean available,
        String networkName,
        boolean shiftPlayerInventory,
        boolean shiftContainer,
        boolean sidebarHidden,
        List<StorageEntry> entries
)
{
    public StorageSnapshot
    {
        networkName = networkName == null ? "" : networkName;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static StorageSnapshot unavailable(boolean shiftPlayerInventory, boolean shiftContainer,
                                              boolean sidebarHidden)
    {
        return new StorageSnapshot(false, "", shiftPlayerInventory, shiftContainer,
                sidebarHidden, List.of());
    }
}
