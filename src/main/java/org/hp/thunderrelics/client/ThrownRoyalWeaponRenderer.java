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
                return ResourceLocation.fromNamespaceAndPath(Thunderrelics.MOD_ID, "geo/thunder_thrown_weapon.geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(ThrownRoyalWeapon entity) {
                return ResourceLocation.fromNamespaceAndPath(Thunderrelics.MOD_ID, "textures/entity/thunder_king_armed.png");
            }

            @Override
            public ResourceLocation getAnimationResource(ThrownRoyalWeapon entity) {
                return ResourceLocation.fromNamespaceAndPath(Thunderrelics.MOD_ID, "animations/thunder_king.animation.json");
            }
        });
        shadowRadius = 0.2F;
    }

    // GeckoLib 4.9.2 的实体渲染实际调用四浮点参数重载；模型戟头沿正 Y 轴，旋转后对齐弹体飞行方向。
    @Override
    protected void applyRotations(ThrownRoyalWeapon entity, PoseStack pose, float age, float yaw,
                                  float partialTick, float nativeScale) {
        float interpolatedYaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float interpolatedPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        pose.mulPose(Axis.YP.rotationDegrees(interpolatedYaw));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F - interpolatedPitch));
    }
}



