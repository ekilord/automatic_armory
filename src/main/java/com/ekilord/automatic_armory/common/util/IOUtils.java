package com.ekilord.automatic_armory.common.util;

import com.ekilord.automatic_armory.AutomaticArmory;
import com.ekilord.automatic_armory.config.AutomaticArmoryConfig;
import com.ekilord.automatic_armory.config.IngotsConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class IOUtils {
    /** Populated fresh at the start of every {@link #runGeneration()} call. */
    private static Map<String, File> modList = new HashMap<>();

    private IOUtils() {
    }

    /**
     * Full generation pipeline: locate installed mods, discover ingot items from each (by scanning each
     * mod's own item textures directly for anything that looks like an ingot - see {@link #lookForIngots}),
     * apply the configured blacklist, then generate tinted weapon-head variants for {@code dixtas_armory}
     * and armor sets for {@code medieval_armory} based on those ingots' textures - each independently, so
     * either integration works with the other mod absent.
     *
     * @return the weapon variants that were generated (empty if dixtas_armory isn't installed or nothing
     * was found) - armor items are registered directly rather than returned, since AutomaticArmory registers those
     * itself (see {@link com.ekilord.automatic_armory.common.armor.AutomaticArmoryArmorItems})
     */
    public static List<GeneratedVariant> runGeneration() {
        modList = new HashMap<>();
        Map<String, BufferedImage> ingotTextures = new LinkedHashMap<>();

        for (File mod : getAvailableMods()) {
            String modId = identifyMod(mod);
            if (modId == null) continue;

            modList.put(modId, mod);
            ingotTextures.putAll(lookForIngots(modId, mod));
        }

        // Blacklisted ingots are always excluded, even if auto-discovered.
        for (String blacklisted : AutomaticArmoryConfig.getBlacklist()) {
            ingotTextures.remove(blacklisted);
        }

        // Seeds ingots.json with a neutral, editable entry for every ingot that's actually going to be
        // generated this run, so the file fills in with real ids instead of staying an empty {} the user
        // has to hand-populate - see IngotsConfig#ensureEntriesExist.
        IngotsConfig.ensureEntriesExist(ingotTextures.keySet());

        List<GeneratedVariant> weaponVariants = List.of();
        File armoryJar = modList.get("dixtas_armory");
        if (armoryJar == null) {
            AutomaticArmory.LOGGER.warn("dixtas_armory is not installed - skipping weapon variant generation.");
        } else {
            weaponVariants = TextureUtils.generateVariants(armoryJar, ingotTextures);
        }

        // Armor generation no longer needs medieval_armory installed at all - its icon/on-body texture
        // templates and model geometry are now bundled with AutomaticArmory itself (see
        // AutomaticArmoryArmorItems and the ported classes under client.model), so this always runs.
        com.ekilord.automatic_armory.common.armor.AutomaticArmoryArmorItems.generateAndRegister(ingotTextures);

        return weaponVariants;
    }

    /**
     * Opens {@code mod}'s jar just far enough to read its mod id from {@code mods.toml}.
     */
    private static String identifyMod(File mod) {
        try (JarFile jarFile = new JarFile(mod)) {
            String modId = extractModId(getFileFromJar(jarFile, AutomaticArmoryConstants.MODS_TOML_LOCATION));
            if (modId == null) {
                AutomaticArmory.LOGGER.warn("Could not read a modId out of {} - check its mods.toml formatting.", mod.getName());
            } else {
                AutomaticArmory.LOGGER.debug("Identified mod jar {} as '{}'", mod.getName(), modId);
            }
            return modId;
        } catch (IOException e) {
            AutomaticArmory.LOGGER.warn("Skipping unreadable mod jar: {}", mod.getName());
            return null;
        }
    }

    public static List<File> getAvailableMods() {
        // debugMode scans mods/test (a dev-only subfolder) instead of the real mods/ folder - see
        // AutomaticArmoryConfig.isDebugMode().
        String modsSubfolder = AutomaticArmoryConfig.isDebugMode() ? "mods/test" : "mods";
        File modsFolder = FMLPaths.GAMEDIR.get().resolve(modsSubfolder).toFile();

        if (!modsFolder.exists() || !modsFolder.isDirectory()) {
            AutomaticArmory.LOGGER.error("Mods folder not found.");
            return List.of();
        }

        File[] files = modsFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null || files.length == 0) {
            AutomaticArmory.LOGGER.warn("Mods folder is empty.");
            return List.of();
        }

        return Arrays.stream(files).toList();
    }

    /**
     * Discovers ingot-like items in a single mod jar directly from its own item textures - no tag or recipe
     * lookup at all, just one pass over the jar's entries checking {@code assets/<modId>/textures/item/*
     * .png} filenames for {@code "ingot"} (the same substring check the old tag-based strategies already
     * used to filter tag entries) and decoding the matches on the spot. This catches any mod that ships an
     * ingot-shaped item, whether or not it bothers tagging it {@code forge:ingots} or referencing it from a
     * sword recipe - both of those were just proxies for "this mod has an ingot texture," so this reads that
     * signal directly instead, in a single cheap jar-entry enumeration with no JSON parsing.
     *
     * @return ingot item id (e.g. {@code "modid:tin_ingot"}) -> its already-decoded item texture
     */
    private static Map<String, BufferedImage> lookForIngots(String modId, File mod) {
        Map<String, BufferedImage> ingots = new LinkedHashMap<>();
        String prefix = "assets/" + modId + "/textures/item/";

        try (JarFile jarFile = new JarFile(mod)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || !name.endsWith(".png")) continue;

                String itemPath = name.substring(prefix.length(), name.length() - ".png".length());
                if (!itemPath.contains("ingot")) continue;

                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image == null) {
                        AutomaticArmory.LOGGER.warn("Ingot texture for {}:{} could not be decoded as an image - skipping.", modId, itemPath);
                        continue;
                    }
                    ingots.put(modId + ":" + itemPath, image);
                } catch (IOException e) {
                    AutomaticArmory.LOGGER.error("Error while reading ingot texture for {}:{}: {}", modId, itemPath, e.getMessage());
                }
            }
        } catch (Exception e) {
            AutomaticArmory.LOGGER.error("Error scanning mod file {}: {}", mod.getName(), e.getMessage());
        }

        return ingots;
    }

    /**
     * Opens a fresh {@link JarFile} for an already-identified mod id. Caller is responsible for closing it
     * (see {@link #closeQuietly(JarFile)}).
     */
    public static JarFile getModJar(String modId) {
        File file = modList.get(modId);
        if (file == null) return null;

        try {
            return new JarFile(file);
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Could not open jar for mod {}: {}", modId, e.getMessage());
            return null;
        }
    }

    public static void closeQuietly(JarFile jarFile) {
        try {
            if (jarFile != null) jarFile.close();
        } catch (IOException ignored) {
        }
    }

    private static final String ARMOR_TEMPLATE_ROOT = "/assets/" + AutomaticArmory.MOD_ID + "/textures/armor_templates/";

    /**
     * Reads one of the armor icon/on-body texture templates ported from {@code medieval_armory} and now
     * bundled directly in AutomaticArmory's own jar (see {@code src/main/resources/assets/automatic_armory/
     * textures/armor_templates/}) - replaces the old approach of locating and opening an installed copy of
     * that mod's jar at runtime. {@code category} is {@code "item"} for 2D icons or {@code "entities"} for
     * on-body textures; {@code name} is the template's filename without the {@code .png} extension.
     */
    public static BufferedImage getArmorTemplate(String category, String name) {
        String path = ARMOR_TEMPLATE_ROOT + category + "/" + name + ".png";

        try (InputStream inputStream = IOUtils.class.getResourceAsStream(path)) {
            if (inputStream == null) return null;
            return ImageIO.read(inputStream);
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Failed to read bundled armor template '{}': {}", path, e.getMessage());
            return null;
        }
    }

    public static InputStream getFileFromJar(JarFile jarFile, String path) {
        JarEntry entry = jarFile.getJarEntry(path);
        if (entry == null) return null;

        try {
            return jarFile.getInputStream(entry);
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Error getting file '{}' from jar {}: {}", path, jarFile.getName(), e.getMessage());
            return null;
        }
    }

    private static final int RESOURCE_PACK_FORMAT = 15; // resource pack format for Minecraft 1.20.1

    /**
     * The items AutomaticArmory generates for {@code dixtas_armory} get registered by {@code dixtas_armory} itself (via
     * its own data-driven config), under its own namespace. AutomaticArmory's own generated armor items (see {@link
     * com.ekilord.automatic_armory.common.armor.AutomaticArmoryArmorItems}) are different: AutomaticArmory registers those itself,
     * so their assets need to live under AutomaticArmory's own mod id instead - see the namespace-taking overloads
     * below.
     */
    public static final String DIXTAS_ARMORY_NAMESPACE = "dixtas_armory";

    public static Path getGeneratedResourcePackRoot() {
        return FMLPaths.CONFIGDIR.get().resolve(AutomaticArmory.MOD_ID).resolve(".cache").resolve("resourcepack");
    }

    private static Path assetsDir(String namespace) {
        return getGeneratedResourcePackRoot().resolve("assets").resolve(namespace);
    }

    /**
     * Writes a generated texture into the resource pack layout under {@link #getGeneratedResourcePackRoot()},
     * creating {@code pack.mcmeta} on first use.
     */
    public static void saveGeneratedTexture(String namespace, String textureName, BufferedImage texture) {
        Path root = getGeneratedResourcePackRoot();
        Path textureFile = assetsDir(namespace).resolve("textures").resolve("item").resolve(textureName + ".png");

        try {
            ensurePackMeta(root);
            Files.createDirectories(textureFile.getParent());
            ImageIO.write(texture, "PNG", textureFile.toFile());
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Failed to write generated texture '{}': {}", textureName, e.getMessage());
        }
    }

    /** Convenience overload for {@code dixtas_armory}-namespaced weapon textures (unchanged call sites). */
    public static void saveGeneratedTexture(String textureName, BufferedImage texture) {
        saveGeneratedTexture(DIXTAS_ARMORY_NAMESPACE, textureName, texture);
    }

    /**
     * Writes a generated on-body armor texture under {@code textures/entities/} - the convention {@code
     * medieval_armory} actually uses for its custom-model armor rendering (one combined texture shared by
     * all four pieces of a set), not vanilla's {@code textures/models/armor/<name>_layer_1/2.png} split,
     * which that mod's own items don't use at all (see {@code AutomaticArmoryArmorItem}'s design notes).
     */
    public static void saveGeneratedEntityTexture(String namespace, String textureName, BufferedImage texture) {
        Path root = getGeneratedResourcePackRoot();
        Path textureFile = assetsDir(namespace).resolve("textures").resolve("entities").resolve(textureName + ".png");

        try {
            ensurePackMeta(root);
            Files.createDirectories(textureFile.getParent());
            ImageIO.write(texture, "PNG", textureFile.toFile());
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Failed to write generated entity texture '{}': {}", textureName, e.getMessage());
        }
    }

    /**
     * Writes an already-built model JSON (typically a patched copy of one of dixtas_armory's own reference
     * models - see {@link TextureUtils}) under the same resource pack.
     */
    public static void saveGeneratedModel(String namespace, String modelName, String modelJson) {
        Path root = getGeneratedResourcePackRoot();
        Path modelFile = assetsDir(namespace).resolve("models").resolve("item").resolve(modelName + ".json");

        try {
            ensurePackMeta(root);
            Files.createDirectories(modelFile.getParent());
            Files.writeString(modelFile, modelJson);
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Failed to write generated model '{}': {}", modelName, e.getMessage());
        }
    }

    /** Convenience overload for {@code dixtas_armory}-namespaced weapon models (unchanged call sites). */
    public static void saveGeneratedModel(String modelName, String modelJson) {
        saveGeneratedModel(DIXTAS_ARMORY_NAMESPACE, modelName, modelJson);
    }

    private static final com.google.gson.Gson LANG_GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    /**
     * Merges the given translation-key -> display-name entries into the generated resource pack's
     * {@code lang/en_us.json} for the given namespace, creating it if needed. Without this, generated items
     * show their raw, untranslated {@code item.<modid>.<name>} key in-game instead of a readable name.
     */
    public static void writeLangEntries(String namespace, Map<String, String> entries) {
        Path root = getGeneratedResourcePackRoot();
        Path langFile = assetsDir(namespace).resolve("lang").resolve("en_us.json");

        try {
            ensurePackMeta(root);
            Files.createDirectories(langFile.getParent());

            Map<String, String> merged = new LinkedHashMap<>();
            if (Files.exists(langFile)) {
                try (Reader reader = Files.newBufferedReader(langFile)) {
                    Map<String, String> existing = LANG_GSON.fromJson(reader,
                            new com.google.gson.reflect.TypeToken<Map<String, String>>() {
                            }.getType());
                    if (existing != null) merged.putAll(existing);
                } catch (Exception e) {
                    AutomaticArmory.LOGGER.warn("Existing generated lang file was unreadable, rebuilding it: {}", e.getMessage());
                }
            }

            merged.putAll(entries);
            Files.writeString(langFile, LANG_GSON.toJson(merged));

            AutomaticArmory.LOGGER.info("Updated generated lang file ({}) with {} entr{}.", namespace, entries.size(), entries.size() == 1 ? "y" : "ies");
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Failed to write generated lang file: {}", e.getMessage());
        }
    }

    /** Convenience overload for {@code dixtas_armory}-namespaced weapon lang entries (unchanged call sites). */
    public static void writeLangEntries(Map<String, String> entries) {
        writeLangEntries(DIXTAS_ARMORY_NAMESPACE, entries);
    }

    private static void ensurePackMeta(Path root) throws IOException {
        Path packMeta = root.resolve("pack.mcmeta");
        if (Files.exists(packMeta)) return;

        Files.createDirectories(root);
        Files.writeString(packMeta, """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "AutomaticArmory generated weapon variant textures"
                  }
                }
                """.formatted(RESOURCE_PACK_FORMAT));
    }

    private static final java.util.regex.Pattern MOD_ID_PATTERN =
            java.util.regex.Pattern.compile("^modId\\s*=\\s*[\"']([^\"']+)[\"']");

    public static String extractModId(InputStream inputStream) {
        if (inputStream == null) return null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                java.util.regex.Matcher matcher = MOD_ID_PATTERN.matcher(line.trim());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Failed to read mods.toml: {}", e.getMessage());
        }

        return null;
    }
}