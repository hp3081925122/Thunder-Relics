package org.hp.thunderrelics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.client.fx.FxDraw;
import org.hp.thunderrelics.entity.ThunderFx;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

// 绘制命中天雷、砸地冲击波与遗石雷殛，坐标全部相对特效实体落点。
public final class ThunderFxRenderer extends EntityRenderer<ThunderFx, ThunderFxRenderer.State> {
    // 提交阶段只读取的快照。
    public static final class State extends EntityRenderState {
        int kind;
        int id;
        double age;
        float param;
        Vec3 origin = Vec3.ZERO;
    }

    public ThunderFxRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    // 遗石雷殛优先使用悬浮石的实时位置作为起点。
    @Override
    public void extractRenderState(ThunderFx fx, State state, float partialTick) {
        super.extractRenderState(fx, state, partialTick);
        state.kind = fx.kind();
        state.id = fx.getId();
        state.age = fx.age(partialTick);
        state.param = fx.param();
        Vec3 origin = fx.origin();
        if (fx.kind() == ThunderFx.RELIC_BOLT) {
            Vec3 live = ThunderKingFx.stoneWorld(fx.ownerEntityId(), (int) fx.param());
            if (live != null && live.distanceToSqr(origin) < 64.0D) origin = live;
        }
        state.origin = origin.subtract(fx.getPosition(partialTick));
    }

    // 特效范围远大于实体碰撞箱，按距离判断是否绘制。
    @Override
    public boolean shouldRender(ThunderFx entity, Frustum frustum, double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 128.0D * 128.0D;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.age < 0.0D || state.age > ThunderFx.life(state.kind, state.param)) return;
        Quaternionf orientation = new Quaternionf(camera.orientation);
        collector.submitCustomGeometry(pose, RenderTypes.lightning(), (entry, vertices) -> {
            Matrix4f matrix = new Matrix4f(entry.pose());
            if (state.kind == ThunderFx.HIT_BOLT) hitBolt(vertices, matrix, orientation, state);
            if (state.kind == ThunderFx.SLAM) slam(vertices, matrix, orientation, state);
            if (state.kind == ThunderFx.RELIC_BOLT) relic(vertices, matrix, orientation, state);
            if (state.kind == ThunderFx.LANE_BOLT) lane(vertices, matrix, orientation, state);
            if (state.kind == ThunderFx.PLAYER_SLASH) slash(vertices, matrix, orientation, state);
            if (state.kind == ThunderFx.CHAIN) chain(vertices, matrix, orientation, state);
        });
    }

    // 命中天雷：两刻内劈到目标，随后电芯渐隐，伴随星芒、地面亮斑和扩散环。
    private static void hitBolt(VertexConsumer v, Matrix4f m, Quaternionf cam, State s) {
        double t = s.age;
        Random random = new Random(91283L + s.id * 1009L);
        Vec3 ground = new Vec3(0, 0.05D, 0);
        Vec3 sky = new Vec3((random.nextDouble() - 0.5D) * 3.0D, 16.0D, (random.nextDouble() - 0.5D) * 3.0D);
        double grow = FxDraw.smooth(t / 2.0D);
        float alpha = FxDraw.clamp01(1.0D - (t - 2.0D) / 9.0D);
        Vec3 head = sky.lerp(ground, grow);
        // 主雷柱与一条细副雷交替刷新，在消散前保留抖动感。
        Random boltRandom = new Random(5531L + s.id * 31L + (long) Math.floor(t / 2.0D));
        FxDraw.bolt(v, m, sky, head, boltRandom, 12, 0.32D, alpha, 1.3D, 3);
        if (t > 1.0D) FxDraw.bolt(v, m, sky.add(1.2D, 0, -0.8D), ground, boltRandom, 10, 0.14D, alpha * 0.55F, 1.6D, 1);
        if (t >= 2.0D) {
            float hit = FxDraw.clamp01(1.0D - (t - 2.0D) / 6.0D);
            FxDraw.glowOrb(v, m, cam, new Vec3(0, 0.6D, 0), 1.1D * hit + 0.2D, hit);
            FxDraw.burst(v, m, new Vec3(0, 0.5D, 0), random, 1.6D, 10, 0.08D, hit);
            FxDraw.groundDisc(v, m, ground, 2.2D, FxDraw.MID[0], FxDraw.MID[1], FxDraw.MID[2], hit * 0.55F);
            double wave = FxDraw.smooth((t - 2.0D) / 10.0D);
            FxDraw.glowRing(v, m, ground, 0.4D + wave * 2.6D, 0.16D, FxDraw.clamp01(1.0D - (t - 2.0D) / 12.0D));
            FxDraw.groundArcs(v, m, ground, new Random(771L + s.id), 2.4D, 7, 0.07D, alpha * 0.9F);
        }
    }

    // 砸地冲击波：中心爆闪、两道先后扩散的发光环、放射地裂电弧与向上溅起的电弧，大招再加三道天雷。
    private static void slam(VertexConsumer v, Matrix4f m, Quaternionf cam, State s) {
        double t = s.age;
        double scale = s.param;
        Random random = new Random(40213L + s.id * 1009L);
        Vec3 ground = new Vec3(0, 0.06D, 0);
        float flash = FxDraw.clamp01(1.0D - t / 6.0D);
        FxDraw.glowOrb(v, m, cam, new Vec3(0, 0.4D, 0), 1.6D * scale * flash + 0.1D, flash);
        FxDraw.groundDisc(v, m, ground, 3.2D * scale, FxDraw.MID[0], FxDraw.MID[1], FxDraw.MID[2], flash * 0.6F);
        FxDraw.burst(v, m, new Vec3(0, 0.3D, 0), random, 1.8D * scale, 12, 0.09D, flash);
        // 两道冲击环错开三刻，外扩速度先快后慢。
        for (int ring = 0; ring < 2; ring++) {
            double rt = t - ring * 3.0D;
            if (rt < 0.0D) continue;
            double radius = scale * (0.6D + 5.2D * FxDraw.smooth(rt / 14.0D));
            float alpha = FxDraw.clamp01(1.0D - rt / 18.0D) * (1.0F - ring * 0.35F);
            FxDraw.glowRing(v, m, ground, radius, 0.22D * scale, alpha);
        }
        // 地裂电弧随时间向外蔓延后整体淡出。
        double reach = scale * (1.5D + 3.8D * FxDraw.smooth(t / 6.0D));
        float crack = FxDraw.clamp01(1.0D - (t - 4.0D) / 14.0D);
        FxDraw.groundArcs(v, m, ground, new Random(9911L + s.id), reach, (int) Math.round(10 * scale), 0.1D, crack);
        // 向上溅起的电弧只在前几刻出现。
        if (t < 7.0D) {
            Random up = new Random(3307L + s.id * 7L + (long) Math.floor(t / 2.0D));
            float a = FxDraw.clamp01(1.0D - t / 7.0D);
            for (int i = 0; i < 6; i++) {
                double angle = up.nextDouble() * Math.PI * 2.0D;
                double r = scale * (0.5D + up.nextDouble() * 1.8D);
                Vec3 base = new Vec3(Math.cos(angle) * r, 0.05D, Math.sin(angle) * r);
                Vec3 top = base.add(Math.cos(angle) * 0.6D, scale * (1.2D + up.nextDouble() * 1.6D), Math.sin(angle) * 0.6D);
                FxDraw.bolt(v, m, base, top, up, 4, 0.08D, a, 0.3D, 0);
            }
        }
        // 大型砸地附带三道落在周围的天雷。
        if (scale > 1.3D && t < 10.0D) {
            float a = FxDraw.clamp01(1.0D - t / 10.0D);
            Random sky = new Random(6619L + s.id * 13L);
            for (int i = 0; i < 3; i++) {
                double angle = i * Math.PI * 2.0D / 3.0D + sky.nextDouble();
                double r = 2.0D + sky.nextDouble() * 2.5D;
                Vec3 end = new Vec3(Math.cos(angle) * r, 0.05D, Math.sin(angle) * r);
                Random flicker = new Random(sky.nextLong() + (long) Math.floor(t / 2.0D));
                FxDraw.bolt(v, m, end.add(0, 14.0D, 0), end, flicker, 10, 0.24D, a, 1.1D, 2);
                FxDraw.glowRing(v, m, end, 0.3D + (1.0D - a) * 1.6D, 0.1D, a);
            }
        }
    }

    // 遗石雷殛：雷光自悬浮石升空，在高空凝成光球，地面预警收缩后劈落。
    private static void relic(VertexConsumer v, Matrix4f m, Quaternionf cam, State s) {
        double t = s.age;
        int strike = ThunderFx.RELIC_STRIKE_TICK;
        Vec3 origin = s.origin;
        Vec3 ground = new Vec3(0, 0.05D, 0);
        Vec3 apex = new Vec3(origin.x * 0.55D, Math.max(origin.y, 0.0D) + 9.0D, origin.z * 0.55D);
        Random random = new Random(12011L + s.id * 1009L);
        // 升空：电束头部四刻内冲到高空，尾迹随后淡出。
        double rise = FxDraw.smooth(t / 4.0D);
        float riseAlpha = FxDraw.clamp01(1.0D - (t - 4.0D) / 6.0D);
        if (riseAlpha > 0.0F) {
            Vec3 headPoint = origin.lerp(apex, rise);
            Random flicker = new Random(4441L + s.id * 17L + (long) Math.floor(t / 2.0D));
            FxDraw.bolt(v, m, origin, headPoint, flicker, 8, 0.2D, riseAlpha, 0.7D, 1);
            if (t < 3.0D) FxDraw.burst(v, m, origin, random, 0.9D, 6, 0.06D, FxDraw.clamp01(1.0D - t / 3.0D));
        }
        // 高空光球随蓄力增大，并持续迸出细电弧。
        if (t >= 3.0D && t < strike + 3.0D) {
            double charge = FxDraw.smooth((t - 3.0D) / (strike - 3.0D));
            double pulse = 0.85D + 0.15D * Math.sin(t * 2.2D);
            float a = 1.0F;
            if (t > strike) a = FxDraw.clamp01(1.0D - (t - strike) / 3.0D);
            FxDraw.glowOrb(v, m, cam, apex, (0.35D + 0.6D * charge) * pulse, a);
            FxDraw.burst(v, m, apex, new Random(2203L + s.id + (long) Math.floor(t / 2.0D)), 1.0D + charge, 6, 0.05D, a * 0.8F);
        }
        // 预警：外圈固定、内圈收缩，中间一道细光柱指向高空光球。
        if (t < strike) {
            double p = t / strike;
            float a = FxDraw.clamp01(t / 3.0D);
            FxDraw.glowRing(v, m, ground, 1.8D, 0.08D, a * 0.7F);
            FxDraw.glowRing(v, m, ground, 0.15D + 1.65D * (1.0D - p), 0.12D, a);
            FxDraw.groundDisc(v, m, ground, 1.8D, FxDraw.HALO[0], FxDraw.HALO[1], FxDraw.HALO[2], a * 0.25F * (float) p);
            FxDraw.segment(v, m, ground, apex, 0.03D, FxDraw.MID[0], FxDraw.MID[1], FxDraw.MID[2], a * 0.18F);
            return;
        }
        // 落雷：主电束与副电束从光球劈向落点，命中闪光、冲击环与地面电弧。
        double hitT = t - strike;
        float alpha = FxDraw.clamp01(1.0D - hitT / 9.0D);
        Random flicker = new Random(8803L + s.id * 31L + (long) Math.floor(hitT / 2.0D));
        FxDraw.bolt(v, m, apex, ground, flicker, 12, 0.34D, alpha, 1.2D, 3);
        FxDraw.bolt(v, m, apex.add(0.6D, 0.4D, -0.4D), ground, flicker, 9, 0.12D, alpha * 0.5F, 1.5D, 1);
        float hit = FxDraw.clamp01(1.0D - hitT / 6.0D);
        FxDraw.glowOrb(v, m, cam, new Vec3(0, 0.5D, 0), 1.3D * hit + 0.1D, hit);
        FxDraw.burst(v, m, new Vec3(0, 0.4D, 0), random, 1.8D, 10, 0.08D, hit);
        FxDraw.groundDisc(v, m, ground, 2.4D, FxDraw.MID[0], FxDraw.MID[1], FxDraw.MID[2], hit * 0.5F);
        double wave = FxDraw.smooth(hitT / 10.0D);
        FxDraw.glowRing(v, m, ground, 0.4D + wave * 3.0D, 0.16D, FxDraw.clamp01(1.0D - hitT / 14.0D));
        FxDraw.groundArcs(v, m, ground, new Random(5577L + s.id), 2.6D, 8, 0.08D, FxDraw.clamp01(1.0D - hitT / 12.0D));
    }

    // 插地雷轨：地面裂纹从上一道雷蔓延而来，预警收缩后天雷劈落，并在落点立起短暂的光柱。
    private static void lane(VertexConsumer v, Matrix4f m, Quaternionf cam, State s) {
        double t = s.age;
        double delay = s.param;
        Vec3 ground = new Vec3(0, 0.05D, 0);
        Vec3 from = s.origin.add(0, 0.05D, 0);
        // 裂纹：在延迟的前段蔓延到落点，落雷后渐隐。
        double grow = FxDraw.smooth(t / Math.max(2.0D, delay - 3.0D));
        float crackAlpha = 1.0F;
        if (t > delay) crackAlpha = FxDraw.clamp01(1.0D - (t - delay) / 10.0D);
        if (grow > 0.01D) {
            Random crack = new Random(33017L + s.id * 1009L);
            FxDraw.bolt(v, m, from, from.lerp(ground, grow), crack, 7, 0.1D, crackAlpha * 0.9F, 0.35D, 1);
        }
        if (t < delay) {
            // 最后八刻显示收缩预警圈。
            double left = delay - t;
            if (left < 8.0D) {
                float a = FxDraw.clamp01((8.0D - left) / 3.0D);
                FxDraw.glowRing(v, m, ground, 1.7D, 0.06D, a * 0.6F);
                FxDraw.glowRing(v, m, ground, 0.2D + 1.5D * left / 8.0D, 0.1D, a);
                FxDraw.groundDisc(v, m, ground, 1.7D, FxDraw.HALO[0], FxDraw.HALO[1], FxDraw.HALO[2], a * 0.3F);
            }
            return;
        }
        double hitT = t - delay;
        Random random = new Random(70117L + s.id * 1009L);
        Vec3 sky = new Vec3((random.nextDouble() - 0.5D) * 2.5D, 18.0D, (random.nextDouble() - 0.5D) * 2.5D);
        double fall = FxDraw.smooth(hitT / 1.5D);
        float alpha = FxDraw.clamp01(1.0D - (hitT - 1.5D) / 9.0D);
        Random flicker = new Random(9013L + s.id * 31L + (long) Math.floor(hitT / 2.0D));
        // 主雷与两道副雷。
        FxDraw.bolt(v, m, sky, sky.lerp(ground, fall), flicker, 13, 0.4D, alpha, 1.3D, 3);
        if (hitT > 1.0D) {
            FxDraw.bolt(v, m, sky.add(1.6D, 1.0D, 0.6D), ground, flicker, 10, 0.16D, alpha * 0.55F, 1.6D, 1);
            FxDraw.bolt(v, m, sky.add(-1.2D, 0.5D, -1.4D), ground, flicker, 10, 0.14D, alpha * 0.45F, 1.6D, 1);
        }
        if (hitT >= 1.5D) {
            float hit = FxDraw.clamp01(1.0D - (hitT - 1.5D) / 7.0D);
            FxDraw.glowOrb(v, m, cam, new Vec3(0, 0.7D, 0), 1.4D * hit + 0.2D, hit);
            FxDraw.burst(v, m, new Vec3(0, 0.5D, 0), random, 2.0D, 12, 0.09D, hit);
            FxDraw.groundDisc(v, m, ground, 2.6D, FxDraw.MID[0], FxDraw.MID[1], FxDraw.MID[2], hit * 0.6F);
            // 落点光柱：粗光晕从地面向上渐隐。
            FxDraw.segment(v, m, ground, new Vec3(0, 3.5D, 0), 0.55D * hit, FxDraw.HALO[0], FxDraw.HALO[1], FxDraw.HALO[2], hit * 0.3F);
            FxDraw.segment(v, m, ground, new Vec3(0, 2.5D, 0), 0.22D * hit, FxDraw.MID[0], FxDraw.MID[1], FxDraw.MID[2], hit * 0.6F);
            double wave = FxDraw.smooth((hitT - 1.5D) / 10.0D);
            FxDraw.glowRing(v, m, ground, 0.4D + wave * 3.2D, 0.16D, FxDraw.clamp01(1.0D - (hitT - 1.5D) / 12.0D));
            FxDraw.groundArcs(v, m, ground, new Random(4421L + s.id), 2.8D, 8, 0.08D, alpha);
        }
    }

    // 月牙斩痕：以攻击者为圆心、经过目标的倾斜弧光，三刻内扫过，外缘白亮刃口向内渐变为蓝色光晕。
    private static void slash(VertexConsumer v, Matrix4f m, Quaternionf cam, State s) {
        double t = s.age;
        Vec3 center = s.origin;
        Vec3 toTarget = center.scale(-1.0D);
        Vec3 flat = new Vec3(toTarget.x, 0.0D, toTarget.z);
        if (flat.lengthSqr() < 1.0E-4D) return;
        Vec3 dir = flat.normalize();
        double radius = Math.max(1.6D, Math.min(4.5D, toTarget.length()));
        Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize();
        // 斩痕平面绕前向轴倾斜，参数决定左上到右下或反向。
        double tilt = Math.toRadians(28.0D) * s.param;
        Vec3 sideTilted = side.scale(Math.cos(tilt)).add(new Vec3(0, 1, 0).scale(Math.sin(tilt)));
        double sweep = FxDraw.smooth(t / 3.0D);
        float fade = FxDraw.clamp01(1.0D - (t - 3.0D) / 8.0D);
        double startAngle = Math.toRadians(-75.0D);
        double endAngle = Math.toRadians(75.0D);
        double head = startAngle + (endAngle - startAngle) * sweep;
        int steps = 24;
        Vec3 prevOuter = null, prevInner = null;
        float prevAlpha = 0.0F;
        // 从起点沿弧线逐段绘制到当前扫过的位置，尾部更暗。
        for (int i = 0; i <= steps; i++) {
            double a = startAngle + (head - startAngle) * i / steps;
            Vec3 radial = dir.scale(Math.cos(a)).add(sideTilted.scale(Math.sin(a)));
            Vec3 outer = center.add(radial.scale(radius));
            Vec3 inner = center.add(radial.scale(radius - 1.1D));
            float alpha = fade * (float) (0.15D + 0.85D * i / steps);
            if (prevOuter != null) {
                Vec3 aw = prevOuter.lerp(prevInner, 0.1D), bw = outer.lerp(inner, 0.1D);
                Vec3 am = prevOuter.lerp(prevInner, 0.4D), bm = outer.lerp(inner, 0.4D);
                FxDraw.ribbon(v, m, prevOuter, aw, outer, bw, FxDraw.CORE[0], FxDraw.CORE[1], FxDraw.CORE[2], prevAlpha, alpha, 0.9F);
                FxDraw.ribbon(v, m, aw, am, bw, bm, FxDraw.MID[0], FxDraw.MID[1], FxDraw.MID[2], prevAlpha * 0.85F, alpha * 0.85F, 0.55F);
                FxDraw.ribbon(v, m, am, prevInner, bm, inner, FxDraw.HALO[0], FxDraw.HALO[1], FxDraw.HALO[2], prevAlpha * 0.5F, alpha * 0.5F, 0.0F);
            }
            prevOuter = outer;
            prevInner = inner;
            prevAlpha = alpha;
        }
        // 刃口外缘跳动的细电弧。
        Random random = new Random(s.id * 7411L + (long) Math.floor(t / 2.0D));
        for (int i = 0; i < 3; i++) {
            double a0 = startAngle + (head - startAngle) * random.nextDouble();
            double a1 = Math.min(head, a0 + 0.35D);
            Vec3 p0 = center.add(dir.scale(Math.cos(a0)).add(sideTilted.scale(Math.sin(a0))).scale(radius + 0.1D));
            Vec3 p1 = center.add(dir.scale(Math.cos(a1)).add(sideTilted.scale(Math.sin(a1))).scale(radius + 0.1D));
            FxDraw.bolt(v, m, p0, p1, random, 4, 0.05D, fade * 0.9F, 0.2D, 0);
        }
        // 命中点星芒。
        if (t >= 1.0D && t < 7.0D) {
            float hit = FxDraw.clamp01(1.0D - (t - 1.0D) / 6.0D);
            FxDraw.glowOrb(v, m, cam, Vec3.ZERO, 0.6D * hit + 0.15D, hit);
            FxDraw.burst(v, m, Vec3.ZERO, new Random(s.id * 389L), 1.1D, 8, 0.06D, hit);
        }
    }

    // 雷链：两个目标之间的跳动电弧，每两刻重抽路径。
    private static void chain(VertexConsumer v, Matrix4f m, Quaternionf cam, State s) {
        double t = s.age;
        float alpha = FxDraw.clamp01(1.0D - t / 9.0D);
        Vec3 from = s.origin;
        Vec3 to = new Vec3(0, 0, 0);
        Random random = new Random(s.id * 1301L + (long) Math.floor(t / 2.0D));
        double jitter = Math.max(0.3D, from.length() * 0.12D);
        FxDraw.bolt(v, m, from, to, random, 8, 0.12D, alpha, jitter, 1);
        FxDraw.bolt(v, m, from, to, random, 6, 0.05D, alpha * 0.6F, jitter * 1.4D, 0);
        FxDraw.glowOrb(v, m, cam, from, 0.3D, alpha);
        FxDraw.glowOrb(v, m, cam, to, 0.4D, alpha);
    }
}
