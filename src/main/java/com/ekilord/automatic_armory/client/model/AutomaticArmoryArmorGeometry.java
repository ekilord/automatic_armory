package com.ekilord.automatic_armory.client.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw cube geometry for every on-body armor model ported from {@code medieval_armory}, extracted
 * directly from that mod's decompiled {@code createBodyLayer()} methods (same {@code texOffs}/
 * {@code addBox}/{@code CubeDeformation}/{@code PartPose} values, just as data instead of 36
 * separate classes - see {@link PortedArmorModel} for what builds a {@code LayerDefinition}/
 * {@code ModelPart} tree out of this). Keyed by {@code "<modelPrefix>_<piece>"}, e.g.
 * {@code "light_iron_armor_helmet"} - see {@link AutomaticArmoryArmorModels} for how a configured
 * armor set resolves to one of these keys.
 * <p>
 * {@code setupAnim}/{@code renderToBuffer} logic from the original per-set model classes is
 * deliberately <b>not</b> reproduced here: this geometry is only ever baked and then handed to
 * vanilla's own {@link net.minecraft.client.model.HumanoidModel}, which already animates/renders
 * its own head/body/arm/leg parts - the original mod's custom animation code only mattered when
 * its own item classes rendered these models directly, which AutomaticArmory never does.
 */
public final class AutomaticArmoryArmorGeometry {

    /** One cuboid: {@code texOffs(u, v).addBox(x, y, z, w, h, d, new CubeDeformation(deform))}. */
    public record Cube(int u, int v, float x, float y, float z, float w, float h, float d, float deform) {
    }

    /** One named {@code PartDefinition}: its cubes plus its {@code PartPose.offset(x, y, z)}. */
    public record Part(String name, float poseX, float poseY, float poseZ, Cube... cubes) {
    }

    /** One model's full geometry: texture dimensions plus every top-level named part. */
    public record Geometry(int textureWidth, int textureHeight, Part... parts) {
    }

    public static final Map<String, Geometry> MODELS = new LinkedHashMap<>();

    private AutomaticArmoryArmorGeometry() {
    }

    // Several ported sets share byte-identical geometry (material tier mostly only changes helmet/
    // chestplate trim, not boots/leggings shape; a handful of unrelated sets happen to reuse the same
    // helmet trim too) - confirmed by a mechanical deep-equality grouping over every MODELS entry, not just
    // by filename similarity. Declared once here and referenced by every key that uses it below, instead of
    // repeating the literal cube data per key.

    private static final Geometry SHARED_HELMET_TRIM = new Geometry(128, 128,
            new Part("bb_main", 0f, 24f, 0f,
                    new Cube(33, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0.75f),
                    new Cube(33, 17, -4f, -8f, -4f, 8f, 8f, 8f, 1f),
                    new Cube(32, 93, 0.5f, -9.25f, -5f, -1f, 2f, 10f, 0.5f)));

    private static final Geometry SHARED_LIGHT_BOOTS = new Geometry(128, 128,
            new Part("BootsR", -2f, 12f, 0f,
                    new Cube(61, 85, -2f, 7f, -2f, 4f, 5f, 4f, 1f),
                    new Cube(61, 95, -2f, 6f, -2f, 4f, 5f, 4f, 0.75f)),
            new Part("BootsL", 2f, 12f, 0f,
                    new Cube(44, 85, -2f, 7f, -2f, 4f, 5f, 4f, 1f),
                    new Cube(44, 95, -2f, 6f, -2f, 4f, 5f, 4f, 0.75f)));

    private static final Geometry SHARED_LIGHT_LEGGINGS = new Geometry(128, 128,
            new Part("LeggingsR", -2f, 12f, 0f,
                    new Cube(51, 68, -2f, 0f, -2f, 4f, 12f, 4f, 0.5f),
                    new Cube(85, 66, -2f, 2f, -2f, 4f, 4f, 4f, 0.75f)),
            new Part("LeggingsL", 2f, 12f, 0f,
                    new Cube(68, 68, -2f, 0f, -2f, 4f, 12f, 4f, 0.5f),
                    new Cube(84, 74, -2f, 2f, -2f, 4f, 4f, 4f, 0.75f),
                    new Cube(78, 86, -2f, -1.25f, -1.9f, 4f, 4f, 4f, 0.75f)),
            new Part("body", 0f, 0f, 0f,
                    new Cube(79, 95, -4f, 9.1f, -2f, 8f, 2f, 4f, 0.48f)));

    private static final Geometry SHARED_MEDIUM_BOOTS = new Geometry(128, 128,
            new Part("BootsR", -2f, 12f, 0f,
                    new Cube(61, 85, -2f, 7f, -2f, 4f, 5f, 4f, 1f)),
            new Part("BootsL", 2f, 12f, 0f,
                    new Cube(44, 85, -2f, 7f, -2f, 4f, 5f, 4f, 1f)));

    private static final Geometry SHARED_MEDIUM_LEGGINGS = new Geometry(128, 128,
            new Part("LeggingsR", -2f, 12f, 0f,
                    new Cube(51, 68, -2f, 0f, -2f, 4f, 12f, 4f, 0.5f),
                    new Cube(85, 66, -2f, 0f, -2f, 4f, 4f, 4f, 0.75f)),
            new Part("LeggingsL", 2f, 12f, 0f,
                    new Cube(68, 68, -2f, 0f, -2f, 4f, 12f, 4f, 0.5f),
                    new Cube(84, 74, -2f, 0f, -2f, 4f, 4f, 4f, 0.75f)),
            new Part("body", 0f, 0f, 0f,
                    new Cube(78, 85, -4f, 9.1f, -2f, 8f, 2f, 4f, 0.48f)));

    private static final Geometry SHARED_MEDIUM_CHESTPLATE = new Geometry(128, 128,
            new Part("ArmsL", 5f, 14f, 0f,
                    new Cube(83, 0, -1f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                    new Cube(83, 17, -1.01f, -2f, -2f, 4f, 5f, 4f, 1f),
                    new Cube(7, 10, -1.01f, 4f, -2f, 4f, 5f, 4f, 0.75f)),
            new Part("ArmsR", -5f, 14f, 0f,
                    new Cube(75, 34, -3f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                    new Cube(76, 51, -2.99f, -2f, -2f, 4f, 5f, 4f, 1f),
                    new Cube(7, 20, -2.99f, 4f, -2f, 4f, 5f, 4f, 0.75f)),
            new Part("Torso", 0f, 12f, 0f,
                    new Cube(50, 34, -4f, 0f, -2f, 8f, 12f, 4f, 0.51f),
                    new Cube(0, 51, -4f, 0.5f, -2f, 8f, 10f, 4f, 0.75f),
                    new Cube(0, 84, -4f, 6.5f, -2f, 8f, 4f, 4f, 0.75f)));

    private static final Geometry SHARED_HEAVY_BOOTS = new Geometry(128, 128,
            new Part("BootsR", -2f, 12f, 0f,
                    new Cube(61, 85, -2f, 8f, -2f, 4f, 4f, 4f, 1f)),
            new Part("BootsL", 2f, 12f, 0f,
                    new Cube(44, 85, -2f, 8f, -2f, 4f, 4f, 4f, 1f)));

    private static final Geometry SHARED_HEAVY_LEGGINGS = new Geometry(128, 128,
            new Part("LeggingsR", -2f, 12f, 0f,
                    new Cube(51, 68, -2f, 0f, -2f, 4f, 12f, 4f, 0.5f),
                    new Cube(85, 66, -2f, 0f, -2f, 3f, 4f, 4f, 1f)),
            new Part("LeggingsL", 2f, 12f, 0f,
                    new Cube(68, 68, -2f, 0f, -2f, 4f, 12f, 4f, 0.5f),
                    new Cube(85, 74, -1f, 0f, -2f, 3f, 4f, 4f, 1f)),
            new Part("body", 0f, 0f, 0f,
                    new Cube(78, 85, -4f, 9.1f, -2f, 8f, 2f, 4f, 0.48f)));

    private static final Geometry SHARED_HEAVY_CHESTPLATE = new Geometry(128, 128,
            new Part("ArmsL", 5f, 14f, 0f,
                    new Cube(83, 0, -1f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                    new Cube(83, 17, -0.51f, -2.5f, -2f, 4f, 5f, 4f, 1f),
                    new Cube(7, 9, -0.5f, 4f, -2f, 4f, 4f, 4f, 1f)),
            new Part("ArmsR", -5f, 14f, 0f,
                    new Cube(75, 34, -3f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                    new Cube(76, 51, -3.49f, -2.5f, -2f, 4f, 5f, 4f, 1f),
                    new Cube(7, 19, -3.5f, 4f, -2f, 4f, 4f, 4f, 1f)),
            new Part("Torso", 0f, 12f, 0f,
                    new Cube(50, 34, -4f, 0f, -2f, 8f, 12f, 4f, 0.5f),
                    new Cube(0, 51, -4f, 0.5f, -2.25f, 8f, 10f, 4f, 1f),
                    new Cube(0, 84, -4f, 6.25f, -2f, 8f, 4f, 4f, 1f)));

    static {
        MODELS.put("copper_scale_armor_chestplate", new Geometry(128, 128,
                new Part("ArmsL", 5f, 14f, 0f,
                        new Cube(83, 0, -1f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(83, 17, -1f, -2f, -2f, 4f, 12f, 4f, 0.75f)),
                new Part("ArmsR", -5f, 14f, 0f,
                        new Cube(75, 34, -3f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(76, 51, -3f, -2f, -2f, 4f, 12f, 4f, 0.75f)),
                new Part("Torso", 0f, 12f, 0f,
                        new Cube(50, 34, -4f, 0f, -2f, 8f, 12f, 4f, 0.51f),
                        new Cube(0, 51, -4f, 0f, -2f, 8f, 12f, 4f, 0.75f))));

        MODELS.put("copper_scale_armor_helmet", SHARED_HELMET_TRIM);

        MODELS.put("copper_scale_armor_leggings", new Geometry(128, 128,
                new Part("LeggingsR", -2f, 12f, 0f,
                        new Cube(51, 68, -2f, 0f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(85, 67, -2f, 0f, -2f, 4f, 4f, 4f, 0.7f)),
                new Part("LeggingsL", 2f, 12f, 0f,
                        new Cube(68, 68, -2f, 0f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(85, 75, -2f, 0f, -2f, 4f, 4f, 4f, 0.7f)),
                new Part("body", 0f, 0f, 0f,
                        new Cube(78, 85, -4f, 9.1f, -2f, 8f, 2f, 4f, 0.48f))));

        MODELS.put("diamond_weave_boots", SHARED_MEDIUM_BOOTS);

        MODELS.put("diamond_weave_chestplate", new Geometry(128, 128,
                new Part("ArmsL", 5f, 14f, 0f,
                        new Cube(83, 0, -1f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(83, 17, -1f, -2f, -2f, 4f, 12f, 4f, 0.74f)),
                new Part("ArmsR", -5f, 14f, 0f,
                        new Cube(75, 34, -3f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(76, 50, -3f, -2f, -2f, 4f, 12f, 4f, 0.74f)),
                new Part("Torso", 0f, 12f, 0f,
                        new Cube(50, 34, -4f, 0f, -2f, 8f, 12f, 4f, 0.51f),
                        new Cube(0, 51, -4f, 0f, -2f, 8f, 12f, 4f, 0.75f),
                        new Cube(27, 85, -0.5f, 8.55f, -1.55f, 4f, 5f, 4f, 0.5f))));

        MODELS.put("diamond_weave_helmet", SHARED_HELMET_TRIM);

        MODELS.put("diamond_weave_leggings", new Geometry(128, 128,
                new Part("LeggingsR", -2f, 12f, 0f,
                        new Cube(51, 68, -2f, 0f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(85, 66, -2f, 0f, -2f, 4f, 5f, 4f, 0.75f)),
                new Part("LeggingsL", 2f, 12f, 0f,
                        new Cube(68, 68, -2f, 0f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(84, 75, -2f, 0f, -2f, 4f, 5f, 4f, 0.75f)),
                new Part("body", 0f, 0f, 0f,
                        new Cube(78, 85, -4f, 9.1f, -2f, 8f, 2f, 4f, 0.48f))));

        MODELS.put("heavy_diamond_armor_boots", SHARED_HEAVY_BOOTS);

        MODELS.put("heavy_diamond_armor_chestplate", SHARED_HEAVY_CHESTPLATE);

        MODELS.put("heavy_diamond_armor_helmet", new Geometry(128, 128,
                new Part("bb_main", 0f, 24f, 0f,
                        new Cube(33, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0.75f),
                        new Cube(33, 17, -4f, -8.25f, -3.75f, 8f, 8f, 8f, 1f),
                        new Cube(17, 34, -4f, -8.25f, -4.25f, 8f, 8f, 8f, 1f))));

        MODELS.put("heavy_diamond_armor_leggings", SHARED_HEAVY_LEGGINGS);

        MODELS.put("heavy_iron_armor_boots", SHARED_HEAVY_BOOTS);

        MODELS.put("heavy_iron_armor_chestplate", SHARED_HEAVY_CHESTPLATE);

        MODELS.put("heavy_iron_armor_helmet", new Geometry(128, 128,
                new Part("bb_main", 0f, 24f, 0f,
                        new Cube(33, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0.75f),
                        new Cube(33, 17, -4f, -8f, -4f, 8f, 8f, 8f, 1f),
                        new Cube(25, 85, 0.5f, -9f, -5f, -1f, 2f, 10f, 0.5f))));

        MODELS.put("heavy_iron_armor_leggings", SHARED_HEAVY_LEGGINGS);

        MODELS.put("heavy_netherite_armor_helmet", new Geometry(128, 128,
                new Part("bb_main", 0f, 24f, 0f,
                        new Cube(33, 17, -4f, -7.95f, -4f, 8f, 8f, 8f, 1f),
                        new Cube(33, 0, -4f, -8.5f, -4f, 8f, 8f, 8f, 0.7f),
                        new Cube(45, 99, -3.5f, -8.5f, -4f, 7f, 8f, 8f, 1f),
                        new Cube(82, 95, -4f, -8f, -4f, 8f, 8f, 8f, 0.75f))));

        MODELS.put("light_diamond_armor_boots", SHARED_LIGHT_BOOTS);

        MODELS.put("light_diamond_armor_chestplate", new Geometry(128, 128,
                new Part("ArmsL", 5f, 14f, 0f,
                        new Cube(83, 0, -1f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(83, 17, -1.01f, -2.25f, -2f, 4f, 5f, 4f, 0.73f),
                        new Cube(106, 17, 1.93f, -7.3f, -2f, 4f, 4f, 4f, 0.9f),
                        new Cube(7, 10, -1.01f, 4f, -2f, 4f, 5f, 4f, 0.75f)),
                new Part("ArmsR", -5f, 14f, 0f,
                        new Cube(75, 34, -3f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(76, 51, -2.97f, -2.25f, -2f, 4f, 5f, 4f, 0.73f),
                        new Cube(7, 20, -2.99f, 4f, -2f, 4f, 5f, 4f, 0.75f),
                        new Cube(106, 8, -0.07f, -7.3f, -2f, 4f, 4f, 4f, 0.9f)),
                new Part("Torso", 0f, 12f, 0f,
                        new Cube(50, 34, -4f, 0f, -2f, 8f, 12f, 4f, 0.51f),
                        new Cube(0, 51, -4f, 0f, -2f, 8f, 11f, 4f, 0.75f),
                        new Cube(0, 84, -4f, 6.5f, -2f, 8f, 4f, 4f, 0.75f))));

        MODELS.put("light_diamond_armor_helmet", new Geometry(128, 128,
                new Part("bb_main", 0f, 24f, 0f,
                        new Cube(33, 17, -4f, -7.95f, -4f, 8f, 8f, 8f, 1f),
                        new Cube(33, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0.75f),
                        new Cube(2, 0, -3.5f, -8f, -4f, 7f, 8f, 8f, 1f))));

        MODELS.put("light_diamond_armor_leggings", SHARED_LIGHT_LEGGINGS);

        MODELS.put("light_iron_armor_boots", SHARED_LIGHT_BOOTS);

        MODELS.put("light_iron_armor_chestplate", new Geometry(128, 128,
                new Part("ArmsL", 5f, 14f, 0f,
                        new Cube(83, 0, -1f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(83, 17, -1.01f, -2f, -2f, 4f, 5f, 4f, 0.9f),
                        new Cube(7, 10, -1.01f, 4f, -2f, 4f, 5f, 4f, 0.75f)),
                new Part("ArmsR", -5f, 14f, 0f,
                        new Cube(75, 34, -3f, -2f, -2f, 4f, 12f, 4f, 0.5f),
                        new Cube(76, 51, -2.97f, -2.25f, -2f, 4f, 5f, 4f, 0.73f),
                        new Cube(7, 20, -2.99f, 4f, -2f, 4f, 5f, 4f, 0.75f)),
                new Part("Torso", 0f, 12f, 0f,
                        new Cube(50, 34, -4f, 0f, -2f, 8f, 12f, 4f, 0.51f),
                        new Cube(0, 51, -4f, 0f, -2f, 8f, 11f, 4f, 0.75f),
                        new Cube(0, 84, -4f, 6.5f, -2f, 8f, 4f, 4f, 0.75f))));

        MODELS.put("light_iron_armor_helmet", new Geometry(128, 128,
                new Part("bb_main", 0f, 24f, 0f,
                        new Cube(33, 17, -4f, -8f, -4f, 8f, 8f, 8f, 1f),
                        new Cube(33, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0.75f),
                        new Cube(2, 0, -3.5f, -8f, -4f, 7f, 8f, 8f, 1f))));

        MODELS.put("light_iron_armor_leggings", SHARED_LIGHT_LEGGINGS);

        MODELS.put("mail_iron_armor_boots", SHARED_MEDIUM_BOOTS);

        MODELS.put("mail_iron_armor_chestplate", new Geometry(128, 128,
                new Part("ArmsL", 5f, 14f, 0f,
                        new Cube(83, 0, -1f, -2f, -2f, 4f, 12f, 4f, 0.5f)),
                new Part("ArmsR", -5f, 14f, 0f,
                        new Cube(75, 34, -3f, -2f, -2f, 4f, 12f, 4f, 0.5f)),
                new Part("Torso", 0f, 12f, 0f,
                        new Cube(50, 34, -4f, 0f, -2f, 8f, 12f, 4f, 0.51f),
                        new Cube(0, 51, -4f, 0f, -2f, 8f, 12f, 4f, 0.75f))));

        MODELS.put("mail_iron_armor_helmet", SHARED_HELMET_TRIM);

        MODELS.put("mail_iron_armor_leggings", SHARED_MEDIUM_LEGGINGS);

        MODELS.put("medium_diamond_armor_boots", SHARED_MEDIUM_BOOTS);

        MODELS.put("medium_diamond_armor_chestplate", SHARED_MEDIUM_CHESTPLATE);

        MODELS.put("medium_diamond_armor_helmet", new Geometry(128, 128,
                new Part("bb_main", 0f, 24f, 0f,
                        new Cube(33, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0.5f),
                        new Cube(33, 17, -4f, -8f, -4f, 8f, 8f, 8f, 0.75f),
                        new Cube(2, 99, -4.2f, -7.8f, -4.2f, 4f, 8f, 8f, 0.55f),
                        new Cube(30, 99, 0.2f, -7.8f, -4.2f, 4f, 8f, 8f, 0.55f),
                        new Cube(45, 99, -3.5f, -8f, -4f, 7f, 8f, 8f, 1f))));

        MODELS.put("medium_diamond_armor_leggings", SHARED_MEDIUM_LEGGINGS);

        MODELS.put("medium_iron_armor_boots", SHARED_MEDIUM_BOOTS);

        MODELS.put("medium_iron_armor_chestplate", SHARED_MEDIUM_CHESTPLATE);

        MODELS.put("medium_iron_armor_helmet", SHARED_HELMET_TRIM);

        MODELS.put("medium_iron_armor_leggings", SHARED_MEDIUM_LEGGINGS);

    }
}
