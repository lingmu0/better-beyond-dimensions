package net.xuwu.betterbeyonddimensions.client;

import com.wintercogs.beyonddimensions.api.ButtonState;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.UnorderedStackHandlerRemoveZero;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.menu.widget.ClientNetStorage;
import com.wintercogs.beyonddimensions.config.CommonConfigRuntime;
import net.xuwu.betterbeyonddimensions.common.StorageEntry;
import net.xuwu.betterbeyonddimensions.common.StorageSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The sidebar's client view is backed by Beyond Dimensions' own filtered and sorted storage view.
 * This keeps its search syntax, pinyin support, tooltip/tag matching, timestamps, and sort settings
 * identical to the native network screen.
 */
public final class ClientStorageView
{
    private final AbstractUnorderedStackHandler sourceStorage =
            new UnorderedStackHandlerRemoveZero(AbstractUnorderedStackHandler.UiTimestampPolicy.NONE);
    private final ClientNetStorage filteredStorage = new ClientNetStorage(sourceStorage);

    private StorageSnapshot loadedSnapshot;
    private String loadedSearch = "";
    private ButtonState loadedPrimarySort;
    private ButtonState loadedSecondarySort;
    private ButtonState loadedReverse;
    private List<Entry> orderedEntries = List.of();

    public List<Entry> entries(StorageSnapshot snapshot, String searchText)
    {
        String normalizedSearch = searchText == null ? "" : searchText.toLowerCase(Locale.ENGLISH);
        boolean snapshotChanged = snapshot != loadedSnapshot;
        boolean searchChanged = !Objects.equals(normalizedSearch, loadedSearch);
        boolean sortChanged = loadedPrimarySort != CommonConfigRuntime.uiSortButton
                || loadedSecondarySort != CommonConfigRuntime.uiSecondSortButton
                || loadedReverse != CommonConfigRuntime.uiReverseButton;

        if (snapshotChanged)
        {
            loadSnapshot(snapshot);
        }

        if (snapshotChanged || searchChanged || sortChanged)
        {
            loadedSearch = normalizedSearch;
            loadedPrimarySort = CommonConfigRuntime.uiSortButton;
            loadedSecondarySort = CommonConfigRuntime.uiSecondSortButton;
            loadedReverse = CommonConfigRuntime.uiReverseButton;

            filteredStorage.setSearchText(normalizedSearch);
            filteredStorage.markForceAllUpdate();
            filteredStorage.resolvePendingOrAllUpdate(false);

            if (snapshot == null || !snapshot.available())
            {
                orderedEntries = List.of();
            }
            else
            {
                List<Integer> indexes = filteredStorage.buildSortedIndex(
                        loadedPrimarySort,
                        loadedSecondarySort,
                        loadedReverse == ButtonState.ENABLED
                );
                ArrayList<Entry> result = new ArrayList<>(indexes.size());
                List<KeyAmount> storage = filteredStorage.getStorage();
                for (int index : indexes)
                {
                    if (index < 0 || index >= storage.size())
                    {
                        continue;
                    }
                    KeyAmount keyAmount = storage.get(index);
                    if (keyAmount != null && keyAmount.key() instanceof ItemStackKey itemKey && keyAmount.amount() > 0L)
                    {
                        result.add(new Entry(itemKey, keyAmount.amount()));
                    }
                }
                orderedEntries = List.copyOf(result);
            }
        }

        return orderedEntries;
    }

    private void loadSnapshot(StorageSnapshot snapshot)
    {
        loadedSnapshot = snapshot;
        sourceStorage.clearStorage();

        if (snapshot == null || !snapshot.available())
        {
            return;
        }

        for (StorageEntry entry : snapshot.entries())
        {
            if (entry == null || entry.stack().isEmpty() || entry.amount() <= 0L)
            {
                continue;
            }

            ItemStackKey key = new ItemStackKey(entry.stack());
            sourceStorage.setAmountByKey(key, entry.amount());
            sourceStorage.setCreationTime(key, entry.insertedTime());
            sourceStorage.setLastModifiedTime(key, entry.modifiedTime());
        }
    }

    public record Entry(ItemStackKey key, long amount)
    {
    }
}
