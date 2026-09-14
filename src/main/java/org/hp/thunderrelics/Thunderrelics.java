package org.hp.thunderrelics;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

@Mod(Thunderrelics.MOD_ID)
public final class Thunderrelics {
    public static final String MOD_ID = "thunderrelics";

    public Thunderrelics(FMLJavaModLoadingContext context) {
        // 将实体和刷怪蛋注册到 Forge 模组事件总线。
        var bus = context.getModEventBus();
        // 注册通用配置，让服务器和单人世界都能从配置文件读取 Boss 属性。
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ThunderrelicsConfig.SPEC);
        ModEntities.ENTITIES.register(bus);
        ModEntities.ITEMS.register(bus);
        bus.addListener(this::registerAttributes);
        bus.addListener(this::registerSpawnPlacements);
        bus.addListener(this::addSpawnEggToTab);
    }

    // 为雷霆君王提供生命、攻击和移动属性。
    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.THUNDER_KING.get(), ThunderKingEntity.createAttributes().build());
    }

    // 允许实体参与原版地面怪物刷怪检查，实际刷怪仍由世界规则决定。
    private void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(ModEntities.THUNDER_KING.get(),
                net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    // 将刷怪蛋放入原版刷怪蛋创造栏。
    private void addSpawnEggToTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModEntities.THUNDER_KING_SPAWN_EGG);
        }
        // 在战斗创造栏提供完整套装和王戟，便于逐件穿戴测试。
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModEntities.ROYAL_HELMET);
            event.accept(ModEntities.ROYAL_CHESTPLATE);
            event.accept(ModEntities.ROYAL_LEGGINGS);
            event.accept(ModEntities.ROYAL_BOOTS);
            event.accept(ModEntities.ROYAL_GLAIVE);
        }
    }
}
