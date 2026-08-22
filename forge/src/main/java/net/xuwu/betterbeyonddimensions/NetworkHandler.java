package net.xuwu.betterbeyonddimensions;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
import net.xuwu.betterbeyonddimensions.common.NetworkStorage;
import net.xuwu.betterbeyonddimensions.common.StorageActions;
import net.xuwu.betterbeyonddimensions.common.StorageEntry;
import net.xuwu.betterbeyonddimensions.common.StorageSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Forge 1.20.1 packet channel for sidebar requests and server snapshots. */
public final class NetworkHandler
{
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            BetterBeyondDimensions.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static int nextId;

    private NetworkHandler()
    {
    }

    public static void register()
    {
        CHANNEL.registerMessage(nextId++, RequestSnapshotPacket.class,
                (packet, buffer) -> { }, buffer -> new RequestSnapshotPacket(),
                NetworkHandler::handleRequestSnapshot,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SnapshotPacket.class,
                NetworkHandler::encodeSnapshot, NetworkHandler::decodeSnapshot,
                NetworkHandler::handleSnapshot,
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
    }

    public static void requestSnapshot()
    {
        CHANNEL.sendToServer(new RequestSnapshotPacket());
    }

    public static void togglePlayerShift()
    {
        CHANNEL.sendToServer(new TogglePacket(StorageActions.TOGGLE_PLAYER_SHIFT));
    }

    public static void toggleContainerShift()
    {
        CHANNEL.sendToServer(new TogglePacket(StorageActions.TOGGLE_CONTAINER_SHIFT));
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

    private static void handleSnapshot(SnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientStorageState.apply(packet.snapshot)));
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

    public static void sendSnapshot(ServerPlayer player)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SnapshotPacket(NetworkStorage.snapshot(player)));
    }

    private static void encodeSnapshot(SnapshotPacket packet, FriendlyByteBuf buffer)
    {
        StorageSnapshot snapshot = packet.snapshot;
        buffer.writeBoolean(snapshot.available());
        buffer.writeUtf(snapshot.networkName(), 256);
        buffer.writeBoolean(snapshot.shiftPlayerInventory());
        buffer.writeBoolean(snapshot.shiftContainer());
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
        boolean available = buffer.readBoolean();
        String networkName = buffer.readUtf(256);
        boolean shiftPlayer = buffer.readBoolean();
        boolean shiftContainer = buffer.readBoolean();
        int count = Math.min(512, Math.max(0, buffer.readVarInt()));
        List<StorageEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            entries.add(new StorageEntry(buffer.readItem(), buffer.readLong(), buffer.readLong(), buffer.readLong()));
        }
        return new SnapshotPacket(new StorageSnapshot(available, networkName, shiftPlayer, shiftContainer, entries));
    }

    private static final class RequestSnapshotPacket
    {
    }

    private static final class SnapshotPacket
    {
        private final StorageSnapshot snapshot;

        private SnapshotPacket(StorageSnapshot snapshot)
        {
            this.snapshot = snapshot;
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
}
