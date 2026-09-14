package org.hp.thunderrelics.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.hp.thunderrelics.Thunderrelics;
import org.hp.thunderrelics.entity.ThrownRoyalWeapon;

// 新版弹体渲染保留原武器几何、图集和飞行方向。
public final class ThrownRoyalWeaponRenderer extends GeoEntityRenderer<ThrownRoyalWeapon, ThrownRoyalWeaponRenderState> {
    // 武器骨骼保持固定，方向由每帧提取的弹体姿态决定。
    public ThrownRoyalWeaponRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<ThrownRoyalWeapon>() {
            // 模型放在 GeckoLib 5 的模型目录。
            @Override
            public Identifier getModelResource(GeoRenderState state) { return Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID, "thunder_thrown_weapon"); }
            // 继续使用原版王戟图集。
            @Override
            public Identifier getTextureResource(GeoRenderState state) { return Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID, "textures/entity/thunder_king_armed.png"); }
            // 弹体没有独立动画控制器。
            @Override
            public Identifier getAnimationResource(ThrownRoyalWeapon entity) { return Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID, "thunder_king"); }
        });
        shadowRadius = 0.2F;
    }

    // 每次提取创建独立状态，避免弹体之间覆盖。
    @Override
    public ThrownRoyalWeaponRenderState createRenderState(ThrownRoyalWeapon entity, Void context) { return new ThrownRoyalWeaponRenderState(); }

    // 提取阶段完成方向插值，延迟绘制不再访问实体。
    @Override
    public void extractRenderState(ThrownRoyalWeapon entity, ThrownRoyalWeaponRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
    }

    // 戟头沿模型正向竖轴，旋转后对齐飞行方向。
    @Override
    protected void applyRotations(RenderPassInfo<ThrownRoyalWeaponRenderState> pass, PoseStack pose, float nativeScale) {
        pose.mulPose(Axis.YP.rotationDegrees(pass.renderState().yaw));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F - pass.renderState().pitch));
    }
}
