package org.hp.thunderrelics;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLLoader;

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
        ModCreativeTabs.register(bus);
        bus.addListener(this::registerAttributes);
        bus.addListener(this::registerSpawnPlacements);
        if (FMLLoader.getDist().isClient()) {
            // 客户端事件只注册在客户端，服务器不会加载相机和渲染类。
            MinecraftForge.EVENT_BUS.addListener(org.hp.thunderrelics.client.ThunderCameraShake::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(org.hp.thunderrelics.client.ThunderCameraShake::onCameraAngles);
        }
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

}
