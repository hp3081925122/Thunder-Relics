package org.hp.thunderrelics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
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
import org.hp.thunderrelics.entity.RoyalStormWave;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

// 引雷仪式与投戟落地的落雷波：收缩预警环、多束天雷、空中电弧与地面余波。
public final class RoyalStormRenderer extends EntityRenderer<RoyalStormWave, RoyalStormRenderer.State> {
    // 延迟提交只读取快照，落点转换为相对实体中心的坐标。
    public static final class State extends EntityRenderState {
        double age;
        int id;
        List<Vec3> points = List.of();
    }

    public RoyalStormRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    // 提取世界时间和落点，避免提交回调持有可变实体。
    @Override
    public void extractRenderState(RoyalStormWave wave, State state, float partialTick) {
        super.extractRenderState(wave, state, partialTick);
        state.age = wave.age(partialTick);
        state.id = wave.getId();
        state.points = wave.points().stream().map(point -> point.subtract(wave.position())).toList();
    }

    // 波次实体在场地中央，边缘落点也需要渲染，因此按场地距离判断。
    @Override
    public boolean shouldRender(RoyalStormWave entity, Frustum frustum, double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 160.0D * 160.0D;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        double age = state.age;
        if (age < 0.0D || age > RoyalStormWave.LIFE_TICKS) return;
        Quaternionf orientation = new Quaternionf(camera.orientation);
        collector.submitCustomGeometry(pose, RenderTypes.lightning(), (entry, vertices) -> {
            Matrix4f matrix = new Matrix4f(entry.pose());
            if (age >= RoyalStormWave.WARNING_TICKS) airborneArcs(vertices, matrix, state.id, age - RoyalStormWave.WARNING_TICKS);
            int index = 0;
            // 每个落点独立绘制预警或落雷。
            for (Vec3 p : state.points) {
                if (age < RoyalStormWave.WARNING_TICKS) warning(vertices, matrix, p, age, index, state.id);
                else strike(vertices, matrix, orientation, p, age - RoyalStormWave.WARNING_TICKS, index, state.id);
                index++;
            }
        });
    }

    // 预警：外圈固定，内圈收缩，四道指针电弧随进度旋转。
    private static void warning(VertexConsumer v, Matrix4f m, Vec3 p, double age, int index, int id) {
        double progress = age / RoyalStormWave.WARNING_TICKS;
        float fadeIn = FxDraw.clamp01(age / 4.0D);
        FxDraw.glowRing(v, m, p, 1.55D, 0.06D, fadeIn * 0.7F);
        FxDraw.glowRing(v, m, p.add(0, 0.01D, 0), 0.18D + 1.37D * (1.0D - progress), 0.1D, fadeIn);
        FxDraw.groundDisc(v, m, p, 1.55D, FxDraw.HALO[0], FxDraw.HALO[1], FxDraw.HALO[2], fadeIn * 0.3F * (float) progress);
        // 四道短电弧指向中心。
        Random random = new Random(id * 911L + index * 37L + (long) Math.floor(age / 3.0D));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0D + progress * 1.2D;
            Vec3 outer = p.add(Math.cos(a) * 1.4D, 0.04D, Math.sin(a) * 1.4D);
            Vec3 inner = p.add(Math.cos(a) * 0.4D, 0.04D, Math.sin(a) * 0.4D);
            FxDraw.bolt(v, m, outer, inner, random, 3, 0.05D, fadeIn * 0.8F, 0.12D, 0);
        }
    }

    // 落雷：三束天雷汇聚落点，命中闪光、光盘、冲击环和地面余波电弧。
    private static void strike(VertexConsumer v, Matrix4f m, Quaternionf cam, Vec3 p, double t, int index, int id) {
        Random random = new Random(87123L + id * 1009L + index * 977L);
        if (t < 8.0D) {
            float alpha = FxDraw.clamp01(1.0D - t / 8.0D);
            Random flicker = new Random(random.nextLong() + (long) Math.floor(t / 2.0D));
            // 三束天雷从不同天空位置汇聚。
            for (int beam = 0; beam < 3; beam++) {
                double angle = beam * Math.PI * 2.0D / 3.0D + random.nextDouble() * 0.35D;
                double radius = 0.8D + random.nextDouble() * 1.4D;
                Vec3 sky = p.add(Math.cos(angle) * radius, 30.0D + random.nextDouble() * 4.0D, Math.sin(angle) * radius);
                FxDraw.bolt(v, m, sky, p.add(0, 0.05D, 0), flicker, 12, 0.24D - beam * 0.05D, alpha, 1.0D, 2);
            }
            float hit = FxDraw.clamp01(1.0D - t / 5.0D);
            FxDraw.glowOrb(v, m, cam, p.add(0, 0.5D, 0), 1.2D * hit + 0.1D, hit);
            FxDraw.groundDisc(v, m, p.add(0, 0.02D, 0), 2.2D, FxDraw.MID[0], FxDraw.MID[1], FxDraw.MID[2], hit * 0.5F);
        }
        float fade = FxDraw.clamp01(1.0D - t / 24.0D);
        FxDraw.glowRing(v, m, p, 0.4D + 2.4D * FxDraw.smooth(t / 10.0D), 0.12D, FxDraw.clamp01(1.0D - t / 14.0D));
        FxDraw.groundArcs(v, m, p, new Random(43971L + id * 1009L + index * 977L), 2.8D, 9, 0.06D, fade * 0.8F);
    }

    // 波次中心上方的云层电弧，形成雷雨中的二级电场。
    private static void airborneArcs(VertexConsumer v, Matrix4f m, int waveId, double elapsed) {
        float fade = FxDraw.clamp01(1.0D - elapsed / 28.0D);
        if (fade <= 0.0F) return;
        Random random = new Random(0x5EEDL + waveId * 31337L);
        // 十八条随机高度与方向的短电弧。
        for (int arc = 0; arc < 18; arc++) {
            double startAngle = random.nextDouble() * Math.PI * 2.0D;
            double startRadius = 5.0D + random.nextDouble() * 22.0D;
            Vec3 start = new Vec3(Math.cos(startAngle) * startRadius, 6.0D + random.nextDouble() * 14.0D,
                    Math.sin(startAngle) * startRadius);
            double endAngle = startAngle + (random.nextDouble() - 0.5D) * 1.8D;
            double length = 3.5D + random.nextDouble() * 7.0D;
            Vec3 end = start.add(Math.cos(endAngle) * length, (random.nextDouble() - 0.5D) * 4.0D, Math.sin(endAngle) * length);
            FxDraw.bolt(v, m, start, end, random, 6, 0.08D, fade * (0.35F + random.nextFloat() * 0.45F), 1.2D, 1);
        }
    }
}
