package org.hp.thunderrelics;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ArmorMaterial;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.thunderrelics.entity.RoyalStormWave;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import org.hp.thunderrelics.entity.ThrownRoyalWeapon;
import org.hp.thunderrelics.item.RoyalArmorItem;
import org.hp.thunderrelics.item.RoyalGlaiveItem;

// 集中注册雷霆君王实体、弹体、装备和刷怪蛋，使用 NeoForge 1.21.1 延迟注册器。
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Thunderrelics.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Thunderrelics.MOD_ID);
    // 1.21.1 将盔甲材质改为数据记录，先注册 Holder 再交给 ArmorItem 使用。
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, Thunderrelics.MOD_ID);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ROYAL_MATERIAL =
            ARMOR_MATERIALS.register("royal", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 4, ArmorItem.Type.CHESTPLATE, 9,
                            ArmorItem.Type.LEGGINGS, 7, ArmorItem.Type.BOOTS, 4),
                    15,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(net.minecraft.world.item.Items.NETHERITE_INGOT),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Thunderrelics.MOD_ID, "royal"))),
                    4.0F,
                    0.15F));

    // 碰撞体覆盖主体躯干，武器和动作伸展部分不改变基础碰撞箱。
    public static final DeferredHolder<EntityType<?>, EntityType<ThunderKingEntity>> THUNDER_KING = ENTITIES.register("thunder_king",
            () -> EntityType.Builder.of(ThunderKingEntity::new, MobCategory.MONSTER)
                    .sized(1.25F, 3.15F).clientTrackingRange(10).updateInterval(3)
                    .build(Thunderrelics.MOD_ID + ":thunder_king"));

    // 独立投掷弹体每刻同步，避免快速飞行时出现明显位置跳跃。
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownRoyalWeapon>> THROWN_ROYAL_WEAPON = ENTITIES.register("thrown_royal_weapon",
            () -> EntityType.Builder.of(ThrownRoyalWeapon::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1)
                    .build(Thunderrelics.MOD_ID + ":thrown_royal_weapon"));

    // 风暴余波实体只携带短时序号，渲染器负责绘制预警、落雷和余波电弧。
    public static final DeferredHolder<EntityType<?>, EntityType<RoyalStormWave>> ROYAL_STORM_WAVE = ENTITIES.register("royal_storm_wave",
            () -> EntityType.Builder.of(RoyalStormWave::new, MobCategory.MISC).sized(.1F, .1F)
                    .clientTrackingRange(12).updateInterval(20).build(Thunderrelics.MOD_ID + ":royal_storm_wave"));

    // 使用 NeoForge 延迟刷怪蛋，确保实体注册完成后再解析实体类型。
    public static final DeferredHolder<Item, SpawnEggItem> THUNDER_KING_SPAWN_EGG = ITEMS.register("thunder_king_spawn_egg",
            () -> new DeferredSpawnEggItem(THUNDER_KING::get, 0x27364A, 0xC8A85B, new Item.Properties()));

    // 战利品装备分别注册，便于后续独立配置掉落与合成。
    public static final DeferredHolder<Item, RoyalArmorItem> ROYAL_HELMET = ITEMS.register("royal_helmet",
            () -> new RoyalArmorItem(ArmorItem.Type.HELMET));
    public static final DeferredHolder<Item, RoyalArmorItem> ROYAL_CHESTPLATE = ITEMS.register("royal_chestplate",
            () -> new RoyalArmorItem(ArmorItem.Type.CHESTPLATE));
    public static final DeferredHolder<Item, RoyalArmorItem> ROYAL_LEGGINGS = ITEMS.register("royal_leggings",
            () -> new RoyalArmorItem(ArmorItem.Type.LEGGINGS));
    public static final DeferredHolder<Item, RoyalArmorItem> ROYAL_BOOTS = ITEMS.register("royal_boots",
            () -> new RoyalArmorItem(ArmorItem.Type.BOOTS));
    public static final DeferredHolder<Item, RoyalGlaiveItem> ROYAL_GLAIVE = ITEMS.register("royal_glaive", RoyalGlaiveItem::new);

    private ModEntities() {
    }
}

