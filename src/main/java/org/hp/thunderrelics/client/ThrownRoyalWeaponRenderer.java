package org.hp.thunderrelics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.hp.thunderrelics.Thunderrelics;
import org.hp.thunderrelics.entity.ThrownRoyalWeapon;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

// 投出的模型直接复用手持武器几何与图集，保证离手前后外形一致。
public final class ThrownRoyalWeaponRenderer extends GeoEntityRenderer<ThrownRoyalWeapon> {
    public ThrownRoyalWeaponRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<ThrownRoyalWeapon>() {
            @Override
            public ResourceLocation getModelResource(ThrownRoyalWeapon entity) {
                return new ResourceLocation(Thunderrelics.MOD_ID, "geo/thunder_thrown_weapon.geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(ThrownRoyalWeapon entity) {
                return new ResourceLocation(Thunderrelics.MOD_ID, "textures/entity/thunder_king_armed.png");
            }

            @Override
            public ResourceLocation getAnimationResource(ThrownRoyalWeapon entity) {
                return new ResourceLocation(Thunderrelics.MOD_ID, "animations/thunder_king.animation.json");
            }
        });
        shadowRadius = 0.2F;
    }

    // 模型戟头沿正 Y 轴，旋转后对齐原版弹体的偏航与仰角。
    @Override
    protected void applyRotations(ThrownRoyalWeapon entity, PoseStack pose, float age, float yaw, float partialTick) {
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F - Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
    }
}
