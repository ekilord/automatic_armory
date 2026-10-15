package com.ekilord.automatic_armory.config;

import java.util.Map;

/**
 * One user-configurable armor set template - a style (matching one of AutomaticArmory's own ported icon
 * sets, e.g. {@code light_iron}, {@code gambison}) plus the stats AutomaticArmory gives every generated armor
 * piece built from it. Every ingot generates one full helmet/chestplate/leggings/boots set per {@link
 * ArmorSetDefinition} in {@link ArmorSetsConfig#getSets()} - nothing here is auto-detected from the
 * template's own materials, per the user's request that defense/toughness/durability/icon all be
 * plainly editable in AutomaticArmory's own config rather than copied from anywhere.
 * <p>
 * Mapped directly from JSON via Gson - fields are deliberately plain (no builders/records) so hand-editing
 * the config file's structure matches this class one-to-one.
 */
public class ArmorSetDefinition {
    /**
     * Which of AutomaticArmory's bundled icon sets (ported from {@code medieval_armory}) to use as the
     * recolor template, e.g. {@code light_iron} -> {@code assets/automatic_armory/textures/armor_templates/
     * item/light_iron_<piece>.png}.
     */
    public String iconStyle;

    /**
     * The word inserted between the material and piece name in generated items' display names, e.g.
     * {@code "Mail"} for the {@code mail} set (whose {@code iconStyle} is {@code iron_mail}) -> "Rose Gold Mail Helmet" (not "Rose Gold Iron Mail
     * Helmet" - the set's own key/{@link #iconStyle} is about which template file to read, not how the item
     * should read in a tooltip, so the two are kept separate rather than deriving one from the other).
     */
    public String displayName;

    /**
     * Which vanilla material's on-body layer textures ({@code iron}, {@code gold}, {@code diamond},
     * {@code netherite}, {@code chainmail}, or {@code leather}) this set's generated armor should use when
     * actually worn. Kept as-is from the original design for compatibility - {@link
     * com.ekilord.automatic_armory.common.armor.AutomaticArmoryArmorMaterial#getName()} is effectively
     * vestigial now that on-body texture resolution goes through {@code getArmorTexture} instead.
     */
    public String wornBodyTexture;

    public int durabilityMultiplier;
    public int enchantmentValue;
    public float toughness;
    public float knockbackResistance;

    /** e.g. {@code "minecraft:iron_ingot"}. */
    public String repairItem;

    /** e.g. {@code "minecraft:item.armor.equip_iron"}. */
    public String soundEvent;

    /** Keys: {@code helmet}, {@code chestplate}, {@code leggings}, {@code boots}. */
    public Map<String, Integer> defense;

    /**
     * The bundled on-body template filename prefix (under {@code assets/automatic_armory/textures/
     * armor_templates/entities/}) used to recolor this set's single shared worn texture, e.g. {@code
     * "light_iron_armor"} -> {@code light_iron_armor_recolor_base.png} / {@code light_iron_armor_fixed.png}.
     * {@code null} or blank means this set has no on-body texture at all (falls back to vanilla's default
     * appearance when worn) - see {@link
     * com.ekilord.automatic_armory.common.armor.AutomaticArmoryArmorItems#generateAndRegisterOneSet}.
     * <p>
     * Deliberately a separate field from {@link #onBodyGeometryPrefix} rather than reusing one prefix for
     * both: the two only coincide for most sets by convention, not by rule - e.g. the {@code scale} set's
     * texture prefix is {@code copper_scale} but its geometry prefix is {@code copper_scale_armor}, and
     * {@code mail}'s texture prefix is {@code iron_mail_armor} while its geometry prefix is {@code
     * mail_iron_armor} (word order swapped) - confirmed by decompiling the original mod, not assumed.
     */
    public String onBodyTextureFile;

    /**
     * The primary {@link com.ekilord.automatic_armory.client.model.AutomaticArmoryArmorGeometry#MODELS} key
     * prefix (e.g. {@code "light_iron_armor"}, combined with a piece suffix like {@code "_helmet"} by {@link
     * com.ekilord.automatic_armory.client.model.AutomaticArmoryArmorModels}) used to resolve this set's
     * on-body model geometry. If {@code MODELS} has no entry for {@code <prefix>_<piece>}, {@link
     * #onBodyGeometryFallbackPrefix} is tried instead before giving up and falling back to vanilla's default
     * model for that piece - see {@link
     * com.ekilord.automatic_armory.client.model.AutomaticArmoryArmorModels#build}. Equal to {@link
     * #onBodyGeometryFallbackPrefix} for most sets (no real fallback needed); they differ for the
     * {@code masterwork_*} sets, whose ported geometry is mostly borrowed wholesale from the diamond tier.
     */
    public String onBodyGeometryPrefix;

    /** @see #onBodyGeometryPrefix */
    public String onBodyGeometryFallbackPrefix;

    /**
     * This set's own icon/on-body brightness and saturation tuning - see {@link TextureAdjustment}. Combined
     * multiplicatively with {@link com.ekilord.automatic_armory.config.AutomaticArmoryConfig}'s global
     * "overall" brightness/saturation multipliers, which apply on top of every set at once. JSON entries
     * that omit this field also get an all-{@code 1.0} {@link TextureAdjustment} (Gson leaves this field
     * constructor-initialized rather than zeroing it, since {@code ArmorSetDefinition} has a no-arg
     * constructor).
     */
    public TextureAdjustment texture = new TextureAdjustment();

    public int defenseFor(String piece) {
        Integer value = defense == null ? null : defense.get(piece);
        return value == null ? 0 : value;
    }
}
