package net.xuwu.betterbeyonddimensions.common;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** Read-only adapter from the Beyond Dimensions API to this add-on's packets. */
public final class NetworkStorage
{
    private static final int MAX_CLIENT_ENTRIES = 512;

    private NetworkStorage()
    {
    }

    public static StorageSnapshot snapshot(Player player)
    {
        boolean shiftPlayer = StorageActions.isShiftPlayerInventoryEnabled(player);
        boolean shiftContainer = StorageActions.isShiftContainerEnabled(player);
        boolean sidebarHidden = StorageActions.isSidebarHidden(player);
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        if (network == null)
        {
            return StorageSnapshot.unavailable(shiftPlayer, shiftContainer, sidebarHidden);
        }

        UnifiedStorage storage = network.getUnifiedStorage();
        List<StorageEntry> entries = new ArrayList<>();
        for (KeyAmount stored : storage.getStorage())
        {
            if (stored.amount() <= 0L || !(stored.key() instanceof ItemStackKey itemKey) || itemKey.isEmpty())
            {
                continue;
            }
            long insertedTime = storage.getCreationTimeMap().getOrDefault(stored.key(), 0L);
            long modifiedTime = storage.getLastModifiedTimeMap().getOrDefault(stored.key(), 0L);
            entries.add(new StorageEntry(itemKey.copyStack(), stored.amount(), insertedTime, modifiedTime));
        }

        if (entries.size() > MAX_CLIENT_ENTRIES)
        {
            entries = new ArrayList<>(entries.subList(0, MAX_CLIENT_ENTRIES));
        }

        return new StorageSnapshot(
                true,
                network.getNetworkName().getString(),
                shiftPlayer,
                shiftContainer,
                sidebarHidden,
                entries
        );
    }
}
