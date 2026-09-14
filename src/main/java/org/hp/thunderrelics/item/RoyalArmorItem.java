package org.hp.thunderrelics.item;

import java.util.function.Consumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.network.chat.Component;
import java.util.List;
import javax.annotation.Nullable;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.hp.thunderrelics.client.RoyalEquipmentModel;
import org.hp.thunderrelics.ModEntities;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

// 四件王铠分别使用原版装备槽位，并共享高于下界合金的专属材料。
public final class RoyalArmorItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RoyalArmorItem(Type type) {
        // 1.21.1 的 ArmorMaterial 不再提供耐久倍率，耐久必须写入物品属性；56 沿用迁移前设定，durability 同时把堆叠上限固定为 1。
        super(ModEntities.ROYAL_MATERIAL, type,
                new Properties().rarity(Rarity.EPIC).durability(type.getDurability(56)));
    }

    // 盔甲骨骼由原版玩家姿态驱动，无需另播实体动画。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // tooltip 使用语言键，提示每件装备的被动效果而不写死可见文本。
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        String key = switch (getType()) {
            case HELMET -> "item.thunderrelics.royal_helmet.tooltip";
            case CHESTPLATE -> "item.thunderrelics.royal_chestplate.tooltip";
            case LEGGINGS -> "item.thunderrelics.royal_leggings.tooltip";
            case BOOTS -> "item.thunderrelics.royal_boots.tooltip";
            case BODY -> "item.thunderrelics.royal_chestplate.tooltip";
        };
        tooltip.add(Component.translatable(key));
    }

    // 客户端延迟创建渲染器，并在每次绘制前同步穿戴者与装备槽。
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoArmorRenderer<RoyalArmorItem> renderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                    EquipmentSlot slot, HumanoidModel<?> original) {
                if (renderer == null) {
                    renderer = new GeoArmorRenderer<>(new RoyalEquipmentModel<>(
                            "royal_armor", "textures/armor/royal_armor.png"));
                }
                renderer.prepForRender(entity, stack, slot, original);
                return renderer;
            }
        });
    }
}



