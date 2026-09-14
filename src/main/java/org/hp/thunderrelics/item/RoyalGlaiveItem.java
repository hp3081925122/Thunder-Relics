package org.hp.thunderrelics.item;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.stats.Stats;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.hp.thunderrelics.ModEntities;
import org.hp.thunderrelics.entity.ThrownRoyalWeapon;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.hp.thunderrelics.client.RoyalGlaiveRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

// 王戟作为正常近战物品使用，沿用剑的耐久、附魔和命中流程。
public final class RoyalGlaiveItem extends SwordItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RoyalGlaiveItem() {
        super(Tiers.DIAMOND, 5, -3.0F, new Properties().rarity(Rarity.EPIC));
    }

    // 静态持握由物品显示变换控制，不播放君王的攻击动画。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // 长按右键进入投掷蓄力，沿用三叉戟的十刻最低蓄力门槛。
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getDamageValue() >= stack.getMaxDamage() - 1) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // 松开右键生成独立投掷实体，命中处理集中在弹体服务端逻辑。
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseDuration) {
        if (!(entity instanceof Player player)) return;
        int charge = getUseDuration(stack) - remainingUseDuration;
        if (charge < 10 || level.isClientSide) return;
        stack.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(player.getUsedItemHand()));
        ThrownRoyalWeapon thrown = new ThrownRoyalWeapon(ModEntities.THROWN_ROYAL_WEAPON.get(), level);
        thrown.setOwner(player);
        thrown.setPos(player.getEyePosition().add(player.getLookAngle().scale(0.6D)));
        thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.5F, 1.0F);
        level.addFreshEntity(thrown);
        level.playSound(null, player, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 0.9F);
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(this, 10);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    // 投掷蓄力时间和王戟的高稀有度写入 tooltip。
    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("item.thunderrelics.royal_glaive.tooltip"));
    }

    // 独立三维物品模型保留君王武器的刃头、握柄与能量轮廓。
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private RoyalGlaiveRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new RoyalGlaiveRenderer();
                }
                return renderer;
            }
        });
    }
}


