package org.hp.thunderrelics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hp.thunderrelics.ModEntities;
import org.hp.thunderrelics.Thunderrelics;

// 客户端事件订阅类只在客户端加载，专用服务器不会解析渲染器。
@EventBusSubscriber(modid = Thunderrelics.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    // 记录一次性客户端资源修复是否完成，便于从启动日志确认颜色处理器已覆盖。
    private static final Logger LOGGER = LoggerFactory.getLogger(Thunderrelics.MOD_ID + ".client");

    // 在客户端主线程注册三叉戟同款蓄力判定，主副手分别根据正在使用的物品切换。
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModEntities.ROYAL_GLAIVE.get(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "throwing"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem()
                        && entity.getUseItem() == stack ? 1.0F : 0.0F));
    }

    // 覆盖 NeoForge 刷怪蛋默认染色，避免自制整张贴图被背景色覆盖。
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> -1, ModEntities.THUNDER_KING_SPAWN_EGG.get());
        LOGGER.info("Registered un-tinted Thunder King spawn egg color handler");
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 将雷霆君王实体绑定到 GeckoLib 渲染器。
        event.registerEntityRenderer(ModEntities.THUNDER_KING.get(), ThunderKingRenderer::new);
        // 飞行武器使用与本体分离的渲染器。
        event.registerEntityRenderer(ModEntities.THROWN_ROYAL_WEAPON.get(), ThrownRoyalWeaponRenderer::new);
        // 同一渲染器绘制整波预警、落雷和余波电弧。
        event.registerEntityRenderer(ModEntities.ROYAL_STORM_WAVE.get(), RoyalStormRenderer::new);
    }

    private ClientEvents() {
    }
}



