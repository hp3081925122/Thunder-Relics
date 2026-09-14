package org.hp.thunderrelics.client;

import net.minecraft.resources.ResourceLocation;
import org.hp.thunderrelics.Thunderrelics;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import software.bernie.geckolib.model.GeoModel;

// GeckoLib 模型资源与当前雷霆君王几何、贴图和动画文件一一对应。
public final class ThunderKingModel extends GeoModel<ThunderKingEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(Thunderrelics.MOD_ID, "geo/thunder_king_armed.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Thunderrelics.MOD_ID, "textures/entity/thunder_king_armed.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(Thunderrelics.MOD_ID, "animations/thunder_king.animation.json");

    @Override
    public ResourceLocation getModelResource(ThunderKingEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ThunderKingEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ThunderKingEntity entity) {
        return ANIMATION;
    }
}
