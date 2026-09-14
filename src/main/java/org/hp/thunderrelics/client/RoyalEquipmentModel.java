package org.hp.thunderrelics.client;

import net.minecraft.resources.ResourceLocation;
import org.hp.thunderrelics.Thunderrelics;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

// 王铠与王戟共用静态资源查找方式，几何和贴图仍分别保存。
public final class RoyalEquipmentModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final ResourceLocation model;
    private final ResourceLocation texture;
    private static final ResourceLocation ANIMATION = new ResourceLocation(
            Thunderrelics.MOD_ID, "animations/royal_equipment.animation.json");

    public RoyalEquipmentModel(String name, String texturePath) {
        model = new ResourceLocation(Thunderrelics.MOD_ID, "geo/" + name + ".geo.json");
        texture = new ResourceLocation(Thunderrelics.MOD_ID, texturePath);
    }

    // 各资源由客户端资源管理器加载，可由资源包替换。
    @Override
    public ResourceLocation getModelResource(T item) { return model; }

    @Override
    public ResourceLocation getTextureResource(T item) { return texture; }

    @Override
    public ResourceLocation getAnimationResource(T item) { return ANIMATION; }
}
