package org.hp.thunderrelics.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import org.hp.thunderrelics.ModEntities;
import org.hp.thunderrelics.Thunderrelics;

// 客户端事件订阅类只在客户端加载，专用服务器不会解析渲染器。
@Mod.EventBusSubscriber(modid = Thunderrelics.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    // 在客户端主线程注册三叉戟同款蓄力判定，主副手分别根据正在使用的物品切换。
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModEntities.ROYAL_GLAIVE.get(),
                new ResourceLocation("minecraft", "throwing"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem()
                        && entity.getUseItem() == stack ? 1.0F : 0.0F));
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
