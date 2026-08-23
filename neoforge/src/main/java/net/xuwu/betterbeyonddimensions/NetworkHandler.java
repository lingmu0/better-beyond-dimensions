package net.xuwu.betterbeyonddimensions;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
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
import java.util.WeakHashMap;

/** NeoForge 1.21.1 custom payloads for the sidebar. */
public final class NetworkHandler
{
    private static final int MAX_SYNC_PACKET_BYTES = 921600;
    private static final Map<ServerPlayer, StorageSyncState> SYNC_STATES = new WeakHashMap<>();
    private static long nextSyncSequence;

    private NetworkHandler()
    {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playBidirectional(RequestSnapshotPacket.TYPE, RequestSnapshotPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(RequestSnapshotPacket::handle, RequestSnapshotPacket::handle));
        registrar.playBidirectional(SnapshotPacket.TYPE, SnapshotPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SnapshotPacket::handle, SnapshotPacket::handle));
        registrar.playBidirectional(StorageDeltaPacket.TYPE, StorageDeltaPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(StorageDeltaPacket::handle, StorageDeltaPacket::handle));
        registrar.playBidirectional(TogglePacket.TYPE, TogglePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(TogglePacket::handle, TogglePacket::handle));
        registrar.playBidirectional(DepositPacket.TYPE, DepositPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DepositPacket::handle, DepositPacket::handle));
        registrar.playBidirectional(WithdrawPacket.TYPE, WithdrawPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(WithdrawPacket::handle, WithdrawPacket::handle));
        registrar.playBidirectional(SidebarClickPacket.TYPE, SidebarClickPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SidebarClickPacket::handle, SidebarClickPacket::handle));
        registrar.playBidirectional(SidebarMenuClickPacket.TYPE, SidebarMenuClickPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SidebarMenuClickPacket::handle, SidebarMenuClickPacket::handle));
        registrar.playBidirectional(SidebarViewPacket.TYPE, SidebarViewPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SidebarViewPacket::handle, SidebarViewPacket::handle));
        registrar.playBidirectional(RecipeFillPacket.TYPE, RecipeFillPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(RecipeFillPacket::handle, RecipeFillPacket::handle));
    }

    public static void requestSnapshot()
    {
        PacketDistributor.sendToServer(new RequestSnapshotPacket());
    }

    public static void togglePlayerShift()
    {
        PacketDistributor.sendToServer(new TogglePacket(StorageActions.TOGGLE_PLAYER_SHIFT));
    }

    public static void toggleContainerShift()
    {
        PacketDistributor.sendToServer(new TogglePacket(StorageActions.TOGGLE_CONTAINER_SHIFT));
    }

    public static void setSidebarHidden(boolean hidden)
    {
        PacketDistributor.sendToServer(new TogglePacket(hidden
                ? StorageActions.HIDE_SIDEBAR
                : StorageActions.SHOW_SIDEBAR));
    }

    public static void depositContainer()
    {
        PacketDistributor.sendToServer(new DepositPacket(StorageActions.DEPOSIT_CONTAINER));
    }

    public static void depositPlayerInventory()
    {
        PacketDistributor.sendToServer(new DepositPacket(StorageActions.DEPOSIT_PLAYER_INVENTORY));
    }

    public static void withdraw(ItemStack stack, int amount)
    {
        ItemStack request = stack.copy();
        request.setCount(1);
        PacketDistributor.sendToServer(new WithdrawPacket(request, Math.max(1, amount)));
    }

    public static void clickSidebar(ItemStack stack, int button)
    {
        ItemStack request = stack == null ? ItemStack.EMPTY : stack.copy();
        if (!request.isEmpty())
        {
            request.setCount(1);
        }
        PacketDistributor.sendToServer(new SidebarClickPacket(request, button));
    }

    public static void clickSidebarSlot(int slotId, int button, ClickType clickType)
    {
        PacketDistributor.sendToServer(new SidebarMenuClickPacket(slotId, button, clickType.ordinal()));
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
        PacketDistributor.sendToServer(new SidebarViewPacket(values));
    }

    public static void fillRecipe(List<RecipeFill> fills)
    {
        if (fills == null || fills.isEmpty())
        {
            return;
        }
        PacketDistributor.sendToServer(new RecipeFillPacket(fills));
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
        List<List<StorageEntry>> chunks = splitEntries(player, entries);
        long sequence = ++nextSyncSequence;
        for (int index = 0; index < chunks.size(); index++)
        {
            StorageSnapshot chunk = new StorageSnapshot(metadata.available(), metadata.networkName(),
                    metadata.shiftPlayerInventory(), metadata.shiftContainer(), metadata.sidebarHidden(),
                    chunks.get(index));
            PacketDistributor.sendToPlayer(player,
                    new SnapshotPacket(sequence, index, chunks.size(), chunk));
        }
    }

    private static void sendDeltaChunks(ServerPlayer player, StorageSnapshot metadata,
                                         List<StorageEntry> changes)
    {
        List<List<StorageEntry>> chunks = splitEntries(player, changes);
        long sequence = ++nextSyncSequence;
        for (int index = 0; index < chunks.size(); index++)
        {
            PacketDistributor.sendToPlayer(player,
                    new StorageDeltaPacket(sequence, index, chunks.size(), metadata, chunks.get(index)));
        }
    }

    private static List<List<StorageEntry>> splitEntries(ServerPlayer player, List<StorageEntry> entries)
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
                int entryBytes = estimateEntryBytes(player, entry);
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

    private static int estimateEntryBytes(ServerPlayer player, StorageEntry entry)
    {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), player.level().registryAccess());
        try
        {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack());
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

    private static StorageSnapshot readSnapshot(RegistryFriendlyByteBuf buffer)
    {
        boolean available = buffer.readBoolean();
        String networkName = buffer.readUtf(256);
        boolean shiftPlayer = buffer.readBoolean();
        boolean shiftContainer = buffer.readBoolean();
        boolean sidebarHidden = buffer.readBoolean();
        int count = Math.max(0, buffer.readVarInt());
        List<StorageEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            entries.add(new StorageEntry(stack, buffer.readLong(), buffer.readLong(), buffer.readLong()));
        }
        return new StorageSnapshot(available, networkName, shiftPlayer, shiftContainer,
                sidebarHidden, entries);
    }

    private static void writeSnapshot(RegistryFriendlyByteBuf buffer, StorageSnapshot snapshot)
    {
        buffer.writeBoolean(snapshot.available());
        buffer.writeUtf(snapshot.networkName(), 256);
        buffer.writeBoolean(snapshot.shiftPlayerInventory());
        buffer.writeBoolean(snapshot.shiftContainer());
        buffer.writeBoolean(snapshot.sidebarHidden());
        buffer.writeVarInt(snapshot.entries().size());
        for (StorageEntry entry : snapshot.entries())
        {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack());
            buffer.writeLong(entry.amount());
            buffer.writeLong(entry.insertedTime());
            buffer.writeLong(entry.modifiedTime());
        }
    }

    private record RequestSnapshotPacket() implements CustomPacketPayload
    {
        private static final Type<RequestSnapshotPacket> TYPE = new Type<>(BetterBeyondDimensions.id("request_snapshot"));
        private static final StreamCodec<ByteBuf, RequestSnapshotPacket> STREAM_CODEC = StreamCodec.unit(new RequestSnapshotPacket());

        private static void handle(RequestSnapshotPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                    {
                        sendSnapshot(player);
                    }
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record SnapshotPacket(long sequence, int chunkIndex, int chunkCount,
                                  StorageSnapshot snapshot) implements CustomPacketPayload
    {
        private static final Type<SnapshotPacket> TYPE = new Type<>(BetterBeyondDimensions.id("snapshot"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPacket> STREAM_CODEC = new StreamCodec<>()
        {
            @Override
            public void encode(RegistryFriendlyByteBuf buffer, SnapshotPacket packet)
            {
                buffer.writeLong(packet.sequence);
                buffer.writeVarInt(packet.chunkIndex);
                buffer.writeVarInt(packet.chunkCount);
                writeSnapshot(buffer, packet.snapshot);
            }

            @Override
            public SnapshotPacket decode(RegistryFriendlyByteBuf buffer)
            {
                return new SnapshotPacket(buffer.readLong(), Math.max(0, buffer.readVarInt()),
                        Math.max(1, buffer.readVarInt()), readSnapshot(buffer));
            }
        };

        private static void handle(SnapshotPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.CLIENTBOUND)
            {
                context.enqueueWork(() -> ClientStorageState.applySnapshotChunk(
                        packet.sequence, packet.chunkIndex, packet.chunkCount, packet.snapshot));
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record StorageDeltaPacket(long sequence, int chunkIndex, int chunkCount,
                                      StorageSnapshot metadata, List<StorageEntry> changes)
            implements CustomPacketPayload
    {
        private static final Type<StorageDeltaPacket> TYPE =
                new Type<>(BetterBeyondDimensions.id("storage_delta"));
        private static final StreamCodec<RegistryFriendlyByteBuf, StorageDeltaPacket> STREAM_CODEC =
                new StreamCodec<>()
                {
                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, StorageDeltaPacket packet)
                    {
                        buffer.writeLong(packet.sequence);
                        buffer.writeVarInt(packet.chunkIndex);
                        buffer.writeVarInt(packet.chunkCount);
                        StorageSnapshot payload = new StorageSnapshot(
                                packet.metadata.available(), packet.metadata.networkName(),
                                packet.metadata.shiftPlayerInventory(), packet.metadata.shiftContainer(),
                                packet.metadata.sidebarHidden(), packet.changes);
                        writeSnapshot(buffer, payload);
                    }

                    @Override
                    public StorageDeltaPacket decode(RegistryFriendlyByteBuf buffer)
                    {
                        long sequence = buffer.readLong();
                        int chunkIndex = Math.max(0, buffer.readVarInt());
                        int chunkCount = Math.max(1, buffer.readVarInt());
                        StorageSnapshot payload = readSnapshot(buffer);
                        StorageSnapshot metadata = new StorageSnapshot(
                                payload.available(), payload.networkName(),
                                payload.shiftPlayerInventory(), payload.shiftContainer(),
                                payload.sidebarHidden(), List.of());
                        return new StorageDeltaPacket(sequence, chunkIndex, chunkCount,
                                metadata, payload.entries());
                    }
                };

        private static void handle(StorageDeltaPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.CLIENTBOUND)
            {
                context.enqueueWork(() -> ClientStorageState.applyDeltaChunk(
                        packet.sequence, packet.chunkIndex, packet.chunkCount,
                        packet.metadata, packet.changes));
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record TogglePacket(int target) implements CustomPacketPayload
    {
        private static final Type<TogglePacket> TYPE = new Type<>(BetterBeyondDimensions.id("toggle"));
        private static final StreamCodec<RegistryFriendlyByteBuf, TogglePacket> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TogglePacket::target, TogglePacket::new);

        private static void handle(TogglePacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                    {
                        StorageActions.toggle(player, packet.target);
                        sendSnapshot(player);
                    }
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record DepositPacket(int target) implements CustomPacketPayload
    {
        private static final Type<DepositPacket> TYPE = new Type<>(BetterBeyondDimensions.id("deposit"));
        private static final StreamCodec<RegistryFriendlyByteBuf, DepositPacket> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, DepositPacket::target, DepositPacket::new);

        private static void handle(DepositPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player))
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
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record WithdrawPacket(ItemStack stack, int amount) implements CustomPacketPayload
    {
        private static final Type<WithdrawPacket> TYPE = new Type<>(BetterBeyondDimensions.id("withdraw"));
        private static final StreamCodec<RegistryFriendlyByteBuf, WithdrawPacket> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC,
                WithdrawPacket::stack,
                ByteBufCodecs.VAR_INT,
                WithdrawPacket::amount,
                WithdrawPacket::new
        );

        private static void handle(WithdrawPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                    {
                        StorageActions.withdraw(player, packet.stack, packet.amount);
                        sendSnapshot(player);
                    }
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record SidebarClickPacket(ItemStack stack, int button) implements CustomPacketPayload
    {
        private static final Type<SidebarClickPacket> TYPE = new Type<>(BetterBeyondDimensions.id("sidebar_click"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SidebarClickPacket> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC,
                SidebarClickPacket::stack,
                ByteBufCodecs.VAR_INT,
                SidebarClickPacket::button,
                SidebarClickPacket::new
        );

        private static void handle(SidebarClickPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                    {
                        StorageActions.clickSidebar(player, packet.stack, packet.button);
                        sendSnapshot(player);
                    }
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record SidebarMenuClickPacket(int slotId, int button, int clickType) implements CustomPacketPayload
    {
        private static final Type<SidebarMenuClickPacket> TYPE =
                new Type<>(BetterBeyondDimensions.id("sidebar_menu_click"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SidebarMenuClickPacket> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT,
                        SidebarMenuClickPacket::slotId,
                        ByteBufCodecs.VAR_INT,
                        SidebarMenuClickPacket::button,
                        ByteBufCodecs.VAR_INT,
                        SidebarMenuClickPacket::clickType,
                        SidebarMenuClickPacket::new
                );

        private static void handle(SidebarMenuClickPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player))
                    {
                        return;
                    }
                    ClickType[] values = ClickType.values();
                    int typeIndex = Math.max(0, Math.min(values.length - 1, packet.clickType));
                    StorageActions.handleSidebarClick(player, packet.slotId, packet.button, values[typeIndex]);
                    sendSnapshot(player);
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record SidebarViewPacket(List<ItemStack> stacks) implements CustomPacketPayload
    {
        private static final Type<SidebarViewPacket> TYPE = new Type<>(BetterBeyondDimensions.id("sidebar_view"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SidebarViewPacket> STREAM_CODEC = new StreamCodec<>()
        {
            @Override
            public void encode(RegistryFriendlyByteBuf buffer, SidebarViewPacket packet)
            {
                List<ItemStack> values = packet.stacks == null ? List.of() : packet.stacks;
                int count = Math.min(40, values.size());
                buffer.writeVarInt(count);
                for (int index = 0; index < count; index++)
                {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, values.get(index));
                }
            }

            @Override
            public SidebarViewPacket decode(RegistryFriendlyByteBuf buffer)
            {
                int count = Math.min(40, Math.max(0, buffer.readVarInt()));
                List<ItemStack> values = new ArrayList<>(count);
                for (int index = 0; index < count; index++)
                {
                    values.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                }
                return new SidebarViewPacket(values);
            }
        };

        private static void handle(SidebarViewPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                    {
                        StorageActions.updateSidebarView(player, packet.stacks);
                    }
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private record RecipeFillPacket(List<RecipeFill> fills) implements CustomPacketPayload
    {
        private static final Type<RecipeFillPacket> TYPE = new Type<>(BetterBeyondDimensions.id("recipe_fill"));
        private static final StreamCodec<RegistryFriendlyByteBuf, RecipeFillPacket> STREAM_CODEC = new StreamCodec<>()
        {
            @Override
            public void encode(RegistryFriendlyByteBuf buffer, RecipeFillPacket packet)
            {
                List<RecipeFill> values = packet.fills == null ? List.of() : packet.fills;
                int count = Math.min(64, values.size());
                buffer.writeVarInt(count);
                for (int index = 0; index < count; index++)
                {
                    RecipeFill fill = values.get(index);
                    buffer.writeVarInt(fill.slotId());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, fill.stack());
                    buffer.writeVarInt(Math.min(64, fill.amount()));
                }
            }

            @Override
            public RecipeFillPacket decode(RegistryFriendlyByteBuf buffer)
            {
                int count = Math.min(64, Math.max(0, buffer.readVarInt()));
                List<RecipeFill> fills = new ArrayList<>(count);
                for (int index = 0; index < count; index++)
                {
                    fills.add(new RecipeFill(
                            buffer.readVarInt(),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                            Math.min(64, Math.max(0, buffer.readVarInt()))
                    ));
                }
                return new RecipeFillPacket(fills);
            }
        };

        private static void handle(RecipeFillPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.SERVERBOUND)
            {
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                    {
                        StorageActions.fillRecipe(player, packet.fills);
                        sendSnapshot(player);
                    }
                });
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }
}
