package org.hp.thunderrelics.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;

// 26.1.2 迁移首版保留 Boss 的基础战斗、索敌、朝向行为和 GeckoLib 行走控制器。
public final class ThunderKingEntity extends Monster implements GeoAnimatable {
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.thunder_king.walk");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    public ThunderKingEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setCustomName(Component.translatable("entity.thunderrelics.thunder_king"));
        this.setCustomNameVisible(true);
        this.xpReward = 50;
    }

    // 生命、攻击和移速数值沿用迁移前 Boss 配置的近似值。
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        // 近战目标会持续刷新，LookAtPlayerGoal 保证索敌时头部朝向玩家。
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        // 移动与攻击期间始终将身体水平朝向当前目标。
        if (this.getTarget() != null) {
            this.getLookControl().setLookAt(this.getTarget(), 30.0F, 30.0F);
            this.setYRot(this.getYHeadRot());
            this.yRotO = this.getYRot();
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return net.minecraft.sounds.SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return net.minecraft.sounds.SoundEvents.IRON_GOLEM_DEATH;
    }

    // 注册行走循环和后续攻击触发控制器，保持 Blockbench 动画命名兼容。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ThunderKingEntity>("combat", 0,
                state -> state.isMoving() ? state.setAndContinue(WALK) : PlayState.STOP));
    }

    // 为每个实体提供独立的 GeckoLib 动画缓存。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}

