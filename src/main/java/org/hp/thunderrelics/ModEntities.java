package org.hp.thunderrelics;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import org.hp.thunderrelics.item.RoyalArmorItem;
import org.hp.thunderrelics.item.RoyalGlaiveItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import org.hp.thunderrelics.entity.ThrownRoyalWeapon;
import org.hp.thunderrelics.entity.RoyalStormWave;

// 集中注册雷霆君王实体与刷怪蛋，公共代码不引用客户端渲染类。
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Thunderrelics.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Thunderrelics.MOD_ID);

    // 碰撞体覆盖主体躯干，武器和动作伸展部分不改变基础碰撞箱。
    public static final RegistryObject<EntityType<ThunderKingEntity>> THUNDER_KING = ENTITIES.register("thunder_king",
            () -> EntityType.Builder.of(ThunderKingEntity::new, MobCategory.MONSTER)
                    .sized(1.25F, 3.15F).clientTrackingRange(10).updateInterval(3)
                    .build(Thunderrelics.MOD_ID + ":thunder_king"));

    // 独立投掷弹体每刻同步，避免快速飞行时出现明显位置跳跃。
    public static final RegistryObject<EntityType<ThrownRoyalWeapon>> THROWN_ROYAL_WEAPON = ENTITIES.register("thrown_royal_weapon",
            () -> EntityType.Builder.of(ThrownRoyalWeapon::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1)
                    .build(Thunderrelics.MOD_ID + ":thrown_royal_weapon"));

    // 使用深蓝钢甲和金色装饰作为刷怪蛋的两个代表色。
    public static final RegistryObject<EntityType<RoyalStormWave>> ROYAL_STORM_WAVE = ENTITIES.register("royal_storm_wave",
            () -> EntityType.Builder.of(RoyalStormWave::new, MobCategory.MISC).sized(.1F, .1F)
                    .clientTrackingRange(12).updateInterval(20).build(Thunderrelics.MOD_ID + ":royal_storm_wave"));

    // 刷怪蛋沿用主体的钢甲和金边颜色。
    public static final RegistryObject<Item> THUNDER_KING_SPAWN_EGG = ITEMS.register("thunder_king_spawn_egg",
            () -> new ForgeSpawnEggItem(THUNDER_KING, 0x27364A, 0xC8A85B, new Item.Properties()));

    // 战利品装备分别注册，便于后续独立配置掉落与合成。
    public static final RegistryObject<RoyalArmorItem> ROYAL_HELMET = ITEMS.register("royal_helmet",
            () -> new RoyalArmorItem(ArmorItem.Type.HELMET));
    public static final RegistryObject<RoyalArmorItem> ROYAL_CHESTPLATE = ITEMS.register("royal_chestplate",
            () -> new RoyalArmorItem(ArmorItem.Type.CHESTPLATE));
    public static final RegistryObject<RoyalArmorItem> ROYAL_LEGGINGS = ITEMS.register("royal_leggings",
            () -> new RoyalArmorItem(ArmorItem.Type.LEGGINGS));
    public static final RegistryObject<RoyalArmorItem> ROYAL_BOOTS = ITEMS.register("royal_boots",
            () -> new RoyalArmorItem(ArmorItem.Type.BOOTS));
    public static final RegistryObject<RoyalGlaiveItem> ROYAL_GLAIVE = ITEMS.register("royal_glaive", RoyalGlaiveItem::new);

    private ModEntities() {
    }
}
