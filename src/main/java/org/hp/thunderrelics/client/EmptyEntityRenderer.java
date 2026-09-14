package org.hp.thunderrelics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

// 迁移阶段的无模型实体渲染器，保证预警逻辑实体有合法客户端渲染入口。
public final class EmptyEntityRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {
    public EmptyEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        // 预警实体的可视化特效将在 26.1.2 专用渲染器迁移时接入。
    }
}
