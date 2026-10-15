package com.ekilord.automatic_armory.common.armor;

import com.ekilord.automatic_armory.AutomaticArmory;
import com.ekilord.automatic_armory.config.ArmorSetDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * An {@link ArmorMaterial} built entirely from one {@link ArmorSetDefinition} - every stat AutomaticArmory's generated
 * armor has comes directly from config, nothing is copied from any other mod.
 * <p>
 * {@link #getName()} is effectively vestigial here: on-body texture resolution goes entirely through
 * {@code AutomaticArmoryArmorItem#getArmorTexture}/{@code initializeClient} instead (matching how the
 * original template mod's own items worked - confirmed by decompiling it), so this is kept simple (just
 * {@link ArmorSetDefinition#wornBodyTexture}) since nothing meaningfully depends on its exact value.
 */
public class AutomaticArmoryArmorMaterial implements ArmorMaterial {
    private final ArmorSetDefinition def;
    private final Supplier<Ingredient> repairIngredient;

    public AutomaticArmoryArmorMaterial(ArmorSetDefinition def) {
        this.def = def;
        // Lazy: resolving the repair item eagerly at construction time could run before the referenced
        // item (e.g. another mod's ingot) is actually registered.
        this.repairIngredient = () -> {
            ResourceLocation id = ResourceLocation.withDefaultNamespace(def.repairItem);
            ItemLike item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) {
                AutomaticArmory.LOGGER.warn("Armor set repair item '{}' could not be resolved; repairing with this material will not work.", def.repairItem);
                return Ingredient.EMPTY;
            }
            return Ingredient.of(item);
        };
    }

    // Vanilla's own ArmorMaterials enum keeps this base-durability-per-slot table private (it's multiplied
    // by each material's durabilityMultiplier internally), so there's no public API to reuse it from - these
    // are vanilla's well-known values (helmet 11, chestplate 16, leggings 15, boots 13), replicated here.
    private static int baseDurability(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 11;
            case CHESTPLATE -> 16;
            case LEGGINGS -> 15;
            case BOOTS -> 13;
        };
    }

    @Override
    public int getDurabilityForType(ArmorItem.@NotNull Type type) {
        return baseDurability(type) * def.durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> def.defenseFor("helmet");
            case CHESTPLATE -> def.defenseFor("chestplate");
            case LEGGINGS -> def.defenseFor("leggings");
            case BOOTS -> def.defenseFor("boots");
        };
    }

    @Override
    public int getEnchantmentValue() {
        return def.enchantmentValue;
    }

    @Override
    public @NotNull SoundEvent getEquipSound() {
        ResourceLocation id = ResourceLocation.withDefaultNamespace(def.soundEvent);
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(id);
        return sound != null ? sound : net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC;
    }

    @Override
    public @NotNull Ingredient getRepairIngredient() {
        return repairIngredient.get();
    }

    @Override
    public @NotNull String getName() {
        return def.wornBodyTexture;
    }

    @Override
    public float getToughness() {
        return def.toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return def.knockbackResistance;
    }
}
