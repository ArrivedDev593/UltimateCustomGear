package arrivedbog593.ultimatecustomgear.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Computes a deterministic hash of ALL config JSONs — from the loose
 * ultimatecustomgear folder AND every zip mounted from packs/ — used by the
 * configuration-phase handshake to verify that a joining client loaded the
 * same content definitions as the server.
 * <p>
 * IMPORTANT — capture semantics: the handshake must compare what each side
 * actually LOADED INTO MEMORY, not what is currently on disk. Item attributes
 * (damage, defense, etc.) are baked at registration time; if a JSON is edited
 * after the game started, the disk no longer reflects the running state.
 * Therefore, the hash is captured via {@link #capture(ContentRoots)} exactly
 * when content is (re)loaded — at startup and on /customgear reload — and the
 * handshake reads {@link #current()}. Never hash the disk at connection time.
 * <p>
 * Normalization rules (so only real content differences cause a mismatch):
 * <ul>
 *   <li>Each file is parsed and re-serialized in canonical form: object keys
 *       sorted alphabetically (recursively), compact output. Whitespace,
 *       CRLF vs LF, indentation, and key order do NOT affect the hash.</li>
 *   <li>File paths AND packaging are ignored: the same JSONs loose, inside
 *       one zip, split across several zips, or in different subfolders all
 *       produce the SAME hash. "Drop this zip in packs/" therefore always
 *       matches a server running the equivalent loose files.</li>
 *   <li>Canonical strings are sorted before hashing, so filesystem walk
 *       order (which differs across OSes) does not affect the result.</li>
 *   <li>Malformed JSONs are skipped — the parsers skip them identically on
 *       both sides, so they cannot cause real gameplay differences.</li>
 *   <li>Files that lost a duplicate id (in a parser or in GlobalIdValidator)
 *       are skipped: which file wins depends on paths and packaging, so the
 *       hash must cover the winner only. Requires capture() to run after
 *       GlobalIdValidator, on the same ContentRoots.</li>
 *   <li>The .cache subfolder is excluded (matching the parsers' filter), and
 *       packs/ is excluded from the loose walk — its zips are hashed through
 *       their own mounted roots, not as opaque binary files.</li>
 *   <li>Only .json files are hashed. Textures and models are deliberately
 *       excluded: the handshake exists to catch STAT desync, and a client
 *       with a different PNG sees a different picture, not different damage.
 *       Enforcing texture equality would kick players over cosmetics.</li>
 * </ul>
 * NOTE: this hashes the SOURCE JSONs (item stats, effects, etc.), which is
 * different from DynamicResourcePack.contentHash() — that one hashes the
 * GENERATED pack (models, lang, recipes). Both are needed: the KnownPack
 * covers data-pack desync, this one covers registry/stat desync.
 */
public final class ContentHasher {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");
    private static final Gson GSON = new Gson(); // compact output — no pretty printing

    /**
     * Hash of the content as it was when last (re)loaded by THIS game instance.
     * "uncomputed" only if capture() was never called (should not happen).
     */
    private static volatile String currentHash = "uncomputed";

    private ContentHasher() {}

    /**
     * Captures the hash across ALL content roots (loose folder + every
     * mounted zip) as this instance's current content state. Call at the
     * exact points where content is loaded into memory: CustomGearMod's
     * constructor and the /customgear reload command — inside the
     * try-with-resources, while the zips are still mounted, and after
     * GlobalIdValidator's drops were passed to {@link ContentRoots#excludeEntries}.
     */
    public static void capture(ContentRoots roots) {
        List<String> canonicalFiles = new ArrayList<>();
        for (ContentRoots.Root root : roots.roots()) {
            collectCanonicalJsons(roots, root.path(), canonicalFiles);
        }
        currentHash = hashCanonicalList(canonicalFiles);
        LOGGER.info("[CustomGear] Content hash captured: {}", currentHash);
    }

    /** The hash captured at the last content (re)load of this game instance. */
    public static String current() {
        return currentHash;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Walks one root and adds the canonical form of every valid .json to
     * {@code out}. Excludes .cache (parser parity) and packs/ (zips are
     * hashed via their own mounted roots, not as binary files).
     * <p>
     * Symlinked files are skipped the same way the parsers skip them: a file
     * GearParser and UniversalParser refuse to load must not influence the
     * hash either, or the handshake would compare content that was never
     * registered on either side.
     */
    private static void collectCanonicalJsons(ContentRoots roots, Path root, List<String> out) {
        if (!Files.isDirectory(root)) return;

        // Only meaningful on the real filesystem; zip roots cannot escape.
        boolean realFs = root.getFileSystem() == FileSystems.getDefault();
        Path canonicalRoot = realFs ? ParserUtils.resolveCanonical(root) : null;
        if (realFs && canonicalRoot == null) {
            out.add("scan-error:" + root);
            return;
        }

        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".json")
                            && !root.relativize(p).startsWith(".cache")
                            && !root.relativize(p).startsWith("packs"))
                    .forEach(path -> {
                        if (realFs && !ParserUtils.isSafeChild(path, canonicalRoot)) {
                            LOGGER.warn("[CustomGear] ContentHasher skipping file outside "
                                    + "ultimatecustomgear/ dir: {}", path);
                            return;
                        }
                        // Read but not loaded (a duplicate id lost): hashing it would let two
                        // sides that kept different files match.
                        if (roots.isExcluded(path)) return;
                        try {
                            String raw = Files.readString(path, StandardCharsets.UTF_8);
                            JsonElement parsed = JsonParser.parseString(raw);
                            out.add(GSON.toJson(canonicalize(parsed)));
                        } catch (Exception e) {
                            // Malformed JSON — the parsers skip it on both sides,
                            // so it must not influence the hash either.
                            LOGGER.debug("[CustomGear] ContentHasher skipping malformed JSON: {}",
                                    path.getFileName());
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("[CustomGear] ContentHasher could not scan {}: {}", root, e.getMessage());
            // Poison the list so the resulting hash won't silently match:
            // refusing a connection is safer than desyncing one.
            out.add("scan-error:" + root);
        }
    }

    /** Sorts the canonical strings and hashes them into a short hex string. */
    private static String hashCanonicalList(List<String> canonicalFiles) {
        if (canonicalFiles.isEmpty()) {
            return "empty";
        }

        canonicalFiles.sort(Comparator.naturalOrder());

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String file : canonicalFiles) {
                digest.update(file.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0); // separator between files
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(24);
            for (int i = 0; i < 12; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 exists on every JVM; defensive fallback only
            return "unhashed-" + canonicalFiles.size();
        }
    }

    /** Recursively sorts object keys so serialization is order-independent. */
    private static JsonElement canonicalize(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            element.getAsJsonObject().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sorted.add(e.getKey(), canonicalize(e.getValue())));
            return sorted;
        }
        if (element.isJsonArray()) {
            JsonArray arr = new JsonArray();
            for (JsonElement e : element.getAsJsonArray()) {
                arr.add(canonicalize(e));
            }
            return arr;
        }
        return element; // primitives and null are already canonical
    }
}