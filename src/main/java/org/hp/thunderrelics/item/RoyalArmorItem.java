package org.hp.thunderrelics.item;

import java.util.function.Consumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.network.chat.Component;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.hp.thunderrelics.client.RoyalEquipmentModel;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

// 四件王铠分别使用原版装备槽位，并共享高于下界合金的专属材料。
public final class RoyalArmorItem extends ArmorItem implements GeoItem {
    // 下界合金耐久倍率为 37，向上取整后的 56 使四件部位都超过其 50%。
    private static final ArmorMaterial ROYAL_MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return switch (type) {
                case HELMET -> 11 * 56;
                case CHESTPLATE -> 16 * 56;
                case LEGGINGS -> 15 * 56;
                case BOOTS -> 13 * 56;
            };
        }

        @Override
        public int getDefenseForType(Type type) {
            return switch (type) {
                case HELMET -> 4;
                case CHESTPLATE -> 9;
                case LEGGINGS -> 7;
                case BOOTS -> 4;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return 15;
        }

        @Override
        public net.minecraft.sounds.SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_NETHERITE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.NETHERITE_INGOT);
        }

        @Override
        public String getName() {
            return "thunderrelics:royal";
        }

        @Override
        public float getToughness() {
            return 4.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.15F;
        }
    };
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RoyalArmorItem(Type type) {
        super(ROYAL_MATERIAL, type, new Properties().rarity(Rarity.EPIC));
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
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        String key = switch (getType()) {
            case HELMET -> "item.thunderrelics.royal_helmet.tooltip";
            case CHESTPLATE -> "item.thunderrelics.royal_chestplate.tooltip";
            case LEGGINGS -> "item.thunderrelics.royal_leggings.tooltip";
            case BOOTS -> "item.thunderrelics.royal_boots.tooltip";
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
