package com.ekilord.automatic_armory.common.armor;

import com.ekilord.automatic_armory.client.model.AutomaticArmoryArmorModels;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * AutomaticArmory's own generated armor item. On-body model geometry is no longer delegated via reflection
 * into an installed copy of {@code medieval_armory} - {@link AutomaticArmoryArmorModels} now owns that
 * geometry directly (ported one-for-one from that mod's decompiled sources, see its javadoc), so this class
 * only needs to know which {@code setKey} (e.g. {@code "light"}, {@code "mail"} - the key in {@link
 * com.ekilord.automatic_armory.config.ArmorSetsConfig#getSets()}) it belongs to.
 */
public class AutomaticArmoryArmorItem extends ArmorItem {
    /** {@link com.ekilord.automatic_armory.config.ArmorSetsConfig} key, e.g. {@code "light"}. Used to look
     * up this piece's on-body model in {@link AutomaticArmoryArmorModels} - {@code null} is not expected in
     * practice (every configured set key has at least a fallback rig), but is handled defensively. */
    private final String setKey;
    private final ResourceLocation armorTexture;

    public AutomaticArmoryArmorItem(ArmorMaterial material, Type type, Item.Properties properties,
                                    String setKey, ResourceLocation armorTexture) {
        super(material, type, properties);
        this.setKey = setKey;
        this.armorTexture = armorTexture;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> defaultModel) {
                HumanoidModel<LivingEntity> model = setKey == null ? null : AutomaticArmoryArmorModels.build(setKey, getType(), living);
                if (model == null) {
                    return defaultModel; // no ported rig for this (set, piece) - fall back to vanilla's own model
                }

                model.crouching = living.isShiftKeyDown();
                model.riding = defaultModel.riding;
                model.young = living.isBaby();

                // Defensive, matching medieval_armory's own layout: irrelevant parts are already empty
                // (no cubes) rather than merely invisible, so this is belt-and-suspenders, not load-bearing.
                model.setAllVisible(false);
                switch (slot) {
                    case HEAD -> model.head.visible = true;
                    case CHEST -> {
                        model.body.visible = true;
                        model.rightArm.visible = true;
                        model.leftArm.visible = true;
                    }
                    case LEGS -> {
                        model.body.visible = true;
                        model.rightLeg.visible = true;
                        model.leftLeg.visible = true;
                    }
                    case FEET -> {
                        model.rightLeg.visible = true;
                        model.leftLeg.visible = true;
                    }
                    default -> {
                    }
                }
                return model;
            }
        });
    }

    /**
     * Declared on {@link net.minecraftforge.common.extensions.IForgeItem} (implemented by {@code Item}
     * itself, and thus inherited on both logical sides, not just the client) - not on {@code
     * IClientItemExtensions}.
     */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return armorTexture != null ? armorTexture.toString() : null;
    }
}
