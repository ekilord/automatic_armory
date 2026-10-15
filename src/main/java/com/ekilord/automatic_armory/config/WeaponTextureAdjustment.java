package com.ekilord.automatic_armory.config;

/**
 * Per-ingot texture tuning for generated {@code dixtas_armory} weapon variants, nested under {@link
 * IngotTextureOverride#weapon}. Unlike {@link TextureAdjustment} (armor), there's no item/worn split here -
 * a weapon variant only ever has the one texture (icon and in-hand render share it), so one set of knobs
 * covers the whole thing.
 */
public class WeaponTextureAdjustment {
    /**
     * Shifts which point along the ingot's own light-to-dark color ramp gets sampled when recoloring this
     * ingot's weapon variants (both the recolored head mask and the gentler whole-texture handle/guard
     * tint) - same mechanism as {@link TextureAdjustment#itemBrightnessMultiplier}. {@code 1.0} leaves
     * sampling unchanged.
     */
    public float brightnessMultiplier = 1.0f;

    /**
     * Scales the HSB saturation of this ingot's generated weapon texture after ramp-based recoloring:
     * {@code 1.0} leaves it unchanged, {@code 0.0} desaturates fully to grayscale, above {@code 1.0} pushes
     * toward more vivid colors (clamped at fully saturated).
     */
    public float saturationMultiplier = 1.0f;

    /**
     * Nudges the HSB saturation of this ingot's generated weapon texture by an amount weighted toward
     * already-muted pixels - see {@link TextureAdjustment#itemVibranceAmount}. {@code 0.0} leaves it
     * unchanged.
     */
    public float vibranceAmount = 0.0f;
}
