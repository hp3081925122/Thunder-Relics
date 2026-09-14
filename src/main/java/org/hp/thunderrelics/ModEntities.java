package org.hp.thunderrelics;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import org.hp.thunderrelics.entity.ThrownRoyalWeapon;
import org.hp.thunderrelics.entity.RoyalStormWave;
import org.hp.thunderrelics.item.RoyalArmorItem;
import org.hp.thunderrelics.item.RoyalGlaiveItem;

// 集中注册 26.1.2 NeoForge 版本的雷霆君王与刷怪蛋。
public final class ModEntities {
    // 新版注册器由注册表键自动推导资源标识符，不再使用旧版 ForgeRegistries。
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Thunderrelics.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Thunderrelics.MOD_ID);

    // 主体碰撞箱覆盖躯干，保持与原模型比例一致。
    public static final DeferredHolder<EntityType<?>, EntityType<ThunderKingEntity>> THUNDER_KING =
            ENTITIES.registerEntityType("thunder_king", ThunderKingEntity::new, MobCategory.MONSTER,
                    builder -> builder.sized(1.25F, 3.15F).clientTrackingRange(10).updateInterval(3));

    // 投掷王戟的弹体和雷霆预警波各自注册，供 Boss 技能服务端生成。
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownRoyalWeapon>> THROWN_ROYAL_WEAPON =
            ENTITIES.registerEntityType("thrown_royal_weapon", ThrownRoyalWeapon::new, MobCategory.MISC,
                    builder -> builder.sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<RoyalStormWave>> ROYAL_STORM_WAVE =
            ENTITIES.registerEntityType("royal_storm_wave", RoyalStormWave::new, MobCategory.MISC,
                    builder -> builder.sized(0.1F, 0.1F).clientTrackingRange(12).updateInterval(1));

    // 26.1.2 使用数据组件声明刷怪蛋对应实体类型。
    public static final DeferredItem<SpawnEggItem> THUNDER_KING_SPAWN_EGG = ITEMS.registerItem("thunder_king_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(THUNDER_KING.get())));

    // 四件王铠和王戟作为独立战利品注册，采用新版组件装备与投掷逻辑。
    public static final DeferredItem<RoyalArmorItem> ROYAL_HELMET =
            ITEMS.registerItem("royal_helmet", properties -> new RoyalArmorItem(properties, ArmorType.HELMET));
    public static final DeferredItem<RoyalArmorItem> ROYAL_CHESTPLATE =
            ITEMS.registerItem("royal_chestplate", properties -> new RoyalArmorItem(properties, ArmorType.CHESTPLATE));
    public static final DeferredItem<RoyalArmorItem> ROYAL_LEGGINGS =
            ITEMS.registerItem("royal_leggings", properties -> new RoyalArmorItem(properties, ArmorType.LEGGINGS));
    public static final DeferredItem<RoyalArmorItem> ROYAL_BOOTS =
            ITEMS.registerItem("royal_boots", properties -> new RoyalArmorItem(properties, ArmorType.BOOTS));
    public static final DeferredItem<RoyalGlaiveItem> ROYAL_GLAIVE =
            ITEMS.registerItem("royal_glaive", RoyalGlaiveItem::new);

    private ModEntities() {
    }
}


