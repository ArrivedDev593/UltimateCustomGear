package arrivedbog593.ultimatecustomgear.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The set of places UltimateCustomGear content can live:
 * <ol>
 *   <li>The loose config folder (ultimatecustomgear/) — always first.</li>
 *   <li>Every .zip inside ultimatecustomgear/packs/, mounted read-only as a
 *       virtual filesystem and walked exactly like the loose folder.</li>
 * </ol>
 * This lets server owners distribute their content as a SINGLE zip file that
 * players drop into packs/ — impossible to half-extract or accidentally edit,
 * and modpack-friendly (CurseForge/Modrinth packs ship config folders as-is).
 * <p>
 * PRECEDENCE: loose files win over zips, and zips are mounted in alphabetical
 * order — deterministic on every OS, so client and server resolve identical
 * content identically (which the handshake hash depends on). Duplicate IDs
 * across roots are caught by the parsers' seen-sets and GlobalIdValidator
 * like any other duplicate: first loaded wins, the loser is logged.
 * <p>
 * LIFECYCLE: obtain via {@link #open(Path)} in a try-with-resources spanning
 * the whole load (parse → hash capture → texture/model loading); zip
 * filesystems are closed when the session closes. Deeply nested resource
 * resolution (texture generators) can reach the active session through
 * {@link #current()} without threading it through every signature.
 */
public final class ContentRoots implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    /**
     * One place content is loaded from.
     *
     * @param label "" for the loose folder; "packs/<file>.zip!" for zips.
     *              Prepended to cache keys so zip entries never collide with
     *              loose files, and shown in logs so errors say WHERE.
     * @param path  the root directory to walk (real folder or zip root)
     */
    public record Root(String label, Path path) {
        public boolean isLoose() {
            return label.isEmpty();
        }
    }

    private static volatile ContentRoots current = null;

    private final Path configFolder;
    private final List<Root> roots;
    private final List<FileSystem> openZipFilesystems;

    /** The file each loaded entry came from, by identity. */
    private final Map<Object, Path> sources = new IdentityHashMap<>();
    /** Files whose content was read but not loaded: duplicate ids and entries dropped by GlobalIdValidator. */
    private final Set<Path> excluded = new HashSet<>();

    private ContentRoots(Path configFolder, List<Root> roots, List<FileSystem> openZipFilesystems) {
        this.configFolder = configFolder;
        this.roots = List.copyOf(roots);
        this.openZipFilesystems = openZipFilesystems;
    }

    /**
     * Opens a content session: the loose folder plus every readable zip in
     * packs/. Corrupt zips are skipped with an error; .rar/.7z files get a
     * how-to-fix warning instead of silent ignoring.
     */
    public static ContentRoots open(Path configFolder) {
        List<Root> roots = new ArrayList<>();
        List<FileSystem> filesystems = new ArrayList<>();

        // Loose folder always first — loose files win ID collisions with zips
        roots.add(new Root("", configFolder));

        Path packsDir = configFolder.resolve("packs");
        if (Files.isDirectory(packsDir)) {
            try (var stream = Files.list(packsDir)) {
                // Sorted: deterministic mount order on every OS. The handshake
                // hash is path-independent, but ID-collision precedence isn't —
                // it must resolve identically on client and server. By file name
                // as a String, not by Path: Windows orders paths ignoring case.
                for (Path zipPath : stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                    String name = zipPath.getFileName().toString().toLowerCase(Locale.ROOT);

                    if (name.endsWith(".zip")) {
                        try {
                            FileSystem fs = FileSystems.newFileSystem(zipPath);
                            filesystems.add(fs);
                            Path zipRoot = fs.getRootDirectories().iterator().next();
                            roots.add(new Root("packs/" + zipPath.getFileName() + "!", zipRoot));
                            LOGGER.info("[CustomGear] Mounted content pack: packs/{}", zipPath.getFileName());
                        } catch (IOException e) {
                            LOGGER.error("[CustomGear] Could not open packs/{} — corrupt or not a real zip? "
                                    + "It will be IGNORED: {}", zipPath.getFileName(), e.getMessage());
                        }
                    } else if (name.endsWith(".rar") || name.endsWith(".7z")) {
                        LOGGER.warn("[CustomGear] Found packs/{} — only .zip is supported, this file is "
                                + "IGNORED. Re-compress it as zip (Windows: right-click → Send to → "
                                + "Compressed folder).", zipPath.getFileName());
                    }
                }
            } catch (IOException e) {
                LOGGER.error("[CustomGear] Could not scan packs/ folder: {}", e.getMessage());
            }

            // Courtesy check: zips dropped in the ROOT folder (instead of packs/)
            // are NOT loaded — tell the user instead of silently ignoring them.
            try (var stream = Files.list(configFolder)) {
                stream.forEach(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z")) {
                        LOGGER.warn("[CustomGear] Found {} in the ultimatecustomgear/ root — archives are "
                                + "only loaded from the packs/ subfolder. Move it to packs/ (and if it's "
                                + "not a .zip, re-compress it as zip).", p.getFileName());
                    }
                });
            } catch (IOException e) {
                LOGGER.debug("[CustomGear] Could not scan root for misplaced archives: {}", e.getMessage());
            }

            ContentRoots session = new ContentRoots(configFolder, roots, filesystems);
            current = session;
            return session;
        }

        ContentRoots session = new ContentRoots(configFolder, roots, filesystems);
        current = session;
        return session;
    }

    /** All roots, loose folder first, then zips alphabetically. */
    public List<Root> roots() {
        return roots;
    }

    /** The loose config folder (where .cache and packs/ live). */
    public Path configFolder() {
        return configFolder;
    }

    // ── What was actually loaded ─────────────────────────────────────────────
    //
    // The content hash ignores paths and packaging, but which file wins a
    // duplicate id depends on both. Without this, a client and a server with
    // the same JSONs arranged differently hash the same and load different
    // stats. Leaving out the files that lost makes the hash follow the winner.

    /** Called by the parsers for every entry they keep. */
    public void recordSource(Object entry, Path file) {
        sources.put(entry, file);
    }

    /** Called by the parsers for a file skipped as a duplicate id. */
    public void exclude(Path file) {
        excluded.add(file);
    }

    /** Excludes the files of entries a later stage dropped (GlobalIdValidator). */
    public void excludeEntries(List<?> entries) {
        for (Object entry : entries) {
            Path file = sources.get(entry);
            if (file != null) excluded.add(file);
        }
    }

    /** Whether ContentHasher must leave this file out. */
    public boolean isExcluded(Path file) {
        return excluded.contains(file);
    }

    /**
     * Resolves a user-referenced resource (custom texture, model file...)
     * against every root in precedence order. A JSON inside a zip can
     * therefore reference textures inside the same zip, and a loose file of
     * the same relative path would override it — consistent with ID
     * precedence.
     */
    public Optional<Path> resolveResource(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return Optional.empty();

        for (Root root : roots) {
            Path base = root.path().normalize();
            Path candidate = base.resolve(relativePath).normalize();

            // Content paths are user input. Without this, "../../secrets.txt"
            // reads a file outside the content folder and injects it into the
            // pack. Escaping is never legitimate: everything a JSON references
            // must ship beside it.
            if (!candidate.startsWith(base)) {
                LOGGER.warn("[CustomGear] Path '{}' escapes the content folder — ignored. "
                                + "Referenced files must live inside {} or a pack zip.",
                        relativePath, root.label().isEmpty() ? "ultimatecustomgear/" : root.label());
                continue;
            }
            if (Files.exists(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /**
     * The active session, for deep static code (texture generators) that
     * can't receive it as a parameter. Null outside a load — callers must
     * handle that (fall back to default resources and log).
     */
    public static ContentRoots current() {
        return current;
    }

    @Override
    public void close() {
        for (FileSystem fs : openZipFilesystems) {
            try {
                fs.close();
            } catch (IOException e) {
                LOGGER.debug("[CustomGear] Error closing zip filesystem: {}", e.getMessage());
            }
        }
        openZipFilesystems.clear();
        if (current == this) {
            current = null;
        }
    }
}