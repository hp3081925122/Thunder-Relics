package org.hp.thunderrelics.client;

import net.minecraft.resources.Identifier;
import org.hp.thunderrelics.Thunderrelics;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

// GeckoLib 5 模型资源指向当前雷霆君王的几何、贴图和动画文件。
public final class ThunderKingModel extends GeoModel<ThunderKingEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID,
            "thunder_king_armed");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID,
            "textures/entity/thunder_king_armed.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID,
            "thunder_king");

    // 几何与贴图资源通过新版渲染状态读取，动画资源仍由实体本身提供。
    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(ThunderKingEntity entity) {
        return ANIMATION;
    }
}
