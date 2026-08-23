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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Stores a separate sidebar offset for each concrete screen/menu layout. */
public final class SidebarPositionStore
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "better_beyond_dimensions-sidebar-positions.json";
    private static final Map<String, Position> POSITIONS = new LinkedHashMap<>();

    private static boolean loaded;

    private SidebarPositionStore()
    {
    }

    public static synchronized Optional<Position> get(String key)
    {
        loadIfNeeded();
        return Optional.ofNullable(POSITIONS.get(key));
    }

    public static synchronized void save(String key, int offsetX, int offsetY)
    {
        if (key == null || key.isBlank())
        {
            return;
        }

        loadIfNeeded();
        POSITIONS.put(key, new Position(offsetX, offsetY));
        writeFile();
    }

    private static void loadIfNeeded()
    {
        if (loaded)
        {
            return;
        }
        loaded = true;

        Path file = positionFile();
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

            JsonObject positions = parsed.getAsJsonObject().getAsJsonObject("positions");
            if (positions == null)
            {
                return;
            }

            for (Map.Entry<String, JsonElement> entry : positions.entrySet())
            {
                if (!entry.getValue().isJsonObject())
                {
                    continue;
                }
                JsonObject value = entry.getValue().getAsJsonObject();
                if (value.has("offsetX") && value.has("offsetY"))
                {
                    POSITIONS.put(entry.getKey(), new Position(
                            value.get("offsetX").getAsInt(),
                            value.get("offsetY").getAsInt()
                    ));
                }
            }
        }
        catch (IOException | RuntimeException exception)
        {
            LOGGER.warn("Could not read Better Beyond Dimensions sidebar positions from {}", file, exception);
        }
    }

    private static void writeFile()
    {
        Path file = positionFile();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject positions = new JsonObject();
        new TreeMap<>(POSITIONS).forEach((key, position) -> {
            JsonObject value = new JsonObject();
            value.addProperty("offsetX", position.offsetX());
            value.addProperty("offsetY", position.offsetY());
            positions.add(key, value);
        });
        root.add("positions", positions);

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
            LOGGER.warn("Could not save Better Beyond Dimensions sidebar positions to {}", file, exception);
        }
    }

    private static Path positionFile()
    {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    public record Position(int offsetX, int offsetY)
    {
    }
}
