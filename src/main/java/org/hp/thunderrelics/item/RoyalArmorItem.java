package org.hp.thunderrelics.item;

import java.util.Map;
import java.util.function.Consumer;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.sounds.SoundEvents;
import org.hp.thunderrelics.client.RoyalEquipmentModel;

// 王铠使用 26.1.2 的 humanoidArmor 物品组件，保留四个装备槽位和属性加成。
public final class RoyalArmorItem extends Item implements GeoItem {
    // 组件材料提供高于下界合金的韧性和击退抗性，并复用原版装备资源键。
    public static final ArmorMaterial ROYAL_MATERIAL = new ArmorMaterial(
            56,
            Map.of(ArmorType.HELMET, 4, ArmorType.CHESTPLATE, 9,
                    ArmorType.LEGGINGS, 7, ArmorType.BOOTS, 4),
            15,
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            4.0F,
            0.15F,
            ItemTags.REPAIRS_NETHERITE_ARMOR,
            EquipmentAssets.NETHERITE);
    private final ArmorType armorType;
    // 四件王铠共享一个 GeckoLib 5 动画缓存类型，但各物品仍有独立实例状态。
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public RoyalArmorItem(Item.Properties properties, ArmorType armorType) {
        super(properties.humanoidArmor(ROYAL_MATERIAL, armorType).rarity(Rarity.EPIC));
        this.armorType = armorType;
    }

    // tooltip 使用语言键，提示每件装备的被动效果而不写死可见文本。
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                Consumer<Component> tooltipAdder, net.minecraft.world.item.TooltipFlag flag) {
        String key = switch (armorType) {
            case HELMET -> "item.thunderrelics.royal_helmet.tooltip";
            case CHESTPLATE -> "item.thunderrelics.royal_chestplate.tooltip";
            case LEGGINGS -> "item.thunderrelics.royal_leggings.tooltip";
            case BOOTS -> "item.thunderrelics.royal_boots.tooltip";
            default -> "item.thunderrelics.royal_armor.tooltip";
        };
        tooltipAdder.accept(Component.translatable(key));
    }

    // 提供槽位给事件和掉落逻辑查询，不再依赖旧版 ArmorItem.Type。
    public EquipmentSlot getEquipmentSlot() {
        return armorType.getSlot();
    }

    // 王铠暂时没有额外控制器，保留标准注册入口以支持未来的装备动画。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    // 返回王铠的 GeckoLib 动画缓存，供盔甲层混入逻辑使用。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    // 将 26.1.2 的盔甲层渲染交给 GeckoLib 5 的通用盔甲渲染器。
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<RoyalArmorItem, HumanoidRenderState> renderer;

            // 四个装备槽复用同一份王铠骨骼模型，具体槽位由 GeckoLib 盔甲层传入。
            @Override
            public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
                if (renderer == null) {
                    renderer = new GeoArmorRenderer<>(new RoyalEquipmentModel<>(
                            "royal_armor", "textures/armor/royal_armor.png"));
                }
                return renderer;
            }
        });
    }
}
