package org.hp.thunderrelics.client;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hp.thunderrelics.Thunderrelics;
import org.hp.thunderrelics.entity.ThunderKingEntity;

// 仅替换雷霆君王对应的血条，原版及其他模组的首领血条继续使用各自样式。
@Mod.EventBusSubscriber(modid = Thunderrelics.MOD_ID, value = Dist.CLIENT)
public final class ThunderBossOverlay {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Thunderrelics.MOD_ID, "textures/gui/thunder_boss.png");

    // 使用服务端同步的唯一编号匹配血条，支持同场多个首领和命名牌。
    @SubscribeEvent
    public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        boolean matching = false;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof ThunderKingEntity king
                    && king.getBossBarId().filter(event.getBossEvent().getId()::equals).isPresent()) {
                matching = true;
                break;
            }
        }
        if (!matching) return;

        // 图集上半为完整边框和空槽，下半为蓝色能量填充；按血量裁切而非缩放纹理。
        event.setCanceled(true);
        event.setIncrement(38);
        var graphics = event.getGuiGraphics();
        int x = event.getX() - 29;
        int y = event.getY();
        RenderSystem.enableBlend();
        graphics.blit(TEXTURE, x, y, 0, 0, 240, 28, 256, 64);
        int width = Math.max(0, Math.min(188, Math.round(188 * event.getBossEvent().getProgress())));
        if (width > 0) graphics.blit(TEXTURE, x + 26, y + 11, 26, 43, width, 7, 256, 64);
        RenderSystem.disableBlend();
        graphics.drawCenteredString(minecraft.font, event.getBossEvent().getName(), x + 120, y - 9, 0xE4D2A4);
    }

    // 工具类不创建实例。
    private ThunderBossOverlay() {}
}
