package net.xuwu.betterbeyonddimensions.common;

import java.util.List;

/** Common menu access used to append the real network-backed sidebar slots. */
public interface NetworkStorageMenuAccess
{
    List<NetworkStorageSlot> bbd$getNetworkSlots();

    void bbd$ensureNetworkSlots(int x, int y);
}
