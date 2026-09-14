package org.hp.thunderrelics.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.hp.thunderrelics.ModEntities;

// 客户端事件只保存当前版本的实体渲染器注册入口。
public final class ClientEvents {
    // 在 NeoForge 模组事件总线上注册雷霆君王的客户端渲染器。
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.THUNDER_KING.get(), ThunderKingRenderer::new);
    }

    private ClientEvents() {
    }
}
