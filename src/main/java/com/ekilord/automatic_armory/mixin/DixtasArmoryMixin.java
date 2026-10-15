package com.ekilord.automatic_armory.mixin;

import com.ekilord.automatic_armory.common.integration.DixtasArmoryWeaponInjector;
import com.ekilord.automatic_armory.common.util.GeneratedVariant;
import com.ekilord.automatic_armory.common.util.IOUtils;
import com.ekilord.automatic_armory.config.AutomaticArmoryConfig;
import net.dixta.dixtas_armory.DixtasArmory;
import net.dixta.dixtas_armory.config.DixtasArmoryJsonConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.util.List;


@Mixin(DixtasArmory.class)
public abstract class DixtasArmoryMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/dixta/dixtas_armory/config/DixtasArmoryJsonConfig;register()V"))
    private void automaticarmory$generateThenRegister() throws IOException {
        automaticarmory$generate();
        DixtasArmoryJsonConfig.register();
    }

    @Unique
    private static void automaticarmory$generate() {
        boolean enabled = AutomaticArmoryConfig.isFeatureEnabled();
        if (!enabled) {
            return;
        }

        List<GeneratedVariant> variants = IOUtils.runGeneration();
        DixtasArmoryWeaponInjector.injectGeneratedWeapons(variants);
    }
}
