package org.hp.thunderrelics.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.ModEntities;
import org.hp.thunderrelics.ThunderrelicsConfig;

// 雷霆君王：招式表驱动的服务端战斗逻辑，特效时刻与 ThunderSkill 共用同一时间线。
public final class ThunderKingEntity extends Monster implements GeoEntity {
    private static final String ANIMATION_PREFIX = "animation.thunder_king.";
    // 血条编号让客户端识别专属血条材质。
    private static final EntityDataAccessor<String> BOSS_BAR_ID = SynchedEntityData.defineId(
            ThunderKingEntity.class, EntityDataSerializers.STRING);
    // 当前招式编号与起手世界时间，客户端据此驱动特效。
    private static final EntityDataAccessor<Integer> SWING_KIND = SynchedEntityData.defineId(
            ThunderKingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SWING_START = SynchedEntityData.defineId(
            ThunderKingEntity.class, EntityDataSerializers.LONG);
    private static final RawAnimation WALK_LEGS = RawAnimation.begin().thenLoop(ANIMATION_PREFIX + "walk_legs");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIMATION_PREFIX + "idle");
    // 近战轮换：横斩、单手重劈、横斩、双手重砸。
    private static final ThunderSkill[] MELEE_ROTATION = {ThunderSkill.CLEAVE, ThunderSkill.HEAVY_SLAM,
            ThunderSkill.CLEAVE, ThunderSkill.TWO_HANDED_SLAM};
    // 每刻最大转向角度，避免大角度瞬间翻转。
    private static final float MAX_TURN_PER_TICK = 12.0F;
    // 行走动画按每秒二点二二格的地面速度制作，播放速率随实际移动速度缩放以防滑步。
    private static final double WALK_DESIGN_BLOCKS_PER_SECOND = 2.22D;
    // 悬浮石在模型中的轴心（几何像素坐标），用于估算遗石雷殛的起点。
    private static final double[][] STONE_PIVOTS = {{16, 50.84, 3}, {-16, 50.84, 3}, {20, 40.84, 5}, {-20, 40.84, 5}};

    private final ServerBossEvent bossEvent;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private ThunderSkill skill;
    private int skillTick;
    private UUID skillTarget;
    private int meleeIndex;
    private int javelinCooldown = 60;
    private int stormCooldown = 160;
    private int relicCooldown = 100;
    private int plantCooldown = 140;
    private final List<LivingEntity> relicTargets = new ArrayList<>();
    // 仅客户端记录上一帧是否在播放行走，用于输出状态切换调试。
    private boolean lastWalkState;

    public ThunderKingEntity(EntityType<? extends ThunderKingEntity> type, Level level) {
        super(type, level);
        // 26.1.2 的首领事件需要显式 UUID，使用实体 UUID 让血条关联稳定。
        bossEvent = new ServerBossEvent(getUUID(), getDisplayName(),
                BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
        xpReward = 25;
        // 配置在实体注册后才可读取；实例创建时同步已加载配置中的生命值和护甲。
        if (ThunderrelicsConfig.SPEC.isLoaded()) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(
                    ThunderrelicsConfig.THUNDER_KING_MAX_HEALTH.get().doubleValue());
            getAttribute(Attributes.ARMOR).setBaseValue(
                    ThunderrelicsConfig.THUNDER_KING_ARMOR.get().doubleValue());
            setHealth(getMaxHealth());
        }
        if (!level.isClientSide()) entityData.set(BOSS_BAR_ID, bossEvent.getId().toString());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOSS_BAR_ID, "");
        builder.define(SWING_KIND, -1);
        builder.define(SWING_START, -1L);
    }

    // 客户端仅使用这些字段控制视觉表现，不参与伤害判定。
    public int getSwingKind() {
        return entityData.get(SWING_KIND);
    }

    public long getSwingStart() {
        return entityData.get(SWING_START);
    }

    // 仅供客户端血条渲染识别对应的雷霆君王。
    public Optional<UUID> getBossBarId() {
        String value = entityData.get(BOSS_BAR_ID);
        if (value.isEmpty()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    // 接引雷霆期间受到的伤害减半。
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (skill == ThunderSkill.STORM) amount *= 0.5F;
        return super.hurtServer(level, source, amount);
    }

    // 实体属性注册发生在配置加载前，注册阶段只能读取默认值。
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, ThunderrelicsConfig.THUNDER_KING_MAX_HEALTH.getDefault().doubleValue())
                .add(Attributes.ARMOR, ThunderrelicsConfig.THUNDER_KING_ARMOR.getDefault().doubleValue())
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    // 原版近战目标负责接近与触发近战招式，出招期间停止寻路。
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false) {
            @Override
            public void tick() {
                if (skill != null) getNavigation().stop();
                else super.tick();
            }
        });
        goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity target = getTarget();
        faceTarget(target);
        if (level().isClientSide()) return;
        bossEvent.setProgress(getHealth() / getMaxHealth());
        bossEvent.setName(getDisplayName());
        if (javelinCooldown > 0) javelinCooldown--;
        if (stormCooldown > 0) stormCooldown--;
        if (relicCooldown > 0) relicCooldown--;
        if (plantCooldown > 0) plantCooldown--;
        if (skill == null) chooseRangedSkill(target);
        if (skill != null) updateSkill(target);
    }

    // 平滑转向目标；近战命中帧之后锁定朝向，让收招不跟随目标转身。
    private void faceTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) return;
        ThunderSkill current = ThunderSkill.byId(getSwingKind());
        if (current != null && isMelee(current) && getSwingStart() >= 0L
                && level().getGameTime() - getSwingStart() > current.event(0)) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - getX()), target.getZ() - getZ()));
        float turned = Mth.approachDegrees(getYRot(), yaw, MAX_TURN_PER_TICK);
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        setYRot(turned);
        setYHeadRot(turned);
        setYBodyRot(turned);
    }

    private static boolean isMelee(ThunderSkill s) {
        return s == ThunderSkill.CLEAVE || s == ThunderSkill.HEAVY_SLAM || s == ThunderSkill.TWO_HANDED_SLAM;
    }

    // 空闲时按优先级选择引雷、遗石雷殛与投戟。
    private void chooseRangedSkill(LivingEntity target) {
        if (!isValidTarget(target)) return;
        double distance = distanceTo(target);
        boolean sight = hasLineOfSight(target);
        if (stormCooldown == 0 && distance <= 26.0D && sight) {
            stormCooldown = 576;
            startSkill(ThunderSkill.STORM, target);
        } else if (plantCooldown == 0 && distance >= 3.0D && distance <= 15.0D && sight) {
            plantCooldown = 240;
            startSkill(ThunderSkill.GROUND_PLANT, target);
        } else if (relicCooldown == 0 && distance >= 4.0D && distance <= 22.0D && sight) {
            relicCooldown = 220;
            startSkill(ThunderSkill.RELIC_BARRAGE, target);
        } else if (javelinCooldown == 0 && distance > 4.5D && distance <= 16.0D && sight) {
            javelinCooldown = 128;
            startSkill(ThunderSkill.JAVELIN, target);
        }
    }

    // 有效敌对目标：存活、非友军、非创造或旁观玩家。
    private boolean isValidTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || target == this || isAlliedTo(target)) return false;
        return !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    // 启动招式：同步编号与起手时刻，触发动画并停止寻路。
    private void startSkill(ThunderSkill next, LivingEntity target) {
        skill = next;
        skillTick = 0;
        skillTarget = target.getUUID();
        entityData.set(SWING_KIND, next.ordinal());
        entityData.set(SWING_START, level().getGameTime());
        getNavigation().stop();
        triggerAnim("combat", next.animation);
        LogUtils.getLogger().debug("Thunder King skill start: entity={}, skill={}, target={}, duration={}",
                getId(), next, skillTarget, next.duration);
    }

    // 逐刻推进招式，在关键事件刻结算伤害与生成特效。
    private void updateSkill(LivingEntity target) {
        int elapsed = skillTick;
        LivingEntity locked = null;
        if (target != null && target.getUUID().equals(skillTarget) && isValidTarget(target)) locked = target;
        if (skill == ThunderSkill.CLEAVE) cleaveStep(locked, elapsed);
        // 遍历当前招式的全部事件刻。
        for (int i = 0; i < skill.eventCount(); i++) {
            if (elapsed == skill.event(i)) onSkillEvent(i, locked);
        }
        if (skill == ThunderSkill.STORM && elapsed > 0 && elapsed % 20 == 0) heal(getMaxHealth() * 0.01F);
        skillTick++;
        if (skillTick >= skill.duration) {
            LogUtils.getLogger().debug("Thunder King skill end: entity={}, skill={}", getId(), skill);
            skill = null;
            relicTargets.clear();
        }
    }

    // 横斩在迈步阶段向目标推进，保留近身停距。
    private void cleaveStep(LivingEntity target, int elapsed) {
        if (target == null || !onGround()) return;
        double phase = elapsed / (double) skill.duration;
        double speed = 0.0D;
        if (phase >= 0.12D && phase <= 0.44D) speed = 0.15D;
        if (phase >= 0.62D && phase <= 0.9D) speed = 0.07D;
        Vec3 toward = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        double distance = toward.length();
        double stop = (getBbWidth() + target.getBbWidth()) * 0.5D + 0.6D;
        if (speed > 0.0D && distance > stop) move(MoverType.SELF, toward.scale(Math.min(speed, distance - stop) / distance));
    }

    // 招式事件分发。
    private void onSkillEvent(int index, LivingEntity target) {
        ServerLevel level = (ServerLevel) level();
        float attack = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (skill == ThunderSkill.CLEAVE) {
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.6F);
            int hits = sweepHit(level, 4.3D, 75.0D, attack + 3.0F, 0.6D, 0.15D, true);
            LogUtils.getLogger().debug("Thunder King cleave: hits={}", hits);
        } else if (skill == ThunderSkill.HEAVY_SLAM) {
            Vec3 impact = ThunderFx.floor(level, frontPoint(4.0D));
            ThunderFx.slam(level, this, impact, 1.0F);
            int hits = areaHit(level, impact, 3.0D, attack * 1.5F + 3.0F, 0.8D, 0.45D);
            hits += sweepHit(level, 4.6D, 28.0D, attack * 1.5F, 0.8D, 0.3D, false);
            LogUtils.getLogger().debug("Thunder King heavy slam: impact={}, hits={}", impact, hits);
        } else if (skill == ThunderSkill.TWO_HANDED_SLAM) {
            Vec3 impact = ThunderFx.floor(level, frontPoint(4.2D));
            ThunderFx.slam(level, this, impact, 1.6F);
            int hits = areaHit(level, impact, 4.5D, attack * 1.8F + 4.0F, 1.1D, 0.7D);
            LogUtils.getLogger().debug("Thunder King two handed slam: impact={}, hits={}", impact, hits);
        } else if (skill == ThunderSkill.JAVELIN) {
            if (index == 0 && target != null && hasLineOfSight(target)) throwWeapon(target);
            if (index == 1) playSound(SoundEvents.TRIDENT_RETURN, 1.0F, 0.8F);
        } else if (skill == ThunderSkill.STORM) {
            stormEvent(level, index, target);
        } else if (skill == ThunderSkill.RELIC_BARRAGE) {
            fireRelic(level, index, target, attack);
        } else if (skill == ThunderSkill.GROUND_PLANT && index == 0) {
            plantWeapon(level, attack);
        }
    }

    // 实体正前方水平距离处的点。
    private Vec3 frontPoint(double distance) {
        double yaw = Math.toRadians(getYRot());
        return position().add(-Math.sin(yaw) * distance, 0.0D, Math.cos(yaw) * distance);
    }

    // 扇形横扫：距离与夹角内且可见的敌人受到伤害与击退，可选附带命中天雷。
    private int sweepHit(ServerLevel level, double reach, double halfAngle, float damage, double knockback,
                         double lift, boolean bolts) {
        Vec3 forward = frontPoint(1.0D).subtract(position()).normalize();
        int hits = 0;
        // 收集范围内候选并逐个做夹角与视线判定。
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach, 2.0D, reach))) {
            if (!isValidTarget(victim)) continue;
            Vec3 offset = victim.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            double distance = offset.length() - victim.getBbWidth() * 0.5D;
            if (distance > reach || Math.abs(victim.getY() - getY()) > 3.0D) continue;
            if (offset.lengthSqr() > 0.01D) {
                double angle = Math.toDegrees(Math.acos(Mth.clamp(offset.normalize().dot(forward), -1.0D, 1.0D)));
                if (angle > halfAngle) continue;
            }
            if (!hasLineOfSight(victim)) continue;
            if (victim.hurtOrSimulate(damageSources().mobAttack(this), damage)) {
                victim.knockback(knockback, -offset.x, -offset.z);
                victim.push(0.0D, lift, 0.0D);
                victim.hurtMarked = true;
                if (bolts && hits < 3) ThunderFx.hitBolt(level, this, victim.position());
                hits++;
            }
        }
        return hits;
    }

    // 圆形范围伤害：以落点为中心，向外击退并击飞。
    private int areaHit(ServerLevel level, Vec3 center, double radius, float damage, double knockback, double lift) {
        int hits = 0;
        AABB box = new AABB(center, center).inflate(radius, 2.5D, radius);
        // 水平距离判定圆形范围。
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!isValidTarget(victim)) continue;
            Vec3 offset = victim.position().subtract(center).multiply(1.0D, 0.0D, 1.0D);
            if (offset.length() > radius + victim.getBbWidth() * 0.5D) continue;
            if (victim.hurtOrSimulate(damageSources().mobAttack(this), damage)) {
                Vec3 push = offset;
                if (push.lengthSqr() < 0.01D) push = victim.position().subtract(position());
                victim.knockback(knockback, -push.x, -push.z);
                victim.push(0.0D, lift, 0.0D);
                victim.hurtMarked = true;
                hits++;
            }
        }
        return hits;
    }

    // 投出王戟：离手点按动画右手前上方位置换算，朝释放时的目标飞行。
    private void throwWeapon(LivingEntity target) {
        double yaw = Math.toRadians(getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 start = position().add(0, 43.0D / 16.0D, 0).add(forward.scale(11.0D / 16.0D)).add(right.scale(8.0D / 16.0D));
        ThrownRoyalWeapon weapon = new ThrownRoyalWeapon(ModEntities.THROWN_ROYAL_WEAPON.get(), level());
        weapon.setOwner(this);
        weapon.setPos(start.x, start.y, start.z);
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55D, 0).subtract(start);
        weapon.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.04D, aim.z, 1.6F, 0.0F);
        level().addFreshEntity(weapon);
        playSound(SoundEvents.TRIDENT_THROW.value(), 1.6F, 0.7F);
    }

    // 引雷仪式：首个事件改变天气，随后三波落雷各自锁定当时目标脚下。
    private void stormEvent(ServerLevel level, int index, LivingEntity target) {
        if (index == 0) {
            var weather = level.getWeatherData();
            weather.setClearWeatherTime(0);
            weather.setRainTime(6000);
            weather.setThunderTime(6000);
            weather.setRaining(true);
            weather.setThundering(true);
            playSound(SoundEvents.TRIDENT_THUNDER.value(), 2.0F, 0.65F);
            return;
        }
        if (target != null) RoyalStormWave.spawn(this, target);
    }

    // 遗石雷殛：第几块悬浮石向高空射出雷光，分配给不同敌人并预判其移动。
    private void fireRelic(ServerLevel level, int stone, LivingEntity target, float attack) {
        if (stone == 0) collectRelicTargets(level, target);
        if (relicTargets.isEmpty()) return;
        LivingEntity victim = relicTargets.get(stone % relicTargets.size());
        if (!victim.isAlive()) victim = relicTargets.get(0);
        Vec3 velocity = victim.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).scale(ThunderFx.RELIC_STRIKE_TICK * 0.6D);
        if (velocity.length() > 3.0D) velocity = velocity.normalize().scale(3.0D);
        Vec3 aim = victim.position().add(velocity);
        // 同一目标被多道雷锁定时，后续落点向周围散开封堵走位。
        if (stone >= relicTargets.size()) {
            double angle = getRandom().nextDouble() * Math.PI * 2.0D;
            aim = aim.add(Math.cos(angle) * 1.8D, 0.0D, Math.sin(angle) * 1.8D);
        }
        Vec3 point = ThunderFx.floor(level, aim);
        ThunderFx.relicBolt(level, this, stone, stonePosition(stone), point, attack * 1.1F, 1.8D);
        LogUtils.getLogger().debug("Thunder King relic fire: stone={}, victim={}, point={}", stone, victim.getUUID(), point);
    }

    // 王戟插地：插地点冲击，三条路径（正前、左前、右前）由近及远依次落雷。
    private void plantWeapon(ServerLevel level, float attack) {
        Vec3 plant = ThunderFx.floor(level, frontPoint(3.0D));
        ThunderFx.slam(level, this, plant, 0.8F);
        int hits = areaHit(level, plant, 2.2D, attack * 1.2F, 0.6D, 0.4D);
        double yaw = Math.toRadians(getYRot());
        int[] order = {1, 0, 2};
        double[] angles = {-32.0D, 0.0D, 32.0D};
        // 每条路径六道雷，相邻间隔二点三格，延迟逐道递增。
        for (int path = 0; path < 3; path++) {
            double a = yaw + Math.toRadians(angles[path]);
            Vec3 dir = new Vec3(-Math.sin(a), 0.0D, Math.cos(a));
            Vec3 previous = plant;
            for (int j = 0; j < 6; j++) {
                Vec3 point = ThunderFx.floor(level, position().add(dir.scale(3.6D + j * 2.3D)).add(0.0D, 1.5D, 0.0D));
                int delay = 6 + j * 3 + order[path];
                ThunderFx.laneBolt(level, this, previous, point, delay, attack * 0.9F + 2.0F, 1.7D);
                previous = point;
            }
        }
        playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
        LogUtils.getLogger().debug("Thunder King ground plant: plant={}, hits={}", plant, hits);
    }

    // 收集二十四格内的敌人，主目标优先，其余按距离排序。
    private void collectRelicTargets(ServerLevel level, LivingEntity target) {
        relicTargets.clear();
        if (isValidTarget(target)) relicTargets.add(target);
        List<LivingEntity> others = new ArrayList<>();
        // 只选择玩家与正在攻击君王的生物作为额外目标。
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(24.0D, 8.0D, 24.0D))) {
            if (entity == target || !isValidTarget(entity) || !hasLineOfSight(entity)) continue;
            if (entity instanceof Player || entity.getLastHurtMob() == this) others.add(entity);
        }
        others.sort(Comparator.comparingDouble(this::distanceToSqr));
        relicTargets.addAll(others);
    }

    // 估算悬浮石的世界坐标：模型绕竖轴按（180 - 身体朝向）旋转。
    private Vec3 stonePosition(int stone) {
        double[] p = STONE_PIVOTS[stone];
        double angle = Math.toRadians(180.0D - yBodyRot);
        double x = p[0] / 16.0D, z = p[2] / 16.0D;
        double rx = x * Math.cos(angle) + z * Math.sin(angle);
        double rz = -x * Math.sin(angle) + z * Math.cos(angle);
        return position().add(rx, p[1] / 16.0D + 0.6D, rz);
    }

    // 原版近战任务只用来起手，伤害在招式事件刻统一结算。
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (skill != null || !(target instanceof LivingEntity living)) return false;
        ThunderSkill next = MELEE_ROTATION[meleeIndex];
        meleeIndex = (meleeIndex + 1) % MELEE_ROTATION.length;
        startSkill(next, living);
        return true;
    }

    // 玩家开始和结束追踪实体时同步增删血条。
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    // 战斗控制器播放触发招式并按招式倍速播放，移动控制器只驱动下肢步态。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<ThunderKingEntity> controller = new AnimationController<ThunderKingEntity>("combat", 0,
                state -> {
                    ThunderKingEntity entity = (ThunderKingEntity) state.animatable();
                    ThunderSkill current = ThunderSkill.byId(entity.getSwingKind());
                    double age = entity.level().getGameTime() - entity.getSwingStart();
                    float speed = 1.0F;
                    if (current != null && age >= 0.0D && age <= current.duration) speed = (float) current.speed;
                    state.setControllerSpeed(speed);
                    // 触发动作由 GeckoLib 网络状态维护，未结束前不被待机覆盖。
                    boolean triggered = state.controller().isPlayingTriggeredAnimation()
                            || state.controller().getCurrentRawAnimation() != null
                            && !state.isCurrentAnimation(IDLE)
                            && !state.controller().hasAnimationFinished();
                    if (triggered) return PlayState.CONTINUE;
                    state.controller().stopTriggeredAnimation();
                    state.setControllerSpeed(1.0F);
                    return state.setAndContinue(IDLE);
                });
        // 注册全部招式动画为可触发动画。
        for (ThunderSkill s : ThunderSkill.values()) {
            controller.triggerableAnim(s.animation, RawAnimation.begin().thenPlay(ANIMATION_PREFIX + s.animation));
        }
        controller.receiveTriggeredAnimations();
        controllers.add(controller);

        AnimationController<ThunderKingEntity> locomotion = new AnimationController<>("locomotion", 0,
                state -> {
                    ThunderKingEntity entity = (ThunderKingEntity) state.animatable();
                    boolean walking = entity.onGround() && state.isMoving();
                    if (walking != entity.lastWalkState) {
                        entity.lastWalkState = walking;
                        LogUtils.getLogger().debug("Thunder King walk state: walking={}, walkSpeed={}",
                                walking, entity.walkAnimation.speed());
                    }
                    if (!walking) return PlayState.STOP;
                    double blocksPerSecond = entity.walkAnimation.speed() * 5.0D;
                    state.setControllerSpeed((float) Mth.clamp(blocksPerSecond / WALK_DESIGN_BLOCKS_PER_SECOND, 0.5D, 1.6D));
                    return state.setAndContinue(WALK_LEGS);
                });
        controllers.add(locomotion);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
