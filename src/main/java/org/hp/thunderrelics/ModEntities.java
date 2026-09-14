package org.hp.thunderrelics;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.thunderrelics.entity.ThunderKingEntity;

// 集中注册 26.1.2 NeoForge 版本的雷霆君王与刷怪蛋。
public final class ModEntities {
    // 新版注册器由注册表键自动推导资源标识符，不再使用旧版 ForgeRegistries。
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Thunderrelics.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Thunderrelics.MOD_ID);

    // 主体碰撞箱覆盖躯干，保持与原模型比例一致。
    public static final DeferredHolder<EntityType<?>, EntityType<ThunderKingEntity>> THUNDER_KING =
            ENTITIES.registerEntityType("thunder_king", ThunderKingEntity::new, MobCategory.MONSTER,
                    builder -> builder.sized(1.25F, 3.15F).clientTrackingRange(10).updateInterval(3));

    // 26.1.2 使用数据组件声明刷怪蛋对应实体类型。
    public static final DeferredItem<SpawnEggItem> THUNDER_KING_SPAWN_EGG = ITEMS.registerItem("thunder_king_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(THUNDER_KING.get())));

    private ModEntities() {
    }
}


