package org.hp.thunderrelics.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.hp.thunderrelics.ModEntities;
import org.hp.thunderrelics.Thunderrelics;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import org.joml.Vector4f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// 仅在开发客户端显式开启；用本地文件调度，不监听网络，也不注入鼠标键盘事件。
@Mod.EventBusSubscriber(modid = Thunderrelics.MOD_ID, value = Dist.CLIENT)
public final class RoyalSlashDebug {
    public static final boolean ENABLED = !FMLEnvironment.production && Boolean.getBoolean("thunderrelics.debug");
    private static final Path ROOT = Path.of("royal-debug");
    private static long nextPoll;
    private static boolean testWorld;
    private static UUID bossId;
    private static UUID targetId;
    private static Vec3 center;
    private static Path captureFolder;
    private static int warmup;
    private static int kind;
    private static int frames;
    private static double lastCapture = -1;
    private static Integer savedDistance;
    private static boolean savedPause;
    private static boolean savedGui;
    private static final StringBuilder trace = new StringBuilder();
    // PNG 编码移到有界后台队列，避免压缩工作阻塞游戏渲染；队列满时跳过采集。
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(12), runnable -> {
                Thread thread = new Thread(runnable, "Royal slash capture");
                thread.setDaemon(true);
                return thread;
            });

    private RoyalSlashDebug() {}

    // 每两百毫秒读取一个明确命令，结果写回状态文件；普通客户端不访问调试目录。
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // 调试客户端失去焦点时仍保持客户端刻运行，文件桥才能在终端操作期间继续响应。
        mc.options.pauseOnLostFocus = false;
        if (warmup > 0 && --warmup == 0 && mc.getSingleplayerServer() != null) {
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                if (level.getEntity(bossId) instanceof ThunderKingEntity boss
                        && level.getEntity(targetId) instanceof ArmorStand target) boss.debugMelee(kind, target);
            });
        }
        if (System.currentTimeMillis() < nextPoll) return;
        nextPoll = System.currentTimeMillis() + 200;
        Path command = ROOT.resolve("command.json");
        if (!Files.isRegularFile(command)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(command, StandardCharsets.UTF_8)).getAsJsonObject();
            Files.delete(command);
            String action = json.get("action").getAsString();
            switch (action) {
                case "open" -> {
                    // 只允许打开显式制作的测试副本，拒绝调试命令改动原存档。
                    String world = json.get("world").getAsString();
                    if (!world.matches("royal-slash-debug-[a-zA-Z0-9_-]+"))
                        throw new IllegalStateException("Only a debug world may be opened");
                    // 开发客户端可能自动恢复上次单人世界，先走正常保存断开流程再切到测试副本。
                    if (mc.level != null) mc.clearLevel(new TitleScreen());
                    testWorld = true;
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
                    status("opening", world);
                }
                case "run" -> run(mc, json);
                // 王庭框架、内饰和技能实机检查共用受限的测试副本入口。
                case "court_frame", "court_detail", "court_export", "court_view", "court_storm", "court_prepare", "court_cast", "court_place" -> RoyalCourtDebug.execute(mc, json);
                case "snapshot" -> {
                    try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                        image.writeToFile(ROOT.resolve("snapshot.png"));
                    }
                    status("snapshot", mc.screen == null ? "world" : mc.screen.getClass().getSimpleName());
                }
                case "status" -> status("ready", mc.level == null ? "menu" : "world");
                case "finish" -> {
                    restoreOptions(mc);
                    status("finished", "Debug capture stopped; view options restored");
                }
                case "quit" -> { restoreOptions(mc); mc.stop(); }
                default -> throw new IllegalArgumentException("Unknown debug action");
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("Royal slash debug command failed", error);
            status("error", error.toString());
        }
    }

    // 在测试副本中生成专用实体和无敌靶子，观察者使用旁观模式，攻击仍走正式同步链路。
    private static void run(Minecraft mc, JsonObject json) throws Exception {
        if (!testWorld || mc.level == null || mc.getSingleplayerServer() == null || mc.player == null)
            throw new IllegalStateException("A debug world must be loaded first");
        if (!mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .toAbsolutePath().normalize().getFileName().toString().startsWith("royal-slash-debug-"))
            throw new IllegalStateException("The current world is not a debug copy");
        if (captureFolder != null || WRITER.getActiveCount() != 0 || !WRITER.getQueue().isEmpty())
            throw new IllegalStateException("Previous capture has not finished");
        String label = json.get("label").getAsString();
        if (!label.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid capture label");
        kind = json.get("kind").getAsInt();
        if (kind < 0 || kind > 3) throw new IllegalArgumentException("Invalid melee kind");
        double cameraX = json.has("side") && json.get("side").getAsBoolean() ? 7 : 3.6;
        double cameraZ = cameraX == 7 ? 0.8 : 5.4;
        // 服务端尚未建立新场景时禁止采集旧实体，防止旧招式计时使新任务立即结束。
        warmup = -1;
        captureFolder = ROOT.resolve(label);
        Files.createDirectories(captureFolder);
        frames = 0;
        lastCapture = -1;
        trace.setLength(0);
        trace.append("age,kind,active,actual_x,actual_y,actual_z,old_x,old_y,old_z,old_offset,samples\n");
        if (savedDistance == null) {
            savedDistance = mc.options.renderDistance().get();
            savedPause = mc.options.pauseOnLostFocus;
            savedGui = mc.options.hideGui;
        }
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        // 固定较低渲染距离降低后台测试开销，画面采集不依赖窗口是否位于前台。
        mc.options.renderDistance().set(5);
        mc.setScreen(null);
        var playerId = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            var level = server.overworld();
            var player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;
            if (center == null) center = new Vec3(Math.floor(player.getX()) + 0.5, player.getY(), Math.floor(player.getZ()) + 0.5);
            if (bossId != null && level.getEntity(bossId) != null) level.getEntity(bossId).discard();
            if (targetId != null && level.getEntity(targetId) != null) level.getEntity(targetId).discard();
            // 清除测试区内旧怪物只发生在副本，保证其他血条和模型不干扰画面。
            for (var entity : level.getEntitiesOfClass(ThunderKingEntity.class, player.getBoundingBox().inflate(40))) entity.discard();
            level.setDayTime(6000);
            ThunderKingEntity boss = new ThunderKingEntity(ModEntities.THUNDER_KING.get(), level);
            boss.moveTo(center.x, center.y, center.z, 0, 0);
            boss.setNoAi(true);
            boss.setNoGravity(true);
            boss.setPersistenceRequired();
            boss.setInvulnerable(true);
            level.addFreshEntity(boss);
            bossId = boss.getUUID();
            ArmorStand target = new ArmorStand(level, center.x, center.y, center.z + 3);
            target.setNoGravity(true);
            target.setInvulnerable(true);
            target.setInvisible(true);
            level.addFreshEntity(target);
            targetId = target.getUUID();
            boss.setTarget(target);
            player.setGameMode(GameType.SPECTATOR);
            float yaw = (float) Math.toDegrees(Math.atan2(cameraX, -cameraZ));
            float pitch = (float) Math.toDegrees(Math.atan2(0.82, Math.hypot(cameraX, cameraZ)));
            player.teleportTo(level, center.x + cameraX, center.y + 1.4, center.z + cameraZ, yaw, pitch);
            mc.execute(() -> warmup = 30);
        });
        status("running", label);
    }

    // 同时记录两种坐标换算结果，能区分骨骼变换偏差与仅由拖尾寿命造成的残留。
    public static void trace(ThunderKingEntity boss, GeoBone bone, Matrix4f actual, double age, boolean active, Vec3 position, int samples) {
        if (!ENABLED || captureFolder == null || !boss.getUUID().equals(bossId)) return;
        Vector4f local = actual.transform(new Vector4f(0, 1.66F, 0, 1));
        Vector4f world = bone.getLocalSpaceMatrix().transform(new Vector4f(0, 1.66F, 0, 1));
        Vec3 a = new Vec3(local.x, local.y, local.z).add(position);
        Vec3 b = new Vec3(world.x, world.y, world.z).add(position);
        trace.append(String.format(Locale.ROOT, "%.4f,%d,%s,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.6f,%d%n",
                age, boss.getSwingKind(), active, a.x, a.y, a.z, b.x, b.y, b.z, a.distanceTo(b), samples));
    }

    // 在一帧绘制完成后读取真正的游戏帧缓冲，每半个游戏刻采集一次，覆盖全套动作。
    @SubscribeEvent
    public static void rendered(TickEvent.RenderTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || captureFolder == null || warmup != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || bossId == null) return;
        ThunderKingEntity boss = null;
        for (var entity : mc.level.entitiesForRendering())
            if (entity.getUUID().equals(bossId) && entity instanceof ThunderKingEntity found) { boss = found; break; }
        if (boss == null || boss.getSwingStart() < 0) return;
        double age = mc.level.getGameTime() + event.renderTickTime - boss.getSwingStart();
        // 调试采集窗口跟随加速后的近战动画，额外保留少量余辉观察时间。
        int end = kind == 0 ? 34 : kind == 1 ? 46 : kind == 2 ? 42 : 46;
        if (age > end) {
            try {
                Files.writeString(captureFolder.resolve("trajectory.csv"), trace, StandardCharsets.UTF_8);
                status("complete", captureFolder.getFileName() + ": " + frames + " frames");
            } catch (Exception error) { LogUtils.getLogger().error("Royal slash trace save failed", error); }
            captureFolder = null;
            return;
        }
        if (age < 0 || age - lastCapture < 0.5 || WRITER.getQueue().remainingCapacity() == 0) return;
        lastCapture = age;
        Path destination = captureFolder.resolve(String.format(Locale.ROOT, "%04d_tick_%06.2f.png", frames++, age));
        var image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        WRITER.execute(() -> {
            try (image) { image.writeToFile(destination); }
            catch (Exception error) { LogUtils.getLogger().error("Royal slash frame save failed", error); }
        });
    }

    // 状态文件只包含调试结果，不包含连接令牌或外部地址。
    // 结束采集时恢复本次临时调整的界面设置，不将测试参数留给正常游玩。
    private static void restoreOptions(Minecraft mc) {
        captureFolder = null;
        warmup = 0;
        if (savedDistance != null) {
            mc.options.renderDistance().set(savedDistance);
            mc.options.pauseOnLostFocus = savedPause;
            mc.options.hideGui = savedGui;
            savedDistance = null;
        }
    }

    // 状态文件只包含调试结果，不包含连接令牌或外部地址。
    private static void status(String state, String detail) {
        try {
            Files.createDirectories(ROOT);
            JsonObject json = new JsonObject();
            json.addProperty("state", state);
            json.addProperty("detail", detail);
            Files.writeString(ROOT.resolve("status.json"), json.toString(), StandardCharsets.UTF_8);
        } catch (Exception error) { LogUtils.getLogger().error("Royal slash status write failed", error); }
    }
}
