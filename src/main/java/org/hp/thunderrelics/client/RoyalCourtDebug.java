package org.hp.thunderrelics.client;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.hp.thunderrelics.entity.ThunderKingEntity;
import org.hp.thunderrelics.ModEntities;
import org.hp.thunderrelics.world.ThunderCourtBuilder;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

// 仅供测试副本的结构搭建和观察，不模拟键盘鼠标，不修改普通存档。
public final class RoyalCourtDebug {
    private static final BlockPos ORIGIN = new BlockPos(256, -60, 128);
    private RoyalCourtDebug() {}

    public static void execute(Minecraft mc, JsonObject json) {
        if (!RoyalSlashDebug.ENABLED || mc.player == null || mc.getSingleplayerServer() == null
                || !mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .toAbsolutePath().normalize().getFileName().toString().startsWith("royal-slash-debug-"))
            throw new IllegalStateException("Court debug requires the dedicated test world");
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        mc.options.renderDistance().set(10);
        mc.setScreen(null);
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try {
                var server=mc.getSingleplayerServer();
                var level=server.overworld();
                var player=server.getPlayerList().getPlayer(id);
                if(player==null)return;
                String action=json.get("action").getAsString();
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                level.setDayTime(6000);
                if(!action.equals("court_storm") && !action.equals("court_cast"))level.setWeatherParameters(6000,0,false,false);
                player.setGameMode(GameType.SPECTATOR);
                if(action.equals("court_frame"))ThunderCourtBuilder.frame(level,ORIGIN);
                if(action.equals("court_detail"))ThunderCourtBuilder.decorate(level,ORIGIN);
                if(action.equals("court_export"))ThunderCourtBuilder.export(level,ORIGIN,Path.of("../src/main/resources/data/thunderrelics/structures/thunder_court.nbt"));
                if(action.equals("court_storm") || action.equals("court_prepare")) {
                    // 使用正式技能入口和实体同步链路，测试靶固定于场地中央。
                    for(var old:level.getEntitiesOfClass(ThunderKingEntity.class,new net.minecraft.world.phys.AABB(ORIGIN,ORIGIN.offset(65,36,73))))old.discard();
                    ThunderKingEntity boss=new ThunderKingEntity(ModEntities.THUNDER_KING.get(),level);
                    boss.moveTo(ORIGIN.getX()+32.5,ORIGIN.getY()+5,ORIGIN.getZ()+39.5,180,0);
                    boss.setNoAi(true);boss.setPersistenceRequired();boss.setInvulnerable(true);
                    level.addFreshEntity(boss);
                    var target=new net.minecraft.world.entity.monster.Husk(net.minecraft.world.entity.EntityType.HUSK,level);
                    target.moveTo(ORIGIN.getX()+32.5,ORIGIN.getY()+5,ORIGIN.getZ()+30.5,0,0);
                    target.setNoAi(true);target.setPersistenceRequired();
                    target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
                    target.setHealth(200);level.addFreshEntity(target);
                    boss.setTarget(target);
                    if(action.equals("court_storm"))boss.startStorm(target);
                }
                // 等客户端已经收到实体后再触发，避免测试时生成包早于动画跟踪订阅。
                if(action.equals("court_cast")) {
                    for(var boss:level.getEntitiesOfClass(ThunderKingEntity.class,new net.minecraft.world.phys.AABB(ORIGIN,ORIGIN.offset(65,36,73))))
                        if(boss.getTarget()!=null)boss.startStorm(boss.getTarget());
                }
                // 从资源包加载最终 NBT 放置到另一块空地，验证发布文件而非内存中的建造结果。
                if(action.equals("court_place")) {
                    boolean worldgen=json.has("worldgen") && json.get("worldgen").getAsBoolean();
                    String position=worldgen?"560 -60 128":"400 -60 128";
                    int result=server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                            "place "+(worldgen?"structure":"template")+" thunderrelics:thunder_court "+position);
                    if(result==0)throw new IllegalStateException("Final court placement failed");
                }
                // 可选相机位置只用于测试副本，默认为入口外部俯视。
                Vec3 camera=json.has("camera")?new Vec3(json.getAsJsonArray("camera").get(0).getAsDouble(),json.getAsJsonArray("camera").get(1).getAsDouble(),json.getAsJsonArray("camera").get(2).getAsDouble()):new Vec3(327,-24,99);
                Vec3 focus=json.has("focus")?new Vec3(json.getAsJsonArray("focus").get(0).getAsDouble(),json.getAsJsonArray("focus").get(1).getAsDouble(),json.getAsJsonArray("focus").get(2).getAsDouble()):new Vec3(288,-50,164);
                Vec3 delta=focus.subtract(camera);
                float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
                float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
                player.teleportTo(level,camera.x,camera.y,camera.z,yaw,pitch);
                JsonObject status=new JsonObject();status.addProperty("state","complete");status.addProperty("detail",action);
                Files.writeString(Path.of("royal-debug/court_status.json"),status.toString(),StandardCharsets.UTF_8);
            }catch(Exception error){
                LogUtils.getLogger().error("Royal court debug failed",error);
                // 将失败回执返回文件桥，测试脚本无需等待超时才能得知放置失败。
                try {
                    JsonObject status=new JsonObject();status.addProperty("state","error");status.addProperty("detail",error.toString());
                    Files.writeString(Path.of("royal-debug/court_status.json"),status.toString(),StandardCharsets.UTF_8);
                }catch(Exception writeError){LogUtils.getLogger().error("Royal court status write failed",writeError);}
            }
        });
    }
}
