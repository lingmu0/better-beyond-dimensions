package net.xuwu.betterbeyonddimensions.client;

import net.xuwu.betterbeyonddimensions.common.StorageEntry;
import net.xuwu.betterbeyonddimensions.common.StorageSnapshot;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Client copy of the last server-authoritative snapshot. */
public final class ClientStorageState
{
    private static volatile StorageSnapshot snapshot = StorageSnapshot.unavailable(false, false, false);
    private static int scrollRow;
    private static long lastAppliedSequence = Long.MIN_VALUE;
    private static long pendingSequence = Long.MIN_VALUE;
    private static boolean pendingSnapshot;
    private static int pendingChunkCount;
    private static StorageSnapshot pendingMetadata;
    private static final Map<Integer, List<StorageEntry>> pendingChunks = new TreeMap<>();

    private ClientStorageState()
    {
    }

    /** Compatibility entry point for local callers; network packets use the chunked method below. */
    public static synchronized void apply(StorageSnapshot next)
    {
        snapshot = next == null ? StorageSnapshot.unavailable(false, false, false) : next;
        scrollRow = 0;
        lastAppliedSequence = Long.MIN_VALUE;
        clearPending();
    }

    public static synchronized void applySnapshotChunk(long sequence, int chunkIndex, int chunkCount,
                                                        StorageSnapshot chunk)
    {
        PendingBatch batch = acceptChunk(sequence, chunkIndex, chunkCount, chunk, true);
        if (batch == null)
        {
            return;
        }

        StorageSnapshot metadata = batch.metadata();
        snapshot = new StorageSnapshot(metadata.available(), metadata.networkName(),
                metadata.shiftPlayerInventory(), metadata.shiftContainer(), metadata.sidebarHidden(),
                batch.entries());
        lastAppliedSequence = sequence;
        scrollRow = 0;
    }

    public static synchronized void applyDeltaChunk(long sequence, int chunkIndex, int chunkCount,
                                                     StorageSnapshot metadata, List<StorageEntry> changes)
    {
        PendingBatch batch = acceptChunk(sequence, chunkIndex, chunkCount,
                new StorageSnapshot(metadata.available(), metadata.networkName(),
                        metadata.shiftPlayerInventory(), metadata.shiftContainer(), metadata.sidebarHidden(),
                        changes), false);
        if (batch == null || sequence <= lastAppliedSequence)
        {
            return;
        }

        StorageSnapshot nextMetadata = batch.metadata();
        if (!nextMetadata.available())
        {
            snapshot = new StorageSnapshot(false, nextMetadata.networkName(),
                    nextMetadata.shiftPlayerInventory(), nextMetadata.shiftContainer(),
                    nextMetadata.sidebarHidden(), List.of());
        }
        else
        {
            Map<ItemStackKey, StorageEntry> merged = new LinkedHashMap<>();
            for (StorageEntry entry : snapshot.entries())
            {
                if (entry != null && !entry.stack().isEmpty() && entry.amount() > 0L)
                {
                    merged.put(new ItemStackKey(entry.stack()), entry);
                }
            }
            for (StorageEntry entry : batch.entries())
            {
                if (entry == null || entry.stack().isEmpty())
                {
                    continue;
                }
                ItemStackKey key = new ItemStackKey(entry.stack());
                if (entry.amount() <= 0L)
                {
                    merged.remove(key);
                }
                else
                {
                    merged.put(key, entry);
                }
            }
            snapshot = new StorageSnapshot(true, nextMetadata.networkName(),
                    nextMetadata.shiftPlayerInventory(), nextMetadata.shiftContainer(),
                    nextMetadata.sidebarHidden(), new ArrayList<>(merged.values()));
        }
        lastAppliedSequence = sequence;
    }

    public static synchronized void clear()
    {
        snapshot = StorageSnapshot.unavailable(false, false, false);
        scrollRow = 0;
        lastAppliedSequence = Long.MIN_VALUE;
        clearPending();
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
    public static synchronized void setSidebarHidden(boolean hidden)
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

    private static PendingBatch acceptChunk(long sequence, int chunkIndex, int chunkCount,
                                            StorageSnapshot metadata, boolean snapshotChunk)
    {
        if (metadata == null || sequence <= lastAppliedSequence
                || chunkCount <= 0 || chunkIndex < 0 || chunkIndex >= chunkCount)
        {
            return null;
        }

        if (pendingSequence != sequence || pendingSnapshot != snapshotChunk
                || pendingChunkCount != chunkCount)
        {
            clearPending();
            pendingSequence = sequence;
            pendingSnapshot = snapshotChunk;
            pendingChunkCount = chunkCount;
            pendingMetadata = metadata;
        }

        pendingChunks.putIfAbsent(chunkIndex, List.copyOf(metadata.entries()));
        if (pendingChunks.size() != pendingChunkCount)
        {
            return null;
        }

        List<StorageEntry> combined = new ArrayList<>();
        for (int index = 0; index < pendingChunkCount; index++)
        {
            List<StorageEntry> entries = pendingChunks.get(index);
            if (entries == null)
            {
                return null;
            }
            combined.addAll(entries);
        }

        PendingBatch result = new PendingBatch(pendingMetadata, List.copyOf(combined));
        clearPending();
        return result;
    }

    private static void clearPending()
    {
        pendingSequence = Long.MIN_VALUE;
        pendingChunkCount = 0;
        pendingMetadata = null;
        pendingChunks.clear();
    }

    private record PendingBatch(StorageSnapshot metadata, List<StorageEntry> entries)
    {
    }

}
