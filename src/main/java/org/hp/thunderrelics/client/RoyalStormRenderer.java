package org.hp.thunderrelics.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.entity.RoyalStormWave;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.Random;

// 蓝色符环、立体落雷和地面电弧都直接绘制网格，使用固定种子保持连续形状。
public final class RoyalStormRenderer extends EntityRenderer<RoyalStormWave> {
    public RoyalStormRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override
    public ResourceLocation getTextureLocation(RoyalStormWave entity) { return new ResourceLocation("minecraft", "textures/misc/white.png"); }
    // 波次实体在场地中央，但边缘落点也需要渲染，因此用场地距离判断。
    @Override
    public boolean shouldRender(RoyalStormWave entity, Frustum frustum, double x, double y, double z) { return entity.distanceToSqr(x, y, z) < 160 * 160; }

    @Override
    public void render(RoyalStormWave wave, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        double age = wave.age(partialTick);
        if (age < 0 || age > RoyalStormWave.LIFE_TICKS) return;
        VertexConsumer vertices = buffers.getBuffer(ThunderLightningMaterial.TYPE);
        Matrix4f matrix = pose.last().pose();
        // 落雷阶段在场地上方增加一层随机电弧，轨迹固定后再随时间淡出，避免画面闪烁。
        if (age >= RoyalStormWave.WARNING_TICKS)
            airborneArcs(vertices, matrix, wave, age - RoyalStormWave.WARNING_TICKS);
        int index = 0;
        for (Vec3 world : wave.points()) {
            Vec3 p = world.subtract(wave.position());
            // 警示圈在落雷前持续收缩，落雷阶段增加多道雷束。
            if (age < RoyalStormWave.WARNING_TICKS) {
                double progress = age / RoyalStormWave.WARNING_TICKS, radius = 2.3 * (1 - progress) + .18;
                ring(vertices, matrix, p, 1.55, .075, .12F, .45F, 1, .9F);
                ring(vertices, matrix, p.add(0, .012, 0), radius, .13, .4F, .85F, 1, 1);
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2 + progress * .35;
                    segment(vertices, matrix, p.add(Math.cos(a) * .35, .025, Math.sin(a) * .35),
                            p.add(Math.cos(a) * .85, .025, Math.sin(a) * .85), .035, .4F, .8F, 1, .95F);
                }
            } else {
                // 一次落雷持续七刻并快速衰减；蓝色包边内保留清晰的白色电芯。
                double t = age - RoyalStormWave.WARNING_TICKS;
                Random random = new Random(87123L + wave.getId() * 1009L + index * 977L);
                if (t < 7) {
                    float alpha = (float) Math.max(0, 1 - t / 7);
                    // 每个落点同时生成三道从不同天空位置汇聚的电束，形成多雷束落地效果。
                    for (int beam = 0; beam < 3; beam++) {
                        double angle = beam * Math.PI * 2.0D / 3.0D + random.nextDouble() * .35D;
                        double skyRadius = .8D + random.nextDouble() * 1.4D;
                        Vec3 sky = p.add(Math.cos(angle) * skyRadius, 33.6D + random.nextDouble() * 2.0D,
                                Math.sin(angle) * skyRadius);
                        Vec3 end = p.add((random.nextDouble() - .5D) * .35D, .05D,
                                (random.nextDouble() - .5D) * .35D);
                        lightningPath(vertices, matrix, sky, end, random, 12, .20D, alpha, .95D);
                    }
                    // 贴图粒子中的瞬时闪白改为纯代码星芒，命中时只出现极短一闪。
                    if (t < 3.5D)
                        flashBurst(vertices, matrix, p.add(0, .12, 0), random, 1.15D,
                                alpha * (float) (1.0D - t / 3.5D));
                }
                // 命中瞬间显示十二条完整的独立随机路径，只做整体淡出，不播放蔓延动画。
                random.setSeed(43971L + wave.getId() * 1009L + index * 977L);
                float fade = (float) Math.max(0, 1 - t / 24);
                double reach = 2.8;
                ring(vertices, matrix, p, Math.max(.1, reach), .07, .3F, .7F, 1, fade * .6F);
                for (int arm = 0; arm < 12; arm++) {
                    // 每条电弧独立抽取方向、长度和侧向偏移，不再组成规则放射图案。
                    double angle = random.nextDouble() * Math.PI * 2;
                    double totalReach = reach * (.55 + random.nextDouble() * .45);
                    Vec3 start = p.add(0, .07, 0);
                    Vec3 end = start.add(Math.cos(angle) * totalReach, .08 + random.nextDouble() * .3,
                            Math.sin(angle) * totalReach);
                    // 地面余波同样使用逐段收束，分叉末端更细，电弧会更像放电而非硬质模型。
                    lightningPath(vertices, matrix, start, end, random, 6, .07D,
                            fade * (.72F + random.nextFloat() * .28F), .85D);
                }
            }
            index++;
        }
    }

    // 双手举高武器的仪式阶段持续接引雷霆；端点使用武器头骨骼矩阵，保证特效不会漂离武器。
    public static void renderWeaponRelay(ThunderKingEntity entity, Matrix4f weaponTransform, PoseStack pose,
                                         MultiBufferSource buffers, float partialTick) {
        if (!entity.isAlive() || weaponTransform == null || entity.getSwingKind() != 5) return;
        double age = entity.level().getGameTime() + partialTick - entity.getSwingStart();
        // 动画前段是同步举手，后段开始收招，只在武器稳定举高的区间显示接引雷霆。
        if (age < 30.0D || age > 100.0D) return;
        float fadeIn = (float) Math.min(1.0D, (age - 30.0D) / 5.0D);
        float fadeOut = (float) Math.min(1.0D, (100.0D - age) / 8.0D);
        float pulse = 0.88F + 0.12F * (float) Math.sin((entity.level().getGameTime() + partialTick) * 0.45D);
        float alpha = fadeIn * fadeOut * pulse;
        if (alpha <= 0.01F) return;

        VertexConsumer vertices = buffers.getBuffer(ThunderLightningMaterial.TYPE);
        Matrix4f matrix = pose.last().pose();
        // weapon_head 的局部 Y 轴长度来自模型武器尖端（约 24 像素），因此端点会随动画精确移动。
        Vector4f transformedTip = weaponTransform.transform(new Vector4f(0.0F, 1.52F, 0.0F, 1.0F));
        Vec3 tip = new Vec3(transformedTip.x(), transformedTip.y(), transformedTip.z());
        // 每条雷线只存在约五刻，并按错峰相位重新抽取路径；因此画面始终有新雷线接引，而不是静态拼接。
        double now = entity.level().getGameTime() + partialTick;
        final double life = 5.0D;
        for (int bolt = 0; bolt < 8; bolt++) {
            double offset = bolt * .63D;
            long cycle = (long) Math.floor((now + offset) / life);
            double phase = (now + offset - cycle * life) / life;
            float boltAlpha = alpha * (float) Math.pow(Math.sin(Math.PI * phase), .65D);
            Random random = new Random(0x71A9BEEFL + entity.getId() * 31337L
                    + cycle * 1009L + bolt * 977L);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 3.0D + random.nextDouble() * 6.0D;
            double height = 16.0D + random.nextDouble() * 9.0D;
            Vec3 sky = tip.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            // 每条接引雷霆都有白色电芯、青白过渡和低透明蓝色光晕，首尾向两端收束。
            // 中继折点显著偏离直线，打破多条细直线组成的漏斗轮廓；分叉从真实折点接出。
            Vec3 junction = tip.lerp(sky, .42D).add((random.nextDouble() - .5D) * 4.0D,
                    0, (random.nextDouble() - .5D) * 4.0D);
            lightningPath(vertices, matrix, sky, junction, random, 7, .18D, boltAlpha, 2.8D);
            lightningPath(vertices, matrix, junction, tip, random, 6, .15D, boltAlpha, 1.8D);
            // 主雷线中段分出短枝；枝条也随本条短命雷线一起更新。
            if (bolt % 2 == 0) {
                Vec3 branchStart = junction;
                Vec3 branchEnd = branchStart.add((random.nextDouble() - .5D) * 3.0D,
                        (random.nextDouble() - .2D) * 2.0D,
                        (random.nextDouble() - .5D) * 3.0D);
                lightningPath(vertices, matrix, branchStart, branchEnd, random, 3, .09D,
                        boltAlpha * .55F, .55D);
            }
        }
        // 武器顶部加一圈小型汇聚环，明确所有雷线的终点。
        ring(vertices, matrix, tip, .32D, .055D, .15F, .65F, 1.0F, alpha * .9F);
        // 戟头附近持续刷新短放电，与天空主雷线形成大小和寿命差异。
        long sparkCycle = (long) Math.floor(now / 3.0D);
        float sparkFade = (float) (1.0D - (now / 3.0D - sparkCycle));
        flashBurst(vertices, matrix, tip, new Random(entity.getId() * 977L + sparkCycle),
                .85D, alpha * sparkFade * .8F);
    }

    // 双手下劈的电弧直接绑定实际刃头矩阵，蓄力包裹戟刃，挥砍阶段强化，释放时短暂放电。
    public static void renderSlamLightning(ThunderKingEntity entity, Matrix4f weaponTransform,
                                           PoseStack pose, MultiBufferSource buffers, float partialTick) {
        if (!entity.isAlive() || weaponTransform == null || entity.getSwingKind() != 3) return;
        double age = entity.level().getGameTime() + partialTick - entity.getSwingStart();
        if (age < 5.0D || age > 23.0D) return;
        VertexConsumer vertices = buffers.getBuffer(ThunderLightningMaterial.TYPE);
        Matrix4f matrix = pose.last().pose();
        float fade = (float) Math.min(1.0D, Math.min((age - 5.0D) / 5.0D, (23.0D - age) / 4.0D));
        // 固定周期内保持路径一致，骨骼变换每帧更新，让电弧随武器运动。
        long cycle = (long) Math.floor(age / 3.0D);
        Random random = new Random(entity.getId() * 31337L + entity.getSwingStart() + cycle * 977L);
        for (int arc = 0; arc < 5; arc++) {
            double angle = arc * Math.PI * 2.0D / 5.0D + random.nextDouble() * .6D;
            float x = (float) Math.cos(angle) * .22F;
            float z = (float) Math.sin(angle) * .22F;
            Vector4f base = weaponTransform.transform(new Vector4f(x, .1F, z, 1));
            Vector4f end = weaponTransform.transform(new Vector4f(-x, 1.66F, -z, 1));
            lightningPath(vertices, matrix, new Vec3(base.x(), base.y(), base.z()),
                    new Vec3(end.x(), end.y(), end.z()), random, 5,
                    age >= 14.8D ? .085D : .045D, fade, .45D);
        }
        // 服务端双手下劈在第十八刻结算；释放闪光从这一刻开始，与伤害时序对应。
        if (age >= 18.0D && age < 22.0D) {
            Vector4f end = weaponTransform.transform(new Vector4f(0, 1.66F, 0, 1));
            flashBurst(vertices, matrix, new Vec3(end.x(), end.y(), end.z()),
                    new Random(entity.getId() * 977L + entity.getSwingStart()), 1.6D,
                    (float) (1.0D - (age - 18.0D) / 4.0D));
        }
    }

    // 在波次中心上方铺开随机高度和随机方向的电弧，形成雷雨云层中的二级电场。
    private static void airborneArcs(VertexConsumer v, Matrix4f m, RoyalStormWave wave, double elapsed) {
        // 空中电弧也在落雷出现的同一帧完整显示，只随生命周期整体淡出。
        float fade = (float) Math.max(0, 1 - elapsed / 28);
        if (fade <= 0) return;
        Random random = new Random(0x5EEDL + wave.getId() * 31337L);
        Vec3 center = Vec3.ZERO;
        for (int arc = 0; arc < 18; arc++) {
            double startAngle = random.nextDouble() * Math.PI * 2;
            double startRadius = 5 + random.nextDouble() * 22;
            double startY = 5 + random.nextDouble() * 15;
            Vec3 start = center.add(Math.cos(startAngle) * startRadius, startY, Math.sin(startAngle) * startRadius);
            double endAngle = startAngle + (random.nextDouble() - .5D) * 1.8D;
            double length = 3.5D + random.nextDouble() * 7.0D;
            Vec3 end = start.add(Math.cos(endAngle) * length, (random.nextDouble() - .5D) * 4.0D,
                    Math.sin(endAngle) * length);
            // 云层电弧采用短路径和首尾渐细的亮度，减少多段等粗线条带来的塑料感。
            lightningPath(v, m, start, end, random, 6, .075D,
                    fade * (.35F + random.nextFloat() * .45F), 1.25D);
        }
    }

    // 水平双面圆环由四十八个梯形构成，避免粒子间隙和近距离闪烁。
    private static void ring(VertexConsumer v, Matrix4f m, Vec3 p, double r, double width, float red, float green, float blue, float alpha) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24, b = (i + 1) * Math.PI / 24;
            // 直接提交四个顶点，避免多波预警时每帧创建上万个临时数组。
            double ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b);
            v.vertex(m,(float)(p.x+ca*r),(float)p.y,(float)(p.z+sa*r)).color(red,green,blue,alpha).endVertex();
            v.vertex(m,(float)(p.x+cb*r),(float)p.y,(float)(p.z+sb*r)).color(red,green,blue,alpha).endVertex();
            v.vertex(m,(float)(p.x+cb*(r+width)),(float)p.y,(float)(p.z+sb*(r+width))).color(red,green,blue,alpha).endVertex();
            v.vertex(m,(float)(p.x+ca*(r+width)),(float)p.y,(float)(p.z+sa*(r+width))).color(red,green,blue,alpha).endVertex();
        }
    }
    // 两片互相垂直的四边形使电弧从侧面也能看见。
    private static void segment(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double width, float red, float green, float blue, float alpha) {
        if (a.distanceToSqr(b) < 1.0E-8D || width <= 0.0D || alpha <= 0.0F) return;
        Vec3 axis = b.subtract(a).normalize();
        Vec3 side = axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1,0,0) : new Vec3(0,1,0)).normalize().scale(width);
        Vec3 other = axis.cross(side).normalize().scale(width);
        for (Vec3 offset : new Vec3[]{side, other})
            for (Vec3 point : new Vec3[]{a.add(offset), b.add(offset), b.subtract(offset), a.subtract(offset)})
                v.vertex(m, (float) point.x, (float) point.y, (float) point.z).color(red, green, blue, alpha).endVertex();
    }

    // 生成一条受控随机折点、端点收束、分叉和三层亮度的连续电束。
    // 折点保留一小段记忆，路径会像斯库拉的程序化闪电一样连贯，不会每段完全独立抖动。
    private static void lightningPath(VertexConsumer v, Matrix4f m, Vec3 start, Vec3 end, Random random,
                                      int steps, double width, float alpha, double jitter) {
        if (steps < 1 || width <= 0.0D || alpha <= 0.0F) return;
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-8D) return;
        Vec3 drift = Vec3.ZERO;
        Vec3 previous = start;
        int branchCount = 0;
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            Vec3 point = start.lerp(end, progress);
            if (step < steps) {
                // 中段偏移更强，记忆项让相邻折点朝同一侧自然偏移。
                double envelope = Math.sin(Math.PI * progress);
                double maxOffset = jitter * (.25D + .75D * envelope)
                        * (.45D + random.nextDouble() * .55D);
                Vec3 offset = randomOrthogonal(direction, random).scale(maxOffset);
                drift = drift.scale(.58D).add(offset.scale(.42D));
                point = point.add(drift);
            }
            double shape = Math.sin(Math.PI * (step - .5D) / steps);
            double taper = .25D + .75D * shape;
            double segmentWidth = width * taper;
            float segmentAlpha = alpha * (float) (.35D + .65D * shape);
            // 三层网格分别提供蓝色光晕、青白过渡和近白电芯，完全不依赖贴图。
            segment(v, m, previous, point, segmentWidth * 1.65D,
                    .04F, .20F, 1.0F, segmentAlpha * .24F);
            segment(v, m, previous, point, segmentWidth * .72D,
                    .24F, .70F, 1.0F, segmentAlpha * .62F);
            segment(v, m, previous, point, segmentWidth * .23D,
                    .82F, .98F, 1.0F, Math.min(1.0F, segmentAlpha * 1.08F));
            // 分叉概率随路径进度降低，限制数量避免雷雨场景产生过多面片。
            if (step >= 2 && step < steps - 1 && branchCount < 2
                    && random.nextDouble() < .24D * (1.0D - progress)) {
                lightningBranch(v, m, point, direction, random, segmentWidth * .70D,
                        segmentAlpha * .65F, jitter * .65D);
                branchCount++;
            }
            previous = point;
        }
    }

    // 从主路径分出两到三段短枝，末端同样逐渐收束。
    private static void lightningBranch(VertexConsumer v, Matrix4f m, Vec3 start, Vec3 direction,
                                        Random random, double width, float alpha, double jitter) {
        if (direction.lengthSqr() < 1.0E-8D || width <= 0.0D || alpha <= 0.0F) return;
        Vec3 forward = direction.normalize();
        Vec3 lateral = randomOrthogonal(forward, random);
        Vec3 branchDirection = forward.scale(-.15D + random.nextDouble() * .35D)
                .add(lateral.scale(.75D + random.nextDouble() * .55D)).normalize();
        int steps = 2 + random.nextInt(2);
        double length = .7D + jitter * (1.2D + random.nextDouble() * 1.4D);
        Vec3 previous = start;
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            Vec3 point = start.add(branchDirection.scale(length * progress));
            if (step < steps) {
                point = point.add(randomOrthogonal(branchDirection, random)
                        .scale(jitter * .25D * (1.0D - progress)));
            }
            double shape = Math.sin(Math.PI * (step - .5D) / steps);
            double taper = .22D + .78D * shape;
            float segmentAlpha = alpha * (float) (.35D + .65D * shape);
            segment(v, m, previous, point, width * taper * 1.55D,
                    .04F, .20F, 1.0F, segmentAlpha * .20F);
            segment(v, m, previous, point, width * taper * .68D,
                    .24F, .70F, 1.0F, segmentAlpha * .58F);
            segment(v, m, previous, point, width * taper * .22D,
                    .82F, .98F, 1.0F, Math.min(1.0F, segmentAlpha));
            previous = point;
        }
    }

    // 选取垂直于主方向的随机单位向量，用于生成稳定的侧向折点。
    private static Vec3 randomOrthogonal(Vec3 direction, Random random) {
        Vec3 probe = new Vec3(random.nextDouble() - .5D, random.nextDouble() - .5D,
                random.nextDouble() - .5D);
        Vec3 orthogonal = direction.cross(probe);
        if (orthogonal.lengthSqr() < 1.0E-8D)
            orthogonal = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        if (orthogonal.lengthSqr() < 1.0E-8D)
            orthogonal = direction.cross(new Vec3(0.0D, 0.0D, 1.0D));
        return orthogonal.normalize();
    }

    // 用短电束组成瞬时星芒，替代贴图粒子的命中闪白，电弧只在极短时间内出现。
    private static void flashBurst(VertexConsumer v, Matrix4f m, Vec3 center, Random random,
                                   double radius, float alpha) {
        if (radius <= 0.0D || alpha <= 0.0F) return;
        int rays = 10;
        for (int ray = 0; ray < rays; ray++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double length = radius * (.45D + random.nextDouble() * .85D);
            Vec3 end = center.add(Math.cos(angle) * length,
                    (random.nextDouble() - .5D) * radius * .7D,
                    Math.sin(angle) * length);
            lightningPath(v, m, center, end, random, 2, .09D,
                    alpha * (.55F + .45F * random.nextFloat()), .25D);
        }
    }
}
