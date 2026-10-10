package org.hp.thunderrelics.client;

import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import net.minecraft.world.item.ItemDisplayContext;
import org.hp.thunderrelics.item.RoyalGlaiveItem;

// 王戟使用 GeckoLib 5 的特殊物品渲染器，并在手持视角修正握柄锚点。
public final class RoyalGlaiveRenderer extends GeoItemRenderer<RoyalGlaiveItem> {
    // 戟的握点在模型原点，刃朝 +Y。手持时把原点放进掌心。
    private static final double GRIP_Y = 0.0D;
    // 仅在开发调试开关启用时，对每个手持视角记录一次握点变换。
    private static final boolean DEBUG = Boolean.getBoolean("thunderrelics.debug");
    private final EnumSet<ItemDisplayContext> loggedContexts = EnumSet.noneOf(ItemDisplayContext.class);

    public RoyalGlaiveRenderer() {
        super(new RoyalEquipmentModel<>("royal_glaive", "textures/item/royal_glaive.png"));
    }

    // 26.1.2 的特殊物品渲染通过 RenderPassInfo 调整姿态，不能再使用旧版 renderByItem。
    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
        ItemDisplayContext context = renderPassInfo.getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        boolean held = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!held) {
            super.adjustRenderPose(renderPassInfo);
            return;
        }

        PoseStack pose = renderPassInfo.poseStack();
        // 手持沿用 0.55 缩放，普通持握和投掷蓄力都把柄心放在掌心。
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.scale(0.55F, 0.55F, 0.55F);
        pose.translate(0.0D, -GRIP_Y / 16.0D, 0.0D);
        pose.translate(-0.5D, -0.51D, -0.5D);
        if (DEBUG && loggedContexts.add(context)) {
            LogUtils.getLogger().debug("Royal glaive grip pose: context={}, gripY={}, scale=0.55", context, GRIP_Y);
        }
        // GeckoLib 默认的居中偏移仍需保留，物品栏和手持矩阵才能共用同一坐标原点。
        super.adjustRenderPose(renderPassInfo);
    }
}


