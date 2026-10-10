package org.hp.thunderrelics.entity;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.ModEntities;
import org.joml.Vector3f;
import org.joml.Vector3fc;

// 雷霆特效载体：服务端生成并同步起止点与时刻，客户端按种类绘制网格；遗石雷殛在落雷刻结算伤害。
public final class ThunderFx extends Entity {
    // 命中天雷、砸地冲击波、遗石雷殛三种特效。
    public static final int HIT_BOLT = 0;
    public static final int SLAM = 1;
    public static final int RELIC_BOLT = 2;
    // 遗石雷殛从发射到落雷的刻数，期间显示预警圈。
    public static final int RELIC_STRIKE_TICK = 14;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ThunderFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(ThunderFx.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3fc> ORIGIN = SynchedEntityData.defineId(ThunderFx.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ThunderFx.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PARAM = SynchedEntityData.defineId(ThunderFx.class, EntityDataSerializers.FLOAT);
    private UUID ownerId;
    private float damage;
    private double radius;

    public ThunderFx(EntityType<? extends ThunderFx> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // 生成一个特效实体，位置即落点，起点单独同步。
    private static ThunderFx create(ServerLevel level, int kind, Vec3 origin, Vec3 target, Entity owner, float param) {
        ThunderFx fx = new ThunderFx(ModEntities.THUNDER_FX.get(), level);
        fx.setPos(target.x, target.y, target.z);
        fx.entityData.set(KIND, kind);
        fx.entityData.set(START, level.getGameTime());
        fx.entityData.set(ORIGIN, new Vector3f((float) origin.x, (float) origin.y, (float) origin.z));
        fx.entityData.set(OWNER, owner.getId());
        fx.entityData.set(PARAM, param);
        fx.ownerId = owner.getUUID();
        return fx;
    }

    // 近战命中时从天而降的雷柱，只负责画面，伤害由攻击者结算。
    public static void hitBolt(ServerLevel level, Entity owner, Vec3 target) {
        ThunderFx fx = create(level, HIT_BOLT, target.add(0, 16, 0), target, owner, 1.0F);
        level.addFreshEntity(fx);
        fx.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4F, 1.25F);
    }

    // 砸地冲击波，参数为整体尺寸倍率。
    public static void slam(ServerLevel level, Entity owner, Vec3 center, float scale) {
        ThunderFx fx = create(level, SLAM, center, center, owner, scale);
        level.addFreshEntity(fx);
        fx.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.6F + scale * 0.4F, 1.35F - scale * 0.2F);
        fx.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.9F + scale * 0.3F, 1.2F);
    }

    // 遗石雷殛：从悬浮石射向高空，延迟后劈落在锁定点；参数为悬浮石编号。
    public static void relicBolt(ServerLevel level, LivingEntity owner, int stone, Vec3 origin, Vec3 target,
                                 float damage, double radius) {
        ThunderFx fx = create(level, RELIC_BOLT, origin, target, owner, stone);
        fx.damage = damage;
        fx.radius = radius;
        level.addFreshEntity(fx);
        fx.playSound(SoundEvents.TRIDENT_THUNDER.value(), 1.1F, 1.6F + stone * 0.08F);
    }

    // 向下寻找可站立表面，让落点贴地。
    public static Vec3 floor(Level level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at.x, at.y + 1.0D, at.z);
        // 最多向下搜索二十四格。
        for (int dy = 0; dy < 24 && pos.getY() - dy > level.getMinY(); dy++) {
            BlockPos below = pos.below(dy);
            var shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty()) {
                return new Vec3(at.x, below.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) + 0.03D, at.z);
            }
        }
        return at;
    }

    public int kind() {
        return entityData.get(KIND);
    }

    public int ownerEntityId() {
        return entityData.get(OWNER);
    }

    public float param() {
        return entityData.get(PARAM);
    }

    public Vec3 origin() {
        Vector3fc v = entityData.get(ORIGIN);
        return new Vec3(v.x(), v.y(), v.z());
    }

    // 以世界时间计算的特效年龄，客户端插值使用帧间刻度。
    public double age(float partialTick) {
        return level().getGameTime() + partialTick - entityData.get(START);
    }

    // 各特效的存续刻数。
    public static int life(int kind) {
        if (kind == SLAM) return 30;
        if (kind == RELIC_BOLT) return RELIC_STRIKE_TICK + 26;
        return 18;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, HIT_BOLT);
        builder.define(START, 0L);
        builder.define(ORIGIN, new Vector3f());
        builder.define(OWNER, -1);
        builder.define(PARAM, 1.0F);
    }

    // 服务端按寿命清理，遗石雷殛在落雷刻做一次范围伤害。
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        int age = (int) age(0.0F);
        if (age >= life(kind())) {
            discard();
            return;
        }
        if (kind() == RELIC_BOLT && age == RELIC_STRIKE_TICK) strikeRelic();
    }

    // 落雷时对半径内的敌对生物造成伤害并短暂缓慢，同一道雷每个目标只结算一次。
    private void strikeRelic() {
        ServerLevel level = (ServerLevel) level();
        Entity owner = null;
        if (ownerId != null) owner = level.getEntity(ownerId);
        playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.8F, 1.4F);
        playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5F, 1.0F);
        if (!(owner instanceof LivingEntity caster) || !caster.isAlive()) return;
        AABB area = new AABB(getX() - radius, getY() - 0.5D, getZ() - radius,
                getX() + radius, getY() + 3.0D, getZ() + radius);
        int hits = 0;
        // 圆形范围判定，排除施法者、友军与创造或旁观玩家。
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (victim == caster || !victim.isAlive() || caster.isAlliedTo(victim)) continue;
            if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            double dx = victim.getX() - getX();
            double dz = victim.getZ() - getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            DamageSource source = damageSources().indirectMagic(this, caster);
            if (victim.hurtOrSimulate(source, damage)) {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
                hits++;
            }
        }
        LogUtils.getLogger().debug("Thunder relic bolt strike: stone={}, pos={}, hits={}, damage={}",
                param(), position(), hits, damage);
    }

    // 特效实体不可被攻击。
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    // 特效短命，不写入存档内容，区块重载后直接按寿命清理。
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    // 特效实体不保存到区块，避免卸载后残留。
    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
