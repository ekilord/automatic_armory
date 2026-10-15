package com.ekilord.automatic_armory.common.util;

/**
 * Describes one generated weapon variant - corresponds to a single item entry that {@link
 * com.ekilord.automatic_armory.common.integration.DixtasArmoryWeaponInjector} adds to
 * dixtas_armory's own config, even though a weapon with separate handheld/gui textures produces two
 * texture files under the hood.
 *
 * @param weapon      the canonical (suffix-stripped) weapon type, e.g. {@code katana} not {@code katana_handheld}
 * @param ingotId     the ingot item id the tint was derived from (e.g. {@code minecraft:iron_ingot})
 * @param textureName the registry name dixtas_armory will register this item under, e.g. {@code tin_katana}
 *                    - also the prefix used for its generated texture/model file(s)
 */
public record GeneratedVariant(String weapon, String ingotId, String textureName) {
}
