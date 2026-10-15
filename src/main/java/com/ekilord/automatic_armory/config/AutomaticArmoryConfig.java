package com.ekilord.automatic_armory.config;

import com.ekilord.automatic_armory.AutomaticArmory;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatic Armory's own tiny config reader/writer, deliberately not using {@code ForgeConfigSpec}.
 * <p>
 * Item registration needs these values synchronously during mod construction (before {@code
 * RegisterEvent<Item>} fires), but Forge doesn't actually load {@code ForgeConfigSpec}-backed config until
 * after mod construction - reading it any earlier throws {@code IllegalStateException}. Since we control
 * the file directly, we can read it whenever we like, with no lifecycle to wait on.
 * <p>
 * The file uses a small TOML-like subset (comments, {@code key = value}, quoted strings, quoted-string
 * arrays) - just enough for the settings below, not a general TOML parser.
 */
public final class AutomaticArmoryConfig {
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve(AutomaticArmory.MOD_ID).resolve("common.toml");

    private static boolean enableFeature = true;
    private static List<String> blacklist = new ArrayList<>();

    // Every default below reproduces a value that used to be a hardcoded constant in TextureUtils -
    // changing these values changes generation output, but a freshly-written common.toml with these
    // defaults must generate byte-identical results to before this file could configure them at all.
    private static boolean debugMode = false;
    private static int colorRampStops = 6;
    private static float weaponHandleTintStrength = 0.4f;
    private static float armorIconTintStrength = 0.92f;
    private static float baseTierGrayscaleSaturationThreshold = 0.20f;
    private static float baseTierBrightVsDarkThreshold = 0.55f;

    // Applied multiplicatively on top of every armor set's own ArmorSetDefinition#texture (a
    // TextureAdjustment) - lets a user nudge every generated set's brightness/saturation at once instead of
    // editing armor_sets.json's per-set fields individually. 1.0 is a no-op, matching TextureAdjustment's
    // own per-set defaults.
    private static float overallItemBrightnessMultiplier = 1.0f;
    private static float overallWornBrightnessMultiplier = 1.0f;
    private static float overallItemSaturationMultiplier = 1.0f;
    private static float overallWornSaturationMultiplier = 1.0f;

    // Additive (not multiplicative) - see TextureAdjustment#itemVibranceAmount. 0.0 is a no-op.
    private static float overallItemVibranceAmount = 0.0f;
    private static float overallWornVibranceAmount = 0.0f;

    private static boolean loaded = false;

    private AutomaticArmoryConfig() {
    }

    /**
     * Loads the config file on first use (writing a default one if it doesn't exist yet). Safe to call
     * repeatedly; only actually reads from disk once per run.
     */
    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        try {
            if (!Files.exists(CONFIG_FILE)) {
                writeDefaults();
                return;
            }

            for (String line : Files.readAllLines(CONFIG_FILE)) {
                parseLine(line);
            }
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Failed to read Automatic Armory config at {}, using defaults: {}", CONFIG_FILE, e.getMessage());
        }
    }

    private static void parseLine(String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) return;

        int eq = line.indexOf('=');
        if (eq < 0) return;

        String key = line.substring(0, eq).trim();
        String value = line.substring(eq + 1).trim();

        switch (key) {
            case "enableFeature" -> enableFeature = Boolean.parseBoolean(value);
            case "debugMode" -> debugMode = Boolean.parseBoolean(value);
            case "blacklist" -> blacklist = parseStringArray(value);
            case "colorRampStops" -> colorRampStops = parseInt(value, "colorRampStops", colorRampStops);
            case "weaponHandleTintStrength" -> weaponHandleTintStrength = parseFloat(value, "weaponHandleTintStrength", weaponHandleTintStrength);
            case "armorIconTintStrength" -> armorIconTintStrength = parseFloat(value, "armorIconTintStrength", armorIconTintStrength);
            case "baseTierGrayscaleSaturationThreshold" ->
                    baseTierGrayscaleSaturationThreshold = parseFloat(value, "baseTierGrayscaleSaturationThreshold", baseTierGrayscaleSaturationThreshold);
            case "baseTierBrightVsDarkThreshold" ->
                    baseTierBrightVsDarkThreshold = parseFloat(value, "baseTierBrightVsDarkThreshold", baseTierBrightVsDarkThreshold);
            case "overallItemBrightnessMultiplier" ->
                    overallItemBrightnessMultiplier = parseFloat(value, "overallItemBrightnessMultiplier", overallItemBrightnessMultiplier);
            case "overallWornBrightnessMultiplier" ->
                    overallWornBrightnessMultiplier = parseFloat(value, "overallWornBrightnessMultiplier", overallWornBrightnessMultiplier);
            case "overallItemSaturationMultiplier" ->
                    overallItemSaturationMultiplier = parseFloat(value, "overallItemSaturationMultiplier", overallItemSaturationMultiplier);
            case "overallWornSaturationMultiplier" ->
                    overallWornSaturationMultiplier = parseFloat(value, "overallWornSaturationMultiplier", overallWornSaturationMultiplier);
            case "overallItemVibranceAmount" ->
                    overallItemVibranceAmount = parseFloat(value, "overallItemVibranceAmount", overallItemVibranceAmount);
            case "overallWornVibranceAmount" ->
                    overallWornVibranceAmount = parseFloat(value, "overallWornVibranceAmount", overallWornVibranceAmount);
            default -> { /* unknown key - ignore, keeps this tolerant of hand edits/old files */ }
        }
    }

    private static int parseInt(String value, String key, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            AutomaticArmory.LOGGER.warn("Invalid {} '{}' in config, keeping '{}'.", key, value, fallback);
            return fallback;
        }
    }

    private static float parseFloat(String value, String key, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            AutomaticArmory.LOGGER.warn("Invalid {} '{}' in config, keeping '{}'.", key, value, fallback);
            return fallback;
        }
    }

    private static List<String> parseStringArray(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        List<String> result = new ArrayList<>();
        if (trimmed.isBlank()) return result;

        for (String part : trimmed.split(",")) {
            String cleaned = unquote(part.trim());
            if (!cleaned.isEmpty()) result.add(cleaned);
        }

        return result;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static void writeDefaults() throws IOException {
        Files.createDirectories(CONFIG_FILE.getParent());

        String contents = """
                # Automatic Armory settings.
                # Whether Automatic Armory should scan mods and generate weapon variants at all.
                enableFeature = true

                # When true, mod scanning reads from mods/test (a dev-only subfolder) instead of the real
                # mods/ folder - useful for testing generation against a curated set of mods without
                # touching your real mods folder. Leave false for normal play.
                debugMode = false

                # Ingot item ids to never generate variants for, even if auto-discovered (see
                # com.ekilord.automatic_armory.common.util.IOUtils#lookForIngots - any item whose texture
                # filename contains "ingot" counts).
                blacklist = []

                # Number of bands in the light-to-dark color ramp built from each ingot's own palette, used
                # when recoloring both weapon variants and armor.
                colorRampStops = 6

                # How strongly non-blade weapon pixels (handle/guard) lean toward the ingot's ramp: 0 leaves
                # them untouched, 1 fully replaces them the same way the blade is.
                weaponHandleTintStrength = 0.4

                # How strongly a recolorable armor icon/on-body pixel leans toward the ingot's ramp: 0 leaves
                # it untouched, 1 fully replaces it.
                armorIconTintStrength = 0.92

                # Below this saturation, an ingot's dominant color reads as gray/silver regardless of
                # brightness and gets the iron base-tier template for weapon variants.
                baseTierGrayscaleSaturationThreshold = 0.20

                # Above this brightness, a colorful (non-grayscale) ingot reads as "bright" and gets the
                # golden base-tier template for weapon variants; at or below, "dark" and gets diamond.
                baseTierBrightVsDarkThreshold = 0.55

                # Applied multiplicatively on top of every armor set's own "texture" brightness/saturation
                # multipliers in armor_sets.json (see ArmorSetDefinition#texture) - lets every generated set be
                # nudged brighter/darker or more/less saturated at once instead of editing each set individually.
                # 1.0 is a no-op.
                overallItemBrightnessMultiplier = 1.0
                overallWornBrightnessMultiplier = 1.0
                overallItemSaturationMultiplier = 1.0
                overallWornSaturationMultiplier = 1.0

                # Same idea as the saturation multipliers above, but additive rather than multiplicative -
                # see TextureAdjustment#itemVibranceAmount. 0.0 is a no-op.
                overallItemVibranceAmount = 0.0
                overallWornVibranceAmount = 0.0
                """;

        Files.writeString(CONFIG_FILE, contents);
    }

    public static boolean isFeatureEnabled() {
        ensureLoaded();
        return enableFeature;
    }

    public static boolean isDebugMode() {
        ensureLoaded();
        return debugMode;
    }

    public static List<String> getBlacklist() {
        ensureLoaded();
        return blacklist;
    }

    public static int getColorRampStops() {
        ensureLoaded();
        return colorRampStops;
    }

    public static float getWeaponHandleTintStrength() {
        ensureLoaded();
        return weaponHandleTintStrength;
    }

    public static float getArmorIconTintStrength() {
        ensureLoaded();
        return armorIconTintStrength;
    }

    public static float getBaseTierGrayscaleSaturationThreshold() {
        ensureLoaded();
        return baseTierGrayscaleSaturationThreshold;
    }

    public static float getBaseTierBrightVsDarkThreshold() {
        ensureLoaded();
        return baseTierBrightVsDarkThreshold;
    }

    public static float getOverallItemBrightnessMultiplier() {
        ensureLoaded();
        return overallItemBrightnessMultiplier;
    }

    public static float getOverallWornBrightnessMultiplier() {
        ensureLoaded();
        return overallWornBrightnessMultiplier;
    }

    public static float getOverallItemSaturationMultiplier() {
        ensureLoaded();
        return overallItemSaturationMultiplier;
    }

    public static float getOverallWornSaturationMultiplier() {
        ensureLoaded();
        return overallWornSaturationMultiplier;
    }

    public static float getOverallItemVibranceAmount() {
        ensureLoaded();
        return overallItemVibranceAmount;
    }

    public static float getOverallWornVibranceAmount() {
        ensureLoaded();
        return overallWornVibranceAmount;
    }
}
