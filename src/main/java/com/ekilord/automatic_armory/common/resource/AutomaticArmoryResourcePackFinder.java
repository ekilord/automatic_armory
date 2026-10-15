package com.ekilord.automatic_armory.common.resource;

import com.ekilord.automatic_armory.AutomaticArmory;
import com.ekilord.automatic_armory.common.util.IOUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exposes {@link IOUtils#getGeneratedResourcePackRoot()} as a built-in, always-active resource pack, so
 * the textures/models {@code IOUtils.saveGeneratedItem} writes at runtime are actually loaded by the
 * game's resource manager instead of just sitting on disk.
 * <p>
 * <b>Note:</b> the exact {@code Pack}/{@code PathPackResources} constructor signatures used here are one
 * of the more version-sensitive corners of Forge's API and have shifted across 1.19-1.20 releases. This
 * targets 1.20.1/Forge 47.x; if it fails to compile against a different patch version, check the local
 * decompiled sources for {@code Pack.readMetaAndCreate} and {@code PathPackResources}'s constructor for
 * the exact parameter list your version expects.
 */
@Mod.EventBusSubscriber(modid = AutomaticArmory.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AutomaticArmoryResourcePackFinder {

    private AutomaticArmoryResourcePackFinder() {
    }

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        Path root = IOUtils.getGeneratedResourcePackRoot();
        if (!Files.exists(root)) return;

        event.addRepositorySource((packConsumer) -> {
            Pack pack = Pack.readMetaAndCreate(
                    "automatic_armory_generated",
                    Component.literal("Automatic Armory Generated Textures"),
                    true,
                    id -> new PathPackResources(id, root, false),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN
            );

            if (pack != null) {
                packConsumer.accept(pack);
            } else {
                AutomaticArmory.LOGGER.warn("Generated resource pack at {} could not be read (missing/invalid pack.mcmeta?).", root);
            }
        });
    }
}
