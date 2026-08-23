package net.xuwu.betterbeyonddimensions.common;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side change accumulator for one player's network view.
 *
 * <p>This follows Beyond Dimensions' {@code DisorderedSlotGroupSync} model: normal delta events
 * are coalesced by key, while an {@code any} event requests a full comparison on the next menu
 * update.  The packet layer decides how the resulting entries are batched.</p>
 */
public final class StorageSyncState
{
    private final UnifiedStorage storage;
    private final Map<ItemStackKey, StorageEntry> known = new LinkedHashMap<>();
    private final Map<ItemStackKey, StorageEntry> pending = new LinkedHashMap<>();
    private final AutoCloseable anySubscription;
    private final AutoCloseable deltaSubscription;
    private boolean fullRescan = true;
    private boolean initialized;
    private StorageSnapshot metadata;

    public StorageSyncState(UnifiedStorage storage)
    {
        this.storage = storage;
        this.anySubscription = storage.subscribeAny(this, this::markFullRescan);
        this.deltaSubscription = storage.subscribeDelta(this, this::recordDelta);
    }

    public UnifiedStorage storage()
    {
        return storage;
    }

    public boolean initialized()
    {
        return initialized;
    }

    public boolean hasChanges()
    {
        return fullRescan || !pending.isEmpty();
    }

    public StorageSnapshot metadata()
    {
        return metadata;
    }

    public void setMetadata(StorageSnapshot metadata)
    {
        this.metadata = metadata;
    }

    public void initialize(List<StorageEntry> entries)
    {
        known.clear();
        if (entries != null)
        {
            for (StorageEntry entry : entries)
            {
                remember(entry);
            }
        }
        pending.clear();
        fullRescan = false;
        initialized = true;
    }

    /** Returns a coalesced delta, comparing the complete storage only when required. */
    public List<StorageEntry> collectChanges()
    {
        if (!fullRescan)
        {
            return List.copyOf(pending.values());
        }

        Map<ItemStackKey, StorageEntry> current = new LinkedHashMap<>();
        for (StorageEntry entry : NetworkStorage.entries(storage))
        {
            if (entry == null || entry.stack().isEmpty() || entry.amount() <= 0L)
            {
                continue;
            }
            current.put(new ItemStackKey(entry.stack()), entry);
        }

        List<StorageEntry> changes = new ArrayList<>();
        for (Map.Entry<ItemStackKey, StorageEntry> entry : current.entrySet())
        {
            if (!sameAmountAndTimes(known.get(entry.getKey()), entry.getValue()))
            {
                changes.add(entry.getValue());
            }
        }
        for (ItemStackKey key : known.keySet())
        {
            if (!current.containsKey(key))
            {
                changes.add(new StorageEntry(key.copyStack(), 0L, 0L, 0L));
            }
        }
        return changes;
    }

    /** Marks exactly the entries just sent to the client as the new client-side baseline. */
    public void markSynced(List<StorageEntry> changes)
    {
        if (changes != null)
        {
            for (StorageEntry entry : changes)
            {
                if (entry == null || entry.stack().isEmpty())
                {
                    continue;
                }
                ItemStackKey key = new ItemStackKey(entry.stack());
                if (entry.amount() <= 0L)
                {
                    known.remove(key);
                }
                else
                {
                    known.put(key, entry);
                }
            }
        }
        pending.clear();
        fullRescan = false;
    }

    public void close()
    {
        closeQuietly(anySubscription);
        closeQuietly(deltaSubscription);
    }

    private void markFullRescan()
    {
        fullRescan = true;
    }

    private void recordDelta(IStackKey<?> rawKey, long ignoredAmount, boolean ignoredRemoved)
    {
        if (fullRescan || !(rawKey instanceof ItemStackKey itemKey) || itemKey.isEmpty())
        {
            return;
        }

        ItemStackKey copiedKey = new ItemStackKey(itemKey.copyStack());
        pending.put(copiedKey, readCurrent(copiedKey));
    }

    private StorageEntry readCurrent(ItemStackKey key)
    {
        KeyAmount stored = storage.getStackByKey(key);
        long amount = stored == null ? 0L : Math.max(0L, stored.amount());
        long inserted = amount > 0L
                ? storage.getCreationTimeMap().getOrDefault(key, 0L) : 0L;
        long modified = amount > 0L
                ? storage.getLastModifiedTimeMap().getOrDefault(key, 0L) : 0L;
        return new StorageEntry(key.copyStack(), amount, inserted, modified);
    }

    private void remember(StorageEntry entry)
    {
        if (entry != null && !entry.stack().isEmpty() && entry.amount() > 0L)
        {
            known.put(new ItemStackKey(entry.stack()), entry);
        }
    }

    private static boolean sameAmountAndTimes(StorageEntry first, StorageEntry second)
    {
        return first != null && second != null
                && first.amount() == second.amount()
                && first.insertedTime() == second.insertedTime()
                && first.modifiedTime() == second.modifiedTime();
    }

    private static void closeQuietly(AutoCloseable subscription)
    {
        if (subscription == null)
        {
            return;
        }
        try
        {
            subscription.close();
        }
        catch (Exception ignored)
        {
            // A disconnected network should not interrupt the server tick.
        }
    }
}
