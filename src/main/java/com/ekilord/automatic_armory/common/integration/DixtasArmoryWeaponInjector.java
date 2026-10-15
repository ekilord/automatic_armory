package com.ekilord.automatic_armory.common.integration;

import com.ekilord.automatic_armory.AutomaticArmory;
import com.ekilord.automatic_armory.common.util.GeneratedVariant;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


public final class DixtasArmoryWeaponInjector {
    private static final String CONFIG_FILE_NAME = "dixtas_armory-common.json";

    // Real prefixes confirmed from an actual dixtas_armory config: "wooden"/"golden", not "wood"/"gold".
    private static final List<String> TIER_PREFERENCE = List.of("diamond", "netherite", "golden", "iron", "stone", "wooden");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DixtasArmoryWeaponInjector() {
    }

    public static void injectGeneratedWeapons(List<GeneratedVariant> variants) {
        if (variants.isEmpty()) return;

        Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
        if (!Files.exists(configPath)) {
            AutomaticArmory.LOGGER.warn("{} not found - run the game once with dixtas_armory installed normally first " +
                    "(so it writes its default config) before Automatic Armory can inject generated weapons into it.", configPath);
            return;
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(configPath)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            AutomaticArmory.LOGGER.error("Failed to read {}: {}", configPath, e.getMessage());
            return;
        }

        JsonObject weapons = getOrCreateObject(root, "weapons");
        JsonObject regular = getOrCreateObject(weapons, "regular");

        int added = 0;
        for (GeneratedVariant variant : variants) {
            String registryName = variant.textureName(); // already "<material>_<canonicalWeapon>"

            if (regular.has(registryName)) {
                continue; // already present from a previous run (or hand-edited) - don't clobber it
            }

            JsonObject template = findTemplate(regular, variant.weapon());
            if (template == null) {
                AutomaticArmory.LOGGER.warn("No reference weapon config found for '{}' (tried tiers: {}) - skipping {}.",
                        variant.weapon(), TIER_PREFERENCE, registryName);
                continue;
            }

            regular.add(registryName, template.deepCopy());
            added++;
        }

        if (added > 0) {
            try {
                Files.writeString(configPath, GSON.toJson(root));
                AutomaticArmory.LOGGER.info("Added {} generated weapon(s) to {}.", added, configPath);
            } catch (IOException e) {
                AutomaticArmory.LOGGER.error("Failed to write {}: {}", configPath, e.getMessage());
            }
        } else {
            AutomaticArmory.LOGGER.info("No new weapon entries to add to {} (all already present).", configPath);
        }
    }

    private static JsonObject getOrCreateObject(JsonObject parent, String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }

    /**
     * Finds an existing {@code weapons.regular.<tier>_<canonicalWeapon>} entry to use as a template,
     * preferring higher tiers first (matching the diamond-tier reference already used for textures).
     */
    private static JsonObject findTemplate(JsonObject regular, String canonicalWeapon) {
        for (String tier : TIER_PREFERENCE) {
            String key = tier + "_" + canonicalWeapon;
            if (regular.has(key) && regular.get(key).isJsonObject()) {
                return regular.getAsJsonObject(key);
            }
        }
        return null;
    }
}
