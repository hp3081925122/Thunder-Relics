package org.hp.thunderrelics.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.hp.thunderrelics.entity.ThunderKingEntity;

// GeckoLib 5 使用实体渲染状态驱动模型动画，模型提交后追加雷霆特效。
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

    // 提取招式编号与招式年龄，特效只读取快照不访问实体。
    @Override
    public void extractRenderState(ThunderKingEntity entity, ThunderKingRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entityId = entity.getId();
        state.alive = entity.isAlive();
        state.worldTime = entity.level().getGameTime() + partialTick;
        state.skill = entity.getSwingKind();
        state.skillAge = -1.0D;
        if (state.skill >= 0 && entity.getSwingStart() >= 0L) state.skillAge = state.worldTime - entity.getSwingStart();
    }

    // 每次渲染前注册骨骼位置监听，用于把特效锚定在武器与悬浮石上。
    @Override
    public void preRenderPass(RenderPassInfo<ThunderKingRenderState> renderPassInfo, SubmitNodeCollector collector) {
        super.preRenderPass(renderPassInfo, collector);
        ThunderKingFx.listen(renderPassInfo, renderPassInfo.renderState().entityId);
    }

    // 刀光与天雷会超出碰撞箱，放大剔除范围避免特效在画面边缘突然消失。
    @Override
    protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(ThunderKingEntity entity) {
        return super.getBoundingBoxForCulling(entity).inflate(6.0D, 12.0D, 6.0D);
    }

    // 先提交模型，再追加特效几何。
    @Override
    public void submit(ThunderKingRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        ThunderKingFx.submit(state, pose, collector, camera);
    }
}
