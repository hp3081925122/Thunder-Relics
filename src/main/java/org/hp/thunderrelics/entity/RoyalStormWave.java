package org.hp.thunderrelics.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.ModEntities;

// 一波共享一个实体，同步固定落点和世界时间，不给每根电弧创建实体。
public final class RoyalStormWave extends Entity {
    // 预警收缩从一秒半压到约一秒，圆圈更快收束后立刻落雷。
    public static final int WARNING_TICKS = 21;
    public static final int LIFE_TICKS = 64;
    private static final EntityDataAccessor<CompoundTag> DATA = SynchedEntityData.defineId(RoyalStormWave.class, EntityDataSerializers.COMPOUND_TAG);
    private UUID ownerId;
    private float attackDamage;
    private CompoundTag cachedData;
    private List<Vec3> cachedPoints = List.of();

    public RoyalStormWave(EntityType<? extends RoyalStormWave> type, Level level) { super(type, level); }

    // 固定玩家脚下落点，并在大范围内挑选互相留有空隙的随机落点。
    public static void spawn(ThunderKingEntity boss, LivingEntity target) {
        ServerLevel level = (ServerLevel) boss.level();
        RoyalStormWave wave = new RoyalStormWave(ModEntities.ROYAL_STORM_WAVE.get(), level);
        wave.setPos(boss.getX(), boss.getY(), boss.getZ());
        wave.ownerId = boss.getUUID();
        wave.attackDamage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        List<Vec3> points = new ArrayList<>();
        points.add(findFloor(level, target.getX(), target.getY() + .2, target.getZ()));
        // 每波最多三十二处，拒绝过密的随机点，玩家可以从标记间穿行。
        for (int tries = 0; points.size() < 32 && tries < 300; tries++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(level.random.nextDouble()) * 22;
            Vec3 point = findFloor(level, boss.getX() + Math.cos(angle) * radius,
                    target.getY() + 4, boss.getZ() + Math.sin(angle) * radius);
            if (points.stream().noneMatch(p -> p.distanceToSqr(point) < 9)) points.add(point);
        }
        // 坐标一次同步后保持不变，避免倒计时中追着玩家移动。
        CompoundTag data = new CompoundTag();
        data.putLong("Start", level.getGameTime());
        ListTag list = new ListTag();
        for (Vec3 point : points) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("X", point.x); entry.putDouble("Y", point.y); entry.putDouble("Z", point.z);
            list.add(entry);
        }
        data.put("Points", list);
        wave.entityData.set(DATA, data);
        level.addFreshEntity(wave);
        com.mojang.logging.LogUtils.getLogger().debug("Royal storm wave: owner={}, points={}, fixedTarget={}", boss.getUUID(), points.size(), points.get(0));
    }

    // 投掷武器命中方块时，在命中点周围八格内生成一圈范围预警，伤害仍按持有者攻击力计算。
    public static void spawnImpactArea(LivingEntity owner, Vec3 impact, double radius, float baseDamage) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        RoyalStormWave wave = new RoyalStormWave(ModEntities.ROYAL_STORM_WAVE.get(), level);
        wave.setPos(impact.x, impact.y, impact.z);
        wave.ownerId = owner.getUUID();
        wave.attackDamage = baseDamage;
        List<Vec3> points = new ArrayList<>();
        points.add(findFloor(level, impact.x, impact.y + .2D, impact.z));
        // 范围内保留十六个随机落点，点之间至少间隔两格，避免预警完全重叠。
        for (int tries = 0; points.size() < 17 && tries < 240; tries++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(level.random.nextDouble()) * radius;
            Vec3 point = findFloor(level, impact.x + Math.cos(angle) * distance,
                    impact.y + 4.0D, impact.z + Math.sin(angle) * distance);
            if (points.stream().noneMatch(existing -> existing.distanceToSqr(point) < 4.0D)) points.add(point);
        }
        CompoundTag data = new CompoundTag();
        data.putLong("Start", level.getGameTime());
        ListTag list = new ListTag();
        for (Vec3 point : points) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("X", point.x);
            entry.putDouble("Y", point.y);
            entry.putDouble("Z", point.z);
            list.add(entry);
        }
        data.put("Points", list);
        wave.entityData.set(DATA, data);
        level.addFreshEntity(wave);
        com.mojang.logging.LogUtils.getLogger().debug("Royal impact wave: owner={}, points={}, center={}",
                owner.getUUID(), points.size(), points.get(0));
    }

    // 从目标附近向下投影到碰撞表面，支持楼层和台阶，不把预警贴在空中。
    private static Vec3 findFloor(ServerLevel level, double x, double y, double z) {
        BlockPos at = BlockPos.containing(x, y, z);
        for (int dy = 0; dy < 32 && at.getY() - dy > level.getMinBuildHeight(); dy++) {
            BlockPos pos = at.below(dy);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) return new Vec3(x, pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) + .035, z);
        }
        return new Vec3(x, y, z);
    }

    @Override
    protected void defineSynchedData() { entityData.define(DATA, new CompoundTag()); }

    // 渲染缓存只在同步数据变化时重建，避免每帧反复解码所有落点。
    public List<Vec3> points() {
        CompoundTag data = entityData.get(DATA);
        if (cachedData != data) {
            cachedData = data;
            ArrayList<Vec3> points = new ArrayList<>();
            ListTag entries = data.getList("Points", 10);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag point = entries.getCompound(i);
                points.add(new Vec3(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z")));
            }
            cachedPoints = List.copyOf(points);
        }
        return cachedPoints;
    }

    public double age(float partialTick) { return level().getGameTime() + partialTick - entityData.get(DATA).getLong("Start"); }

    // 伤害只在服务端两个时点执行，同一波重叠区域不会重复扣血。
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        int age = (int) age(0);
        if (age >= LIFE_TICKS || ownerId == null || !(((ServerLevel) level()).getEntity(ownerId) instanceof LivingEntity owner) || !owner.isAlive()) { discard(); return; }
        if (age == WARNING_TICKS || age == WARNING_TICKS + 10) {
            boolean bolt = age == WARNING_TICKS;
            double radius = bolt ? 1.55 : 2.8;
            HashSet<UUID> hit = new HashSet<>();
            for (Vec3 point : points()) {
                AABB area = new AABB(point.x - radius, point.y - .5, point.z - radius, point.x + radius, point.y + 3, point.z + radius);
                for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, area)) {
                    if (victim == owner || !victim.isAlive() || owner.isAlliedTo(victim)
                            || victim instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                    double dx = victim.getX() - point.x, dz = victim.getZ() - point.z;
                    if (dx * dx + dz * dz > radius * radius || !hit.add(victim.getUUID())) continue;
                    float before=victim.getHealth();
                    // 落雷与后续电弧都按君王攻击力计算，并整体提高百分之二十。
                    boolean applied=victim.hurt(damageSources().indirectMagic(this, owner), attackDamage * (bolt ? 1.08F : .30F));
                    // 只有实际造成伤害时施加缓慢 I，持续五秒。
                    if (applied) victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
                    // 仅开发模式记录实际扣血，便于核对预警落点与伤害阶段。
                    if(Boolean.getBoolean("thunderrelics.debug"))com.mojang.logging.LogUtils.getLogger().debug(
                            "Royal storm hit: stage={}, target={}, applied={}, healthBefore={}, healthAfter={}, point={}",
                            bolt?"bolt":"arc",victim.getUUID(),applied,before,victim.getHealth(),point);
                }
            }
            playSound(bolt ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.TRIDENT_THUNDER, bolt ? 2.2F : .8F, bolt ? 1.15F : 1.5F);
        }
    }

    // 标准生成包配合同步字段传输预警数据；存档恢复沿用原始时刻，不重新落雷。
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return new ClientboundAddEntityPacket(this); }
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Wave", entityData.get(DATA));
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putFloat("Damage", attackDamage);
    }
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA, tag.getCompound("Wave"));
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        attackDamage = tag.getFloat("Damage");
    }
}


