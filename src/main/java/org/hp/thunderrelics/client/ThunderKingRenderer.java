package org.hp.thunderrelics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import com.geckolib.renderer.GeoEntityRenderer;

// GeckoLib 5 使用实体渲染状态驱动模型动画和新版提交流程。
public final class ThunderKingRenderer extends GeoEntityRenderer<ThunderKingEntity, ThunderKingRenderState> {
    public ThunderKingRenderer(EntityRendererProvider.Context context) {
        super(context, new ThunderKingModel());
        this.shadowRadius = 0.8F;
    }

    // 实体渲染器为每个实体创建独立状态，避免动画数据互相覆盖。
    @Override
    public ThunderKingRenderState createRenderState(ThunderKingEntity entity, Void context) {
        return new ThunderKingRenderState();
    }

    // GeckoLib 负责骨骼提交，保留实体阴影、名称和模型的统一绘制路径。
    @Override
    public void submit(ThunderKingRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
    }
}
