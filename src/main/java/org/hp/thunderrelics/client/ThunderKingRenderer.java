package org.hp.thunderrelics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.hp.thunderrelics.entity.ThunderKingEntity;

// 26.1.2 使用新的提交流程，先提供稳定的实体渲染器壳，避免旧版 RenderType API 误用。
public final class ThunderKingRenderer extends EntityRenderer<ThunderKingEntity, EntityRenderState> {
    public ThunderKingRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8F;
    }

    // 每帧创建新版实体渲染状态对象。
    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    // 当前迁移阶段保留阴影和名称提交，模型渲染将在 26.1.2 兼容模型管线后接入。
    @Override
    public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                        CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
    }
}
