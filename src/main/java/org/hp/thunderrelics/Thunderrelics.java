package org.hp.thunderrelics;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLLoader;
import org.hp.thunderrelics.entity.ThunderKingEntity;

// 模组入口负责注册内容、配置和 NeoForge 模组事件监听器。
@Mod(Thunderrelics.MOD_ID)
public final class Thunderrelics {
    public static final String MOD_ID = "thunderrelics";

    public Thunderrelics(IEventBus modEventBus, ModContainer modContainer) {
        // 将实体和物品注册到当前 NeoForge 模组事件总线。
        ModEntities.ENTITIES.register(modEventBus);
        ModEntities.ITEMS.register(modEventBus);
        ModEntities.ARMOR_MATERIALS.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        // 注册通用配置，让服务器和单人世界都能读取 Boss 属性。
        modContainer.registerConfig(ModConfig.Type.COMMON, ThunderrelicsConfig.SPEC);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::registerSpawnPlacements);
        if (FMLLoader.getDist().isClient()) {
            // 客户端事件只注册在客户端，服务器不会加载相机和渲染类。
            NeoForge.EVENT_BUS.addListener(org.hp.thunderrelics.client.ThunderCameraShake::onClientTick);
            NeoForge.EVENT_BUS.addListener(org.hp.thunderrelics.client.ThunderCameraShake::onCameraAngles);
        }
    }

    // 为雷霆君王提供生命、攻击和移动属性。
    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.THUNDER_KING.get(), ThunderKingEntity.createAttributes().build());
    }

    // 允许实体参与原版地面怪物刷怪检查，实际刷怪仍由世界规则决定。
    private void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.THUNDER_KING.get(),
                SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

}

