package org.hp.thunderrelics.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.ModEntities;

// 一波共享一个实体，同步固定落点和世界时间，不给每根电弧创建实体。
public final class RoyalStormWave extends Entity {
    // 预警收缩约一秒后落雷，随后短暂保留电弧伤害窗口。
    public static final int WARNING_TICKS = 21;
    public static final int LIFE_TICKS = 64;
    private static final EntityDataAccessor<String> DATA = SynchedEntityData.defineId(
            RoyalStormWave.class, EntityDataSerializers.STRING);
    private UUID ownerId;
    private float attackDamage;
    private String cachedData;
    private List<Vec3> cachedPoints = List.of();

    public RoyalStormWave(EntityType<? extends RoyalStormWave> type, Level level) {
        super(type, level);
    }

    // 固定玩家脚下落点，并在大范围内挑选互相留有空隙的随机落点。
    public static void spawn(ThunderKingEntity boss, LivingEntity target) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        RoyalStormWave wave = new RoyalStormWave(ModEntities.ROYAL_STORM_WAVE.get(), level);
        wave.setPos(boss.getX(), boss.getY(), boss.getZ());
        wave.ownerId = boss.getUUID();
        wave.attackDamage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        List<Vec3> points = new ArrayList<>();
        points.add(findFloor(level, target.getX(), target.getY() + .2D, target.getZ()));
        // 每波最多三十二处，拒绝过密的随机点，玩家可以从标记间穿行。
        for (int tries = 0; points.size() < 32 && tries < 300; tries++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = Math.sqrt(level.getRandom().nextDouble()) * 22.0D;
            Vec3 point = findFloor(level, boss.getX() + Math.cos(angle) * radius,
                    target.getY() + 4.0D, boss.getZ() + Math.sin(angle) * radius);
            if (points.stream().noneMatch(existing -> existing.distanceToSqr(point) < 9.0D)) points.add(point);
        }
        wave.entityData.set(DATA, encode(level.getGameTime(), points));
        level.addFreshEntity(wave);
        com.mojang.logging.LogUtils.getLogger().debug("Royal storm wave: owner={}, points={}, fixedTarget={}",
                boss.getUUID(), points.size(), points.get(0));
    }

    // 投掷武器命中方块时，在命中点周围八格内生成一圈范围预警。
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
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(level.getRandom().nextDouble()) * radius;
            Vec3 point = findFloor(level, impact.x + Math.cos(angle) * distance,
                    impact.y + 4.0D, impact.z + Math.sin(angle) * distance);
            if (points.stream().noneMatch(existing -> existing.distanceToSqr(point) < 4.0D)) points.add(point);
        }
        wave.entityData.set(DATA, encode(level.getGameTime(), points));
        level.addFreshEntity(wave);
        com.mojang.logging.LogUtils.getLogger().debug("Royal impact wave: owner={}, points={}, center={}",
                owner.getUUID(), points.size(), points.get(0));
    }

    // 从目标附近向下投影到碰撞表面，支持楼层和台阶，不把预警贴在空中。
    private static Vec3 findFloor(ServerLevel level, double x, double y, double z) {
        BlockPos at = BlockPos.containing(x, y, z);
        for (int dy = 0; dy < 32 && at.getY() - dy > level.getMinY(); dy++) {
            BlockPos pos = at.below(dy);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return new Vec3(x, pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) + .035D, z);
            }
        }
        return new Vec3(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA, "");
    }

    // 将落点编码成字符串，适配 26.1.2 移除 CompoundTag 数据同步器后的接口。
    private static String encode(long start, List<Vec3> points) {
        StringBuilder value = new StringBuilder(Long.toString(start));
        for (Vec3 point : points) value.append(';').append(point.x).append(',').append(point.y).append(',').append(point.z);
        return value.toString();
    }

    // 渲染缓存只在同步数据变化时重建，避免每帧反复解析所有落点。
    public List<Vec3> points() {
        String data = entityData.get(DATA);
        if (!data.equals(cachedData)) {
            cachedData = data;
            ArrayList<Vec3> points = new ArrayList<>();
            String[] entries = data.split(";");
            for (int i = 1; i < entries.length; i++) {
                String[] coordinates = entries[i].split(",");
                if (coordinates.length != 3) continue;
                try {
                    points.add(new Vec3(Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1]),
                            Double.parseDouble(coordinates[2])));
                } catch (NumberFormatException ignored) {
                    // 同步数据损坏时跳过单个落点，避免整波特效阻塞实体刻更新。
                }
            }
            cachedPoints = List.copyOf(points);
        }
        return cachedPoints;
    }

    // 读取编码中的起始刻，空数据按零处理。
    public double age(float partialTick) {
        String data = entityData.get(DATA);
        int separator = data.indexOf(';');
        String start = separator < 0 ? data : data.substring(0, separator);
        try {
            return level().getGameTime() + partialTick - Long.parseLong(start);
        } catch (NumberFormatException ignored) {
            return LIFE_TICKS;
        }
    }

    // 伤害只在服务端两个时点执行，同一波重叠区域不会重复扣血。
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        int currentAge = (int) age(0.0F);
        if (currentAge >= LIFE_TICKS || ownerId == null
                || !(((ServerLevel) level()).getEntity(ownerId) instanceof LivingEntity owner)
                || !owner.isAlive()) {
            discard();
            return;
        }
        if (currentAge == WARNING_TICKS || currentAge == WARNING_TICKS + 10) {
            boolean bolt = currentAge == WARNING_TICKS;
            double radius = bolt ? 1.55D : 2.8D;
            HashSet<UUID> hit = new HashSet<>();
            for (Vec3 point : points()) {
                AABB area = new AABB(point.x - radius, point.y - .5D, point.z - radius,
                        point.x + radius, point.y + 3.0D, point.z + radius);
                for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, area)) {
                    if (victim == owner || !victim.isAlive() || owner.isAlliedTo(victim)
                            || victim instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
                    double dx = victim.getX() - point.x;
                    double dz = victim.getZ() - point.z;
                    if (dx * dx + dz * dz > radius * radius || !hit.add(victim.getUUID())) continue;
                    float before = victim.getHealth();
                    // 落雷与后续电弧都按君王攻击力计算，并整体提高百分之二十。
                    boolean applied = victim.hurtOrSimulate(damageSources().indirectMagic(this, owner),
                            attackDamage * (bolt ? 1.08F : .30F));
                    // 只有实际造成伤害时施加缓慢 I，持续五秒。
                    if (applied) victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0));
                    if (Boolean.getBoolean("thunderrelics.debug")) {
                        com.mojang.logging.LogUtils.getLogger().debug(
                                "Royal storm hit: stage={}, target={}, applied={}, healthBefore={}, healthAfter={}, point={}",
                                bolt ? "bolt" : "arc", victim.getUUID(), applied, before, victim.getHealth(), point);
                    }
                }
            }
            if (bolt) playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2.2F, 1.15F);
            else playSound(SoundEvents.TRIDENT_THUNDER.value(), .8F, 1.5F);
        }
    }

    // 预警实体本身不接受伤害，避免玩家攻击特效实体产生额外状态。
    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    // 标准生成包配合同步字段传输预警数据；存档恢复沿用原始时刻，不重新落雷。
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("Wave", entityData.get(DATA));
        if (ownerId != null) output.putString("Owner", ownerId.toString());
        output.putFloat("Damage", attackDamage);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(DATA, input.getStringOr("Wave", ""));
        String owner = input.getStringOr("Owner", "");
        try {
            ownerId = owner.isEmpty() ? null : UUID.fromString(owner);
        } catch (IllegalArgumentException ignored) {
            ownerId = null;
        }
        attackDamage = input.getFloatOr("Damage", 0.0F);
    }
}
