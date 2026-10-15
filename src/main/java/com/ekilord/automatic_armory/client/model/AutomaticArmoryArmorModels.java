package com.ekilord.automatic_armory.client.model;

import com.ekilord.automatic_armory.config.ArmorSetDefinition;
import com.ekilord.automatic_armory.config.ArmorSetsConfig;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Builds the on-body {@link HumanoidModel} for a generated armor piece directly from the ported geometry in
 * {@link AutomaticArmoryArmorGeometry} - the replacement for {@code AutomaticArmoryArmorItem}'s old
 * reflection-based delegation into an installed copy of {@code medieval_armory}.
 * <p>
 * Which geometry a given (set, piece) resolves to is entirely config-driven now (see {@link
 * ArmorSetDefinition#onBodyGeometryPrefix}/{@link ArmorSetDefinition#onBodyGeometryFallbackPrefix}), rather
 * than hardcoded in a switch here - {@link #resolveGeometryKey} just tries the primary prefix, then the
 * fallback prefix, against {@link AutomaticArmoryArmorGeometry#MODELS}. This is what reproduces, purely from
 * data, the two things about the original {@code medieval_armory} models worth calling out (both confirmed
 * by decompiling that mod, not assumed): {@code masterwork_light}/{@code masterwork_half_plate} don't have
 * their own on-body models at all - all four pieces just reuse the diamond-tier model wholesale (their
 * fallback prefix equals their primary prefix's diamond-tier equivalent). {@code masterwork_full_plate} is
 * the odd one out: its helmet has a genuine dedicated model (primary prefix resolves), but its chestplate/
 * leggings/boots still fall back to heavy_diamond's (primary prefix has no entry for those pieces, so the
 * fallback prefix is used instead).
 * <p>
 * {@link #SLOT_NAMES} is the other generic piece: every set uses the exact same named-part scheme per
 * {@link ArmorItem.Type} (confirmed identical across all eleven ported sets), so which geometry part goes
 * into which {@link HumanoidModel} slot never needs to vary per set - only which geometry to bake does.
 */
public final class AutomaticArmoryArmorModels {

    private AutomaticArmoryArmorModels() {
    }

    /**
     * Maps each {@link ArmorItem.Type}'s geometry part names (as they appear in {@link
     * AutomaticArmoryArmorGeometry}) onto the {@link HumanoidModel} slot they fill. Every set's ported
     * geometry uses these same part names for a given piece type - e.g. every helmet's single part is
     * named {@code "bb_main"}, every chestplate's three parts are {@code "Torso"}/{@code "ArmsL"}/
     * {@code "ArmsR"}, and so on.
     */
    private static final Map<ArmorItem.Type, Map<String, String>> SLOT_NAMES = Map.of(
            ArmorItem.Type.HELMET, Map.of("bb_main", "head"),
            ArmorItem.Type.CHESTPLATE, Map.of("Torso", "body", "ArmsL", "left_arm", "ArmsR", "right_arm"),
            ArmorItem.Type.LEGGINGS, Map.of("LeggingsL", "left_leg", "LeggingsR", "right_leg", "body", "body"),
            ArmorItem.Type.BOOTS, Map.of("BootsL", "left_leg", "BootsR", "right_leg")
    );

    private static final List<String> ALL_HUMANOID_SLOTS =
            List.of("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg");

    /**
     * @return the on-body model for this (set, piece), or {@code null} if this set has no ported geometry
     * at all for this piece (e.g. {@code copper_scale} has no boots piece in the original mod either) -
     * callers should fall back to leaving the vanilla default model untouched in that case.
     * <p>
     * <b>Follow-up (not yet done):</b> this is called once per equipped armor piece per rendered entity per
     * frame, and rebuilds a fresh {@link HumanoidModel} plus a full set of empty placeholder {@link
     * ModelPart}s every time, even though the result is 100% static per {@code (setKey, type)}. Caching the
     * built model per key would remove that repeated per-frame allocation; not done here since it wasn't
     * part of this cleanup pass's scope.
     */
    public static HumanoidModel<LivingEntity> build(String setKey, ArmorItem.Type type, LivingEntity living) {
        ArmorSetDefinition def = ArmorSetsConfig.getSets().get(setKey);
        if (def == null) return null;

        String geometryKey = resolveGeometryKey(def.onBodyGeometryPrefix, type);
        if (geometryKey == null) {
            geometryKey = resolveGeometryKey(def.onBodyGeometryFallbackPrefix, type);
        }
        if (geometryKey == null) return null;

        Map<String, ModelPart> named = PortedArmorModel.bakeNamedParts(geometryKey);
        if (named.isEmpty()) return null;

        Map<String, String> slotNames = SLOT_NAMES.get(type);
        Map<String, ModelPart> slots = new java.util.HashMap<>();
        for (String slot : ALL_HUMANOID_SLOTS) {
            slots.put(slot, new ModelPart(Collections.emptyList(), Collections.emptyMap()));
        }
        for (Map.Entry<String, String> mapping : slotNames.entrySet()) {
            ModelPart part = named.get(mapping.getKey());
            if (part != null) {
                slots.put(mapping.getValue(), part);
            }
        }

        return new HumanoidModel<>(new ModelPart(Collections.emptyList(), slots));
    }

    /**
     * @return {@code prefix + "_" + pieceName(type)} if that key actually exists in {@link
     * AutomaticArmoryArmorGeometry#MODELS}, otherwise {@code null} (including when {@code prefix} itself is
     * {@code null}) - lets {@link #build} try a primary prefix, then a fallback, without needing to
     * special-case any particular set or piece.
     */
    private static String resolveGeometryKey(String prefix, ArmorItem.Type type) {
        if (prefix == null) return null;

        String key = prefix + "_" + pieceName(type);
        return AutomaticArmoryArmorGeometry.MODELS.containsKey(key) ? key : null;
    }

    private static String pieceName(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            case BOOTS -> "boots";
        };
    }
}
