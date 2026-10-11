package org.hp.thunderrelics.client;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.client.fx.FxDraw;
import org.hp.thunderrelics.entity.ThunderSkill;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

// 雷霆君王身上的特效：骨骼位置缓存、刀光拖尾、武器电弧、浮石充能与引雷接引。
public final class ThunderKingFx {
    // 每个实体最近一帧的骨骼位置，坐标相对实体原点（方块单位）。
    static final class Bones {
        Vec3 grip;
        Vec3 head;
        Vec3 handLeft;
        final Vec3[] stones = new Vec3[4];
        Vec3 entityPos = Vec3.ZERO;
        double lastSeen;
    }

    // 刀光采样点，世界坐标保存，生物移动时残光留在原位。
    private record Sample(double time, Vec3 outer, Vec3 inner) {
    }

    private static final Map<Integer, Bones> BONES = new HashMap<>();
    private static final Map<Integer, List<Sample>> TRAILS = new HashMap<>();
    private static final String[] STONE_BONES = {"stone_0", "stone_1", "stone_2", "stone_3"};
    // 戟头轴心到刃尖的长度（方块）。
    private static final double TIP_LENGTH = 1.44D;
    private static final double TRAIL_LIFE = 5.0D;

    private ThunderKingFx() {
    }

    private static final class State {
        int entityId;
        double worldTime;
        int skill;
        double skillAge;
    }

    static void capture(int entityId, String name, Vec3 position) {
        Bones bones = BONES.computeIfAbsent(entityId, id -> new Bones());
        if (name.equals("weapon_grip")) bones.grip = position;
        if (name.equals("weapon_head")) bones.head = position;
        if (name.equals("hand_left")) bones.handLeft = position;
        for (int i = 0; i < STONE_BONES.length; i++) {
            if (name.equals(STONE_BONES[i])) bones.stones[i] = position;
        }
    }
    // 供其他特效读取悬浮石的世界坐标，缓存缺失时返回空。
    public static Vec3 stoneWorld(int entityId, int index) {
        Bones bones = BONES.get(entityId);
        if (bones == null || index < 0 || index >= 4 || bones.stones[index] == null) return null;
        return bones.entityPos.add(bones.stones[index]);
    }

    // 清理长时间未渲染的实体缓存，避免切换世界后累积。
    private static void prune(double now) {
        Iterator<Map.Entry<Integer, Bones>> it = BONES.entrySet().iterator();
        // 超过十秒未更新即移除骨骼与刀光记录。
        while (it.hasNext()) {
            Map.Entry<Integer, Bones> entry = it.next();
            if (Math.abs(now - entry.getValue().lastSeen) > 200.0D) {
                TRAILS.remove(entry.getKey());
                it.remove();
            }
        }
    }

    static void render(ThunderKingEntity entity, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        Bones bones = BONES.get(entity.getId());
        if (bones == null || !entity.isAlive()) return;
        State state = new State();
        state.entityId = entity.getId();
        state.worldTime = entity.level().getGameTime() + partialTick;
        state.skill = entity.getSwingKind();
        state.skillAge = entity.getSwingStart() < 0L ? -1.0D : state.worldTime - entity.getSwingStart();
        Vec3 origin = entity.getPosition(partialTick);
        bones.entityPos = origin;
        bones.lastSeen = state.worldTime;
        if (((long) state.worldTime) % 100L == 0L) prune(state.worldTime);
        ThunderSkill skill = ThunderSkill.byId(state.skill);
        if (skill != null && state.skillAge > skill.duration) skill = null;
        double age = state.skillAge;
        Weapon weapon = Weapon.of(bones);
        List<Sample> trail = updateTrail(state, skill, age, weapon, origin);
        Quaternionf orientation = new Quaternionf(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        drawTrail(vertices, matrix, trail, state.worldTime, origin, state.entityId);
        drawStones(vertices, matrix, orientation, bones.stones, bones.handLeft, skill, age, state.worldTime, state.entityId);
        if (weapon != null) drawWeapon(vertices, matrix, orientation, weapon, skill, age, state.worldTime, state.entityId);
    }
    // 由握柄与戟头轴心推算刃尖与刃根，武器被隐藏（缩放为零）时返回空。
    private record Weapon(Vec3 grip, Vec3 head, Vec3 tip, Vec3 base) {
        static Weapon of(Bones bones) {
            if (bones.grip == null || bones.head == null) return null;
            Vec3 axis = bones.head.subtract(bones.grip);
            if (axis.length() < 0.5D) return null;
            Vec3 dir = axis.normalize();
            return new Weapon(bones.grip, bones.head, bones.head.add(dir.scale(TIP_LENGTH)), bones.head.subtract(dir.scale(1.0D)));
        }
    }

    // 判断动画秒区间是否覆盖当前招式年龄。
    private static boolean in(ThunderSkill skill, double age, double fromSec, double toSec) {
        return age >= skill.toTicks(fromSec) && age <= skill.toTicks(toSec);
    }

    // 区间内的归一化进度。
    private static double progress(ThunderSkill skill, double age, double fromSec, double toSec) {
        double from = skill.toTicks(fromSec);
        double to = skill.toTicks(toSec);
        return Math.max(0.0D, Math.min(1.0D, (age - from) / Math.max(1.0D, to - from)));
    }

    // 挥砍窗口内逐帧记录刃尖与刃根，过期样本移除。
    private static List<Sample> updateTrail(State state, ThunderSkill skill, double age,
                                            Weapon weapon, Vec3 origin) {
        List<Sample> samples = TRAILS.computeIfAbsent(state.entityId, id -> new ArrayList<>());
        double now = state.worldTime;
        samples.removeIf(sample -> now - sample.time() > TRAIL_LIFE || now < sample.time());
        boolean active = false;
        if (skill == ThunderSkill.CLEAVE) active = in(skill, age, 0.56D, 0.95D);
        if (skill == ThunderSkill.HEAVY_SLAM) active = in(skill, age, 0.84D, 1.1D);
        if (skill == ThunderSkill.TWO_HANDED_SLAM) active = in(skill, age, 0.98D, 1.2D);
        if (active && weapon != null) {
            Sample sample = new Sample(now, origin.add(weapon.tip()), origin.add(weapon.base()));
            boolean add = samples.isEmpty();
            if (!add) add = now - samples.get(samples.size() - 1).time() >= 0.12D;
            // 两次采样间距过大说明招式切换或瞬移，清空后重新开始。
            if (!samples.isEmpty() && sample.outer().distanceToSqr(samples.get(samples.size() - 1).outer()) > 36.0D)
                samples.clear();
            if (add) samples.add(sample);
            if (samples.size() > 80) samples.remove(0);
        }
        return new ArrayList<>(samples);
    }

    // 刀光三层渐变带：刃尖白芯、青蓝过渡、深蓝外尾，并在带面上爬行细电弧。
    private static void drawTrail(VertexConsumer v, Matrix4f m, List<Sample> samples, double now, Vec3 origin, int id) {
        if (samples.size() < 2) return;
        int count = samples.size();
        // 相邻样本组成一段光带。
        for (int i = 1; i < count; i++) {
            Sample a = samples.get(i - 1);
            Sample b = samples.get(i);
            float alphaA = FxDraw.clamp01((TRAIL_LIFE - (now - a.time())) / (TRAIL_LIFE * 0.85D));
            float alphaB = FxDraw.clamp01((TRAIL_LIFE - (now - b.time())) / (TRAIL_LIFE * 0.85D));
            Vec3 ao = a.outer().subtract(origin), ai = a.inner().subtract(origin);
            Vec3 bo = b.outer().subtract(origin), bi = b.inner().subtract(origin);
            Vec3 aw = ao.lerp(ai, 0.12D), bw = bo.lerp(bi, 0.12D);
            Vec3 am = ao.lerp(ai, 0.42D), bm = bo.lerp(bi, 0.42D);
            FxDraw.ribbon(v, m, ao, aw, bo, bw, FxDraw.CORE[0], FxDraw.CORE[1], FxDraw.CORE[2], alphaA, alphaB, 0.9F);
            FxDraw.ribbon(v, m, aw, am, bw, bm, FxDraw.MID[0], FxDraw.MID[1], FxDraw.MID[2], alphaA * 0.85F, alphaB * 0.85F, 0.6F);
            FxDraw.ribbon(v, m, am, ai, bm, bi, FxDraw.HALO[0], FxDraw.HALO[1], FxDraw.HALO[2], alphaA * 0.5F, alphaB * 0.5F, 0.0F);
            // 每隔三段沿带面生成一条短电弧，种子按样本时间固定避免闪烁。
            if (i % 3 == 0 && i + 2 < count) {
                Sample c = samples.get(i + 2);
                Random random = new Random((long) (a.time() * 1000.0D) * 31L + id);
                Vec3 from = ao.lerp(ai, 0.2D + random.nextDouble() * 0.5D);
                Vec3 to = c.outer().subtract(origin).lerp(c.inner().subtract(origin), random.nextDouble() * 0.6D);
                FxDraw.bolt(v, m, from, to, random, 4, 0.05D, alphaA * 0.9F, 0.18D, 0);
            }
        }
    }

    // 武器特效：蓄力电弧、双手举戟时的天雷接引、引雷仪式接引、投掷与召回闪光。
    private static void drawWeapon(VertexConsumer v, Matrix4f m, Quaternionf cam, Weapon w, ThunderSkill skill,
                                   double age, double now, int id) {
        if (skill == null) {
            // 平时戟刃偶尔闪过一道细电弧，保持雷霆君王的存在感。
            long cycle = (long) Math.floor(now / 9.0D);
            double phase = now / 9.0D - cycle;
            Random random = new Random(id * 7919L + cycle * 131L);
            if (random.nextDouble() < 0.45D && phase < 0.35D)
                bladeArcs(v, m, w, random, 2, 0.5F * (float) (1.0D - phase / 0.35D));
            return;
        }
        if (skill == ThunderSkill.CLEAVE && in(skill, age, 0.15D, 0.75D)) {
            charge(v, m, cam, w, id, now, progress(skill, age, 0.15D, 0.6D), 3);
        }
        if (skill == ThunderSkill.HEAVY_SLAM && in(skill, age, 0.25D, 1.0D)) {
            charge(v, m, cam, w, id, now, progress(skill, age, 0.25D, 0.9D), 4);
        }
        if (skill == ThunderSkill.TWO_HANDED_SLAM) {
            if (in(skill, age, 0.25D, 1.1D)) charge(v, m, cam, w, id, now, progress(skill, age, 0.25D, 0.95D), 5);
            // 双手举戟停顿时，天空连续降下雷线汇入戟尖。
            if (in(skill, age, 0.6D, 1.04D)) {
                float fade = (float) Math.min(1.0D, Math.min(progress(skill, age, 0.6D, 0.72D) * 1.5D,
                        (1.0D - progress(skill, age, 0.6D, 1.04D)) * 6.0D));
                skyRelay(v, m, cam, w.tip(), id, now, fade, 4, 12.0D, 3.0D);
            }
        }
        if (skill == ThunderSkill.JAVELIN) {
            if (in(skill, age, 0.3D, 1.2D)) charge(v, m, cam, w, id, now, progress(skill, age, 0.3D, 1.1D), 4);
            // 召回瞬间在手中爆出星芒。
            double back = age - skill.event(1);
            if (back >= 0.0D && back < 5.0D) {
                Random random = new Random(id * 4241L + skill.event(1));
                float alpha = (float) (1.0D - back / 5.0D);
                FxDraw.burst(v, m, w.grip(), random, 1.3D, 9, 0.07D, alpha);
                FxDraw.glowOrb(v, m, cam, w.grip(), 0.55D, alpha);
            }
        }
        if (skill == ThunderSkill.GROUND_PLANT) {
            if (in(skill, age, 0.1D, 0.72D)) charge(v, m, cam, w, id, now, progress(skill, age, 0.1D, 0.65D), 5);
            // 插地后戟身持续放电，电弧从刃尖向地面四散。
            if (in(skill, age, 0.7D, 2.05D)) {
                float hold = (float) (1.0D - progress(skill, age, 1.7D, 2.05D));
                Random random = new Random(id * 2287L + (long) Math.floor(now / 2.0D));
                bladeArcs(v, m, w, random, 4, hold);
                FxDraw.groundArcs(v, m, w.tip(), random, 2.2D, 5, 0.07D, hold * 0.9F);
                FxDraw.glowOrb(v, m, cam, w.tip(), 0.5D * (0.85D + 0.15D * Math.sin(now * 1.9D)), hold);
            }
            double plant = age - skill.event(0);
            if (plant >= 0.0D && plant < 6.0D) {
                float flash = (float) (1.0D - plant / 6.0D);
                FxDraw.burst(v, m, w.tip(), new Random(id * 6113L), 2.2D, 12, 0.09D, flash);
                FxDraw.glowOrb(v, m, cam, w.tip(), 1.2D * flash + 0.2D, flash);
            }
        }
        if (skill == ThunderSkill.STORM && in(skill, age, 1.5D, 5.0D)) {
            float fade = (float) Math.min(progress(skill, age, 1.5D, 1.75D), 1.0D - progress(skill, age, 4.6D, 5.0D));
            skyRelay(v, m, cam, w.tip(), id, now, fade, 8, 20.0D, 7.0D);
            bladeArcs(v, m, w, new Random(id * 31L + (long) Math.floor(now / 2.0D)), 3, fade * 0.8F);
        }
    }

    // 蓄力：电弧沿戟身爬向刃尖，刃尖光球随进度增大。
    private static void charge(VertexConsumer v, Matrix4f m, Quaternionf cam, Weapon w, int id, double now,
                               double progress, int arcs) {
        float alpha = (float) (0.35D + 0.65D * progress);
        Random random = new Random(id * 977L + (long) Math.floor(now / 2.0D) * 7L);
        bladeArcs(v, m, w, random, arcs, alpha);
        double pulse = 0.85D + 0.15D * Math.sin(now * 1.3D);
        FxDraw.glowOrb(v, m, cam, w.tip(), (0.15D + 0.3D * progress) * pulse, alpha * 0.9F);
    }

    // 戟身电弧：起点散布在杆身上段，终点汇向刃尖附近。
    private static void bladeArcs(VertexConsumer v, Matrix4f m, Weapon w, Random random, int arcs, float alpha) {
        Vec3 axis = w.tip().subtract(w.grip());
        // 每条电弧从杆身随机位置出发，绕过杆身汇向刃尖。
        for (int i = 0; i < arcs; i++) {
            Vec3 start = w.grip().add(axis.scale(0.35D + random.nextDouble() * 0.4D))
                    .add(FxDraw.orthogonal(axis, random).scale(0.12D));
            Vec3 end = w.tip().add(FxDraw.orthogonal(axis, random).scale(0.1D + random.nextDouble() * 0.2D));
            FxDraw.bolt(v, m, start, end, random, 5, 0.06D, alpha, 0.22D, 1);
        }
    }

    // 天空雷线接引：多条错峰刷新的雷线从高空折向目标点。
    private static void skyRelay(VertexConsumer v, Matrix4f m, Quaternionf cam, Vec3 target, int id, double now,
                                 float alpha, int bolts, double height, double spread) {
        if (alpha <= 0.01F) return;
        double life = 5.0D;
        // 每条雷线约五刻寿命，按相位错开，画面持续有新雷线。
        for (int bolt = 0; bolt < bolts; bolt++) {
            double offset = bolt * life / bolts;
            long cycle = (long) Math.floor((now + offset) / life);
            double phase = (now + offset - cycle * life) / life;
            float boltAlpha = alpha * (float) Math.pow(Math.sin(Math.PI * phase), 0.6D);
            Random random = new Random(0x71A9BEEFL + id * 31337L + cycle * 1009L + bolt * 977L);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = spread * (0.4D + random.nextDouble() * 0.6D);
            Vec3 sky = target.add(Math.cos(angle) * radius, height * (0.75D + random.nextDouble() * 0.4D), Math.sin(angle) * radius);
            Vec3 joint = target.lerp(sky, 0.4D).add((random.nextDouble() - 0.5D) * spread * 0.5D, 0,
                    (random.nextDouble() - 0.5D) * spread * 0.5D);
            FxDraw.bolt(v, m, sky, joint, random, 7, 0.2D, boltAlpha, 1.6D, 2);
            FxDraw.bolt(v, m, joint, target, random, 6, 0.16D, boltAlpha, 1.0D, 1);
        }
        double pulse = 0.85D + 0.15D * Math.sin(now * 1.7D);
        FxDraw.glowOrb(v, m, cam, target, 0.45D * pulse, alpha);
        FxDraw.burst(v, m, target, new Random(id * 977L + (long) Math.floor(now / 3.0D)), 0.9D, 6, 0.06D, alpha * 0.7F);
    }

    // 悬浮石：平时微光，遗石雷殛时逐块充能、互相连弧并在发射刻爆闪。
    private static void drawStones(VertexConsumer v, Matrix4f m, Quaternionf cam, Vec3[] stones, Vec3 handLeft,
                                   ThunderSkill skill, double age, double now, int id) {
        boolean barrage = skill == ThunderSkill.RELIC_BARRAGE;
        // 每块石头单独处理光晕。
        for (int i = 0; i < 4; i++) {
            Vec3 stone = stones[i];
            if (stone == null) continue;
            double pulse = 0.8D + 0.2D * Math.sin(now * 0.18D + i * 1.7D);
            if (!barrage) {
                FxDraw.glowOrb(v, m, cam, stone.add(0, -0.1D, 0), 0.16D * pulse, 0.35F);
                continue;
            }
            int fire = skill.event(i);
            if (age < fire) {
                double charge = Math.min(1.0D, age / Math.max(1.0D, fire - 2.0D));
                FxDraw.glowOrb(v, m, cam, stone, (0.2D + 0.35D * charge) * pulse, (float) (0.5D + 0.5D * charge));
                // 充能后期石头表面迸出短电弧。
                if (charge > 0.4D) {
                    Random random = new Random(id * 131L + i * 17L + (long) Math.floor(now / 2.0D));
                    FxDraw.burst(v, m, stone, random, 0.55D * charge, 4, 0.04D, (float) charge * 0.8F);
                }
            } else if (age < fire + 6.0D) {
                float flash = (float) (1.0D - (age - fire) / 6.0D);
                FxDraw.glowOrb(v, m, cam, stone, 0.7D * flash + 0.15D, flash);
                FxDraw.burst(v, m, stone, new Random(id * 389L + i), 1.2D, 8, 0.07D, flash);
            } else {
                FxDraw.glowOrb(v, m, cam, stone, 0.14D, 0.3F);
            }
        }
        if (!barrage) return;
        // 充能期间相邻悬浮石之间串起电弧，形成背后的雷光环。
        double ringFade = Math.min(1.0D, age / 8.0D) * (1.0D - progress(skill, age, 1.4D, 1.7D));
        if (ringFade > 0.01D) {
            Random random = new Random(id * 53L + (long) Math.floor(now / 2.0D));
            int[][] links = {{0, 1}, {0, 2}, {1, 3}, {2, 3}};
            for (int[] link : links) {
                Vec3 a = stones[link[0]], b = stones[link[1]];
                if (a != null && b != null) FxDraw.bolt(v, m, a, b, random, 6, 0.07D, (float) ringFade * 0.85F, 0.35D, 1);
            }
        }
        // 副手托举时掌心汇聚光球。
        if (handLeft != null && in(skill, age, 0.2D, 1.6D)) {
            double p = progress(skill, age, 0.2D, 0.8D);
            float alpha = (float) (p * (1.0D - progress(skill, age, 1.4D, 1.6D)));
            FxDraw.glowOrb(v, m, cam, handLeft.add(0, -0.15D, 0), 0.3D * p + 0.1D, alpha);
        }
    }
}

