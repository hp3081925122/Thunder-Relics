package org.hp.thunderrelics.item;

import java.util.function.Consumer;
import com.mojang.logging.LogUtils;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.stats.Stats;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.entity.ThunderFx;
import org.hp.thunderrelics.ModEntities;
import org.hp.thunderrelics.client.RoyalGlaiveRenderer;
import org.hp.thunderrelics.entity.ThrownRoyalWeapon;

// 王戟使用 26.1.2 的物品组件属性，保留近战、蓄力和投掷流程。
public final class RoyalGlaiveItem extends Item implements GeoItem {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    // 物品是单例动画对象，缓存负责保存 GeckoLib 5 的客户端渲染状态。
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public RoyalGlaiveItem(Item.Properties properties) {
        // 使用下界合金剑属性作为基础，再提高耐久和稀有度。
        super(properties.sword(ToolMaterial.NETHERITE, 5.0F, -3.0F)
                .durability(2031).rarity(Rarity.EPIC));
    }

    // 长按右键进入投掷蓄力，沿用三叉戟的十刻最低蓄力门槛。
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.nextDamageWillBreak()) return InteractionResult.FAIL;
        // 记录蓄力起点，确认客户端确实进入了物品使用状态。
        LOGGER.debug("Royal glaive use started: hand={}, client={}, animation={}", hand, level.isClientSide(), getUseAnimation(stack));
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    // 松开右键生成独立投掷实体，命中处理集中在弹体服务端逻辑。
    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseDuration) {
        if (!(entity instanceof Player player)) return false;
        int charge = getUseDuration(stack, player) - remainingUseDuration;
        // 记录松开时长，区分未达到投掷门槛和客户端没有进入蓄力状态。
        LOGGER.debug("Royal glaive use released: charge={}, client={}", charge, level.isClientSide());
        if (charge < 10 || level.isClientSide()) return false;
        stack.hurtAndBreak(1, player, player.getUsedItemHand());
        ThrownRoyalWeapon thrown = new ThrownRoyalWeapon(ModEntities.THROWN_ROYAL_WEAPON.get(), level);
        thrown.setOwner(player);
        thrown.setPos(player.getEyePosition().add(player.getLookAngle().scale(0.6D)));
        thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.5F, 1.0F);
        level.addFreshEntity(thrown);
        level.playSound(null, player, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.9F);
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(stack, 10);
        return true;
    }

    // 平砍命中：生成月牙雷光斩痕，并向附近至多两只敌对生物跳出雷链。
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
        super.hurtEnemy(stack, mob, attacker);
        if (!(attacker.level() instanceof ServerLevel level)) return;
        float tilt = 1.0F;
        if (attacker.tickCount % 2 == 0) tilt = -1.0F;
        Vec3 chest = mob.position().add(0.0D, mob.getBbHeight() * 0.5D, 0.0D);
        ThunderFx.playerSlash(level, attacker, chest, tilt);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.35F, 1.8F);
        int chained = 0;
        // 只跳向怪物，避免误伤玩家与宠物。
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(5.0D),
                entity -> entity != mob && entity != attacker && entity.isAlive() && entity instanceof Enemy)) {
            if (chained >= 2) break;
            Vec3 otherChest = other.position().add(0.0D, other.getBbHeight() * 0.5D, 0.0D);
            ThunderFx.chain(level, attacker, chest, otherChest);
            other.hurtOrSimulate(level.damageSources().indirectMagic(attacker, attacker), 4.0F);
            chained++;
        }
        LOGGER.debug("Royal glaive melee hit: target={}, chained={}", mob.getType(), chained);
    }

    // 三叉戟动作提供投掷蓄力和抬臂姿势；新版长矛动作依赖动能武器组件，不适用于王戟。
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    // 王戟目前没有独立控制器，保留注册入口以接入 GeckoLib 5 的物品动画缓存。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    // 返回当前王戟实例的动画缓存，保证特殊物品渲染器能够取得渲染状态。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    // 将 GeckoLib 5 的物品渲染器提供给 26.1.2 的 minecraft:special 模型。
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private RoyalGlaiveRenderer renderer;

            // 渲染器只在客户端第一次真正绘制王戟时创建。
            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) renderer = new RoyalGlaiveRenderer();
                return renderer;
            }
        });
    }
}
