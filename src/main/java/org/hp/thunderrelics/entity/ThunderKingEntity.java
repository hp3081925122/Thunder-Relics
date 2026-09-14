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
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

// 雷霆君王的服务端实体，攻击时触发持械模型的横斩动画并持续面向目标。
public final class ThunderKingEntity extends Monster implements GeoEntity {
    private static final String ANIMATION_PREFIX = "animation.thunder_king.";
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
    // 投标枪动作在第二十四刻脱手，总时长五十二刻。
    private static final RawAnimation JAVELIN_THROW = RawAnimation.begin().thenPlay(ANIMATION_PREFIX + "javelin_throw");
    private int throwCooldown = 40;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int attackCounter;
    private int attackType;
    private int nextAttackType;
    private UUID attackTarget;

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

    // 目标存在时每刻更新头、身体和实体朝向，避免攻击过程中转身滞后。
    @Override
    public void tick() {
        super.tick();
        // 服务端同步血量和名称，覆盖受伤、治疗以及命名后的显示变化。
        if (!level().isClientSide) {
            bossEvent.setProgress(getHealth() / getMaxHealth());
            bossEvent.setName(getDisplayName());
            if (throwCooldown > 0) throwCooldown--;
        }
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - getX()), target.getZ() - getZ()));
            setYRot(yaw);
            setYHeadRot(yaw);
            setYBodyRot(yaw);
        }
        // 中距离有视线时启动投掷，近距离仍保留原有近战连招。
        if (!level().isClientSide && attackCounter == 0 && throwCooldown == 0
                && target != null && target.isAlive() && distanceToSqr(target) > 16.0D
                && distanceToSqr(target) <= 256.0D && hasLineOfSight(target) && !isAlliedTo(target)) {
            attackType = 4;
            entityData.set(SWING_KIND, 4);
            entityData.set(SWING_START, level().getGameTime());
            attackCounter = 52;
            attackTarget = target.getUUID();
            throwCooldown = 160;
            getNavigation().stop();
            triggerAnim("combat", "javelin_throw");
        }
        // 转向完成后按动画命中帧结算；目标换人或失效不会把伤害转移给新目标。
        if (!level().isClientSide && attackCounter > 0) {
            int duration = attackType == 4 ? 52 : attackType == 3 ? 46 : attackType == 1 ? 47 : attackType == 2 ? 41 : 33;
            int elapsed = duration - attackCounter + 1;
            // 离手位置按动画右手的前上方位置换算，弹体朝释放时的目标飞行。
            if (attackType == 4 && elapsed == 24 && target != null && target.isAlive()
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
            if (attackType == 4 && elapsed == 44) playSound(SoundEvents.TRIDENT_RETURN, 1.0F, 0.8F);
            boolean strike = attackType != 4 && (attackType == 3 ? elapsed == 22 : attackType == 2 ? elapsed == 20 : elapsed == 15 || attackType == 1 && elapsed == 27);
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
                    DecorativeLightning.strike(level(), target.position().add(0, target.getBbHeight(), 0),
                            this, target, 3.0F, 0);
                }
                // 每次命中窗口仅记录一次调试，便于区分躲开、遮挡和实际伤害。
                LogUtils.getLogger().debug("Thunder King strike: type={}, tick={}, inReach={}, hit={}, damage={}",
                        attackType, elapsed, inReach, hit, damage);
            }
            attackCounter--;
        }
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
            attackCounter = attackType == 3 ? 46 : attackType == 1 ? 47 : attackType == 2 ? 41 : 33;
            triggerAnim("combat", attackType == 3 ? "two_handed_slam" : attackType == 1 ? "double_cleave" : attackType == 2 ? "heavy_slam" : "cleave");
            getNavigation().stop();
            LogUtils.getLogger().debug("Thunder King attack started: type={}, target={}", attackType, attackTarget);
            return true;
        }
        return false;
    }

    // 开发测试仍走正常攻击入口与命中计时，只允许无人工智能的测试实体指定招式。
    public void debugMelee(int kind, LivingEntity target) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.production || !Boolean.getBoolean("thunderrelics.debug")
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
        controllers.add(new AnimationController<>(this, "combat", 0,
                state -> onGround() && state.isMoving() ? state.setAndContinue(WALK) : PlayState.STOP)
                .triggerableAnim("cleave", CLEAVE)
                .triggerableAnim("double_cleave", DOUBLE_CLEAVE)
                .triggerableAnim("heavy_slam", HEAVY_SLAM)
                .triggerableAnim("two_handed_slam", TWO_HANDED_SLAM)
                .triggerableAnim("javelin_throw", JAVELIN_THROW));
    }

    // GeckoLib 为每个实体提供独立动画缓存，避免不同雷霆君王互相覆盖姿态。
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
