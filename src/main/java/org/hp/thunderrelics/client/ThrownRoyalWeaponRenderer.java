package org.hp.thunderrelics.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.Thunderrelics;
import org.hp.thunderrelics.client.fx.FxDraw;
import org.hp.thunderrelics.entity.ThrownRoyalWeapon;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

// 新版弹体渲染保留原武器几何，并在飞行中绘制螺旋雷光拖尾与戟身电弧。
public final class ThrownRoyalWeaponRenderer extends GeoEntityRenderer<ThrownRoyalWeapon, ThrownRoyalWeaponRenderState> {
    // 世界坐标的飞行轨迹采样，按实体编号保存。
    private record Point(double time, Vec3 pos) {
    }

    private static final Map<Integer, List<Point>> TRAILS = new HashMap<>();
    private static final double TRAIL_LIFE = 7.0D;

    public ThrownRoyalWeaponRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<ThrownRoyalWeapon>() {
            // 模型放在 GeckoLib 5 的模型目录。
            @Override
            public Identifier getModelResource(GeoRenderState state) {
                return Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID, "thunder_thrown_weapon");
            }

            // 继续使用王戟图集。
            @Override
            public Identifier getTextureResource(GeoRenderState state) {
                return Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID, "textures/entity/thunder_king_armed.png");
            }

            // 弹体没有独立动画控制器。
            @Override
            public Identifier getAnimationResource(ThrownRoyalWeapon entity) {
                return Identifier.fromNamespaceAndPath(Thunderrelics.MOD_ID, "thunder_king");
            }
        });
        shadowRadius = 0.2F;
    }

    @Override
    public ThrownRoyalWeaponRenderState createRenderState(ThrownRoyalWeapon entity, Void context) {
        return new ThrownRoyalWeaponRenderState();
    }

    // 提取阶段完成方向插值，并记录飞行方向与位置供特效使用。
    @Override
    public void extractRenderState(ThrownRoyalWeapon entity, ThrownRoyalWeaponRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        state.entityId = entity.getId();
        state.worldTime = entity.level().getGameTime() + partialTick;
        state.worldPos = entity.getPosition(partialTick);
        Vec3 motion = entity.getDeltaMovement();
        state.direction = Vec3.ZERO;
        if (motion.lengthSqr() > 1.0E-6D) state.direction = motion.normalize();
    }

    // 戟头沿模型正向竖轴，旋转后对齐飞行方向，再绕杆身转九十度让刃面竖直。
    @Override
    protected void applyRotations(RenderPassInfo<ThrownRoyalWeaponRenderState> pass, PoseStack pose, float nativeScale) {
        pose.mulPose(Axis.YP.rotationDegrees(pass.renderState().yaw));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F - pass.renderState().pitch));
        pose.mulPose(Axis.YP.rotationDegrees(90.0F));
    }

    // 模型提交后追加拖尾特效。
    @Override
    public void submit(ThrownRoyalWeaponRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        List<Point> trail = TRAILS.computeIfAbsent(state.entityId, id -> new ArrayList<>());
        double now = state.worldTime;
        trail.removeIf(point -> now - point.time() > TRAIL_LIFE || now < point.time());
        if (trail.isEmpty() || now - trail.get(trail.size() - 1).time() >= 0.25D) trail.add(new Point(now, state.worldPos));
        // 很久未更新的弹体轨迹清除，防止缓存增长。
        if (TRAILS.size() > 8) TRAILS.entrySet().removeIf(entry -> entry.getValue().isEmpty()
                || Math.abs(now - entry.getValue().get(entry.getValue().size() - 1).time()) > 40.0D);
        if (state.direction.lengthSqr() < 0.5D) return;
        List<Point> snapshot = new ArrayList<>(trail);
        Vec3 origin = state.worldPos;
        Vec3 dir = state.direction;
        Quaternionf orientation = new Quaternionf(camera.orientation);
        collector.submitCustomGeometry(pose, RenderTypes.lightning(), (entry, vertices) -> {
            Matrix4f matrix = new Matrix4f(entry.pose());
            helix(vertices, matrix, snapshot, origin, now, state.entityId);
            weaponArcs(vertices, matrix, orientation, dir, now, state.entityId);
        });
    }

    // 三股螺旋电光缠绕飞行轨迹，越旧越细越暗。
    private static void helix(VertexConsumer v, Matrix4f m, List<Point> trail, Vec3 origin, double now, int id) {
        if (trail.size() < 2) return;
        // 三股螺旋相位相差一百二十度。
        for (int strand = 0; strand < 3; strand++) {
            Vec3 previous = null;
            for (int i = 0; i < trail.size(); i++) {
                Point point = trail.get(i);
                Vec3 forward = Vec3.ZERO;
                if (i + 1 < trail.size()) forward = trail.get(i + 1).pos().subtract(point.pos());
                else if (i > 0) forward = point.pos().subtract(trail.get(i - 1).pos());
                if (forward.lengthSqr() < 1.0E-6D) continue;
                forward = forward.normalize();
                Vec3 side = forward.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1.0E-4D) side = new Vec3(1, 0, 0);
                side = side.normalize();
                Vec3 up = side.cross(forward).normalize();
                double phase = point.time() * 1.6D + strand * Math.PI * 2.0D / 3.0D;
                double radius = 0.32D * (0.6D + 0.4D * FxDraw.clamp01((now - point.time()) / 3.0D));
                Vec3 pos = point.pos().subtract(origin).add(side.scale(Math.cos(phase) * radius)).add(up.scale(Math.sin(phase) * radius));
                if (previous != null) {
                    float alpha = FxDraw.clamp01(1.0D - (now - point.time()) / TRAIL_LIFE);
                    FxDraw.glowSegment(v, m, previous, pos, 0.07D * alpha + 0.02D, alpha * 0.9F);
                }
                previous = pos;
            }
        }
        // 轨迹中心一条淡蓝光带，强调飞行路线。
        for (int i = 1; i < trail.size(); i++) {
            Point a = trail.get(i - 1), b = trail.get(i);
            float alpha = FxDraw.clamp01(1.0D - (now - b.time()) / TRAIL_LIFE);
            FxDraw.segment(v, m, a.pos().subtract(origin), b.pos().subtract(origin), 0.12D * alpha,
                    FxDraw.HALO[0], FxDraw.HALO[1], FxDraw.HALO[2], alpha * 0.35F);
        }
    }

    // 飞行中的戟身电弧与刃尖光球。
    private static void weaponArcs(VertexConsumer v, Matrix4f m, Quaternionf cam, Vec3 dir, double now, int id) {
        Vec3 tip = dir.scale(3.4D);
        Vec3 head = dir.scale(1.97D);
        Vec3 butt = dir.scale(-1.1D);
        Random random = new Random(id * 6151L + (long) Math.floor(now / 2.0D));
        // 四条电弧从杆身窜向刃尖。
        for (int i = 0; i < 4; i++) {
            Vec3 start = butt.lerp(head, random.nextDouble()).add(FxDraw.orthogonal(dir, random).scale(0.15D));
            Vec3 end = tip.add(FxDraw.orthogonal(dir, random).scale(0.2D));
            FxDraw.bolt(v, m, start, end, random, 5, 0.06D, 0.9F, 0.25D, 1);
        }
        double pulse = 0.85D + 0.15D * Math.sin(now * 2.0D);
        FxDraw.glowOrb(v, m, cam, tip, 0.45D * pulse, 0.9F);
        FxDraw.glowOrb(v, m, cam, head, 0.3D, 0.5F);
    }
}
