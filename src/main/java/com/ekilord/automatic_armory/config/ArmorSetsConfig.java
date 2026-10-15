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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AutomaticArmory's own config listing every armor set (style + stat block) it should generate one of per discovered
 * ingot - see {@link ArmorSetDefinition}. Every number here is AutomaticArmory's own, plainly editable, rather
 * than copied from the original template mod. Every set listed is generated for every ingot
 * (no per-ingot filtering) - if you don't want a given style generated at all, delete its entry.
 * <p>
 * The shipped defaults are loosely modeled on vanilla's equivalent tiers (iron/diamond/netherite/chainmail/
 * leather) as a reasonable starting point, <b>not</b> reverse-engineered from the original template mod's own
 * actual values - treat every number as a placeholder to tune, not an authoritative match to any other
 * mod's balance.
 */
public final class ArmorSetsConfig {
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve(AutomaticArmory.MOD_ID).resolve("armor_sets.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SETS_TYPE = new TypeToken<Map<String, ArmorSetDefinition>>() {
    }.getType();

    private static Map<String, ArmorSetDefinition> sets = new LinkedHashMap<>();
    private static boolean loaded = false;

    private ArmorSetsConfig() {
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        try {
            if (!Files.exists(CONFIG_FILE)) {
                sets = buildDefaults();
                writeToDisk(sets);
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                Map<String, ArmorSetDefinition> parsed = GSON.fromJson(reader, SETS_TYPE);
                sets = parsed != null ? parsed : buildDefaults();
            }

            warnAboutMissingOnBodyFields();
        } catch (IOException | RuntimeException e) {
            AutomaticArmory.LOGGER.error("Failed to read {}, using built-in defaults: {}", CONFIG_FILE, e.getMessage());
            sets = buildDefaults();
        }
    }

    /**
     * {@link ArmorSetDefinition#onBodyGeometryPrefix}/{@link ArmorSetDefinition#onBodyTextureFile} are new
     * fields - an {@code armor_sets.json} written before they existed will deserialize them as {@code null}
     * (unlike the {@code float} brightness fields, Gson doesn't apply a field-initializer default to a
     * missing {@code String} field). A set with a {@code null} geometry prefix silently renders with no
     * on-body model at all (falls back to vanilla's default appearance) instead of failing loudly, so warn
     * once at load time rather than leaving that a silent surprise.
     */
    private static void warnAboutMissingOnBodyFields() {
        for (Map.Entry<String, ArmorSetDefinition> entry : sets.entrySet()) {
            if (entry.getValue().onBodyGeometryPrefix == null) {
                AutomaticArmory.LOGGER.warn("Armor set '{}' in {} has no onBodyGeometryPrefix (likely an armor_sets.json " +
                        "written before this field existed) - it will render with no on-body model when worn. " +
                        "Delete or manually update {} to pick up the new default.", entry.getKey(), CONFIG_FILE, CONFIG_FILE);
            }
        }
    }

    private static void writeToDisk(Map<String, ArmorSetDefinition> defaults) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, GSON.toJson(defaults));
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Failed to write default {}: {}", CONFIG_FILE, e.getMessage());
        }
    }

    public static Map<String, ArmorSetDefinition> getSets() {
        ensureLoaded();
        return sets;
    }

    private static ArmorSetDefinition set(String iconStyle, String displayName, String wornBodyTexture, int durabilityMultiplier,
                                          int enchantmentValue, float toughness, float knockbackResistance,
                                          String repairItem, String soundEvent,
                                          int helmet, int chestplate, int leggings, int boots,
                                          String onBodyTextureFile, String onBodyGeometryPrefix, String onBodyGeometryFallbackPrefix) {
        ArmorSetDefinition def = new ArmorSetDefinition();
        def.iconStyle = iconStyle;
        def.displayName = displayName;
        def.wornBodyTexture = wornBodyTexture;
        def.durabilityMultiplier = durabilityMultiplier;
        def.enchantmentValue = enchantmentValue;
        def.toughness = toughness;
        def.knockbackResistance = knockbackResistance;
        def.repairItem = repairItem;
        def.soundEvent = soundEvent;
        def.defense = Map.of("helmet", helmet, "chestplate", chestplate, "leggings", leggings, "boots", boots);
        def.onBodyTextureFile = onBodyTextureFile;
        def.onBodyGeometryPrefix = onBodyGeometryPrefix;
        def.onBodyGeometryFallbackPrefix = onBodyGeometryFallbackPrefix;
        return def;
    }

    private static Map<String, ArmorSetDefinition> buildDefaults() {
        Map<String, ArmorSetDefinition> defaults = new LinkedHashMap<>();

        // "light_iron"/"light_diamond"/"light_netherite" etc. are three distinct generated items per ingot
        // (different stats, different recolor template) - every ingot generates all of them, so they can't
        // share a display name or item-id key (three same-named "Rose Gold Light Helmet"s with different
        // stats would be indistinguishable in-game). iconStyle keeps pointing at the bundled template
        // filenames (fixed, can't rename those); only the set's own key/displayName change, using a quality
        // word to tell the iron/diamond/netherite version of each coverage style apart.
        defaults.put("light", set("light_iron", "Light", "iron", 15, 9, 0f, 0f,
                "minecraft:iron_ingot", "minecraft:item.armor.equip_iron", 1, 4, 3, 1,
                "light_iron_armor", "light_iron_armor", "light_iron_armor"));
        defaults.put("half_plate", set("medium_iron", "Half-Plate", "iron", 15, 9, 0f, 0f,
                "minecraft:iron_ingot", "minecraft:item.armor.equip_iron", 2, 5, 4, 2,
                "medium_iron_armor", "medium_iron_armor", "medium_iron_armor"));
        defaults.put("full_plate", set("heavy_iron", "Full Plate", "iron", 15, 9, 0f, 0f,
                "minecraft:iron_ingot", "minecraft:item.armor.equip_iron", 3, 6, 5, 2,
                "heavy_iron_armor", "heavy_iron_armor", "heavy_iron_armor"));

        defaults.put("fine_light", set("light_diamond", "Fine Light", "diamond", 33, 10, 2.0f, 0f,
                "minecraft:diamond", "minecraft:item.armor.equip_diamond", 2, 5, 4, 2,
                "light_diamond_armor", "light_diamond_armor", "light_diamond_armor"));
        defaults.put("fine_half_plate", set("medium_diamond", "Fine Half-Plate", "diamond", 33, 10, 2.0f, 0f,
                "minecraft:diamond", "minecraft:item.armor.equip_diamond", 3, 6, 5, 2,
                "medium_diamond_armor", "medium_diamond_armor", "medium_diamond_armor"));
        defaults.put("fine_full_plate", set("heavy_diamond", "Fine Full Plate", "diamond", 33, 10, 2.0f, 0f,
                "minecraft:diamond", "minecraft:item.armor.equip_diamond", 4, 7, 6, 3,
                "heavy_diamond_armor", "heavy_diamond_armor", "heavy_diamond_armor"));

        defaults.put("masterwork_light", set("light_netherite", "Masterwork Light", "netherite", 37, 15, 3.0f, 0.1f,
                "minecraft:netherite_ingot", "minecraft:item.armor.equip_netherite", 2, 5, 5, 2,
                "light_netherite_armor", "light_diamond_armor", "light_diamond_armor"));
        defaults.put("masterwork_half_plate", set("medium_netherite", "Masterwork Half-Plate", "netherite", 37, 15, 3.0f, 0.1f,
                "minecraft:netherite_ingot", "minecraft:item.armor.equip_netherite", 3, 6, 6, 3,
                "medium_netherite_armor", "medium_diamond_armor", "medium_diamond_armor"));
        defaults.put("masterwork_full_plate", set("heavy_netherite", "Masterwork Full Plate", "netherite", 37, 15, 3.0f, 0.1f,
                "minecraft:netherite_ingot", "minecraft:item.armor.equip_netherite", 4, 7, 7, 4,
                "heavy_netherite_armor", "heavy_netherite_armor", "heavy_diamond_armor"));

        defaults.put("scale", set("copper_scale", "Scale", "iron", 11, 8, 0f, 0f,
                "minecraft:copper_ingot", "minecraft:item.armor.equip_iron", 1, 3, 3, 1,
                "copper_scale", "copper_scale_armor", "copper_scale_armor"));
        defaults.put("weave", set("diamond_weave", "Weave", "diamond", 20, 12, 1.0f, 0f,
                "minecraft:diamond", "minecraft:item.armor.equip_diamond", 2, 4, 4, 2,
                "diamond_weave_armor", "diamond_weave", "diamond_weave"));
        defaults.put("mail", set("iron_mail", "Mail", "chainmail", 15, 12, 0f, 0f,
                "minecraft:iron_ingot", "minecraft:item.armor.equip_chain", 1, 4, 4, 1,
                "iron_mail_armor", "mail_iron_armor", "mail_iron_armor"));

        return defaults;
    }
}
