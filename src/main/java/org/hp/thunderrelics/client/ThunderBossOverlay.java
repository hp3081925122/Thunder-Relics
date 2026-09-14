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
    private static final ResourceLocation FRAME_TEXTURE = new ResourceLocation(Thunderrelics.MOD_ID, "textures/gui/thunder_boss.png");
    private static final ResourceLocation FILL_TEXTURE = new ResourceLocation(Thunderrelics.MOD_ID, "textures/gui/thunder_boss_fill.png");
    private static final int FRAME_WIDTH = 320;
    private static final int FRAME_HEIGHT = 52;
    private static final int FILL_WIDTH = 230;
    private static final int FILL_HEIGHT = 14;
    // 原图尺寸只用于纹理采样，屏幕尺寸独立设置，避免随图集尺寸撑大界面。
    private static final int DISPLAY_WIDTH = 170;
    private static final int DISPLAY_HEIGHT = 28;
    private static final int SLOT_WIDTH = 128;
    private static final int SLOT_HEIGHT = 5;

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

        // 取消原版血条并绘制新的雷霆王冠边框，填充条单独裁切以保持比例。
        event.setCanceled(true);
        event.setIncrement(DISPLAY_HEIGHT + 12);
        var graphics = event.getGuiGraphics();
        // 直接使用界面中心定位，标题与边框共享同一中心线。
        int centerX = graphics.guiWidth() / 2;
        int x = centerX - DISPLAY_WIDTH / 2;
        int y = event.getY();
        RenderSystem.enableBlend();
        graphics.blit(FRAME_TEXTURE, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT,
                0, 0, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
        int width = Math.max(0, Math.min(SLOT_WIDTH, Math.round(SLOT_WIDTH * event.getBossEvent().getProgress())));
        if (width > 0) {
            // 槽位对应原图约第 29 至 38 行，裁切比例与屏幕宽度同步，避免残血时拉伸电弧。
            int sourceWidth = Math.round((float) width * FILL_WIDTH / SLOT_WIDTH);
            graphics.blit(FILL_TEXTURE, x + 21, y + 15, width, SLOT_HEIGHT,
                    0, 0, sourceWidth, FILL_HEIGHT, FILL_WIDTH, FILL_HEIGHT);
            // 最后重绘中央王冠窄条，保证其尖端遮挡电能，不被满血填充覆盖。
            graphics.blit(FRAME_TEXTURE, x + 81, y, 8, DISPLAY_HEIGHT,
                    152, 0, 16, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
        }
        RenderSystem.disableBlend();
        graphics.drawCenteredString(minecraft.font, event.getBossEvent().getName(), centerX, y - 9, 0xE4D2A4);
    }

    // 工具类不创建实例。
    private ThunderBossOverlay() {}
}
