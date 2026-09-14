package org.hp.thunderrelics.effect;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 装饰雷霆只生成视觉闪电，伤害和着火由本类对指定目标显式结算。
public final class DecorativeLightning {
    private DecorativeLightning() {
    }

    // 生成不会点火、不会触发附近实体雷击的视觉闪电，并对单个目标结算伤害；有目标时固定落在目标脚下。
    public static void strike(Level level, Vec3 position, @Nullable Entity source,
                              @Nullable LivingEntity target, float damage, int fireSeconds) {
        if (level.isClientSide()) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            Vec3 feet = target == null ? position : target.position();
            bolt.setPos(feet.x, feet.y, feet.z);
            level.addFreshEntity(bolt);
        }
        if (target != null && target.isAlive()) {
            target.hurtOrSimulate(level.damageSources().lightningBolt(), damage);
            if (fireSeconds > 0) target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), fireSeconds * 20));
        }
    }

    // 一道视觉闪电覆盖范围目标，排除施法者和已直接命中的目标以免重复伤害。
    public static void strikeArea(Level level, Vec3 position, @Nullable Entity source,
                                  @Nullable LivingEntity excluded, float damage, int fireSeconds, double radius) {
        if (level.isClientSide()) return;
        strike(level, position, source, null, 0.0F, 0);
        AABB area = new AABB(position, position).inflate(radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity.isAlive() && entity != source && entity != excluded
                        && (source == null || !source.isAlliedTo(entity)));
        for (LivingEntity target : targets) {
            target.hurtOrSimulate(level.damageSources().lightningBolt(), damage);
            if (fireSeconds > 0) target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), fireSeconds * 20));
        }
    }
}
