package org.hp.thunderrelics.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

// 使用 GeoEntityRenderer 绘制实体，并让模型的像素贴图保持原始清晰度。
public final class ThunderKingRenderer extends GeoEntityRenderer<ThunderKingEntity> {
    private final RoyalSlashRenderer slash = new RoyalSlashRenderer();
    private Matrix4f renderOriginInverse;
    private Matrix4f bladeTransform;
    public ThunderKingRenderer(EntityRendererProvider.Context context) {
        super(context, new ThunderKingModel());
        shadowRadius = 0.8F;
    }

    // 骨骼完成本帧变换后采集刃头位置，刀光跟随真实挥砍路径。
    @Override
    public void render(ThunderKingEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        // 从绘制入口的实体相对矩阵消除相机变换，避免辅助骨骼矩阵的单位矩阵相加偏移。
        renderOriginInverse = new Matrix4f(pose.last().pose()).invert();
        bladeTransform = null;
        if (RoyalSlashDebug.ENABLED) getGeoModel().getBone("weapon_head").ifPresent(bone -> bone.setTrackingMatrices(true));
        super.render(entity, yaw, partialTick, pose, buffers, light);
        slash.render(entity, getGeoModel(), bladeTransform, pose, buffers, partialTick);
        // 引雷仪式双手举高武器时，以真实武器骨骼顶部为端点绘制接引雷霆。
        RoyalStormRenderer.renderWeaponRelay(entity, bladeTransform, pose, buffers, partialTick);
        // 双手下劈的雷电使用同一个真实刃头矩阵，避免特效与挥砍方向脱节。
        RoyalStormRenderer.renderSlamLightning(entity, bladeTransform, pose, buffers, partialTick);
    }

    // 使用与实际武器绘制完全相同的父级、枢轴和旋转，采集未经辅助方法加工的刃头矩阵。
    @Override
    public void renderRecursively(PoseStack pose, ThunderKingEntity entity, GeoBone bone, RenderType type,
                                  MultiBufferSource buffers, VertexConsumer vertices, boolean reRender, float partialTick,
                                  int light, int overlay, float red, float green, float blue, float alpha) {
        if (!reRender && bone.getName().equals("weapon_head")) {
            pose.pushPose();
            RenderUtils.translateMatrixToBone(pose, bone);
            RenderUtils.translateToPivotPoint(pose, bone);
            RenderUtils.rotateMatrixAroundBone(pose, bone);
            RenderUtils.scaleMatrixForBone(pose, bone);
            bladeTransform = new Matrix4f(renderOriginInverse).mul(pose.last().pose());
            pose.popPose();
        }
        super.renderRecursively(pose, entity, bone, type, buffers, vertices, reRender, partialTick,
                light, overlay, red, green, blue, alpha);
    }

    // 挥砍时扩大剔除范围，避免主体离开画面边缘时刀光突然消失。
    @Override
    public boolean shouldRender(ThunderKingEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        long age = entity.level().getGameTime() - entity.getSwingStart();
        return super.shouldRender(entity, frustum, x, y, z)
                || (entity.isAlive() && entity.getSwingKind() >= 0 && entity.getSwingKind() < 4
                && age >= 0 && age <= 40 && entity.shouldRender(x, y, z)
                && frustum.isVisible(entity.getBoundingBox().inflate(4)));
    }
}
