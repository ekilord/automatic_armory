package com.ekilord.automatic_armory.common.armor;

import com.ekilord.automatic_armory.AutomaticArmory;
import com.ekilord.automatic_armory.common.util.IOUtils;
import com.ekilord.automatic_armory.common.util.TextureUtils;
import com.ekilord.automatic_armory.config.ArmorSetDefinition;
import com.ekilord.automatic_armory.config.ArmorSetsConfig;
import com.ekilord.automatic_armory.config.AutomaticArmoryConfig;
import com.ekilord.automatic_armory.config.IngotsConfig;
import com.ekilord.automatic_armory.config.TextureAdjustment;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates and registers AutomaticArmory's own armor items: one full helmet/chestplate/leggings/boots set
 * per (ingot, {@link ArmorSetDefinition}) pair.
 * <p>
 * Previously this recolored {@code medieval_armory}'s own icon/on-body textures read out of that mod's
 * installed jar at runtime, and delegated on-body model rendering to that mod's classes via reflection.
 * Both of those templates and that model geometry have now been ported directly into AutomaticArmory (see
 * {@link IOUtils#getArmorTemplate} for the bundled textures and {@link
 * com.ekilord.automatic_armory.client.model.AutomaticArmoryArmorModels} for the on-body models) - so
 * {@code medieval_armory} no longer needs to be installed at all for armor generation to work.
 */
public final class AutomaticArmoryArmorItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AutomaticArmory.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AutomaticArmory.MOD_ID);

    private static final List<RegistryObject<Item>> GENERATED_ITEMS = new ArrayList<>();
    // Tracked separately so the tab has a chestplate to show as its icon - see currentIcon().
    private static final List<RegistryObject<Item>> GENERATED_CHESTPLATES = new ArrayList<>();

    public static final RegistryObject<CreativeModeTab> ARMORS_TAB = TABS.register("armors", () ->
            // Column 0 is a very common default other simple mods also reach for; given mod construction
            // order isn't reliably stable across launches, colliding with another mod's tab there can make
            // ours silently lose out on some launches while the grid still reserves the slot. An unusual,
            // out-of-the-way column number reduces that risk without needing a different positioning API.
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 27)
                    .title(Component.translatable("itemGroup." + AutomaticArmory.MOD_ID + ".armors"))
                    .icon(AutomaticArmoryArmorItems::currentIcon)
                    .displayItems((parameters, output) -> {
                        for (RegistryObject<Item> item : GENERATED_ITEMS) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    private AutomaticArmoryArmorItems() {
    }

    /**
     * Wires this class's {@link DeferredRegister}s to the mod event bus, and eagerly writes the tab's own
     * title translation (so it doesn't depend on any ingot ever actually being generated). Must be called
     * once, early in {@code AutomaticArmory}'s constructor - the actual per-ingot {@code ITEMS.register(...)}
     * calls (see {@link #generateAndRegister}) can happen any time afterward, as long as it's still within
     * the same mod construction pass (i.e. before {@code RegisterEvent} fires), which calling it
     * synchronously later in the same constructor satisfies.
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);

        IOUtils.writeLangEntries(AutomaticArmory.MOD_ID,
                Map.of("itemGroup." + AutomaticArmory.MOD_ID + ".armors", "Automatic Armory - Armors"));
    }

    /**
     * Picks one generated chestplate at random to show as the tab's icon - chosen once and cached (not
     * re-rolled on every call), since there's no need for it to change over time. Falls back to a plain iron
     * chestplate before any armor has actually been generated (e.g. if the tab renders before {@link
     * #generateAndRegister} has run, or if no ingots were discovered at all).
     */
    private static ItemStack cachedIcon = null;

    private static ItemStack currentIcon() {
        if (cachedIcon != null) {
            return cachedIcon;
        }

        if (GENERATED_CHESTPLATES.isEmpty()) {
            return new ItemStack(Items.IRON_CHESTPLATE);
        }

        RegistryObject<Item> chosen = GENERATED_CHESTPLATES.get(new Random().nextInt(GENERATED_CHESTPLATES.size()));
        cachedIcon = new ItemStack(chosen.get());
        return cachedIcon;
    }

    /**
     * For every discovered ingot, generates and registers one full armor set per configured {@link
     * ArmorSetDefinition}. No-op (with a warning) if no ingots were discovered or no armor sets are
     * configured.
     */
    public static void generateAndRegister(Map<String, BufferedImage> ingotTextures) {
        if (ingotTextures.isEmpty()) {
            AutomaticArmory.LOGGER.info("No ingot textures were discovered; nothing to generate for armor.");
            return;
        }

        Map<String, ArmorSetDefinition> sets = ArmorSetsConfig.getSets();
        if (sets.isEmpty()) {
            AutomaticArmory.LOGGER.warn("No armor sets configured in armor_sets.json - skipping armor generation.");
            return;
        }

        Map<String, String> langEntries = new LinkedHashMap<>();
        int registeredCount = 0;

        for (Map.Entry<String, BufferedImage> ingotEntry : ingotTextures.entrySet()) {
            String ingotId = ingotEntry.getKey();
            BufferedImage ingotTexture = ingotEntry.getValue();
            if (ingotTexture == null) continue;

            List<Color> ingotColors = TextureUtils.getColorsInImage(ingotTexture);
            if (ingotColors.isEmpty()) continue;

            String materialSlug = TextureUtils.materialFromIngotId(ingotId);

            for (Map.Entry<String, ArmorSetDefinition> setEntry : sets.entrySet()) {
                String setKey = setEntry.getKey();
                ArmorSetDefinition def = setEntry.getValue();

                registeredCount += generateAndRegisterOneSet(ingotId, materialSlug, setKey, def, ingotColors, langEntries);
            }
        }

        if (!langEntries.isEmpty()) {
            IOUtils.writeLangEntries(AutomaticArmory.MOD_ID, langEntries);
        }

        AutomaticArmory.LOGGER.info("AutomaticArmory generated and registered {} armor item(s).", registeredCount);
    }

    private static int generateAndRegisterOneSet(String ingotId, String materialSlug, String setKey, ArmorSetDefinition def,
                                                 List<Color> ingotColors, Map<String, String> langEntries) {
        String textureName = materialSlug + "_" + setKey;
        AutomaticArmoryArmorMaterial material = new AutomaticArmoryArmorMaterial(def);

        // Three layers combine here: this set's own TextureAdjustment (armor_sets.json), AutomaticArmoryConfig's
        // global "overall" multipliers (common.toml, nudges every set at once), and this ingot's own override
        // (ingots.json, nudges every set for just this one material) - multiplicatively for brightness/
        // saturation, additively for vibrance (a delta, not a scale factor).
        TextureAdjustment ingotArmorAdjustment = IngotsConfig.getOverride(ingotId).armor;
        float itemBrightness = def.texture.itemBrightnessMultiplier * AutomaticArmoryConfig.getOverallItemBrightnessMultiplier() * ingotArmorAdjustment.itemBrightnessMultiplier;
        float itemSaturation = def.texture.itemSaturationMultiplier * AutomaticArmoryConfig.getOverallItemSaturationMultiplier() * ingotArmorAdjustment.itemSaturationMultiplier;
        float itemVibrance = def.texture.itemVibranceAmount + AutomaticArmoryConfig.getOverallItemVibranceAmount() + ingotArmorAdjustment.itemVibranceAmount;
        float wornBrightness = def.texture.wornBrightnessMultiplier * AutomaticArmoryConfig.getOverallWornBrightnessMultiplier() * ingotArmorAdjustment.wornBrightnessMultiplier;
        float wornSaturation = def.texture.wornSaturationMultiplier * AutomaticArmoryConfig.getOverallWornSaturationMultiplier() * ingotArmorAdjustment.wornSaturationMultiplier;
        float wornVibrance = def.texture.wornVibranceAmount + AutomaticArmoryConfig.getOverallWornVibranceAmount() + ingotArmorAdjustment.wornVibranceAmount;

        boolean hasOnBodyTexture = def.onBodyTextureFile != null && !def.onBodyTextureFile.isBlank();
        ResourceLocation onBodyTexture = hasOnBodyTexture
                ? generateOnBodyTexture(def.onBodyTextureFile, textureName, ingotColors, wornBrightness, wornSaturation, wornVibrance)
                : null;

        int registered = 0;

        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            String piece = pieceName(type);
            String referenceIconName = def.iconStyle + "_" + piece;

            BufferedImage recolorBase = IOUtils.getArmorTemplate("item", referenceIconName + "_recolor_base");
            BufferedImage fixedTemplate = IOUtils.getArmorTemplate("item", referenceIconName + "_fixed");
            if (recolorBase == null || fixedTemplate == null) {
                AutomaticArmory.LOGGER.warn("No bundled baked-mask template '{}' for armor set '{}' - skipping this piece.",
                        referenceIconName, setKey);
                continue;
            }

            BufferedImage generatedIcon = TextureUtils.recolorFromBakedMask(recolorBase, fixedTemplate, ingotColors, itemBrightness, itemSaturation, itemVibrance);

            String registryName = textureName + "_" + piece;
            IOUtils.saveGeneratedTexture(AutomaticArmory.MOD_ID, registryName, generatedIcon);
            saveFlatItemModel(registryName);

            RegistryObject<Item> item = ITEMS.register(registryName,
                    () -> new AutomaticArmoryArmorItem(material, type, new Item.Properties(), setKey, onBodyTexture));
            GENERATED_ITEMS.add(item);
            if (type == ArmorItem.Type.CHESTPLATE) {
                GENERATED_CHESTPLATES.add(item);
            }

            langEntries.put("item." + AutomaticArmory.MOD_ID + "." + registryName,
                    TextureUtils.prettify(materialSlug) + " " + def.displayName + " " + TextureUtils.prettify(piece));

            registered++;
        }

        return registered;
    }

    /**
     * Generates the single on-body texture shared by all four pieces of a set (see {@link
     * ArmorSetDefinition#onBodyTextureFile}), from the same two baked mask-tool templates icons use (see
     * {@link #generateAndRegisterOneSet}) - {@code recolorFromBakedMask} is resolution/layout-agnostic since
     * it works per-pixel, so the same method serves both the small icons and this larger on-body atlas
     * texture. Returns {@code null} (falling back to the vanilla default texture resolution) if either baked
     * template is missing.
     */
    private static ResourceLocation generateOnBodyTexture(String textureFilePrefix, String textureName, List<Color> ingotColors, float brightnessMultiplier, float saturationMultiplier, float vibranceAmount) {
        BufferedImage recolorBase = IOUtils.getArmorTemplate("entities", textureFilePrefix + "_recolor_base");
        BufferedImage fixedTemplate = IOUtils.getArmorTemplate("entities", textureFilePrefix + "_fixed");
        if (recolorBase == null || fixedTemplate == null) {
            AutomaticArmory.LOGGER.warn("No bundled baked-mask on-body template '{}' - '{}' will render with the vanilla default appearance when worn.",
                    textureFilePrefix, textureName);
            return null;
        }

        BufferedImage generated = TextureUtils.recolorFromBakedMask(recolorBase, fixedTemplate, ingotColors, brightnessMultiplier, saturationMultiplier, vibranceAmount);
        String entityTextureName = textureName + "_armor";
        IOUtils.saveGeneratedEntityTexture(AutomaticArmory.MOD_ID, entityTextureName, generated);

        return ResourceLocation.fromNamespaceAndPath(AutomaticArmory.MOD_ID, "textures/entities/" + entityTextureName + ".png");
    }

    private static String pieceName(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            case BOOTS -> "boots";
        };
    }

    /**
     * A plain 2D inventory-icon model (the same convention vanilla uses for its own armor items) - the
     * on-body worn appearance is handled separately, per-item, by {@code AutomaticArmoryArmorItem} itself
     * (see {@link #generateOnBodyTexture}), not by this model file.
     */
    private static void saveFlatItemModel(String registryName) {
        String modelJson = """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "%s:item/%s"
                  }
                }
                """.formatted(AutomaticArmory.MOD_ID, registryName);

        IOUtils.saveGeneratedModel(AutomaticArmory.MOD_ID, registryName, modelJson);
    }
}