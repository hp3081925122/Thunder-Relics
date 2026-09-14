package org.hp.thunderrelics.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import org.hp.thunderrelics.entity.RoyalStormWave;

// 监听雷束落地时刻，为附近玩家提供轻微、短时的镜头晃动。
public final class ThunderCameraShake {
    private static final Map<Integer, Integer> LAST_AGES = new HashMap<>();
    private static float trauma;

    // 在客户端刻更新中检测预警实体第一次进入落雷阶段。
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            LAST_AGES.clear();
            trauma = 0.0F;
            return;
        }
        trauma *= 0.82F;
        Set<Integer> active = new HashSet<>();
        AABB scanArea = minecraft.player.getBoundingBox().inflate(48.0D);
        for (RoyalStormWave wave : minecraft.level.getEntitiesOfClass(RoyalStormWave.class, scanArea)) {
            active.add(wave.getId());
            int age = (int) wave.age(0.0F);
            int previousAge = LAST_AGES.getOrDefault(wave.getId(), -1);
            if (previousAge < RoyalStormWave.WARNING_TICKS && age >= RoyalStormWave.WARNING_TICKS) {
                trauma = Math.min(1.0F, trauma + 0.22F);
            }
            LAST_AGES.put(wave.getId(), age);
        }
        LAST_AGES.keySet().removeIf(id -> !active.contains(id));
    }

    // 通过相机角度事件叠加小幅随机感摆动，不改变玩家实际朝向或移动。
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0.001F) return;
        Minecraft minecraft = Minecraft.getInstance();
        double time = minecraft.level == null ? 0.0D : minecraft.level.getGameTime() + event.getPartialTick();
        float strength = trauma * trauma;
        event.setYaw(event.getYaw() + (float) Math.sin(time * 2.7D) * strength * 0.65F);
        event.setPitch(event.getPitch() + (float) Math.cos(time * 3.1D) * strength * 0.45F);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 4.3D) * strength * 0.25F);
    }

    private ThunderCameraShake() {
    }
}
