package com.ekilord.automatic_armory.client.model;

import com.ekilord.automatic_armory.AutomaticArmory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the {@link net.minecraft.client.model.geom.builders.LayerDefinition} for every entry in {@link
 * AutomaticArmoryArmorGeometry#MODELS} - one generic loop instead of one hand-written registration line per
 * ported model (there used to be 36 of those, one per {@code medieval_armory} model class).
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = {Dist.CLIENT})
public final class AutomaticArmoryModModels {

    private AutomaticArmoryModModels() {
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        int count = 0;
        for (String geometryKey : AutomaticArmoryArmorGeometry.MODELS.keySet()) {
            event.registerLayerDefinition(PortedArmorModel.layerLocation(geometryKey),
                    () -> PortedArmorModel.createBodyLayer(geometryKey));
            count++;
        }

        AutomaticArmory.LOGGER.debug("Registered {} ported armor model layer definitions.", count);
    }
}
