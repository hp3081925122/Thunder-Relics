package org.hp.thunderrelics;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.hp.thunderrelics.entity.ThunderKingEntity;

// 雷霆遗迹 26.1.2 NeoForge 入口，公共注册只依赖当前版本 API。
@Mod(Thunderrelics.MOD_ID)
public final class Thunderrelics {
    public static final String MOD_ID = "thunderrelics";

    public Thunderrelics(IEventBus modEventBus) {
        // 注册实体和物品，NeoForge 会在注册阶段绑定资源键。
        ModEntities.ENTITIES.register(modEventBus);
        ModEntities.ITEMS.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::registerSpawnPlacements);
        // 仅在客户端注册渲染事件，服务器不会解析客户端渲染类。
        if (FMLEnvironment.getDist().isClient()) {
            modEventBus.addListener(org.hp.thunderrelics.client.ClientEvents::registerRenderers);
            // 客户端事件只注册在客户端，服务器不会加载相机和渲染类。
            NeoForge.EVENT_BUS.addListener(org.hp.thunderrelics.client.ThunderCameraShake::onClientTick);
            NeoForge.EVENT_BUS.addListener(org.hp.thunderrelics.client.ThunderCameraShake::onCameraAngles);
        }
    }

    // 为雷霆君王注册生命、攻击和移动属性。
    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.THUNDER_KING.get(), ThunderKingEntity.createAttributes().build());
    }

    // 使用 26.1.2 的 SpawnPlacementType 与实体生成原因签名。
    private void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.THUNDER_KING.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

}




