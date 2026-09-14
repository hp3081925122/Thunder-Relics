package org.hp.thunderrelics.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import org.hp.thunderrelics.ModEntities;
import org.hp.thunderrelics.ThunderrelicsConfig;
import org.hp.thunderrelics.effect.DecorativeLightning;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.UUID;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

// 雷霆君王的服务端实体，攻击时触发持械模型的横斩动画并持续面向目标。
public final class ThunderKingEntity extends Monster implements GeoEntity {
    private static final String ANIMATION_PREFIX = "animation.thunder_king.";
    // 近战和投掷攻击周期缩短到原来的八成，约等于频率提高百分之二十五。
    private static final double COMBAT_RATE = 0.80D;
    private static final double COMBAT_ANIMATION_SPEED = 1.0D / COMBAT_RATE;
    // 使用血条唯一编号识别专属材质，不依赖可被命名牌修改的名称。
    private static final EntityDataAccessor<Optional<UUID>> BOSS_BAR_ID = SynchedEntityData.defineId(
            ThunderKingEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    // 同步技能类型与起手世界时间，让刀光只在对应挥砍窗口出现。
    private static final EntityDataAccessor<Integer> SWING_KIND = SynchedEntityData.defineId(
            ThunderKingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SWING_START = SynchedEntityData.defineId(
            ThunderKingEntity.class, EntityDataSerializers.LONG);
    // 行走和攻击共用控制器，攻击触发后优先播放完整攻击动作。
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIMATION_PREFIX + "walk");
    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
    private static final RawAnimation CLEAVE = RawAnimation.begin().thenPlay(ANIMATION_PREFIX + "cleave");
    private static final RawAnimation DOUBLE_CLEAVE = RawAnimation.begin().thenPlay(ANIMATION_PREFIX + "double_cleave");
    private static final RawAnimation HEAVY_SLAM = RawAnimation.begin().thenPlay(ANIMATION_PREFIX + "heavy_slam");
    // 双手重劈在头顶停顿六个游戏刻，再沿身体中线快速下劈。
    private static final RawAnimation TWO_HANDED_SLAM = RawAnimation.begin().thenPlay(ANIMATION_PREFIX + "two_handed_slam");
    // 投标枪动作的脱手与结束时机会按战斗速率缩短。
    private static final RawAnimation JAVELIN_THROW = RawAnimation.begin().thenPlay(ANIMATION_PREFIX + "javelin_throw");
    // 转身动画为一秒点二，服务端用二十四刻锁定导航和技能，和 GeckoLib 的二十帧每秒一致。
    private static final RawAnimation TURN = RawAnimation.begin().thenPlay(ANIMATION_PREFIX + "turn");
    private static final RawAnimation TURN_LEFT = RawAnimation.begin().thenPlay(ANIMATION_PREFIX + "turn_left");
    private static final int TURN_ATTACK_TYPE = 6;
    private static final int TURN_DURATION = 24;
    private static final float TURN_TRIGGER_ANGLE = 50.0F;
    private static final int TURN_COOLDOWN = 8;
    private int throwCooldown = fasterTicks(40);
    // 引雷再次发动间隔按原值八成，完整举戟仪式仍持续六秒。
    private int stormCooldown = fasterTicks(100);
    private static final RawAnimation STORM = RawAnimation.begin().thenPlay(ANIMATION_PREFIX + "storm_ritual");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int attackCounter;
    private int attackType;
    private int nextAttackType;
    private UUID attackTarget;
    // 记录转身结束时应对齐的目标方向，目标丢失时仍能完成当前转身。
    private float turnTargetYaw;
    private int turnCooldown;

    // 将与动画长度对应的游戏刻按统一比例缩短，并至少保留一刻避免零时长攻击。
    private static int fasterTicks(int original) {
        return Math.max(1, (int) Math.round(original * COMBAT_RATE));
    }

    public ThunderKingEntity(EntityType<? extends ThunderKingEntity> type, Level level) {
        super(type, level);
        xpReward = 25;
        // 配置在实体注册后才可读取；实例创建时同步已加载配置中的生命值和护甲。
        if (ThunderrelicsConfig.SPEC.isLoaded()) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(
                    ThunderrelicsConfig.THUNDER_KING_MAX_HEALTH.get().doubleValue());
            getAttribute(Attributes.ARMOR).setBaseValue(
                    ThunderrelicsConfig.THUNDER_KING_ARMOR.get().doubleValue());
            setHealth(getMaxHealth());
        }
        // 客户端通过同步字段将原版血条事件关联到本实体。
        if (!level.isClientSide) entityData.set(BOSS_BAR_ID, Optional.of(bossEvent.getId()));
    }

    // 在实体初始化阶段定义网络同步字段。
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BOSS_BAR_ID, Optional.empty());
        entityData.define(SWING_KIND, -1);
        entityData.define(SWING_START, -1L);
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
        return entityData.get(BOSS_BAR_ID);
    }

    // 接引雷霆期间将所有实际受到的伤害减半，客户端预测仍由服务端结果校正。
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!level().isClientSide && attackType == 5 && attackCounter > 0) amount *= 0.5F;
        return super.hurt(source, amount);
    }

    // 实体属性注册发生在 Forge 配置文件加载前，因此注册阶段只能读取已核对的默认值。
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

    // 使用原版近战目标、索敌和基础游荡目标，保持新模组的依赖面最小。
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false) {
            // 挥击过程中停止寻路推进，避免贴身推挤并使命中距离稳定。
            @Override
            public void tick() {
                if (attackCounter > 0) getNavigation().stop();
                else super.tick();
            }

            // 长柄武器在三点五格内开始预备，命中帧再重新检查距离。
            @Override
            protected double getAttackReachSqr(LivingEntity target) {
                return 3.5D * 3.5D;
            }
        });
        goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 目标存在时每刻更新头、身体和实体朝向；转身窗口交给动画根骨骼平滑完成。
    @Override
    public void tick() {
        super.tick();
        // 服务端同步血量和名称，覆盖受伤、治疗以及命名后的显示变化。
        if (!level().isClientSide) {
            bossEvent.setProgress(getHealth() / getMaxHealth());
            bossEvent.setName(getDisplayName());
            if (throwCooldown > 0) throwCooldown--;
            if (stormCooldown > 0) stormCooldown--;
            if (turnCooldown > 0) turnCooldown--;
        }
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - getX()), target.getZ() - getZ()));
            boolean turnActive = getSwingKind() == TURN_ATTACK_TYPE
                    && (level().isClientSide || attackType == TURN_ATTACK_TYPE && attackCounter > 0);
            // 只有明显偏转且当前没有技能时才进入转身，避免目标小幅移动造成动画抖动。
            if (!level().isClientSide && !turnActive && attackCounter == 0 && turnCooldown == 0) {
                float delta = wrapDegrees(yaw - getYRot());
                if (Math.abs(delta) >= TURN_TRIGGER_ANGLE) {
                    startTurn(target, yaw, delta);
                    turnActive = true;
                }
            }
            // 转身期间保留实体原朝向，动画根骨骼负责旋转；其他状态继续实时锁定目标。
            if (turnActive) {
                getNavigation().stop();
            } else {
                getLookControl().setLookAt(target, 30.0F, 30.0F);
                setYRot(yaw);
                setYHeadRot(yaw);
                setYBodyRot(yaw);
            }
        }
        // 转身单独处理，避免落入近战命中帧、投掷或引雷分支。
        if (!level().isClientSide && attackType == TURN_ATTACK_TYPE && attackCounter > 0) {
            int elapsed = TURN_DURATION - attackCounter + 1;
            getNavigation().stop();
            if (elapsed >= TURN_DURATION) {
                LivingEntity turnTarget = getTarget();
                float finalYaw = turnTarget != null && turnTarget.isAlive() ? targetYaw(turnTarget, this) : turnTargetYaw;
                setYRot(finalYaw);
                setYHeadRot(finalYaw);
                setYBodyRot(finalYaw);
                attackCounter = 0;
                attackType = -1;
                entityData.set(SWING_KIND, -1);
                entityData.set(SWING_START, -1L);
                turnCooldown = TURN_COOLDOWN;
            } else {
                attackCounter--;
            }
            return;
        }
        // 大范围技能优先于投掷，起手时仍持续看向目标。
        if (!level().isClientSide && attackCounter == 0 && stormCooldown == 0 && target != null
                && target.isAlive() && distanceToSqr(target) <= 26 * 26 && hasLineOfSight(target)
                && !isAlliedTo(target)) startStorm(target);
        // 中距离有视线时启动投掷，近距离仍保留原有近战连招。
        if (!level().isClientSide && attackCounter == 0 && throwCooldown == 0
                && target != null && target.isAlive() && distanceToSqr(target) > 16.0D
                && distanceToSqr(target) <= 256.0D && hasLineOfSight(target) && !isAlliedTo(target)) {
            attackType = 4;
            entityData.set(SWING_KIND, 4);
            entityData.set(SWING_START, level().getGameTime());
            attackCounter = fasterTicks(52);
            attackTarget = target.getUUID();
            throwCooldown = fasterTicks(160);
            getNavigation().stop();
            triggerAnim("combat", "javelin_throw");
        }
        // 转向完成后按动画命中帧结算；目标换人或失效不会把伤害转移给新目标。
        if (!level().isClientSide && attackCounter > 0) {
            // 引雷仪式保留六秒蓄力，其余攻击使用加速后的动画时长。
            int duration = attackType == 5 ? 120 : attackType == 4 ? fasterTicks(52) : attackType == 3 ? fasterTicks(46) : attackType == 1 ? fasterTicks(47) : attackType == 2 ? fasterTicks(41) : fasterTicks(33);
            int elapsed = duration - attackCounter + 1;
            // 单次横斩在迈步与后脚跟进阶段推进实体，使用碰撞移动避免穿墙，并保留近身停距。
            if (attackType == 0 && onGround() && target != null && target.isAlive()
                    && target.getUUID().equals(attackTarget) && !isAlliedTo(target) && hasLineOfSight(target)) {
                double phase = (double) elapsed / duration;
                double speed = phase >= 0.12D && phase <= 0.44D ? 0.14D
                        : phase >= 0.62D && phase <= 0.9D ? 0.07D : 0.0D;
                Vec3 toward = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
                double distance = toward.length();
                double stopDistance = (getBbWidth() + target.getBbWidth()) * 0.5D + 0.5D;
                if (speed > 0.0D && distance > stopDistance) {
                    move(net.minecraft.world.entity.MoverType.SELF,
                            toward.scale(Math.min(speed, distance - stopDistance) / distance));
                }
            }
            // 双手向上托举完成时改变天气，随后三波分别固定当时目标脚下的位置。
            if (attackType == 5) {
                if (elapsed == 42 && level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.setWeatherParameters(0, 6000, true, true);
                    playSound(SoundEvents.TRIDENT_THUNDER, 2, .65F);
                }
                if ((elapsed == 44 || elapsed == 64 || elapsed == 84) && target != null && target.isAlive()
                        && target.getUUID().equals(attackTarget)) RoyalStormWave.spawn(this, target);
                // 接引阶段每秒恢复最大生命值的百分之一，六秒仪式最多触发六次。
                if (elapsed % 20 == 0) {
                    heal(getMaxHealth() * 0.01F);
                    LogUtils.getLogger().debug("Thunder King storm relay healing: tick={}, health={}", elapsed, getHealth());
                }
            }
            // 离手位置按动画右手的前上方位置换算，弹体朝释放时的目标飞行。
            if (attackType == 4 && elapsed == fasterTicks(24) && target != null && target.isAlive()
                    && target.getUUID().equals(attackTarget) && hasLineOfSight(target)) {
                double yaw = Math.toRadians(getYRot());
                Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
                Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
                Vec3 start = position().add(0, 43.0D / 16.0D, 0)
                        .add(forward.scale(11.0D / 16.0D)).add(right.scale(8.0D / 16.0D));
                ThrownRoyalWeapon weapon = new ThrownRoyalWeapon(ModEntities.THROWN_ROYAL_WEAPON.get(), level());
                weapon.setOwner(this);
                weapon.setPos(start.x, start.y, start.z);
                Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55D, 0).subtract(start);
                weapon.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.04D, aim.z, 1.6F, 0.0F);
                level().addFreshEntity(weapon);
                playSound(SoundEvents.TRIDENT_THROW, 1.6F, 0.7F);
            }
            // 武器恢复可见时给出召回提示，投掷阶段不会触发近战伤害窗口。
            if (attackType == 4 && elapsed == fasterTicks(44)) playSound(SoundEvents.TRIDENT_RETURN, 1.0F, 0.8F);
            boolean strike = attackType < 4 && (attackType == 3 ? elapsed == fasterTicks(22) : attackType == 2 ? elapsed == fasterTicks(20) : elapsed == fasterTicks(15) || attackType == 1 && elapsed == fasterTicks(27));
            if (strike && target != null && target.getUUID().equals(attackTarget) && target.isAlive()) {
                Vec3 offset = target.position().subtract(position());
                double reach = attackType >= 2 ? 3.8D : 4.0D;
                boolean inReach = offset.horizontalDistance() <= reach && Math.abs(offset.y) < 3
                        && hasLineOfSight(target) && !isAlliedTo(target)
                        && !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
                float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * (attackType >= 2 ? 1.5F : 1.0F);
                boolean hit = inReach && target.hurt(damageSources().mobAttack(this), damage);
                if (hit) {
                    target.knockback(attackType >= 2 ? 1.0D : 0.5D, -offset.x, -offset.z);
                    target.hurtMarked = true;
                    // Boss 每次近战命中额外落下一道装饰雷霆，雷霆伤害独立于挥砍伤害。
                    // 近战命中附雷固定落在目标脚下。
                    DecorativeLightning.strike(level(), target.position(),
                            this, target, 3.0F, 0);
                }
                // 每次命中窗口仅记录一次调试，便于区分躲开、遮挡和实际伤害。
                LogUtils.getLogger().debug("Thunder King strike: type={}, tick={}, inReach={}, hit={}, damage={}",
                        attackType, elapsed, inReach, hit, damage);
            }
            attackCounter--;
        }
    }

    // 计算实体正面指向目标的 Minecraft 偏航角，统一服务端转身和收招对齐逻辑。
    private static float targetYaw(LivingEntity target, ThunderKingEntity self) {
        return (float) Math.toDegrees(Math.atan2(-(target.getX() - self.getX()), target.getZ() - self.getZ()));
    }

    // 保留带符号的最短偏航差，正值和负值分别选择左右转身动画。
    private static float wrapDegrees(float degrees) {
        while (degrees > 180.0F) degrees -= 360.0F;
        while (degrees < -180.0F) degrees += 360.0F;
        return degrees;
    }

    // 启动转身并冻结导航；左右动画的选择与最短偏航差保持一致。
    private void startTurn(LivingEntity target, float yaw, float delta) {
        attackType = TURN_ATTACK_TYPE;
        attackCounter = TURN_DURATION;
        attackTarget = target.getUUID();
        turnTargetYaw = yaw;
        entityData.set(SWING_KIND, TURN_ATTACK_TYPE);
        entityData.set(SWING_START, level().getGameTime());
        getNavigation().stop();
        triggerAnim("combat", delta < 0.0F ? "turn" : "turn_left");
        LogUtils.getLogger().debug("Thunder King turn started: delta={}, targetYaw={}", delta, yaw);
    }

    // 原版近战任务只启动动作，取消接触时立即扣血；伤害由上方命中帧统一结算。
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        if (!level().isClientSide && attackCounter == 0 && target instanceof LivingEntity) {
            attackType = nextAttackType;
            entityData.set(SWING_KIND, attackType);
            entityData.set(SWING_START, level().getGameTime());
            nextAttackType = (nextAttackType + 1) % 4;
            attackTarget = target.getUUID();
            attackCounter = attackType == 3 ? fasterTicks(46) : attackType == 1 ? fasterTicks(47) : attackType == 2 ? fasterTicks(41) : fasterTicks(33);
            triggerAnim("combat", attackType == 3 ? "two_handed_slam" : attackType == 1 ? "double_cleave" : attackType == 2 ? "heavy_slam" : "cleave");
            getNavigation().stop();
            LogUtils.getLogger().debug("Thunder King attack started: type={}, target={}", attackType, attackTarget);
            return true;
        }
        return false;
    }

    // 开发测试仍走正常攻击入口与命中计时，只允许无人工智能的测试实体指定招式。
    public void startStorm(LivingEntity target) {
        if (level().isClientSide || attackCounter > 0) return;
        attackType = 5;
        attackCounter = 120;
        attackTarget = target.getUUID();
        // 当前战斗速率下再把引雷间隔翻倍，使该技能释放频率降低百分之五十。
        stormCooldown = fasterTicks(360) * 2;
        entityData.set(SWING_KIND, 5);
        entityData.set(SWING_START, level().getGameTime());
        getNavigation().stop();
        triggerAnim("combat", "storm_ritual");
        LogUtils.getLogger().debug("Thunder King storm started: target={}", attackTarget);
    }

    // 测试入口沿用正式攻击流程。
    public void debugMelee(int kind, LivingEntity target) {
        if (net.neoforged.fml.loading.FMLEnvironment.isProduction() || !Boolean.getBoolean("thunderrelics.debug")
                || level().isClientSide || !isNoAi() || kind < 0 || kind > 3) return;
        nextAttackType = kind;
        setTarget(target);
        doHurtTarget(target);
    }

    // 玩家开始和结束追踪实体时同步增删血条，支持多人各自进出追踪范围。
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    // 卸载、离开范围和实体移除时由原版追踪流程清理玩家血条。
    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    // 地面移动播放行走，停下恢复静止；服务端触发的攻击覆盖行走循环。
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 起手和收招已写入动画，不额外插入控制器过渡，保证服务端命中时刻一致。
        AnimationController<ThunderKingEntity> controller = new AnimationController<>(this, "combat", 0,
                state -> onGround() && state.isMoving() ? state.setAndContinue(WALK) : PlayState.STOP)
                .triggerableAnim("cleave", CLEAVE)
                .triggerableAnim("double_cleave", DOUBLE_CLEAVE)
                .triggerableAnim("heavy_slam", HEAVY_SLAM)
                .triggerableAnim("two_handed_slam", TWO_HANDED_SLAM)
                .triggerableAnim("javelin_throw", JAVELIN_THROW)
                .triggerableAnim("turn", TURN)
                .triggerableAnim("turn_left", TURN_LEFT)
                .triggerableAnim("storm_ritual", STORM);
        // 仅加速近战和投掷触发动画，引雷仪式继续保持完整六秒蓄力。
        controller.setAnimationSpeedHandler(entity -> {
            int kind = entity.getSwingKind();
            double age = entity.level().getGameTime() - entity.getSwingStart();
            boolean accelerated = kind >= 0 && kind <= 4 && age >= 0.0D
                    && age <= (kind == 4 ? fasterTicks(52) : kind == 3 ? fasterTicks(46)
                    : kind == 1 ? fasterTicks(47) : kind == 2 ? fasterTicks(41) : fasterTicks(33));
            return accelerated ? COMBAT_ANIMATION_SPEED : 1.0D;
        });
        controllers.add(controller);
    }

    // GeckoLib 为每个实体提供独立动画缓存，避免不同雷霆君王互相覆盖姿态。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}






