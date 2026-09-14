package org.hp.thunderrelics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.hp.thunderrelics.item.RoyalGlaiveItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

// 将靠近柄尾金箍上方的柄段放入手掌，普通持握和蓄力共用同一握点。
public final class RoyalGlaiveRenderer extends GeoItemRenderer<RoyalGlaiveItem> {
    // 金箍顶部为 -12.15，按当前缩放留出半只手掌长度，使整只手位于金箍上方。
    private static final double GRIP_Y = -8.5D;
    // 仅显式开启开发调试时，每个持握视角输出一次定位信息。
    private final EnumSet<ItemDisplayContext> loggedContexts = EnumSet.noneOf(ItemDisplayContext.class);
    public RoyalGlaiveRenderer() {
        super(new RoyalEquipmentModel<>("royal_glaive", "textures/item/royal_glaive.png"));
    }

    // 只在玩家持握视角换算握点，物品栏、地面和展示框继续使用独立显示配置。
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        boolean held = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        pose.pushPose();
        try {
            if (held) {
                // 抵消 ItemRenderer 的半格偏移，让显示变换直接作用于柄部原点。
                pose.translate(0.5D, 0.5D, 0.5D);
                pose.scale(0.55F, 0.55F, 0.55F);
                // 把指定柄段移到显示配置的手掌锚点，平移随模型缩放保持比例。
                pose.translate(0.0D, -GRIP_Y / 16.0D, 0.0D);
                // 抵消 GeckoLib 的物品居中偏移，避免柄从玩家手掌旁边经过。
                pose.translate(-0.5D, -0.51D, -0.5D);
                // 限频日志用于确认新握点换算已经进入对应手持渲染路径。
                if (RoyalSlashDebug.ENABLED && loggedContexts.add(context)) {
                    LogUtils.getLogger().debug("Royal glaive grip: context={}, gripY={}, scale=0.55, centered=true", context, GRIP_Y);
                }
            }
            super.renderByItem(stack, context, pose, buffers, light, overlay);
        } finally {
            // 恢复矩阵，防止另一只手和后续物品继承王戟偏移。
            pose.popPose();
        }
    }
}


