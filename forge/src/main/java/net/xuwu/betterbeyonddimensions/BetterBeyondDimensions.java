package net.xuwu.betterbeyonddimensions;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

/**
 * Better Beyond Dimensions entry point.
 *
 * <p>The actual storage access is deliberately kept in the server-side
 * {@code common} package so every action is checked against the current
 * Beyond Dimensions network.</p>
 */
@Mod(BetterBeyondDimensions.MODID)
public final class BetterBeyondDimensions
{
    public static final String MODID = "better_beyond_dimensions";

    public BetterBeyondDimensions()
    {
        NetworkHandler.register();
    }

    @SuppressWarnings("removal")
    public static ResourceLocation id(String path)
    {
        return new ResourceLocation(MODID, path);
    }
}
