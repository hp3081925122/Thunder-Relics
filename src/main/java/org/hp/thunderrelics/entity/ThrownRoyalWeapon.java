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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.effect.DecorativeLightning;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
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
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    // 保持较平直的短程投掷弧线，收招前清理弹体，防止武器重复存在。
    @Override
    protected double getDefaultGravity() {
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
        if (level() instanceof ServerLevel level && getOwner() instanceof LivingEntity owner) {
            Vec3 impact = result.getLocation();
            LivingEntity hitLiving = null;
            if (result instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
                hitLiving = living;
                impact = living.position();
            }
            Vec3 ground = ThunderFx.floor(level, impact.add(0.0D, 0.5D, 0.0D));
            if (result.getType() == HitResult.Type.BLOCK) {
                // 命中方块后生成冲击波和半径八格的圆圈预警。
                ThunderFx.slam(level, owner, ground, 0.8F);
                RoyalStormWave.spawnImpactArea(owner, impact, 8.0D,
                        (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F);
            } else {
                // 命中生物时生成天雷和小型冲击波，玩家投掷再扩散雷伤。
                ThunderFx.hitBolt(level, owner, ground);
                ThunderFx.slam(level, owner, ground, 0.7F);
                if (owner instanceof Player player) splash(level, player, ground, hitLiving,
                        (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F, 4.0D);
            }
            playSound(SoundEvents.TRIDENT_HIT, 1.0F, 0.8F);
            discard();
        }
    }

    // 玩家投戟命中后的范围雷伤，排除投掷者、直击目标与友军。
    private static void splash(ServerLevel level, Player player, Vec3 center, LivingEntity excluded,
                               float damage, double radius) {
        AABB area = new AABB(center, center).inflate(radius, 2.0D, radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity.isAlive() && entity != player && entity != excluded && !player.isAlliedTo(entity))) {
            target.hurt(level.damageSources().indirectMagic(player, player), damage);
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



