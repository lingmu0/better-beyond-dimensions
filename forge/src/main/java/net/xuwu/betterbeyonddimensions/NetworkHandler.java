package net.xuwu.betterbeyonddimensions;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
import net.xuwu.betterbeyonddimensions.client.SidebarSettingsStore;
import net.xuwu.betterbeyonddimensions.common.NetworkStorage;
import net.xuwu.betterbeyonddimensions.common.RecipeFill;
import net.xuwu.betterbeyonddimensions.common.StorageActions;
import net.xuwu.betterbeyonddimensions.common.StorageEntry;
import net.xuwu.betterbeyonddimensions.common.StorageSnapshot;
import net.xuwu.betterbeyonddimensions.common.StorageSyncState;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Forge 1.20.1 packet channel for sidebar requests and server snapshots. */
public final class NetworkHandler
{
    private static final String PROTOCOL_VERSION = "4";
    private static final int MAX_SYNC_PACKET_BYTES = 921600;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            BetterBeyondDimensions.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static final Map<ServerPlayer, StorageSyncState> SYNC_STATES = new WeakHashMap<>();
    private static int nextId;
    private static long nextSyncSequence;

    private NetworkHandler()
    {
    }

    public static void register()
    {
        CHANNEL.registerMessage(nextId++, RequestSnapshotPacket.class,
                (packet, buffer) -> { }, buffer -> new RequestSnapshotPacket(),
                NetworkHandler::handleRequestSnapshot,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SetShiftSettingsPacket.class,
                (packet, buffer) -> {
                    buffer.writeBoolean(packet.playerShift);
                    buffer.writeBoolean(packet.containerShift);
                },
                buffer -> new SetShiftSettingsPacket(buffer.readBoolean(), buffer.readBoolean()),
                NetworkHandler::handleSetShiftSettings,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SnapshotPacket.class,
                NetworkHandler::encodeSnapshot, NetworkHandler::decodeSnapshot,
                NetworkHandler::handleSnapshot,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, StorageDeltaPacket.class,
                NetworkHandler::encodeStorageDelta, NetworkHandler::decodeStorageDelta,
                NetworkHandler::handleStorageDelta,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, TogglePacket.class,
                (packet, buffer) -> buffer.writeVarInt(packet.target),
                buffer -> new TogglePacket(buffer.readVarInt()),
                NetworkHandler::handleToggle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, DepositPacket.class,
                (packet, buffer) -> buffer.writeVarInt(packet.target),
                buffer -> new DepositPacket(buffer.readVarInt()),
                NetworkHandler::handleDeposit,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, WithdrawPacket.class,
                (packet, buffer) -> {
                    buffer.writeItem(packet.stack);
                    buffer.writeVarInt(packet.amount);
                },
                buffer -> new WithdrawPacket(buffer.readItem(), buffer.readVarInt()),
                NetworkHandler::handleWithdraw,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SidebarClickPacket.class,
                (packet, buffer) -> {
                    buffer.writeItem(packet.stack);
                    buffer.writeVarInt(packet.button);
                },
                buffer -> new SidebarClickPacket(buffer.readItem(), buffer.readVarInt()),
                NetworkHandler::handleSidebarClick,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SidebarMenuClickPacket.class,
                (packet, buffer) -> {
                    buffer.writeVarInt(packet.slotId);
                    buffer.writeVarInt(packet.button);
                    buffer.writeVarInt(packet.clickType);
                },
                buffer -> new SidebarMenuClickPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()),
                NetworkHandler::handleSidebarMenuClick,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SidebarViewPacket.class,
                (packet, buffer) -> {
                    int count = Math.min(40, packet.stacks.size());
                    buffer.writeVarInt(count);
                    for (int index = 0; index < count; index++)
                    {
                        buffer.writeItem(packet.stacks.get(index));
                    }
                },
                buffer -> {
                    int count = Math.min(40, Math.max(0, buffer.readVarInt()));
                    List<ItemStack> stacks = new ArrayList<>(count);
                    for (int index = 0; index < count; index++)
                    {
                        stacks.add(buffer.readItem());
                    }
                    return new SidebarViewPacket(stacks);
                },
                NetworkHandler::handleSidebarView,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, RecipeFillPacket.class,
                (packet, buffer) -> {
                    int count = Math.min(64, packet.fills.size());
                    buffer.writeVarInt(count);
                    for (int index = 0; index < count; index++)
                    {
                        RecipeFill fill = packet.fills.get(index);
                        buffer.writeVarInt(fill.slotId());
                        buffer.writeItem(fill.stack());
                        buffer.writeVarInt(Math.min(64, fill.amount()));
                    }
                },
                buffer -> {
                    int count = Math.min(64, Math.max(0, buffer.readVarInt()));
                    List<RecipeFill> fills = new ArrayList<>(count);
                    for (int index = 0; index < count; index++)
                    {
                        fills.add(new RecipeFill(buffer.readVarInt(), buffer.readItem(),
                                Math.min(64, Math.max(0, buffer.readVarInt()))));
                    }
                    return new RecipeFillPacket(fills);
                },
                NetworkHandler::handleRecipeFill,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void requestSnapshot()
    {
        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        sendShiftSettings(settings.playerShift(), settings.containerShift());
        CHANNEL.sendToServer(new RequestSnapshotPacket());
    }

    public static void togglePlayerShift()
    {
        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        setShiftSettings(!settings.playerShift(), settings.containerShift());
    }

    public static void toggleContainerShift()
    {
        SidebarSettingsStore.Settings settings = SidebarSettingsStore.get();
        setShiftSettings(settings.playerShift(), !settings.containerShift());
    }

    public static void setShiftSettings(boolean playerShift, boolean containerShift)
    {
        SidebarSettingsStore.set(playerShift, containerShift);
        sendShiftSettings(playerShift, containerShift);
        CHANNEL.sendToServer(new RequestSnapshotPacket());
    }

    private static void sendShiftSettings(boolean playerShift, boolean containerShift)
    {
        CHANNEL.sendToServer(new SetShiftSettingsPacket(playerShift, containerShift));
    }

    public static void setSidebarHidden(boolean hidden)
    {
        CHANNEL.sendToServer(new TogglePacket(hidden
                ? StorageActions.HIDE_SIDEBAR
                : StorageActions.SHOW_SIDEBAR));
    }

    public static void depositContainer()
    {
        CHANNEL.sendToServer(new DepositPacket(StorageActions.DEPOSIT_CONTAINER));
    }

    public static void depositPlayerInventory()
    {
        CHANNEL.sendToServer(new DepositPacket(StorageActions.DEPOSIT_PLAYER_INVENTORY));
    }

    public static void withdraw(ItemStack stack, int amount)
    {
        ItemStack request = stack.copy();
        request.setCount(1);
        CHANNEL.sendToServer(new WithdrawPacket(request, Math.max(1, amount)));
    }

    public static void clickSidebar(ItemStack stack, int button)
    {
        ItemStack request = stack == null ? ItemStack.EMPTY : stack.copy();
        if (!request.isEmpty())
        {
            request.setCount(1);
        }
        CHANNEL.sendToServer(new SidebarClickPacket(request, button));
    }

    public static void clickSidebarSlot(int slotId, int button, ClickType clickType)
    {
        CHANNEL.sendToServer(new SidebarMenuClickPacket(slotId, button, clickType.ordinal()));
    }

    public static void updateSidebarView(List<ItemStack> stacks)
    {
        List<ItemStack> values = new ArrayList<>(Math.min(40, stacks == null ? 0 : stacks.size()));
        if (stacks != null)
        {
            for (int index = 0; index < Math.min(40, stacks.size()); index++)
            {
                ItemStack stack = stacks.get(index);
                values.add(stack == null ? ItemStack.EMPTY : stack.copy());
            }
        }
        CHANNEL.sendToServer(new SidebarViewPacket(values));
    }

    public static void fillRecipe(List<RecipeFill> fills)
    {
        if (fills == null || fills.isEmpty())
        {
            return;
        }
        CHANNEL.sendToServer(new RecipeFillPacket(List.copyOf(fills)));
    }

    private static void handleRequestSnapshot(RequestSnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                sendSnapshot(player);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSetShiftSettings(SetShiftSettingsPacket packet,
                                                Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                StorageActions.setShiftSettings(player, packet.playerShift, packet.containerShift);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSnapshot(SnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientStorageState.applySnapshotChunk(
                        packet.sequence, packet.chunkIndex, packet.chunkCount, packet.snapshot)));
        context.setPacketHandled(true);
    }

    private static void handleStorageDelta(StorageDeltaPacket packet,
                                           Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientStorageState.applyDeltaChunk(
                        packet.sequence, packet.chunkIndex, packet.chunkCount,
                        packet.metadata, packet.changes)));
        context.setPacketHandled(true);
    }

    private static void handleToggle(TogglePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                StorageActions.toggle(player, packet.target);
                sendSnapshot(player);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleDeposit(DepositPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null)
            {
                return;
            }
            if (packet.target == StorageActions.DEPOSIT_CONTAINER)
            {
                StorageActions.depositContainer(player);
            }
            else if (packet.target == StorageActions.DEPOSIT_PLAYER_INVENTORY)
            {
                StorageActions.depositPlayerInventory(player);
            }
            sendSnapshot(player);
        });
        context.setPacketHandled(true);
    }

    private static void handleWithdraw(WithdrawPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                StorageActions.withdraw(player, packet.stack, packet.amount);
                sendSnapshot(player);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSidebarClick(SidebarClickPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                StorageActions.clickSidebar(player, packet.stack, packet.button);
                sendSnapshot(player);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSidebarMenuClick(SidebarMenuClickPacket packet,
                                               Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null)
            {
                return;
            }
            ClickType[] values = ClickType.values();
            int typeIndex = Math.max(0, Math.min(values.length - 1, packet.clickType));
            StorageActions.handleSidebarClick(player, packet.slotId, packet.button, values[typeIndex]);
            sendSnapshot(player);
        });
        context.setPacketHandled(true);
    }

    private static void handleSidebarView(SidebarViewPacket packet,
                                          Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                StorageActions.updateSidebarView(player, packet.stacks);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleRecipeFill(RecipeFillPacket packet,
                                         Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null)
            {
                StorageActions.fillRecipe(player, packet.fills);
                sendSnapshot(player);
            }
        });
        context.setPacketHandled(true);
    }

    public static void sendSnapshot(ServerPlayer player)
    {
        if (player == null)
        {
            return;
        }
        StorageActions.refreshSidebarSlots(player);

        UnifiedStorage storage = currentStorage(player);
        if (storage == null)
        {
            closeState(player);
            sendSnapshotChunks(player, NetworkStorage.metadata(player), List.of());
            return;
        }

        StorageSyncState state = stateFor(player, storage);
        if (!state.initialized())
        {
            StorageSnapshot full = NetworkStorage.snapshot(player);
            sendSnapshotChunks(player, full, full.entries());
            state.initialize(full.entries());
            state.setMetadata(metadataOnly(full));
            return;
        }

        flushChanges(player, state);
    }

    /** Flushes listener-collected changes once per server tick, like the native menu sync. */
    public static void tick(ServerPlayer player)
    {
        if (player == null)
        {
            return;
        }

        StorageSyncState state = SYNC_STATES.get(player);
        if (state == null)
        {
            return;
        }

        if (currentStorage(player) != state.storage())
        {
            sendSnapshot(player);
            return;
        }

        if (state.initialized() && state.hasChanges())
        {
            StorageActions.refreshSidebarSlots(player);
            flushChanges(player, state);
        }
    }

    private static void flushChanges(ServerPlayer player, StorageSyncState state)
    {
        StorageSnapshot metadata = NetworkStorage.metadata(player);
        boolean metadataChanged = !sameMetadata(metadata, state.metadata());
        List<StorageEntry> changes = state.hasChanges()
                ? state.collectChanges() : List.of();
        if (!metadataChanged && changes.isEmpty())
        {
            return;
        }

        sendDeltaChunks(player, metadata, changes);
        state.markSynced(changes);
        state.setMetadata(metadata);
    }

    private static UnifiedStorage currentStorage(ServerPlayer player)
    {
        DimensionsNet network = DimensionsNet.getNetFromPlayer(player);
        return network == null ? null : network.getUnifiedStorage();
    }

    private static StorageSyncState stateFor(ServerPlayer player, UnifiedStorage storage)
    {
        StorageSyncState state = SYNC_STATES.get(player);
        if (state == null || state.storage() != storage)
        {
            if (state != null)
            {
                state.close();
            }
            state = new StorageSyncState(storage);
            SYNC_STATES.put(player, state);
        }
        return state;
    }

    private static void closeState(ServerPlayer player)
    {
        StorageSyncState state = SYNC_STATES.remove(player);
        if (state != null)
        {
            state.close();
        }
    }

    private static StorageSnapshot metadataOnly(StorageSnapshot snapshot)
    {
        return new StorageSnapshot(snapshot.available(), snapshot.networkName(),
                snapshot.shiftPlayerInventory(), snapshot.shiftContainer(), snapshot.sidebarHidden(), List.of());
    }

    private static boolean sameMetadata(StorageSnapshot first, StorageSnapshot second)
    {
        return first != null && second != null
                && first.available() == second.available()
                && first.shiftPlayerInventory() == second.shiftPlayerInventory()
                && first.shiftContainer() == second.shiftContainer()
                && first.sidebarHidden() == second.sidebarHidden()
                && first.networkName().equals(second.networkName());
    }

    private static void sendSnapshotChunks(ServerPlayer player, StorageSnapshot metadata,
                                           List<StorageEntry> entries)
    {
        List<List<StorageEntry>> chunks = splitEntries(entries);
        long sequence = ++nextSyncSequence;
        for (int index = 0; index < chunks.size(); index++)
        {
            StorageSnapshot chunk = new StorageSnapshot(metadata.available(), metadata.networkName(),
                    metadata.shiftPlayerInventory(), metadata.shiftContainer(), metadata.sidebarHidden(),
                    chunks.get(index));
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new SnapshotPacket(sequence, index, chunks.size(), chunk));
        }
    }

    private static void sendDeltaChunks(ServerPlayer player, StorageSnapshot metadata,
                                         List<StorageEntry> changes)
    {
        List<List<StorageEntry>> chunks = splitEntries(changes);
        long sequence = ++nextSyncSequence;
        for (int index = 0; index < chunks.size(); index++)
        {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new StorageDeltaPacket(sequence, index, chunks.size(), metadata, chunks.get(index)));
        }
    }

    private static List<List<StorageEntry>> splitEntries(List<StorageEntry> entries)
    {
        List<List<StorageEntry>> chunks = new ArrayList<>();
        List<StorageEntry> current = new ArrayList<>();
        int currentBytes = 0;
        if (entries != null)
        {
            for (StorageEntry entry : entries)
            {
                if (entry == null || entry.stack().isEmpty())
                {
                    continue;
                }
                int entryBytes = estimateEntryBytes(entry);
                if (!current.isEmpty() && currentBytes + entryBytes > MAX_SYNC_PACKET_BYTES - 1024)
                {
                    chunks.add(List.copyOf(current));
                    current = new ArrayList<>();
                    currentBytes = 0;
                }
                current.add(entry);
                currentBytes += entryBytes;
            }
        }
        if (!current.isEmpty() || chunks.isEmpty())
        {
            chunks.add(List.copyOf(current));
        }
        return chunks;
    }

    private static int estimateEntryBytes(StorageEntry entry)
    {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try
        {
            buffer.writeItem(entry.stack());
            buffer.writeLong(entry.amount());
            buffer.writeLong(entry.insertedTime());
            buffer.writeLong(entry.modifiedTime());
            return Math.max(1, buffer.readableBytes());
        }
        finally
        {
            buffer.release();
        }
    }

    private static void encodeSnapshot(SnapshotPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeLong(packet.sequence);
        buffer.writeVarInt(packet.chunkIndex);
        buffer.writeVarInt(packet.chunkCount);
        StorageSnapshot snapshot = packet.snapshot;
        buffer.writeBoolean(snapshot.available());
        buffer.writeUtf(snapshot.networkName(), 256);
        buffer.writeBoolean(snapshot.shiftPlayerInventory());
        buffer.writeBoolean(snapshot.shiftContainer());
        buffer.writeBoolean(snapshot.sidebarHidden());
        buffer.writeVarInt(snapshot.entries().size());
        for (StorageEntry entry : snapshot.entries())
        {
            buffer.writeItem(entry.stack());
            buffer.writeLong(entry.amount());
            buffer.writeLong(entry.insertedTime());
            buffer.writeLong(entry.modifiedTime());
        }
    }

    private static SnapshotPacket decodeSnapshot(FriendlyByteBuf buffer)
    {
        long sequence = buffer.readLong();
        int chunkIndex = Math.max(0, buffer.readVarInt());
        int chunkCount = Math.max(1, buffer.readVarInt());
        boolean available = buffer.readBoolean();
        String networkName = buffer.readUtf(256);
        boolean shiftPlayer = buffer.readBoolean();
        boolean shiftContainer = buffer.readBoolean();
        boolean sidebarHidden = buffer.readBoolean();
        int count = Math.max(0, buffer.readVarInt());
        List<StorageEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            entries.add(new StorageEntry(buffer.readItem(), buffer.readLong(), buffer.readLong(), buffer.readLong()));
        }
        return new SnapshotPacket(sequence, chunkIndex, chunkCount,
                new StorageSnapshot(available, networkName, shiftPlayer, shiftContainer,
                        sidebarHidden, entries));
    }

    private static void encodeStorageDelta(StorageDeltaPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeLong(packet.sequence);
        buffer.writeVarInt(packet.chunkIndex);
        buffer.writeVarInt(packet.chunkCount);
        StorageSnapshot metadata = packet.metadata;
        buffer.writeBoolean(metadata.available());
        buffer.writeUtf(metadata.networkName(), 256);
        buffer.writeBoolean(metadata.shiftPlayerInventory());
        buffer.writeBoolean(metadata.shiftContainer());
        buffer.writeBoolean(metadata.sidebarHidden());
        buffer.writeVarInt(packet.changes.size());
        for (StorageEntry entry : packet.changes)
        {
            buffer.writeItem(entry.stack());
            buffer.writeLong(entry.amount());
            buffer.writeLong(entry.insertedTime());
            buffer.writeLong(entry.modifiedTime());
        }
    }

    private static StorageDeltaPacket decodeStorageDelta(FriendlyByteBuf buffer)
    {
        long sequence = buffer.readLong();
        int chunkIndex = Math.max(0, buffer.readVarInt());
        int chunkCount = Math.max(1, buffer.readVarInt());
        boolean available = buffer.readBoolean();
        String networkName = buffer.readUtf(256);
        boolean shiftPlayer = buffer.readBoolean();
        boolean shiftContainer = buffer.readBoolean();
        boolean sidebarHidden = buffer.readBoolean();
        int count = Math.max(0, buffer.readVarInt());
        List<StorageEntry> changes = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            changes.add(new StorageEntry(buffer.readItem(), buffer.readLong(), buffer.readLong(), buffer.readLong()));
        }
        return new StorageDeltaPacket(sequence, chunkIndex, chunkCount,
                new StorageSnapshot(available, networkName, shiftPlayer, shiftContainer,
                        sidebarHidden, List.of()), changes);
    }

    private static final class RequestSnapshotPacket
    {
    }

    private static final class SetShiftSettingsPacket
    {
        private final boolean playerShift;
        private final boolean containerShift;

        private SetShiftSettingsPacket(boolean playerShift, boolean containerShift)
        {
            this.playerShift = playerShift;
            this.containerShift = containerShift;
        }
    }

    private static final class SnapshotPacket
    {
        private final long sequence;
        private final int chunkIndex;
        private final int chunkCount;
        private final StorageSnapshot snapshot;

        private SnapshotPacket(long sequence, int chunkIndex, int chunkCount, StorageSnapshot snapshot)
        {
            this.sequence = sequence;
            this.chunkIndex = chunkIndex;
            this.chunkCount = chunkCount;
            this.snapshot = snapshot;
        }
    }

    private static final class StorageDeltaPacket
    {
        private final long sequence;
        private final int chunkIndex;
        private final int chunkCount;
        private final StorageSnapshot metadata;
        private final List<StorageEntry> changes;

        private StorageDeltaPacket(long sequence, int chunkIndex, int chunkCount,
                                   StorageSnapshot metadata, List<StorageEntry> changes)
        {
            this.sequence = sequence;
            this.chunkIndex = chunkIndex;
            this.chunkCount = chunkCount;
            this.metadata = metadata;
            this.changes = changes == null ? List.of() : List.copyOf(changes);
        }
    }

    private static final class TogglePacket
    {
        private final int target;

        private TogglePacket(int target)
        {
            this.target = target;
        }
    }

    private static final class DepositPacket
    {
        private final int target;

        private DepositPacket(int target)
        {
            this.target = target;
        }
    }

    private static final class WithdrawPacket
    {
        private final ItemStack stack;
        private final int amount;

        private WithdrawPacket(ItemStack stack, int amount)
        {
            this.stack = stack;
            this.amount = amount;
        }
    }

    private static final class SidebarClickPacket
    {
        private final ItemStack stack;
        private final int button;

        private SidebarClickPacket(ItemStack stack, int button)
        {
            this.stack = stack;
            this.button = button;
        }
    }

    private static final class SidebarMenuClickPacket
    {
        private final int slotId;
        private final int button;
        private final int clickType;

        private SidebarMenuClickPacket(int slotId, int button, int clickType)
        {
            this.slotId = slotId;
            this.button = button;
            this.clickType = clickType;
        }
    }

    private static final class SidebarViewPacket
    {
        private final List<ItemStack> stacks;

        private SidebarViewPacket(List<ItemStack> stacks)
        {
            this.stacks = stacks == null ? List.of() : List.copyOf(stacks);
        }
    }

    private static final class RecipeFillPacket
    {
        private final List<RecipeFill> fills;

        private RecipeFillPacket(List<RecipeFill> fills)
        {
            this.fills = fills == null ? List.of() : List.copyOf(fills);
        }
    }
}
