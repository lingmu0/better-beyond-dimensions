package net.xuwu.betterbeyonddimensions;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge 1.21.1 entry point. */
@Mod(BetterBeyondDimensions.MODID)
public final class BetterBeyondDimensions
{
    public static final String MODID = "better_beyond_dimensions";

    public BetterBeyondDimensions(IEventBus modEventBus, ModContainer ignored)
    {
        modEventBus.addListener(NetworkHandler::registerPayloads);
    }

    public static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
