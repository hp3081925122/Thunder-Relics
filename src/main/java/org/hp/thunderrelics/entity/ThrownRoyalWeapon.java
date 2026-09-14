package org.hp.thunderrelics.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.effect.DecorativeLightning;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

// 复用原版弹体扫掠碰撞，飞行中不追踪目标、不破坏方块。
public final class ThrownRoyalWeapon extends ThrowableProjectile implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int age;

    public ThrownRoyalWeapon(EntityType<? extends ThrownRoyalWeapon> type, Level level) {
        super(type, level);
    }

    // 弹体没有额外同步状态，速度和位置由实体追踪器同步。
    @Override
    protected void defineSynchedData() {
    }

    // 保持较平直的短程投掷弧线，收招前清理弹体，防止武器重复存在。
    @Override
    protected float getGravity() {
        return 0.015F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (++age >= 18 || getOwner() == null || !getOwner().isAlive())) discard();
    }

    // 排除持有者及友军，避免刚离手就撞到自身模型。
    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity owner = getOwner();
        return super.canHitEntity(entity) && entity != owner && (owner == null || !owner.isAlliedTo(entity));
    }

    // 真正命中时按持有者当前攻击力结算，力量效果可以影响投掷伤害。
    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide && getOwner() instanceof LivingEntity owner) {
            float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5F;
            result.getEntity().hurt(damageSources().thrown(this, owner), damage);
        }
    }

    // 命中方块或实体后立即结束本次飞行，不穿墙或反复扣血。
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        // 玩家投掷命中后只召唤一道装饰雷霆，并对周围敌人追加小范围伤害。
        if (!level().isClientSide && getOwner() instanceof Player player) {
            Vec3 impact = result.getLocation();
            DecorativeLightning.strikeArea(level(), impact, player,
                    result instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living
                            ? living : null,
                    (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F, 0, 4.0D);
        }
        if (!level().isClientSide) {
            playSound(SoundEvents.TRIDENT_HIT, 1.0F, 0.8F);
            discard();
        }
    }

    // 保存寿命，区块重新加载后仍然按原有剩余时间清理。
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FlightAge", age);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        age = tag.getInt("FlightAge");
    }

    // 武器形状保持固定，由渲染器按飞行速度旋转。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
