package com.ekilord.automatic_armory.client.model;

import com.ekilord.automatic_armory.AutomaticArmory;
import com.ekilord.automatic_armory.client.model.AutomaticArmoryArmorGeometry.Cube;
import com.ekilord.automatic_armory.client.model.AutomaticArmoryArmorGeometry.Geometry;
import com.ekilord.automatic_armory.client.model.AutomaticArmoryArmorGeometry.Part;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns one {@link Geometry} entry from {@link AutomaticArmoryArmorGeometry} into a real {@link
 * LayerDefinition} (for registration - see {@link AutomaticArmoryModModels}) or a baked, named {@link
 * ModelPart} tree (for actually building an on-body model - see {@link AutomaticArmoryArmorModels}).
 * <p>
 * This is the one place that replaces what used to be 36 near-identical {@code Model<set>_<piece>} classes:
 * every one of them was just {@code createBodyLayer()} (a {@code LayerDefinition} builder) plus a
 * constructor that pulled specific named children back out via {@code root.getChild(name)}. Both of those
 * are now fully generic over the data in {@link AutomaticArmoryArmorGeometry}.
 */
public final class PortedArmorModel {

    private PortedArmorModel() {
    }

    /** @return the {@link ModelLayerLocation} a given geometry key bakes to, under AutomaticArmory's own namespace. */
    public static ModelLayerLocation layerLocation(String geometryKey) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(AutomaticArmory.MOD_ID, "model" + geometryKey), "main");
    }

    /** Builds the {@link LayerDefinition} for one geometry entry - the {@code createBodyLayer()} replacement. */
    public static LayerDefinition createBodyLayer(String geometryKey) {
        Geometry geometry = AutomaticArmoryArmorGeometry.MODELS.get(geometryKey);
        if (geometry == null) {
            throw new IllegalArgumentException("No ported geometry registered for key '" + geometryKey + "'");
        }

        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        for (Part part : geometry.parts()) {
            CubeListBuilder builder = CubeListBuilder.create();
            for (Cube cube : part.cubes()) {
                builder.texOffs(cube.u(), cube.v())
                        .addBox(cube.x(), cube.y(), cube.z(), cube.w(), cube.h(), cube.d(),
                                new CubeDeformation(cube.deform()));
            }
            root.addOrReplaceChild(part.name(), builder,
                    PartPose.offset(part.poseX(), part.poseY(), part.poseZ()));
        }

        return LayerDefinition.create(mesh, geometry.textureWidth(), geometry.textureHeight());
    }

    /**
     * Bakes the given geometry's layer and returns every one of its named parts, ready to be dropped into
     * a {@link net.minecraft.client.model.HumanoidModel}'s root {@link ModelPart} - the {@code root.getChild(name)}
     * replacement each of the old per-piece constructors did by hand.
     */
    public static Map<String, ModelPart> bakeNamedParts(String geometryKey) {
        Geometry geometry = AutomaticArmoryArmorGeometry.MODELS.get(geometryKey);
        if (geometry == null) return Map.of();

        ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(layerLocation(geometryKey));

        Map<String, ModelPart> named = new HashMap<>();
        for (Part part : geometry.parts()) {
            named.put(part.name(), root.getChild(part.name()));
        }
        return named;
    }
}
