package com.ekilord.automatic_armory.config;

/**
 * Per-armor-set texture tuning, nested under {@link ArmorSetDefinition#texture} rather than sitting as flat
 * sibling fields on {@code ArmorSetDefinition} itself, so a set's icon/on-body recolor knobs read as one
 * grouped {@code "texture": {...}} block in {@code armor_sets.json}.
 * <p>
 * Every multiplier here is combined <b>multiplicatively</b> with its {@link AutomaticArmoryConfig} "overall"
 * counterpart (e.g. {@link AutomaticArmoryConfig#getOverallItemBrightnessMultiplier()}) and its {@link
 * IngotTextureOverride#armor} counterpart from {@code ingots.json} (per-ingot, see {@link IngotsConfig}),
 * which both apply on top of every set at once - this class only ever holds one set's own contribution. The
 * vibrance fields below are combined <b>additively</b> instead, since they're a delta rather than a scale
 * factor. Mapped directly from JSON via Gson, same as {@link ArmorSetDefinition} - fields are deliberately
 * plain, no builders/records.
 */
public class TextureAdjustment {
    /**
     * Shifts which point along the ingot's own light-to-dark color ramp gets sampled when recoloring this
     * set's inventory icon textures - see {@link
     * com.ekilord.automatic_armory.common.util.TextureUtils#recolorFromBakedMask}. {@code 1.0} leaves
     * sampling unchanged; below {@code 1.0} pulls every pixel toward the ingot's own darkest ramp stop,
     * above {@code 1.0} toward its lightest - both real colors already present in the ingot's palette, not
     * synthetic black/white, so hue and saturation stay natural.
     */
    public float itemBrightnessMultiplier = 1.0f;

    /**
     * Same as {@link #itemBrightnessMultiplier}, but for this set's single shared on-body (worn) texture
     * instead of the inventory icons - the two are recolored from separate baked-mask templates and so are
     * tuned independently.
     */
    public float wornBrightnessMultiplier = 1.0f;

    /**
     * Scales the HSB saturation of this set's inventory icon textures after ramp-based recoloring: {@code
     * 1.0} leaves it unchanged, {@code 0.0} desaturates fully to grayscale, values above {@code 1.0} push
     * toward more vivid colors (clamped at fully saturated).
     */
    public float itemSaturationMultiplier = 1.0f;

    /** Same as {@link #itemSaturationMultiplier}, but for this set's shared on-body (worn) texture. */
    public float wornSaturationMultiplier = 1.0f;

    /**
     * Nudges the HSB saturation of this set's inventory icon textures by an amount weighted toward
     * already-muted pixels rather than scaling every pixel uniformly like {@link #itemSaturationMultiplier}
     * does: {@code 0.0} leaves it unchanged, positive values boost duller pixels the most (a vivid pixel
     * near full saturation barely moves), negative values pull duller pixels toward gray the most.
     */
    public float itemVibranceAmount = 0.0f;

    /** Same as {@link #itemVibranceAmount}, but for this set's shared on-body (worn) texture. */
    public float wornVibranceAmount = 0.0f;
}
