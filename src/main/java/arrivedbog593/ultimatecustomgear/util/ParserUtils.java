package arrivedbog593.ultimatecustomgear.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Shared utilities for GearParser and UniversalParser.
 * Centralizes logic that was duplicated across both classes.
 */
public final class ParserUtils {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    private ParserUtils() {}

    /**
     * Extracts the "type" field from a JSON string without fully parsing it.
     * Returns {@code null} if the JSON is malformed or the field is absent.
     */
    public static String extractType(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonElement typeEl = root.getAsJsonObject().get("type");
            if (typeEl == null || !typeEl.isJsonPrimitive()) return null;
            return typeEl.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates {@code folder} if it does not exist.
     * Returns {@code false} and logs an error if creation fails,
     * which should cause the caller to abort loading.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean ensureFolderExists(Path folder) {
        if (Files.exists(folder)) return true;
        try {
            Files.createDirectories(folder);
            LOGGER.info("[CustomGear] Folder created: {}", folder);
            return true;
        } catch (IOException e) {
            LOGGER.error("[CustomGear] Could not create folder: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns {@code null} and logs an error if resolution fails,
     * which should cause the caller to abort loading.
     */
    public static Path resolveCanonical(Path folder) {
        try {
            return folder.toRealPath();
        } catch (IOException e) {
            LOGGER.error("[CustomGear] Cannot resolve base folder path: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns {@code true} if {@code file} is safely inside {@code canonicalBase}.
     * Always call {@link #resolveCanonical} on the base folder first.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isSafeChild(Path file, Path canonicalBase) {
        try {
            return file.toRealPath().startsWith(canonicalBase);
        } catch (IOException e) {
            LOGGER.warn("[CustomGear] Cannot resolve path, skipping: {} — {}", file, e.getMessage());
            return false;
        }
    }

    /**
     * The order the parsers visit the files of one root: by path relative to
     * the root, with '/' as the separator, case-sensitive. The first file with
     * a given id wins, so client and server must visit files in the same
     * order — and neither Files.walk nor Path's own ordering gives that across
     * OSes (Windows compares ignoring case and with '\').
     */
    public static Comparator<Path> walkOrder(Path root) {
        return Comparator.comparing(p -> root.relativize(p).toString().replace('\\', '/'));
    }
}