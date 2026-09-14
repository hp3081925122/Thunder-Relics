package org.hp.thunderrelics.client;

import net.minecraft.resources.Identifier;
import org.hp.thunderrelics.Thunderrelics;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

// 王铠与王戟共用静态资源查找方式，几何和贴图仍分别保存。
public final class RoyalEquipmentModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final Identifier model;
    private final Identifier texture;
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(
            Thunderrelics.MOD_ID, "royal_equipment");

    public RoyalEquipmentModel(String name, String texturePath) {
        // GeckoLib 5 会在 geckolib/models 下按命名空间和名称查找几何文件。
        model = Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID, name);
        texture = Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID, texturePath);
    }

    // 几何资源由 GeckoLib 5 的资源缓存加载，可由资源包替换。
    @Override
    public Identifier getModelResource(GeoRenderState state) { return model; }

    @Override
    public Identifier getTextureResource(GeoRenderState state) { return texture; }

    @Override
    public Identifier getAnimationResource(T item) { return ANIMATION; }
}


