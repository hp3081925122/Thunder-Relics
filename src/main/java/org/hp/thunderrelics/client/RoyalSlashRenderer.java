package org.hp.thunderrelics.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.model.GeoModel;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

// 直接绘制有宽度和渐变的刀光网格，不使用原版粒子拼接主体。
public final class RoyalSlashRenderer {
    // 刀光窗口与服务端攻击周期保持相同的八成时长。
    private static final double COMBAT_RATE = 0.80D;
    private final Map<ThunderKingEntity, Trail> trails = new WeakHashMap<>();

    // 延长残光后最多保留六十四段，缓存不反向引用实体，换世界后可自动回收。
    private static final class Trail {
        long start = -1;
        double lastSample = Double.NEGATIVE_INFINITY;
        final ArrayList<Sample> samples = new ArrayList<>();
    }

    private record Sample(double time, Vec3 outer, Vec3 inner, int stroke) {}

    // 横斩形成宽弧，连斩形成双层细弧，重劈形成更宽且稍长的纵向残光。
    public void render(ThunderKingEntity entity, GeoModel<ThunderKingEntity> model, Matrix4f bladeTransform, PoseStack pose,
                       MultiBufferSource buffers, float partialTick) {
        int kind = entity.getSwingKind();
        long start = entity.getSwingStart();
        double now = entity.level().getGameTime() + partialTick;
        double age = now - start;
        if (!entity.isAlive() || kind < 0 || kind > 3 || age < 0 || age > 36) {
            trails.remove(entity);
            return;
        }
        Trail trail = trails.computeIfAbsent(entity, ignored -> new Trail());
        if (trail.start != start) {
            trail.start = start;
            trail.samples.clear();
            trail.lastSample = Double.NEGATIVE_INFINITY;
        }
        // 每段残光寿命翻倍：横斩与连斩半秒，重劈七成秒。
        double life = kind >= 2 ? 8.0D : 6.0D;
        trail.samples.removeIf(sample -> now - sample.time() > life || now < sample.time());
        boolean active = switch (kind) {
            case 0 -> age >= 10.5D * COMBAT_RATE && age <= 15.5D * COMBAT_RATE;
            case 1 -> age >= 10.5D * COMBAT_RATE && age <= 15.5D * COMBAT_RATE
                    || age >= 22.0D * COMBAT_RATE && age <= 28.0D * COMBAT_RATE;
            case 2 -> age >= 15.0D * COMBAT_RATE && age <= 21.0D * COMBAT_RATE;
            default -> age >= 18.5D * COMBAT_RATE && age <= 24.5D * COMBAT_RATE;
        };
        Vec3 entityPosition = entity.getPosition(partialTick);
        // 世界坐标保存旧刀光，使生物移动时残光留在原挥砍位置。
        var bone = model.getBone("weapon_head").orElse(null);
        // 开发采集同时输出真实骨骼矩阵和刀光采样量，正式运行不做文件记录。
        if (RoyalSlashDebug.ENABLED && bone != null && bladeTransform != null)
            RoyalSlashDebug.trace(entity, bone, bladeTransform, age, active, entityPosition, trail.samples.size());
        if (active && bladeTransform != null && now - trail.lastSample >= 0.2D) {
            float width = kind >= 2 ? 1.15F : kind == 1 ? 0.55F : 0.8F;
            Vector4f outer = bladeTransform.transform(new Vector4f(0, 1.66F, 0, 1));
            Vector4f inner = bladeTransform.transform(new Vector4f(0, 1.66F - width, 0, 1));
            Sample sample = new Sample(now, new Vec3(outer.x(), outer.y(), outer.z()).add(entityPosition),
                    new Vec3(inner.x(), inner.y(), inner.z()).add(entityPosition), kind == 1 && age >= 22.0D * COMBAT_RATE ? 1 : 0);
            if (!trail.samples.isEmpty() && sample.outer().distanceToSqr(trail.samples.get(trail.samples.size() - 1).outer()) > 16) {
                trail.samples.clear();
            }
            trail.samples.add(sample);
            if (trail.samples.size() > 64) trail.samples.remove(0);
            trail.lastSample = now;
        }
        if (trail.samples.size() < 2) return;
        VertexConsumer vertices = buffers.getBuffer(ThunderLightningMaterial.TYPE);
        Matrix4f matrix = pose.last().pose();
        // 三层连续色阶：窄白芯、青蓝过渡、透明蓝尾，旧轨迹逐段消散。
        for (int i = 1; i < trail.samples.size(); i++) {
            Sample a = trail.samples.get(i - 1);
            Sample b = trail.samples.get(i);
            // 连斩前后两刀可同时残留，但不跨过停顿连接成额外光面。
            if (a.stroke() != b.stroke()) continue;
            // 前两成寿命保留完整亮度，随后平滑淡出，提高刀光的清晰度。
            float alphaA = (float) Math.max(0, Math.min(1, (life - (now - a.time())) / (life * 0.8D)));
            float alphaB = (float) Math.max(0, Math.min(1, (life - (now - b.time())) / (life * 0.8D)));
            float taper = (float) Math.sin(Math.PI * i / trail.samples.size());
            float widthScale = 0.15F + 0.85F * taper;
            strip(vertices, matrix, entityPosition, a, b, 0, 0.09F * widthScale,
                    0.9F, 0.98F, 1.0F, alphaA, alphaB, 1);
            strip(vertices, matrix, entityPosition, a, b, 0.09F * widthScale, 0.32F * widthScale,
                    0.28F, 0.72F, 1.0F, alphaA, alphaB, 0.9F);
            strip(vertices, matrix, entityPosition, a, b, 0.32F * widthScale, widthScale,
                    0.08F, 0.3F, 1.0F, alphaA * 0.8F, alphaB * 0.8F, 0);
            // 连斩的第二条细亮线沿内缘展开，区别于普通横扫与厚重下劈。
            if (kind == 1) {
                strip(vertices, matrix, entityPosition, a, b, 0.65F * widthScale, 0.7F * widthScale,
                        0.6F, 0.85F, 1.0F, alphaA * 0.95F, alphaB * 0.95F, 0.95F);
            }
        }
    }

    // 连续两次采样组成一块双面四边形，内外边使用不同透明度形成软边。
    private static void strip(VertexConsumer vertices, Matrix4f matrix, Vec3 origin, Sample a, Sample b,
                              float from, float to, float red, float green, float blue,
                              float alphaA, float alphaB, float innerAlpha) {
        Vec3[] points = {a.outer().lerp(a.inner(), from), b.outer().lerp(b.inner(), from),
                b.outer().lerp(b.inner(), to), a.outer().lerp(a.inner(), to)};
        float[] alpha = {alphaA, alphaB, alphaB * innerAlpha, alphaA * innerAlpha};
        for (int i = 0; i < 4; i++) {
            Vec3 point = points[i].subtract(origin);
            vertices.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                    .color(red, green, blue, alpha[i]).endVertex();
        }
    }
}
