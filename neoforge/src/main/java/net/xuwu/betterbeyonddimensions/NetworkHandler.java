package net.xuwu.betterbeyonddimensions;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
import net.xuwu.betterbeyonddimensions.common.NetworkStorage;
import net.xuwu.betterbeyonddimensions.common.StorageActions;
import net.xuwu.betterbeyonddimensions.common.StorageEntry;
import net.xuwu.betterbeyonddimensions.common.StorageSnapshot;

import java.util.ArrayList;
import java.util.List;

/** NeoForge 1.21.1 custom payloads for the sidebar. */
public final class NetworkHandler
{
    private NetworkHandler()
    {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playBidirectional(RequestSnapshotPacket.TYPE, RequestSnapshotPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(RequestSnapshotPacket::handle, RequestSnapshotPacket::handle));
        registrar.playBidirectional(SnapshotPacket.TYPE, SnapshotPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SnapshotPacket::handle, SnapshotPacket::handle));
        registrar.playBidirectional(TogglePacket.TYPE, TogglePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(TogglePacket::handle, TogglePacket::handle));
        registrar.playBidirectional(DepositPacket.TYPE, DepositPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(DepositPacket::handle, DepositPacket::handle));
        registrar.playBidirectional(WithdrawPacket.TYPE, WithdrawPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(WithdrawPacket::handle, WithdrawPacket::handle));
        registrar.playBidirectional(SidebarClickPacket.TYPE, SidebarClickPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SidebarClickPacket::handle, SidebarClickPacket::handle));
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

    public static void sendSnapshot(ServerPlayer player)
    {
        PacketDistributor.sendToPlayer(player, new SnapshotPacket(NetworkStorage.snapshot(player)));
    }

    private static StorageSnapshot readSnapshot(RegistryFriendlyByteBuf buffer)
    {
        boolean available = buffer.readBoolean();
        String networkName = buffer.readUtf(256);
        boolean shiftPlayer = buffer.readBoolean();
        boolean shiftContainer = buffer.readBoolean();
        int count = Math.min(512, Math.max(0, buffer.readVarInt()));
        List<StorageEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            entries.add(new StorageEntry(stack, buffer.readLong(), buffer.readLong(), buffer.readLong()));
        }
        return new StorageSnapshot(available, networkName, shiftPlayer, shiftContainer, entries);
    }

    private static void writeSnapshot(RegistryFriendlyByteBuf buffer, StorageSnapshot snapshot)
    {
        buffer.writeBoolean(snapshot.available());
        buffer.writeUtf(snapshot.networkName(), 256);
        buffer.writeBoolean(snapshot.shiftPlayerInventory());
        buffer.writeBoolean(snapshot.shiftContainer());
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

    private record SnapshotPacket(StorageSnapshot snapshot) implements CustomPacketPayload
    {
        private static final Type<SnapshotPacket> TYPE = new Type<>(BetterBeyondDimensions.id("snapshot"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPacket> STREAM_CODEC = new StreamCodec<>()
        {
            @Override
            public void encode(RegistryFriendlyByteBuf buffer, SnapshotPacket packet)
            {
                writeSnapshot(buffer, packet.snapshot);
            }

            @Override
            public SnapshotPacket decode(RegistryFriendlyByteBuf buffer)
            {
                return new SnapshotPacket(readSnapshot(buffer));
            }
        };

        private static void handle(SnapshotPacket packet, IPayloadContext context)
        {
            if (context.flow() == PacketFlow.CLIENTBOUND)
            {
                context.enqueueWork(() -> ClientStorageState.apply(packet.snapshot));
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
}
