package net.xuwu.betterbeyonddimensions.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Persists the two client-side defaults that are sent to the server when a sidebar opens. */
public final class SidebarSettingsStore
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "better_beyond_dimensions-settings.json";

    private static boolean loaded;
    private static boolean playerShift;
    private static boolean containerShift;

    private SidebarSettingsStore()
    {
    }

    public static synchronized Settings get()
    {
        loadIfNeeded();
        return new Settings(playerShift, containerShift);
    }

    public static synchronized void set(boolean playerShiftEnabled, boolean containerShiftEnabled)
    {
        loadIfNeeded();
        playerShift = playerShiftEnabled;
        containerShift = containerShiftEnabled;
        writeFile();
    }

    private static void loadIfNeeded()
    {
        if (loaded)
        {
            return;
        }
        loaded = true;

        Path file = settingsFile();
        if (!Files.isRegularFile(file))
        {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject())
            {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("playerShift"))
            {
                playerShift = root.get("playerShift").getAsBoolean();
            }
            if (root.has("containerShift"))
            {
                containerShift = root.get("containerShift").getAsBoolean();
            }
        }
        catch (IOException | RuntimeException exception)
        {
            LOGGER.warn("Could not read Better Beyond Dimensions sidebar settings from {}", file, exception);
        }
    }

    private static void writeFile()
    {
        Path file = settingsFile();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("playerShift", playerShift);
        root.addProperty("containerShift", containerShift);

        try
        {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try
            {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException ignored)
            {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException exception)
        {
            LOGGER.warn("Could not save Better Beyond Dimensions sidebar settings to {}", file, exception);
        }
    }

    private static Path settingsFile()
    {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    public record Settings(boolean playerShift, boolean containerShift)
    {
    }
}
