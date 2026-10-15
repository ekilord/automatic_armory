package com.ekilord.automatic_armory;

import com.ekilord.automatic_armory.common.armor.AutomaticArmoryArmorItems;
import com.ekilord.automatic_armory.config.AutomaticArmoryConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Dixtas Armory - Armory Variants.
 * <p>
 * Scans installed mods' own item textures for anything that looks like an ingot (see {@link
 * com.ekilord.automatic_armory.common.util.IOUtils#runGeneration()}), then generates tinted variants of two
 * other mods' equipment based on each discovered material's color:
 * <ul>
 *     <li>{@code dixtas_armory} weapons - AutomaticArmory does <b>not</b> register these itself. That mod is
 *     data-driven off its own config file, so {@link com.ekilord.automatic_armory.common.integration.DixtasArmoryWeaponInjector}
 *     just adds new entries there and lets {@code dixtas_armory} register the actual items when it loads.
 *     See {@link com.ekilord.automatic_armory.mixin.DixtasArmoryMixin} for why and how that generation is
 *     triggered from a mixin rather than from this constructor - {@code dixtas_armory} reads its own config
 *     far too early in its own constructor for any {@code mods.toml} ordering declaration to reliably beat
 *     it to the file.</li>
 *     <li>AutomaticArmory's own armor items - ported from {@code medieval_armory} (icons, on-body textures,
 *     and on-body model geometry are now all bundled/owned directly - see {@link AutomaticArmoryArmorItems}
 *     and {@link com.ekilord.automatic_armory.client.model.AutomaticArmoryArmorModels}), with stats defined
 *     entirely in AutomaticArmory's own {@link com.ekilord.automatic_armory.config.ArmorSetsConfig}. Unlike
 *     the dixtas_armory integration, this no longer requires medieval_armory to be installed at all.</li>
 * </ul>
 * Both generation passes actually run inside {@link com.ekilord.automatic_armory.common.util.IOUtils#runGeneration()},
 * which the {@code dixtas_armory} mixin calls at the right point in that mod's constructor. This
 * constructor's only remaining job is to wire up AutomaticArmory's own armor {@code DeferredRegister} early enough
 * that it's ready to receive those registrations whenever they happen.
 */
@Mod(AutomaticArmory.MOD_ID)
public class AutomaticArmory {
    public static final String MOD_ID = "automatic_armory";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AutomaticArmory() {
        if (!AutomaticArmoryConfig.isFeatureEnabled()) {
            LOGGER.info("AutomaticArmory variant generation is disabled via config.");
            return;
        }

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        AutomaticArmoryArmorItems.register(modEventBus);
    }
}
