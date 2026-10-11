package org.hp.thunderrelics.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.hp.thunderrelics.Thunderrelics;
import org.hp.thunderrelics.entity.ThunderKingEntity;

// 仅替换雷霆君王对应的血条，原版及其他模组的首领血条继续使用各自样式。
@EventBusSubscriber(modid = Thunderrelics.MOD_ID, value = Dist.CLIENT)
public final class ThunderBossOverlay {
    private static final ResourceLocation FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(Thunderrelics.MOD_ID, "textures/gui/thunder_boss.png");
    private static final ResourceLocation FILL_TEXTURE = ResourceLocation.fromNamespaceAndPath(Thunderrelics.MOD_ID, "textures/gui/thunder_boss_fill.png");
    private static final int FRAME_WIDTH = 320;
    private static final int FRAME_HEIGHT = 46;
    private static final int FILL_WIDTH = 230;
    private static final int FILL_HEIGHT = 8;
    // 原图尺寸只用于纹理采样，屏幕尺寸独立设置，避免随图集尺寸撑大界面。
    private static final int DISPLAY_WIDTH = 170;
    private static final int DISPLAY_HEIGHT = 25;
    private static final int SLOT_WIDTH = 128;
    private static final int SLOT_HEIGHT = 3;
    private static final Map<UUID, double[]> GHOSTS = new HashMap<>();
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
        event.setIncrement(DISPLAY_HEIGHT + 12);
        var graphics = event.getGuiGraphics();
        if (!debugLogged) {
            LogUtils.getLogger().debug("Thunder King custom boss bar overlay active: frame={}, fill={}, gui={}x{}, progress={}",
                    FRAME_TEXTURE, FILL_TEXTURE, graphics.guiWidth(), graphics.guiHeight(),
                    event.getBossEvent().getProgress());
            debugLogged = true;
        }
        // 直接使用界面中心定位，标题与边框共享同一中心线。
        int centerX = graphics.guiWidth() / 2;
        int x = centerX - DISPLAY_WIDTH / 2;
        int y = event.getY();
        RenderSystem.enableBlend();
        graphics.blit(FRAME_TEXTURE, x, y, DISPLAY_WIDTH, DISPLAY_HEIGHT,
                0, 0, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
        float progress = Math.max(0.0F, Math.min(1.0F, event.getBossEvent().getProgress()));
        float ghost = updateGhost(event.getBossEvent().getId(), progress, minecraft.level.getGameTime());
        int width = Math.round(SLOT_WIDTH * progress);
        int ghostWidth = Math.round(SLOT_WIDTH * ghost);
        // 掉血残影短暂停留后缓慢追上当前血量，保持与 26.1.2 一致。
        if (ghostWidth > width) {
            graphics.fill(x + 21 + width, y + 15, x + 21 + ghostWidth, y + 15 + SLOT_HEIGHT, 0xFFE8CF8E);
            graphics.fill(x + 21 + width, y + 15 + SLOT_HEIGHT - 1, x + 21 + ghostWidth,
                    y + 15 + SLOT_HEIGHT, 0xFFB08A4E);
        }
        if (width > 0) {
            // 槽位对应原图约第 29 至 38 行，裁切比例与屏幕宽度同步，避免残血时拉伸电弧。
            int sourceWidth = Math.round((float) width * FILL_WIDTH / SLOT_WIDTH);
            graphics.blit(FILL_TEXTURE, x + 21, y + 15, width, SLOT_HEIGHT,
                    0, 0, sourceWidth, FILL_HEIGHT, FILL_WIDTH, FILL_HEIGHT);
        }
        if (width > 0 || ghostWidth > 0) {
            // 重绘中央王冠窄条，避免填充条和残影盖住尖端。
            graphics.blit(FRAME_TEXTURE, x + 81, y, 8, DISPLAY_HEIGHT,
                    152, 0, 16, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
        }
        RenderSystem.disableBlend();
        graphics.drawCenteredString(minecraft.font, event.getBossEvent().getName(), centerX, y - 9, 0xE4D2A4);
    }

    // 记录每个首领血条的残影进度，回血时立即追平，掉血时延迟十刻再收缩。
    private static float updateGhost(UUID id, float progress, long time) {
        if (GHOSTS.size() > 16) GHOSTS.clear();
        double[] state = GHOSTS.computeIfAbsent(id, key -> new double[]{progress, time, time, progress});
        if (progress < state[3]) state[1] = time + 10;
        state[3] = progress;
        if (progress >= state[0]) {
            state[0] = progress;
        } else if (time > state[1] && time > state[2]) {
            double steps = Math.min(5.0D, time - state[2]);
            state[0] -= (state[0] - progress) * (1.0D - Math.pow(0.88D, steps));
            if (state[0] - progress < 0.002D) state[0] = progress;
        }
        state[2] = time;
        return (float) state[0];
    }

    // 工具类不创建实例。
    private ThunderBossOverlay() {}
}



