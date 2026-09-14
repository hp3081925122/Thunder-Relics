package org.hp.thunderrelics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.mojang.logging.LogUtils;
import org.hp.thunderrelics.Thunderrelics;
import org.hp.thunderrelics.entity.ThunderKingEntity;

// 仅替换雷霆君王对应的血条，原版及其他模组的首领血条继续使用各自样式。
@EventBusSubscriber(modid = Thunderrelics.MOD_ID, value = Dist.CLIENT)
public final class ThunderBossOverlay {
    private static final Identifier FRAME_TEXTURE = Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID,
            "textures/gui/thunder_boss.png");
    private static final Identifier FILL_TEXTURE = Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID,
            "textures/gui/thunder_boss_fill.png");
    private static final int FRAME_WIDTH = 320;
    private static final int FRAME_HEIGHT = 52;
    private static final int FILL_WIDTH = 230;
    private static final int FILL_HEIGHT = 14;
    // 原图尺寸只用于纹理采样，屏幕尺寸独立设置，避免随图集尺寸撑大界面。
    private static final int DISPLAY_WIDTH = 170;
    private static final int DISPLAY_HEIGHT = 28;
    private static final int SLOT_WIDTH = 128;
    private static final int SLOT_HEIGHT = 5;
    private static boolean debugLogged;

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
        var graphics = event.getGuiGraphics();
        if (!debugLogged) {
            LogUtils.getLogger().debug("Thunder King custom boss bar overlay active: frame={}, fill={}, eventX={}, eventY={}, gui={}x{}, progress={}",
                    FRAME_TEXTURE, FILL_TEXTURE, event.getX(), event.getY(), graphics.guiWidth(), graphics.guiHeight(),
                    event.getBossEvent().getProgress());
            debugLogged = true;
        }
        event.setIncrement(DISPLAY_HEIGHT + 12);
        // 使用原版血条的中心点定位，避免界面缩放或多个血条时出现水平偏移。
        int centerX = event.getX() + 91;
        int x = centerX - DISPLAY_WIDTH / 2;
        int y = event.getY();
        // 26.1.2 的像素重载参数是纹理源区域，不能把宽高当作右下角坐标传入。
        graphics.blit(RenderPipelines.GUI_TEXTURED, FRAME_TEXTURE, x, y, 0.0F, 0.0F,
                DISPLAY_WIDTH, DISPLAY_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
        int width = Math.max(0, Math.min(SLOT_WIDTH, Math.round(SLOT_WIDTH * event.getBossEvent().getProgress())));
        if (width > 0) {
            // 槽位对应原图约第 29 至 38 行，裁切比例与屏幕宽度同步，避免残血时拉伸电弧。
            int sourceWidth = Math.round((float) width * FILL_WIDTH / SLOT_WIDTH);
            graphics.blit(RenderPipelines.GUI_TEXTURED, FILL_TEXTURE, x + 21, y + 15, 0.0F, 0.0F,
                    width, SLOT_HEIGHT, sourceWidth, FILL_HEIGHT, FILL_WIDTH, FILL_HEIGHT);
            // 最后重绘中央王冠窄条，保证其尖端遮挡电能，不被满血填充覆盖。
            graphics.blit(RenderPipelines.GUI_TEXTURED, FRAME_TEXTURE, x + 81, y, 152.0F, 0.0F,
                    8, DISPLAY_HEIGHT, 16, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
        }
        graphics.centeredText(minecraft.font, event.getBossEvent().getName(), centerX, y - 9, 0xE4D2A4);
    }

    // 工具类不创建实例。
    private ThunderBossOverlay() {}
}


