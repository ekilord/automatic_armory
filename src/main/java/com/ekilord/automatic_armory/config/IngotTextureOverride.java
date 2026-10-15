package com.ekilord.automatic_armory.config;

/**
 * One ingot's texture tuning override, keyed by ingot item id (e.g. {@code "metal_works:bronze_ingot"}) in
 * {@link IngotsConfig}. Armor and weapon generation are tuned completely separately - a material can read
 * brighter on its generated armor icons without changing its weapon variants at all, or vice versa - because
 * they're recolored from different templates by different pipelines (see {@link
 * com.ekilord.automatic_armory.common.util.TextureUtils#recolorFromBakedMask} vs. {@link
 * com.ekilord.automatic_armory.common.util.TextureUtils#generateVariants}).
 */
public class IngotTextureOverride {
    public TextureAdjustment armor = new TextureAdjustment();
    public WeaponTextureAdjustment weapon = new WeaponTextureAdjustment();
}
