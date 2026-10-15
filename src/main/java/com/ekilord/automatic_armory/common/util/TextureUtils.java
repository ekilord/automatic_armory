package com.ekilord.automatic_armory.common.util;

import com.ekilord.automatic_armory.AutomaticArmory;
import com.ekilord.automatic_armory.config.AutomaticArmoryConfig;
import com.ekilord.automatic_armory.config.IngotsConfig;
import com.ekilord.automatic_armory.config.WeaponTextureAdjustment;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public final class TextureUtils {

    private TextureUtils() {
    }

    /**
     * AutomaticArmory no longer hardcodes which logical weapons dixtas_armory has (a list like this went stale the
     * moment the mod added or renamed a weapon). Instead, {@link #gatherTieredTextures} discovers weapon
     * types - and which texture-variant suffixes each one uses - directly from whatever {@code
     * assets/dixtas_armory/textures/item/<tier>_*.png} entries actually exist in the installed jar, every
     * time generation runs. That naturally stays correct across dixtas_armory updates with no code change
     * here.
     */
    private static final String TEXTURE_ITEM_DIR = "assets/" + IOUtils.DIXTAS_ARMORY_NAMESPACE + "/textures/item/";

    /**
     * Strips the {@code _handheld}/{@code _gui} texture-variant suffix off a weapon type, leaving the
     * logical weapon name (e.g. {@code katana_handheld} -> {@code katana}).
     */
    public static String canonicalWeaponName(String weaponType) {
        if (weaponType.endsWith("_handheld")) {
            return weaponType.substring(0, weaponType.length() - "_handheld".length());
        }
        if (weaponType.endsWith("_gui")) {
            return weaponType.substring(0, weaponType.length() - "_gui".length());
        }
        return weaponType;
    }

    /**
     * For each discovered ingot texture, tints a difference-mask of every {@code dixtas_armory} weapon head
     * (the region that visually changes between the iron and diamond tiers) toward that ingot's dominant
     * color, and merges the tinted head back onto a base weapon texture. Which tier's texture (and matching
     * model) serves as that base is chosen per-ingot by {@link #classifyBaseTier} - golden, diamond, or
     * iron - based on how bright/colorful/grayish the ingot's dominant color is, rather than always using
     * diamond regardless of fit.
     * <p>
     * Output is named to match {@code dixtas_armory}'s own {@code <tier>_<weaponType>} convention (e.g.
     * {@code tin_katana_handheld.png}) rather than AutomaticArmory's own scheme, since {@code dixtas_armory} is what
     * will actually register and render these items once its config lists them - see {@link
     * com.ekilord.automatic_armory.common.integration.DixtasArmoryWeaponInjector}. One {@link
     * GeneratedVariant} is emitted per logical weapon (i.e. per registered item), even though a weapon with
     * both {@code _handheld} and {@code _gui} variants produces two texture files (plus a third, "bare"
     * dispatcher model that's the one actually looked up for the registered item - see {@link
     * #copyAndPatchModels}).
     *
     * @param armoryJar      the {@code dixtas_armory} mod jar
     * @param ingotTextures  ingot item id -> its item texture
     * @return the list of variants that were generated
     */
    public static List<GeneratedVariant> generateVariants(File armoryJar, Map<String, BufferedImage> ingotTextures) {
        List<GeneratedVariant> generated = new ArrayList<>();
        Map<String, String> langEntries = new LinkedHashMap<>();

        if (ingotTextures.isEmpty()) {
            AutomaticArmory.LOGGER.info("No ingot textures were discovered; nothing to generate.");
            return generated;
        }

        try (JarFile jarFile = new JarFile(armoryJar)) {
            Map<String, BufferedImage> ironWeapons = gatherTieredTextures(jarFile, "iron");
            Map<String, BufferedImage> goldenWeapons = gatherTieredTextures(jarFile, "golden");
            Map<String, BufferedImage> diamondWeapons = gatherTieredTextures(jarFile, "diamond");
            Map<String, BufferedImage> headMasks = computeHeadMasks(ironWeapons, diamondWeapons);

            if (headMasks.isEmpty()) {
                AutomaticArmory.LOGGER.warn("No weapon head masks could be computed from dixtas_armory - check its texture paths.");
                return generated;
            }

            // Which tier's own (untinted) texture + model to build each material's variant from. Chosen
            // per-ingot below via classifyBaseTier, so e.g. a pale, colorful ingot borrows golden's warm
            // base look while a dull gray one borrows iron's - rather than always flattening every material
            // onto the diamond template regardless of how well that actually matches its color.
            Map<String, Map<String, BufferedImage>> weaponsByBaseTier = Map.of(
                    "golden", goldenWeapons,
                    "diamond", diamondWeapons,
                    "iron", ironWeapons
            );

            Map<String, List<String>> textureKeysByCanonical = headMasks.keySet().stream()
                    .collect(Collectors.groupingBy(TextureUtils::canonicalWeaponName));

            for (Map.Entry<String, BufferedImage> ingotEntry : ingotTextures.entrySet()) {
                generateVariantsForIngot(ingotEntry.getKey(), ingotEntry.getValue(), weaponsByBaseTier,
                        diamondWeapons, ironWeapons, textureKeysByCanonical, headMasks, jarFile, generated, langEntries);
            }
        } catch (IOException e) {
            AutomaticArmory.LOGGER.error("Failed to open dixtas_armory jar for variant generation: {}", e.getMessage());
        }

        if (!langEntries.isEmpty()) {
            IOUtils.writeLangEntries(langEntries);
        }

        return generated;
    }

    /**
     * One ingot's worth of {@link #generateVariants}: builds its color ramp, classifies its base tier, then
     * generates+saves every weapon/texture-key variant for it and patches that weapon's models. Split out of
     * {@link #generateVariants} purely to keep the outer per-ingot loop readable, mirroring how {@link
     * com.ekilord.automatic_armory.common.armor.AutomaticArmoryArmorItems#generateAndRegisterOneSet} is
     * factored out of its own outer loop.
     */
    private static void generateVariantsForIngot(String ingotId, BufferedImage ingotTexture,
            Map<String, Map<String, BufferedImage>> weaponsByBaseTier, Map<String, BufferedImage> diamondWeapons,
            Map<String, BufferedImage> ironWeapons, Map<String, List<String>> textureKeysByCanonical,
            Map<String, BufferedImage> headMasks, JarFile jarFile,
            List<GeneratedVariant> generated, Map<String, String> langEntries) {
        if (ingotTexture == null) return;

        List<Color> ingotColors = getColorsInImage(ingotTexture);
        if (ingotColors.isEmpty()) return;

        Color dominant = getDominantColor(ingotColors);
        String materialSlug = materialFromIngotId(ingotId);
        WeaponTextureAdjustment weaponAdjustment = IngotsConfig.getOverride(ingotId).weapon;

        String baseTier = classifyBaseTier(dominant);
        Map<String, BufferedImage> baseWeapons = weaponsByBaseTier.get(baseTier);
        AutomaticArmory.LOGGER.debug("Ingot {} (dominant color {}) -> using {} as base template.",
                ingotId, dominant, baseTier);

        List<RampStop> ramp = buildColorRamp(ingotColors, AutomaticArmoryConfig.getColorRampStops());
        if (ramp.isEmpty()) return;

        // Discovered straight from headMasks (see gatherTieredTextures), not a hardcoded list - whatever
        // logical weapons dixtas_armory actually ships with iron+diamond textures show up here automatically.
        for (String canonicalWeapon : textureKeysByCanonical.keySet()) {
            List<String> textureKeys = textureKeysByCanonical.get(canonicalWeapon);

            for (String textureKey : textureKeys) {
                BufferedImage mask = headMasks.get(textureKey);
                // Fall back to diamond, then iron, if the chosen base tier happens not to ship this
                // particular weapon/suffix combination (tiers aren't always perfectly symmetric).
                BufferedImage baseWeapon = baseWeapons.get(textureKey);
                if (baseWeapon == null) baseWeapon = diamondWeapons.get(textureKey);
                if (baseWeapon == null) baseWeapon = ironWeapons.get(textureKey);
                if (mask == null || baseWeapon == null) continue;

                BufferedImage tintedMask = colorShift(mask, ramp, weaponAdjustment.brightnessMultiplier);
                BufferedImage tintedBase = tintWholeImage(baseWeapon, ramp, AutomaticArmoryConfig.getWeaponHandleTintStrength(), weaponAdjustment.brightnessMultiplier);
                BufferedImage merged = mergeBack(tintedBase, tintedMask);
                BufferedImage finalTexture = adjustSaturationAndVibrance(merged, weaponAdjustment.saturationMultiplier, weaponAdjustment.vibranceAmount);

                String outputTextureName = materialSlug + "_" + textureKey;
                IOUtils.saveGeneratedTexture(outputTextureName, finalTexture);
            }

            copyAndPatchModels(jarFile, canonicalWeapon, materialSlug, baseTier);

            String registryName = materialSlug + "_" + canonicalWeapon;
            generated.add(new GeneratedVariant(canonicalWeapon, ingotId, registryName));
            langEntries.put("item." + IOUtils.DIXTAS_ARMORY_NAMESPACE + "." + registryName,
                    prettify(materialSlug) + " " + prettify(canonicalWeapon));
        }
    }

    /**
     * Copies and patches every model file that exists for a weapon's {@code baseTier} reference - the bare
     * {@code <baseTier>_<weapon>.json} (the actual model the game looks up for the registered item; for
     * split-texture weapons this is a {@code forge:separate_transforms} dispatcher referencing the other
     * two), plus whatever other suffixed variants exist (e.g. {@code _gui}, {@code _handheld}, and, for
     * throwing-type weapons, {@code _empty}/{@code _throwing}). Rather than guessing at a fixed list of
     * suffixes - which previously missed the throwing-weapon variants entirely, leaving their thrown/empty
     * models unresolvable in-game - this scans the jar's actual model directory for anything starting with
     * {@code <baseTier>_<weapon>} and ending in {@code .json}, the same discovery approach {@link
     * #gatherTieredTextures} uses for textures. A blanket substring replace of {@code <baseTier>_<weapon>}
     * -> {@code <material>_<weapon>} then patches whichever forms actually exist without needing to
     * distinguish dispatcher-style from simple models: it leaves shared, non-tier-specific parent
     * references (e.g. a shared handheld geometry template) untouched, since those don't contain the old
     * texture-key substring. {@code baseTier} matches whichever tier's texture {@link #generateVariants}
     * chose for this material (see {@link #classifyBaseTier}), so the model and texture this variant ends
     * up using are consistent.
     */
    private static void copyAndPatchModels(JarFile jarFile, String canonicalWeapon, String materialSlug, String baseTier) {
        String oldBase = baseTier + "_" + canonicalWeapon;
        String newBase = materialSlug + "_" + canonicalWeapon;
        String modelDir = "assets/" + IOUtils.DIXTAS_ARMORY_NAMESPACE + "/models/item/";
        String prefix = modelDir + oldBase;

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(prefix) || !name.endsWith(".json")) continue;

            String suffix = name.substring(prefix.length(), name.length() - ".json".length());

            try (InputStream modelStream = jarFile.getInputStream(entry)) {
                String modelJson = new String(modelStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                String patched = modelJson.replace(oldBase, newBase);
                IOUtils.saveGeneratedModel(newBase + suffix, patched);
            } catch (IOException e) {
                AutomaticArmory.LOGGER.error("Failed to copy/patch model for {}{}: {}", newBase, suffix, e.getMessage());
            }
        }
    }

    /**
     * Picks which existing dixtas_armory tier's texture/model best matches an ingot's dominant color, so
     * generated variants borrow a base look that actually fits the material instead of always flattening
     * onto diamond:
     * <ul>
     *     <li>low saturation (grayish/silverish, regardless of brightness) -> {@code iron}</li>
     *     <li>colorful and bright -> {@code golden}</li>
     *     <li>colorful and dark -> {@code diamond}</li>
     * </ul>
     * Both thresholds are configurable (see {@link AutomaticArmoryConfig#getBaseTierGrayscaleSaturationThreshold}/
     * {@link AutomaticArmoryConfig#getBaseTierBrightVsDarkThreshold}) - tuned by eye by default against a
     * handful of real ingot textures (iron, gold, copper-ish, silver-ish materials).
     */
    private static String classifyBaseTier(Color dominant) {
        float[] hsb = Color.RGBtoHSB(dominant.getRed(), dominant.getGreen(), dominant.getBlue(), null);
        float saturation = hsb[1];
        float brightness = hsb[2];

        if (saturation < AutomaticArmoryConfig.getBaseTierGrayscaleSaturationThreshold()) {
            return "iron";
        }

        return brightness >= AutomaticArmoryConfig.getBaseTierBrightVsDarkThreshold() ? "golden" : "diamond";
    }

    /**
     * Recolors one armor icon/on-body texture from its two bundled, pre-authored templates instead of a
     * single raw reference image with a runtime leather heuristic:
     * <ul>
     *     <li>{@code recolorBase} - only the pixels that should be tinted toward the ingot, already
     *     converted to neutral (percentile-equalized) grayscale luminance offline by the mask-authoring
     *     tool, fully transparent everywhere else.</li>
     *     <li>{@code fixed} - only the pixels that should never be recolored (straps, cloth, rivets),
     *     original color untouched, fully transparent everywhere else.</li>
     * </ul>
     * Both templates come from {@link IOUtils#getArmorTemplate} and are always the same dimensions (they're
     * generated together, from the same source image, by the same offline tool - see the {@code
     * armor_mask_tool.py} script and its {@code review/*.json} files in the mod's dev tooling). This
     * replaces the old {@code generateArmorIcon}/{@code looksLikeLeather}/{@code percentileRank} runtime
     * pipeline entirely: the leather-vs-metal split and the percentile luminance stretch were both baked in
     * ahead of time by a human reviewing every texture, rather than guessed at via a hue/saturation/
     * brightness heuristic every time an ingot generates.
     *
     * @param brightness shifts which point along the ingot's own light-to-dark ramp gets sampled for
     *                   each pixel, rather than lightening/darkening the finished color directly: it scales
     *                   {@code recolorBase}'s baked luminance before handing it to {@link #sampleRamp}, so
     *                   {@code 1.0} samples the ramp unchanged, below {@code 1.0} pulls every pixel toward
     *                   the ramp's darkest stop, and above {@code 1.0} pulls it toward the lightest stop.
     *                   Both ends are real colors already present in the ingot's own palette (see {@link
     *                   #buildColorRamp}), not synthetic black/white, so hue and saturation stay natural
     *                   instead of washing out - unlike scaling the output RGB directly, which desaturates
     *                   toward gray/white and can't brighten a near-zero channel at all (anything times
     *                   zero is still zero).
     * @param saturation scales the HSB saturation of every opaque tinted pixel after ramp sampling: {@code
     *                   1.0} leaves it unchanged, {@code 0.0} desaturates fully to grayscale, above {@code
     *                   1.0} pushes toward more vivid colors (clamped at fully saturated). Applied only to
     *                   the recolored pixels, not the untouched {@code fixed} template pixels composited
     *                   back in afterward.
     * @param vibrance   nudges the same pixels' saturation by an amount weighted toward already-muted pixels
     *                   rather than scaling uniformly - see {@link #adjustSaturationAndVibrance}. {@code 0.0}
     *                   leaves it unchanged.
     */
    public static BufferedImage recolorFromBakedMask(BufferedImage recolorBase, BufferedImage fixed, List<Color> ingotColors, float brightness, float saturation, float vibrance) {
        float[] maskRange = luminanceRange(recolorBase);
        List<RampStop> ramp = buildColorRamp(ingotColors, AutomaticArmoryConfig.getColorRampStops(), maskRange[0], maskRange[1]);

        // How strongly a recolorable pixel leans toward the ingot's ramp (see
        // AutomaticArmoryConfig#getArmorIconTintStrength) - unlike weapon heads (a masked region recolored
        // at full strength) or handles (a gentle harmonizing nudge), a full armor icon has no other
        // invariant region to preserve, so this defaults high, close to a full replace, while still leaving
        // a hint of the baked recolor-base's own shading via <1.0. "keep" pixels (the bundled *_fixed.png
        // template) are composited back in separately below and never touch this strength at all.
        BufferedImage tinted = ramp.isEmpty()
                ? new BufferedImage(recolorBase.getWidth(), recolorBase.getHeight(), BufferedImage.TYPE_INT_ARGB)
                // recolorBase is already grayscale (R == G == B, baked by the mask tool), so any channel is
                // directly the pre-stretched luminance to sample the ramp at. sampleRamp clamps out-of-
                // [0,255] input to the ramp's nearest end stop on its own, so scaling by brightness here
                // needs no extra clamping even when it pushes the value past either bound.
                : blendTowardRamp(recolorBase, ramp, AutomaticArmoryConfig.getArmorIconTintStrength(), c -> c.getRed() * brightness);

        return overlayWhereOpaque(adjustSaturationAndVibrance(tinted, saturation, vibrance), fixed);
    }

    /**
     * Adjusts the HSB saturation of every opaque pixel in {@code source} two ways at once, leaving hue and
     * brightness untouched:
     * <ul>
     *     <li>{@code saturationMultiplier} scales every pixel's saturation uniformly, regardless of how
     *     saturated it already is ({@code 1.0} = unchanged, {@code 0.0} = grayscale).</li>
     *     <li>{@code vibranceAmount} then nudges the result toward (positive) or away from (negative) full
     *     saturation, weighted by each pixel's remaining headroom ({@code 1 - saturation}) - an already-vivid
     *     pixel barely moves, while a duller one shifts the most. {@code 0.0} = unchanged.</li>
     * </ul>
     * A no-op fast path for the {@code 1.0}/{@code 0.0} identity pair avoids the per-pixel HSB round-trip
     * for the common case where neither knob is in use.
     */
    private static BufferedImage adjustSaturationAndVibrance(BufferedImage source, float saturationMultiplier, float vibranceAmount) {
        if (saturationMultiplier == 1.0f && vibranceAmount == 0.0f) {
            return source;
        }

        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        float[] hsb = new float[3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color original = new Color(source.getRGB(x, y), true);
                if (original.getAlpha() == 0) continue;

                Color.RGBtoHSB(original.getRed(), original.getGreen(), original.getBlue(), hsb);
                float saturation = clamp01(hsb[1] * saturationMultiplier);
                saturation = clamp01(saturation + vibranceAmount * (1f - saturation));
                int rgb = Color.HSBtoRGB(hsb[0], saturation, hsb[2]);

                result.setRGB(x, y, (original.getAlpha() << 24) | (rgb & 0x00FFFFFF));
            }
        }

        return result;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static String materialFromIngotId(String ingotId) {
        String path = ingotId.contains(":") ? ingotId.substring(ingotId.indexOf(':') + 1) : ingotId;
        if (path.endsWith("_ingot")) {
            path = path.substring(0, path.length() - "_ingot".length());
        }
        return path;
    }

    public static String prettify(String snakeCase) {
        String[] words = snakeCase.split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }

        return result.toString();
    }

    /**
     * Computes, for every texture key present in both tiers, a grayscale mask of the pixels that differ
     * between the iron and diamond textures. This is purely structural (which pixels make up the
     * recolorable "head" of the weapon) - it's intentionally always derived from iron vs. diamond
     * regardless of which tier {@link #generateVariants} ends up using as a given material's base texture,
     * since every tier shares the same underlying geometry and only the head actually changes per tier.
     */
    private static Map<String, BufferedImage> computeHeadMasks(Map<String, BufferedImage> ironWeapons, Map<String, BufferedImage> diamondWeapons) {
        Map<String, BufferedImage> masks = new HashMap<>();

        for (Map.Entry<String, BufferedImage> ironEntry : ironWeapons.entrySet()) {
            BufferedImage diamond = diamondWeapons.get(ironEntry.getKey());
            if (diamond == null) continue;

            masks.put(ironEntry.getKey(), grayscaleConversion(calculateDifference(ironEntry.getValue(), diamond)));
        }

        return masks;
    }

    /**
     * Discovers every {@code <tier>_<textureKey>.png} entry actually present under {@link
     * #TEXTURE_ITEM_DIR} in the jar for the given tier, and loads each one. This is a real directory scan,
     * not a probe against a fixed weapon list: whatever weapons (and whichever {@code _gui}/{@code
     * _handheld} suffix variants) dixtas_armory ships for this tier are picked up as-is, so a mod update
     * that adds, removes, or renames a weapon needs no change here.
     */
    public static Map<String, BufferedImage> gatherTieredTextures(JarFile jarFile, String tier) {
        Map<String, BufferedImage> tieredWeapons = new HashMap<>();
        String prefix = TEXTURE_ITEM_DIR + tier + "_";

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(prefix) || !name.endsWith(".png")) continue;

            String textureKey = name.substring(prefix.length(), name.length() - ".png".length());

            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                tieredWeapons.put(textureKey, ImageIO.read(inputStream));
            } catch (IOException e) {
                AutomaticArmory.LOGGER.error("Failed to load {} texture for: {}", tier, textureKey);
            }
        }

        return tieredWeapons;
    }

    /**
     * Overlays {@code overlay} onto {@code base}, wherever the overlay image has any alpha. Shared by the
     * weapon path ({@link #mergeBack}, compositing a tinted head back onto a tinted base weapon) and the
     * armor path ({@link #recolorFromBakedMask}, compositing the untouched {@code fixed} template back over
     * the tinted result) - both are the same "keep the overlay's pixel if it's opaque, otherwise keep the
     * base's" operation.
     */
    private static BufferedImage overlayWhereOpaque(BufferedImage base, BufferedImage overlay) {
        int width = base.getWidth();
        int height = base.getHeight();

        BufferedImage merged = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        if (width != overlay.getWidth() || height != overlay.getHeight()) {
            AutomaticArmory.LOGGER.error("Error while compositing overlay image: dimension mismatch");
            return merged;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int baseRgba = base.getRGB(x, y);
                int overlayRgba = overlay.getRGB(x, y);

                Color color = new Color(overlayRgba, true);
                if (color.getAlpha() > 0) {
                    merged.setRGB(x, y, overlayRgba);
                } else {
                    merged.setRGB(x, y, baseRgba);
                }
            }
        }

        return merged;
    }

    /**
     * Overlays {@code headImage} onto {@code weaponImage}, wherever the head image has any alpha.
     */
    public static BufferedImage mergeBack(BufferedImage weaponImage, BufferedImage headImage) {
        return overlayWhereOpaque(weaponImage, headImage);
    }

    public static BufferedImage grayscaleConversion(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage grayscaleImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);

                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                int gray = (int) luminance(red, green, blue);

                int alpha = (rgb >> 24) & 0xFF;
                int newRgb = (alpha << 24) | (gray << 16) | (gray << 8) | gray;

                grayscaleImage.setRGB(x, y, newRgb);
            }
        }

        return grayscaleImage;
    }

    private static BufferedImage calculateDifference(BufferedImage image1, BufferedImage image2) {
        int width = image1.getWidth();
        int height = image1.getHeight();

        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba1 = image1.getRGB(x, y);
                int rgba2 = image2.getRGB(x, y);

                diff.setRGB(x, y, rgba1 != rgba2 ? rgba2 : 0x00000000);
            }
        }
        return diff;
    }

    public static List<Color> getColorsInImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        List<Color> colors = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getAlpha() > 0) {
                    colors.add(color);
                }
            }
        }

        return colors;
    }

    /**
     * A representative color for a band of the ingot's tonal range - see {@link #buildColorRamp}.
     */
    private record RampStop(float luminance, Color color) {
    }

    /**
     * Picked as a single overall stand-in color for the ingot - used only for {@link #classifyBaseTier}
     * (and logging), never for the actual recoloring anymore (see {@link #colorShift}, which uses the
     * ingot's full palette instead of one flat color). A straight average across every opaque pixel is more
     * representative of the ingot's overall look than picking whichever single exact RGB value happens to
     * repeat most often, which tends to overweight flat outline/border colors in pixel art over the shaded
     * body of the ingot.
     */
    public static Color getDominantColor(List<Color> colors) {
        long r = 0, g = 0, b = 0;
        for (Color color : colors) {
            r += color.getRed();
            g += color.getGreen();
            b += color.getBlue();
        }

        int size = colors.size();
        return new Color((int) (r / size), (int) (g / size), (int) (b / size));
    }

    /**
     * Builds a small light-to-dark gradient out of an ingot's own colors, instead of collapsing the whole
     * ingot down to one flat target color. Pixels are sorted by luminance and bucketed into up to {@code
     * stopCount} even-<b>count</b> bands (fewer if the ingot doesn't have that many distinct luminance
     * levels), and each band's colors are averaged into one representative stop - except the two end bands,
     * which are overridden below to the true extreme pixel color rather than a diluted band average. The
     * result captures how the ingot's hue actually shifts from shadow to highlight (e.g. a warm gold
     * highlight fading into a deeper brown shadow) rather than flattening everything toward a single midtone
     * color.
     * <p>
     * Equal-count bucketing (rather than bucketing by equal luminance range) is deliberate: it's robust to
     * however unevenly an ingot's pixels are distributed across the luminance spectrum, since every band
     * always represents the same *share* of the ingot regardless of how tightly or loosely its pixels
     * cluster. Bucketing by value instead was tried and reverted - it let whichever luminance cluster
     * happened to hold the bulk of an ingot's "body" pixels dominate whatever band its value range fell
     * into, washing that band's color toward the body average even when it wasn't supposed to represent the
     * body at all, and made results look uniformly brighter/flatter across every ingot, not just the ones
     * with a genuine dilution problem.
     * <p>
     * That dilution problem is real for the two end stops specifically, though: a genuine specular highlight
     * (or a deep shadow/outline) is often only a handful of pixels sitting at the extreme edge of the
     * ingot's own luminance range, in a much larger pool of duller "body" pixels. Equal-count bucketing would
     * force that tiny highlight cluster to share a band with enough neighboring body pixels to fill out its
     * count quota, averaging the true highlight color away into something duller - so after the main
     * count-based pass below, {@link #extremeStop} replaces the first and last stops with the exact
     * (averaged only across pixels tied at that precise extreme) darkest/lightest color actually present, no
     * matter how few pixels support it. Every interior stop is left exactly as the count-based pass computed
     * it.
     *
     * This overload leaves every stop at its natural, ingot-raw luminance position - used by the weapon path
     * ({@link #colorShift}/{@link #tintWholeImage}), which samples a raw, non-equalized grayscale diff and
     * has no separate "target domain" to line the ramp up against.
     */
    private static List<RampStop> buildColorRamp(List<Color> colors, int stopCount) {
        return buildBandedRamp(colors, stopCount);
    }

    /**
     * Same banding as the natural-position overload above, but with stop positions afterward remapped to be
     * evenly spaced across {@code [domainMin, domainMax]} instead of left at their natural ingot-raw
     * positions - used by the armor path (see {@link #recolorFromBakedMask}), passing the baked {@code
     * recolorBase} mask's own true observed luminance range as the domain.
     * <p>
     * That domain must come from the mask's actual pixels, not a hardcoded {@code [0, 255]} assumption: a
     * mask whose own luminance never reaches down near 0 (e.g. a template baked with a true range of
     * {@code [52, 255]}, which is common - baked masks are "percentile-equalized" in the sense of spreading
     * across most of the range, not literally touching both ends) would, under a fixed 0-255 domain, never
     * produce a raw value low enough to actually select the ramp's darkest/richest stop - the mask's own
     * darkest pixel would land almost exactly on stop <i>two</i> instead of stop one, silently wasting
     * whatever real richness {@link #extremeStop} just went to the trouble of preserving there. Matching the
     * domain to the mask's real range guarantees the mask's own darkest and lightest pixels actually land on
     * the ramp's darkest and lightest stops.
     */
    private static List<RampStop> buildColorRamp(List<Color> colors, int stopCount, float domainMin, float domainMax) {
        List<RampStop> ramp = buildBandedRamp(colors, stopCount);

        int n = ramp.size();
        List<RampStop> normalized = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            float position = n == 1 ? (domainMin + domainMax) / 2f : domainMin + (i * (domainMax - domainMin)) / (n - 1);
            normalized.add(new RampStop(position, ramp.get(i).color()));
        }
        return normalized;
    }

    private static List<RampStop> buildBandedRamp(List<Color> colors, int stopCount) {
        if (colors.isEmpty()) {
            return List.of();
        }

        List<Color> sorted = new ArrayList<>(colors);
        sorted.sort(Comparator.comparingDouble(TextureUtils::luminance));

        int bands = Math.max(1, Math.min(stopCount, sorted.size()));
        List<RampStop> ramp = new ArrayList<>(bands);

        int size = sorted.size();
        for (int band = 0; band < bands; band++) {
            int start = band * size / bands;
            int end = (band + 1) * size / bands;
            if (end <= start) continue;

            long r = 0, g = 0, b = 0;
            double lumSum = 0;
            for (int i = start; i < end; i++) {
                Color c = sorted.get(i);
                r += c.getRed();
                g += c.getGreen();
                b += c.getBlue();
                lumSum += luminance(c);
            }

            int count = end - start;
            ramp.add(new RampStop((float) (lumSum / count), new Color((int) (r / count), (int) (g / count), (int) (b / count))));
        }

        if (ramp.size() > 1) {
            ramp.set(0, extremeStop(sorted, true));
            ramp.set(ramp.size() - 1, extremeStop(sorted, false));
        }

        return ramp;
    }

    /**
     * The true observed {@code [min, max]} luminance range of {@code image}'s opaque pixels - see {@link
     * #buildColorRamp(List, int, float, float)}. Falls back to {@code [0, 255]} if the image has no opaque
     * pixels at all (nothing will be sampled against it anyway in that case).
     */
    private static float[] luminanceRange(BufferedImage image) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x, y), true);
                if (c.getAlpha() == 0) continue;

                float lum = (float) luminance(c);
                min = Math.min(min, lum);
                max = Math.max(max, lum);
            }
        }

        return min <= max ? new float[]{min, max} : new float[]{0f, 255f};
    }

    /**
     * The true darkest ({@code darkest = true}) or lightest ({@code darkest = false}) color actually present
     * in {@code sorted} (a list already sorted ascending by luminance, as {@link #buildColorRamp} produces),
     * averaged only across whichever pixels are tied at that exact extreme luminance value - not diluted by
     * however many other, less-extreme pixels a count-based band would otherwise need to reach its quota.
     * See {@link #buildColorRamp}'s own javadoc for why this matters.
     */
    private static RampStop extremeStop(List<Color> sorted, boolean darkest) {
        double target = darkest ? luminance(sorted.get(0)) : luminance(sorted.get(sorted.size() - 1));

        long r = 0, g = 0, b = 0;
        int count = 0;
        if (darkest) {
            for (Color c : sorted) {
                if (luminance(c) != target) break;
                r += c.getRed();
                g += c.getGreen();
                b += c.getBlue();
                count++;
            }
        } else {
            for (int i = sorted.size() - 1; i >= 0; i--) {
                Color c = sorted.get(i);
                if (luminance(c) != target) break;
                r += c.getRed();
                g += c.getGreen();
                b += c.getBlue();
                count++;
            }
        }

        return new RampStop((float) target, new Color((int) (r / count), (int) (g / count), (int) (b / count)));
    }

    private static double luminance(Color color) {
        return luminance(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static double luminance(int red, int green, int blue) {
        return 0.299 * red + 0.587 * green + 0.114 * blue;
    }

    /**
     * Linearly interpolates a color for the given luminance from a ramp built by {@link #buildColorRamp},
     * clamping to the nearest end stop if {@code luminance} falls outside the ramp's range.
     */
    private static Color sampleRamp(List<RampStop> ramp, float luminance) {
        if (ramp.size() == 1) {
            return ramp.get(0).color();
        }

        if (luminance <= ramp.get(0).luminance()) {
            return ramp.get(0).color();
        }
        if (luminance >= ramp.get(ramp.size() - 1).luminance()) {
            return ramp.get(ramp.size() - 1).color();
        }

        for (int i = 0; i < ramp.size() - 1; i++) {
            RampStop lower = ramp.get(i);
            RampStop upper = ramp.get(i + 1);
            if (luminance < lower.luminance() || luminance > upper.luminance()) continue;

            float span = upper.luminance() - lower.luminance();
            float t = span <= 0 ? 0f : (luminance - lower.luminance()) / span;

            int r = Math.round(lower.color().getRed() + (upper.color().getRed() - lower.color().getRed()) * t);
            int g = Math.round(lower.color().getGreen() + (upper.color().getGreen() - lower.color().getGreen()) * t);
            int b = Math.round(lower.color().getBlue() + (upper.color().getBlue() - lower.color().getBlue()) * t);
            return new Color(clamp(r), clamp(g), clamp(b));
        }

        return ramp.get(ramp.size() - 1).color();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /**
     * Blends every opaque pixel of {@code source} toward a color sampled off {@code ramp} at that pixel's
     * luminance (as computed by {@code luminanceFn}), by {@code strength} (0 = untouched, 1 = fully
     * replaced). Shared by every "recolor toward the ingot's ramp" call site in this class - {@link
     * #colorShift} (a full-strength replace of the weapon head mask), {@link #tintWholeImage} (a gentler
     * harmonizing nudge over a whole weapon texture), and {@link #recolorFromBakedMask} (blending a baked
     * grayscale armor template) - which differ only in their luminance source and strength.
     */
    private static BufferedImage blendTowardRamp(BufferedImage source, List<RampStop> ramp, float strength, ToDoubleFunction<Color> luminanceFn) {
        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color original = new Color(source.getRGB(x, y), true);
                if (original.getAlpha() == 0) continue;

                Color target = sampleRamp(ramp, (float) luminanceFn.applyAsDouble(original));

                int r = Math.round(original.getRed() + (target.getRed() - original.getRed()) * strength);
                int g = Math.round(original.getGreen() + (target.getGreen() - original.getGreen()) * strength);
                int b = Math.round(original.getBlue() + (target.getBlue() - original.getBlue()) * strength);

                result.setRGB(x, y, new Color(clamp(r), clamp(g), clamp(b), original.getAlpha()).getRGB());
            }
        }

        return result;
    }

    /**
     * Recolors a grayscale head mask (see {@link #computeHeadMasks}) using a gradient built from the
     * ingot's own palette (see {@link #buildColorRamp}): each mask pixel's luminance is looked up against
     * that gradient rather than being shifted toward one flat target color, so the ingot's own highlight-
     * to-shadow hue variation carries through into the generated weapon head instead of being lost. Always a
     * full-strength replace (unlike {@link #tintWholeImage}'s gentler blend) - the mask has no original
     * material read worth preserving.
     *
     * @param brightness shifts which point along the ramp gets sampled, same mechanism (and same {@code
     *                   1.0} = unchanged convention) as {@link #recolorFromBakedMask}'s own brightness
     *                   parameter - see {@link com.ekilord.automatic_armory.config.WeaponTextureAdjustment}.
     */
    private static BufferedImage colorShift(BufferedImage image, List<RampStop> ramp, float brightness) {
        if (ramp.isEmpty()) {
            return image;
        }

        // The mask is already grayscale (R == G == B, see grayscaleConversion), so any channel is that
        // pixel's luminance/brightness value directly.
        return blendTowardRamp(image, ramp, 1.0f, c -> c.getRed() * brightness);
    }

    /**
     * Gently nudges every opaque pixel of a whole weapon texture toward the ingot's own ramp, at
     * {@code strength} (0 = untouched, 1 = fully replaced). Unlike {@link #colorShift}, which is used only
     * on the head mask and fully replaces those pixels, this is meant for the parts of the sprite the head
     * mask doesn't cover at all - primarily the handle/guard, which dixtas_armory draws identically across
     * every tier and which {@link #mergeBack} would otherwise leave as whatever raw color the chosen base
     * tier's template happens to have, regardless of the actual ingot.
     *
     * @param brightness same convention as {@link #colorShift}'s own brightness parameter.
     */
    private static BufferedImage tintWholeImage(BufferedImage image, List<RampStop> ramp, float strength, float brightness) {
        if (ramp.isEmpty() || strength <= 0f) {
            return image;
        }

        return blendTowardRamp(image, ramp, strength, c -> luminance(c) * brightness);
    }
}
