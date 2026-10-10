package org.hp.thunderrelics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// 雷霆特效共用的网格绘制工具，全部输出到叠加混合的闪电渲染类型。
public final class FxDraw {
    // 雷光三层配色：外层深蓝光晕、中层青蓝、内层近白电芯。
    public static final float[] HALO = {0.05F, 0.22F, 1.0F};
    public static final float[] MID = {0.28F, 0.72F, 1.0F};
    public static final float[] CORE = {0.85F, 0.97F, 1.0F};

    private FxDraw() {
    }

    // 写入一个带颜色的顶点。
    public static void vertex(VertexConsumer v, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        v.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    // 两片互相垂直的四边形组成线段，从任何角度都能看到宽度。
    public static void segment(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double width,
                               float r, float g, float bl, float alpha) {
        if (a.distanceToSqr(b) < 1.0E-8D || width <= 0.0D || alpha <= 0.003F) return;
        Vec3 axis = b.subtract(a).normalize();
        Vec3 probe = new Vec3(0, 1, 0);
        if (Math.abs(axis.y) > 0.9D) probe = new Vec3(1, 0, 0);
        Vec3 side = axis.cross(probe).normalize().scale(width);
        Vec3 other = axis.cross(side).normalize().scale(width);
        // 闪电管线会剔除背面，每片面都要正反各写一次，四周才都能看见。
        for (Vec3 offset : new Vec3[]{side, other}) {
            Vec3 a0 = a.add(offset);
            Vec3 a1 = a.subtract(offset);
            Vec3 b0 = b.add(offset);
            Vec3 b1 = b.subtract(offset);
            vertex(v, m, a0, r, g, bl, alpha);
            vertex(v, m, b0, r, g, bl, alpha);
            vertex(v, m, b1, r, g, bl, alpha);
            vertex(v, m, a1, r, g, bl, alpha);
            vertex(v, m, a1, r, g, bl, alpha);
            vertex(v, m, b1, r, g, bl, alpha);
            vertex(v, m, b0, r, g, bl, alpha);
            vertex(v, m, a0, r, g, bl, alpha);
        }
    }

    // 三层同轴线段：光晕、过渡与电芯，形成发光电束截面。
    public static void glowSegment(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double width, float alpha) {
        segment(v, m, a, b, width * 2.8D, HALO[0], HALO[1], HALO[2], alpha * 0.26F);
        segment(v, m, a, b, width * 1.1D, MID[0], MID[1], MID[2], alpha * 0.7F);
        segment(v, m, a, b, width * 0.4D, CORE[0], CORE[1], CORE[2], Math.min(1.0F, alpha * 1.15F));
    }

    // 水平圆环，由多段梯形组成，避免粒子间隙。
    public static void ring(VertexConsumer v, Matrix4f m, Vec3 c, double radius, double width,
                            float r, float g, float bl, float alpha) {
        if (radius <= 0.0D || alpha <= 0.003F) return;
        int steps = 48;
        // 每段写入内外边四个顶点。
        for (int i = 0; i < steps; i++) {
            double a0 = i * Math.PI * 2.0D / steps;
            double a1 = (i + 1) * Math.PI * 2.0D / steps;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            double outer = radius + width;
            vertex(v, m, new Vec3(c.x + c0 * radius, c.y, c.z + s0 * radius), r, g, bl, alpha);
            vertex(v, m, new Vec3(c.x + c1 * radius, c.y, c.z + s1 * radius), r, g, bl, alpha);
            vertex(v, m, new Vec3(c.x + c1 * outer, c.y, c.z + s1 * outer), r, g, bl, 0.0F);
            vertex(v, m, new Vec3(c.x + c0 * outer, c.y, c.z + s0 * outer), r, g, bl, 0.0F);
        }
    }

    // 发光地面环：内亮外暗的三层环，用于冲击波和预警。
    public static void glowRing(VertexConsumer v, Matrix4f m, Vec3 c, double radius, double width, float alpha) {
        ring(v, m, c, radius, width * 3.2D, HALO[0], HALO[1], HALO[2], alpha * 0.4F);
        ring(v, m, c, radius, width * 1.4D, MID[0], MID[1], MID[2], alpha * 0.8F);
        ring(v, m, c, radius + width * 0.15D, width * 0.5D, CORE[0], CORE[1], CORE[2], alpha);
    }

    // 水平实心光盘，中心亮边缘透明，用于地面受击亮斑。
    public static void groundDisc(VertexConsumer v, Matrix4f m, Vec3 c, double radius,
                                  float r, float g, float bl, float alpha) {
        if (radius <= 0.0D || alpha <= 0.003F) return;
        int steps = 32;
        // 中心点重复一次组成退化四边形扇面。
        for (int i = 0; i < steps; i++) {
            double a0 = i * Math.PI * 2.0D / steps;
            double a1 = (i + 1) * Math.PI * 2.0D / steps;
            vertex(v, m, c, r, g, bl, alpha);
            vertex(v, m, c, r, g, bl, alpha);
            vertex(v, m, new Vec3(c.x + Math.cos(a1) * radius, c.y, c.z + Math.sin(a1) * radius), r, g, bl, 0.0F);
            vertex(v, m, new Vec3(c.x + Math.cos(a0) * radius, c.y, c.z + Math.sin(a0) * radius), r, g, bl, 0.0F);
        }
    }

    // 面向相机的径向光晕，三层叠加得到柔和的发光球。
    public static void glowOrb(VertexConsumer v, Matrix4f m, Quaternionf camera, Vec3 c, double radius, float alpha) {
        billboardDisc(v, m, camera, c, radius * 2.8D, HALO[0], HALO[1], HALO[2], alpha * 0.3F);
        billboardDisc(v, m, camera, c, radius * 1.3D, MID[0], MID[1], MID[2], alpha * 0.6F);
        billboardDisc(v, m, camera, c, radius * 0.5D, CORE[0], CORE[1], CORE[2], alpha);
    }

    // 相机朝向的扇形光盘，由相机四元数得到屏幕右向与上向。
    public static void billboardDisc(VertexConsumer v, Matrix4f m, Quaternionf camera, Vec3 c, double radius,
                                     float r, float g, float bl, float alpha) {
        if (radius <= 0.0D || alpha <= 0.003F) return;
        Vector3f right = camera.transform(new Vector3f(1, 0, 0));
        Vector3f up = camera.transform(new Vector3f(0, 1, 0));
        int steps = 20;
        // 以中心为顶点逐段写入扇面。
        for (int i = 0; i < steps; i++) {
            double a0 = i * Math.PI * 2.0D / steps;
            double a1 = (i + 1) * Math.PI * 2.0D / steps;
            Vec3 p0 = c.add(right.x * Math.cos(a0) * radius + up.x * Math.sin(a0) * radius,
                    right.y * Math.cos(a0) * radius + up.y * Math.sin(a0) * radius,
                    right.z * Math.cos(a0) * radius + up.z * Math.sin(a0) * radius);
            Vec3 p1 = c.add(right.x * Math.cos(a1) * radius + up.x * Math.sin(a1) * radius,
                    right.y * Math.cos(a1) * radius + up.y * Math.sin(a1) * radius,
                    right.z * Math.cos(a1) * radius + up.z * Math.sin(a1) * radius);
            vertex(v, m, c, r, g, bl, alpha);
            vertex(v, m, c, r, g, bl, alpha);
            vertex(v, m, p1, r, g, bl, 0.0F);
            vertex(v, m, p0, r, g, bl, 0.0F);
        }
    }

    // 受控随机折点的连续电束，端点收束、中段分叉，三层亮度叠加。
    public static void bolt(VertexConsumer v, Matrix4f m, Vec3 start, Vec3 end, Random random,
                            int steps, double width, float alpha, double jitter, int maxBranches) {
        if (steps < 1 || width <= 0.0D || alpha <= 0.003F) return;
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-8D) return;
        Vec3 drift = Vec3.ZERO;
        Vec3 previous = start;
        int branches = 0;
        // 逐段生成折点，记忆项让相邻折点朝同侧连贯偏移。
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            Vec3 point = start.lerp(end, progress);
            if (step < steps) {
                double envelope = Math.sin(Math.PI * progress);
                double offset = jitter * (0.25D + 0.75D * envelope) * (0.45D + random.nextDouble() * 0.55D);
                drift = drift.scale(0.58D).add(orthogonal(direction, random).scale(offset * 0.42D));
                point = point.add(drift);
            }
            double shape = Math.sin(Math.PI * (step - 0.5D) / steps);
            double segmentWidth = width * 1.35D * (0.3D + 0.7D * shape);
            float segmentAlpha = alpha * (float) (0.4D + 0.6D * shape);
            glowSegment(v, m, previous, point, segmentWidth, segmentAlpha);
            // 分叉概率随进度降低，限制数量控制面片。
            if (step >= 2 && step < steps - 1 && branches < maxBranches
                    && random.nextDouble() < 0.3D * (1.0D - progress)) {
                branch(v, m, point, direction, random, segmentWidth * 0.65D, segmentAlpha * 0.7F, jitter * 0.7D);
                branches++;
            }
            previous = point;
        }
    }

    // 从主干折点分出两到三段逐渐收束的短枝。
    private static void branch(VertexConsumer v, Matrix4f m, Vec3 start, Vec3 direction, Random random,
                               double width, float alpha, double jitter) {
        Vec3 forward = direction.normalize();
        Vec3 lateral = orthogonal(forward, random);
        Vec3 dir = forward.scale(0.1D + random.nextDouble() * 0.35D)
                .add(lateral.scale(0.7D + random.nextDouble() * 0.5D)).normalize();
        int steps = 2 + random.nextInt(2);
        double length = 0.6D + jitter * (1.1D + random.nextDouble() * 1.3D);
        Vec3 previous = start;
        // 枝条逐段前进，末端更细更暗。
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            Vec3 point = start.add(dir.scale(length * progress));
            if (step < steps) point = point.add(orthogonal(dir, random).scale(jitter * 0.25D * (1.0D - progress)));
            double taper = 1.0D - progress * 0.7D;
            glowSegment(v, m, previous, point, width * taper, alpha * (float) taper);
            previous = point;
        }
    }

    // 以中心向四周放射短电弧组成的星芒爆闪。
    public static void burst(VertexConsumer v, Matrix4f m, Vec3 center, Random random, double radius,
                             int rays, double width, float alpha) {
        if (radius <= 0.0D || alpha <= 0.003F) return;
        // 每条射线方向在球面上随机，纵向压扁一半。
        for (int ray = 0; ray < rays; ray++) {
            double yaw = random.nextDouble() * Math.PI * 2.0D;
            double pitch = (random.nextDouble() - 0.5D) * Math.PI * 0.6D;
            double length = radius * (0.45D + random.nextDouble() * 0.75D);
            Vec3 end = center.add(Math.cos(yaw) * Math.cos(pitch) * length, Math.sin(pitch) * length,
                    Math.sin(yaw) * Math.cos(pitch) * length);
            bolt(v, m, center, end, random, 3, width, alpha * (0.6F + 0.4F * random.nextFloat()), length * 0.18D, 0);
        }
    }

    // 地面放射电弧：贴地扩散的若干条随机电束。
    public static void groundArcs(VertexConsumer v, Matrix4f m, Vec3 center, Random random, double reach,
                                  int arms, double width, float alpha) {
        // 每条电弧独立抽取方向和长度，不形成规则图案。
        for (int arm = 0; arm < arms; arm++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double length = reach * (0.5D + random.nextDouble() * 0.5D);
            Vec3 start = center.add(0, 0.06D, 0);
            Vec3 end = start.add(Math.cos(angle) * length, 0.05D + random.nextDouble() * 0.25D,
                    Math.sin(angle) * length);
            bolt(v, m, start, end, random, 6, width, alpha * (0.7F + 0.3F * random.nextFloat()), 0.55D, 1);
        }
    }

    // 两条采样边之间的光带，用于刀光拖尾；外沿与内沿透明度不同形成软边。正反各写一次，绕到另一侧也能看见。
    public static void ribbon(VertexConsumer v, Matrix4f m, Vec3 aOuter, Vec3 aInner, Vec3 bOuter, Vec3 bInner,
                              float r, float g, float bl, float alphaA, float alphaB, float innerScale) {
        vertex(v, m, aOuter, r, g, bl, alphaA);
        vertex(v, m, bOuter, r, g, bl, alphaB);
        vertex(v, m, bInner, r, g, bl, alphaB * innerScale);
        vertex(v, m, aInner, r, g, bl, alphaA * innerScale);
        vertex(v, m, aOuter, r, g, bl, alphaA);
        vertex(v, m, aInner, r, g, bl, alphaA * innerScale);
        vertex(v, m, bInner, r, g, bl, alphaB * innerScale);
        vertex(v, m, bOuter, r, g, bl, alphaB);
    }

    // 取与给定方向垂直的随机单位向量。
    public static Vec3 orthogonal(Vec3 direction, Random random) {
        Vec3 probe = new Vec3(random.nextDouble() - 0.5D, random.nextDouble() - 0.5D, random.nextDouble() - 0.5D);
        Vec3 result = direction.cross(probe);
        if (result.lengthSqr() < 1.0E-8D) result = direction.cross(new Vec3(1, 0, 0));
        if (result.lengthSqr() < 1.0E-8D) result = direction.cross(new Vec3(0, 0, 1));
        return result.normalize();
    }

    // 平滑步进，将输入限制在零到一并做三次缓动。
    public static double smooth(double x) {
        if (x <= 0.0D) return 0.0D;
        if (x >= 1.0D) return 1.0D;
        return x * x * (3.0D - 2.0D * x);
    }

    // 将数值夹在零到一之间。
    public static float clamp01(double x) {
        if (x <= 0.0D) return 0.0F;
        if (x >= 1.0D) return 1.0F;
        return (float) x;
    }
}
