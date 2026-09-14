package org.hp.thunderrelics;

import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import org.hp.thunderrelics.entity.ThunderKingEntity;

// 模组入口负责注册内容、配置和 NeoForge 模组事件监听器。
@Mod(Thunderrelics.MOD_ID)
public final class Thunderrelics {
    public static final String MOD_ID = "thunderrelics";

    public Thunderrelics(IEventBus modEventBus, ModContainer modContainer) {
        // 将实体和物品注册到当前 NeoForge 模组事件总线。
        ModEntities.ENTITIES.register(modEventBus);
        ModEntities.ITEMS.register(modEventBus);
        // 注册通用配置，让服务器和单人世界都能读取 Boss 属性。
        modContainer.registerConfig(ModConfig.Type.COMMON, ThunderrelicsConfig.SPEC);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::registerSpawnPlacements);
        modEventBus.addListener(this::addSpawnEggToTab);
    }

    // 为雷霆君王提供生命、攻击和移动属性。
    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.THUNDER_KING.get(), ThunderKingEntity.createAttributes().build());
    }

    // 允许实体参与原版地面怪物刷怪检查，实际刷怪仍由世界规则决定。
    private void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.THUNDER_KING.get(),
                SpawnPlacements.Type.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    // 将刷怪蛋、装备和王戟放入原版创造栏，便于测试迁移后的注册内容。
    private void addSpawnEggToTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModEntities.THUNDER_KING_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModEntities.ROYAL_HELMET);
            event.accept(ModEntities.ROYAL_CHESTPLATE);
            event.accept(ModEntities.ROYAL_LEGGINGS);
            event.accept(ModEntities.ROYAL_BOOTS);
            event.accept(ModEntities.ROYAL_GLAIVE);
        }
    }
}
