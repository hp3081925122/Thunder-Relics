package org.hp.thunderrelics.entity;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.ModEntities;

// 雷霆特效载体：服务端生成并同步起止点与时刻，客户端按种类绘制网格；遗石雷殛在落雷刻结算伤害。
public final class ThunderFx extends Entity {
    // 命中天雷、砸地冲击波、遗石雷殛三种特效。
    public static final int HIT_BOLT = 0;
    public static final int SLAM = 1;
    public static final int RELIC_BOLT = 2;
    // 插地雷轨：参数为落雷延迟刻数，起点为同一路径上一道雷的位置。
    public static final int LANE_BOLT = 3;
    // 玩家王戟平砍的月牙斩痕，起点为玩家位置，参数为斩痕倾斜方向。
    public static final int PLAYER_SLASH = 4;
    // 雷链：从起点生物跳到落点生物的电弧。
    public static final int CHAIN = 5;
    // 遗石雷殛从发射到落雷的刻数，期间显示预警圈。
    public static final int RELIC_STRIKE_TICK = 14;
    private static final EntityDataAccessor<CompoundTag> DATA = SynchedEntityData.defineId(
            ThunderFx.class, EntityDataSerializers.COMPOUND_TAG);
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
        CompoundTag data = new CompoundTag();
        data.putInt("Kind", kind);
        data.putLong("Start", level.getGameTime());
        data.putDouble("OriginX", origin.x);
        data.putDouble("OriginY", origin.y);
        data.putDouble("OriginZ", origin.z);
        data.putInt("Owner", owner.getId());
        data.putFloat("Param", param);
        fx.entityData.set(DATA, data);
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

    // 插地雷轨的单道雷：先沿地面裂纹蔓延并显示预警，延迟后天雷劈落。
    public static void laneBolt(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 target, int delay,
                                float damage, double radius) {
        ThunderFx fx = create(level, LANE_BOLT, from, target, owner, delay);
        fx.damage = damage;
        fx.radius = radius;
        level.addFreshEntity(fx);
    }

    // 玩家平砍斩痕，落点为命中目标胸口高度。
    public static void playerSlash(ServerLevel level, LivingEntity attacker, Vec3 target, float tilt) {
        ThunderFx fx = create(level, PLAYER_SLASH, attacker.position().add(0, attacker.getBbHeight() * 0.6D, 0), target, attacker, tilt);
        level.addFreshEntity(fx);
    }

    // 雷链电弧，只负责画面。
    public static void chain(ServerLevel level, Entity owner, Vec3 from, Vec3 to) {
        ThunderFx fx = create(level, CHAIN, from, to, owner, 1.0F);
        level.addFreshEntity(fx);
    }

    // 向下寻找可站立表面，让落点贴地。
    public static Vec3 floor(Level level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at.x, at.y + 1.0D, at.z);
        // 最多向下搜索二十四格。
        for (int dy = 0; dy < 24 && pos.getY() - dy > level.getMinBuildHeight(); dy++) {
            BlockPos below = pos.below(dy);
            var shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty()) {
                return new Vec3(at.x, below.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) + 0.03D, at.z);
            }
        }
        return at;
    }

    public int kind() {
        return entityData.get(DATA).getInt("Kind");
    }

    public int ownerEntityId() {
        return entityData.get(DATA).getInt("Owner");
    }

    public float param() {
        return entityData.get(DATA).getFloat("Param");
    }

    public Vec3 origin() {
        CompoundTag data = entityData.get(DATA);
        return new Vec3(data.getDouble("OriginX"), data.getDouble("OriginY"), data.getDouble("OriginZ"));
    }

    // 以世界时间计算的特效年龄，客户端插值使用帧间刻度。
    public double age(float partialTick) {
        return level().getGameTime() + partialTick - entityData.get(DATA).getLong("Start");
    }

    // 各特效的存续刻数。
    public static int life(int kind, float param) {
        if (kind == LANE_BOLT) return (int) param + 18;
        if (kind == SLAM) return 30;
        if (kind == PLAYER_SLASH) return 11;
        if (kind == CHAIN) return 9;
        if (kind == RELIC_BOLT) return RELIC_STRIKE_TICK + 26;
        return 18;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA, new CompoundTag());
    }

    // 服务端按寿命清理，遗石雷殛在落雷刻做一次范围伤害。
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        int age = (int) age(0.0F);
        if (age >= life(kind(), param())) {
            discard();
            return;
        }
        if (kind() == RELIC_BOLT && age == RELIC_STRIKE_TICK) strikeArea("relic");
        if (kind() == LANE_BOLT && age == (int) param()) strikeArea("lane");
    }

    // 落雷时对半径内的敌对生物造成伤害并短暂缓慢，同一道雷每个目标只结算一次。
    private void strikeArea(String type) {
        ServerLevel level = (ServerLevel) level();
        Entity owner = ownerId == null ? null : level.getEntity(ownerId);
        // 雷轨数量多，音量减半避免叠加刺耳。
        float volume = kind() == LANE_BOLT ? 0.5F : 1.0F;
        playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.8F * volume, 1.4F);
        playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5F * volume, 1.0F);
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
            if (victim.hurt(source, damage)) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                hits++;
            }
        }
        LogUtils.getLogger().debug("Thunder {} bolt strike: param={}, pos={}, hits={}, damage={}",
                type, param(), position(), hits, damage);
    }

    // 特效实体不可被攻击。
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    // 特效短命，不写入存档内容，区块重载后直接按寿命清理。
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    // 特效实体不保存到区块，避免卸载后残留。
    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
