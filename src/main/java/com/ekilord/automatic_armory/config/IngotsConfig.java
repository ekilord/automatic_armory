package com.ekilord.automatic_armory.config;

import com.ekilord.automatic_armory.AutomaticArmory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-ingot texture overrides, keyed by full ingot item id (e.g. {@code "metal_works:bronze_ingot"} - the
 * same id format {@link AutomaticArmoryConfig#getBlacklist()} uses) at {@code config/automatic_armory/
 * ingots.json} - see {@link IngotTextureOverride}.
 * <p>
 * Deliberately a third config file rather than folded into {@link ArmorSetsConfig}: {@code armor_sets.json}
 * is keyed by <i>armor set</i> (a style generated once per ingot) and its {@code texture} block tunes every
 * ingot's version of that one set identically, while this file is keyed by <i>ingot</i> and tunes one
 * material's look across every set (and every weapon) at once - e.g. "make bronze brighter everywhere"
 * belongs here, "make the mail set generally read a bit dark" belongs in {@code armor_sets.json}. The two
 * combine multiplicatively (additively for the vibrance fields) rather than one overriding the other - see
 * {@link com.ekilord.automatic_armory.common.armor.AutomaticArmoryArmorItems#generateAndRegisterOneSet} and
 * {@link com.ekilord.automatic_armory.common.util.TextureUtils#generateVariantsForIngot}.
 * <p>
 * No whitelist/blacklist semantics here - an ingot simply not listed gets an all-neutral {@link
 * IngotTextureOverride} ({@code 1.0} multipliers, {@code 0.0} vibrance), the same as an entry that
 * explicitly lists every field at its default. The shipped default file is an empty {@code {}}, since ingot
 * ids are only known at runtime - but {@link #ensureEntriesExist} is called once per {@code
 * IOUtils#runGeneration()} run with every ingot that was actually discovered that run, adding a neutral
 * entry (and persisting the file) for any id not already present. So in practice the file fills in with one
 * editable entry per currently-installed ingot after the mod has run at least once, rather than staying
 * empty and requiring ids to be hand-typed.
 */
public final class IngotsConfig {
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve(AutomaticArmory.MOD_ID).resolve("ingots.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type OVERRIDES_TYPE = new TypeToken<Map<String, IngotTextureOverride>>() {
    }.getType();
    private static final IngotTextureOverride NEUTRAL = new IngotTextureOverride();

    private static Map<String, IngotTextureOverride> overrides = new LinkedHashMap<>();
    private static boolean loaded = false;

    private IngotsConfig() {
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        try {
            if (!Files.exists(CONFIG_FILE)) {
                writeToDisk(overrides);
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                Map<String, IngotTextureOverride> parsed = GSON.fromJson(reader, OVERRIDES_TYPE);
                // Copied into a fresh LinkedHashMap rather than used as-is: Gson's own Map implementation
                // isn't guaranteed mutable, but ensureEntriesExist() needs to add to this map later.
                overrides = parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
            }
        } catch (IOException | RuntimeException e) {
            AutomaticArmory.LOGGER.error("Failed to read {}, using no ingot overrides: {}", CONFIG_FILE, e.getMessage());
            overrides = new LinkedHashMap<>();
        }
    }

    private static void writeToDisk(Map<String, IngotTextureOverride> contents) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, GSON.toJson(contents));
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Failed to write default {}: {}", CONFIG_FILE, e.getMessage());
        }
    }

    /**
     * Returns the configured override for {@code ingotId}, or an all-neutral one (every multiplier {@code
     * 1.0}, every vibrance {@code 0.0}) if it has no entry.
     */
    public static IngotTextureOverride getOverride(String ingotId) {
        ensureLoaded();
        return overrides.getOrDefault(ingotId, NEUTRAL);
    }

    /**
     * Adds a neutral {@link IngotTextureOverride} entry for every id in {@code ingotIds} that doesn't
     * already have one, and persists the file if anything was added. Existing entries (and whatever values
     * a user has already tuned on them) are left completely untouched; an ingot that stops being discovered
     * (e.g. its providing mod is removed) keeps its old entry rather than being deleted, so re-adding the
     * mod later doesn't lose the tuning.
     */
    public static synchronized void ensureEntriesExist(Collection<String> ingotIds) {
        ensureLoaded();

        boolean changed = false;
        for (String ingotId : ingotIds) {
            if (!overrides.containsKey(ingotId)) {
                overrides.put(ingotId, new IngotTextureOverride());
                changed = true;
            }
        }

        if (changed) {
            writeToDisk(overrides);
        }
    }
}
