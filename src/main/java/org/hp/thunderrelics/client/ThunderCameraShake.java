package org.hp.thunderrelics.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.hp.thunderrelics.entity.RoyalStormWave;
import org.hp.thunderrelics.entity.ThunderFx;

// 监听落雷、砸地与遗石雷殛的冲击时刻，按距离为玩家叠加短时镜头晃动。
public final class ThunderCameraShake {
    private static final Map<Integer, Double> LAST_AGES = new HashMap<>();
    private static float trauma;

    private ThunderCameraShake() {
    }

    // 客户端刻检测各特效第一次跨过冲击刻。
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            LAST_AGES.clear();
            trauma = 0.0F;
            return;
        }
        trauma *= 0.82F;
        Set<Integer> active = new HashSet<>();
        AABB area = minecraft.player.getBoundingBox().inflate(48.0D);
        // 引雷落雷波：落雷刻轻微晃动。
        for (RoyalStormWave wave : minecraft.level.getEntitiesOfClass(RoyalStormWave.class, area)) {
            active.add(wave.getId());
            if (crossed(wave.getId(), wave.age(0.0F), RoyalStormWave.WARNING_TICKS)) add(0.22F, 1.0D);
        }
        // 特效实体：按种类设定冲击刻与强度，并按距离衰减。
        for (ThunderFx fx : minecraft.level.getEntitiesOfClass(ThunderFx.class, area)) {
            active.add(fx.getId());
            double distance = fx.distanceTo(minecraft.player);
            double age = fx.age(0.0F);
            if (fx.kind() == ThunderFx.HIT_BOLT && crossed(fx.getId(), age, 2)) add(0.16F, falloff(distance, 18.0D));
            if (fx.kind() == ThunderFx.SLAM && crossed(fx.getId(), age, 0)) add(0.28F * fx.param(), falloff(distance, 26.0D));
            if (fx.kind() == ThunderFx.RELIC_BOLT && crossed(fx.getId(), age, ThunderFx.RELIC_STRIKE_TICK))
                add(0.2F, falloff(distance, 22.0D));
        }
        LAST_AGES.keySet().removeIf(id -> !active.contains(id));
    }

    // 记录年龄并判断本刻是否首次跨过阈值。
    private static boolean crossed(int id, double age, int threshold) {
        double previous = LAST_AGES.getOrDefault(id, -1.0D);
        LAST_AGES.put(id, age);
        return previous < threshold && age >= threshold;
    }

    // 线性距离衰减，超出半径为零。
    private static double falloff(double distance, double radius) {
        return Math.max(0.0D, 1.0D - distance / radius);
    }

    private static void add(float amount, double scale) {
        trauma = Math.min(1.0F, trauma + amount * (float) scale);
    }

    // 通过相机角度事件叠加小幅摆动，不改变玩家实际朝向。
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0.001F) return;
        Minecraft minecraft = Minecraft.getInstance();
        double time = event.getPartialTick();
        if (minecraft.level != null) time += minecraft.level.getGameTime();
        float strength = trauma * trauma;
        event.setYaw(event.getYaw() + (float) Math.sin(time * 2.7D) * strength * 1.1F);
        event.setPitch(event.getPitch() + (float) Math.cos(time * 3.1D) * strength * 0.8F);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 4.3D) * strength * 0.5F);
    }
}
