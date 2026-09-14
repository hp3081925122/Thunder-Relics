package org.hp.thunderrelics.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hp.thunderrelics.ModEntities;
import org.hp.thunderrelics.entity.ThunderKingEntity;

// 分框架与内饰两个阶段制作王庭，最终导出标准 NBT 供自然结构复用。
public final class ThunderCourtBuilder {
    public static final Vec3i SIZE = new Vec3i(65, 36, 73);
    private ThunderCourtBuilder() {}

    // 框架包括台基、四座塔楼、两侧回廊与后方王座大厅，中央保持露天。
    public static void frame(ServerLevel level, BlockPos origin) {
        fill(level, origin, 0,0,0,64,35,72,Blocks.AIR);
        fill(level, origin, 3,0,6,61,3,69,Blocks.DEEPSLATE_BRICKS);
        fill(level, origin, 4,4,7,60,4,68,Blocks.POLISHED_DEEPSLATE);
        // 基座收边形成上下错台，中央战场净宽四十一格。
        for (int y : new int[]{1,3}) {
            fill(level,origin,3,y,6,61,y,6,Blocks.CHISELED_DEEPSLATE);
            fill(level,origin,3,y,69,61,y,69,Blocks.CHISELED_DEEPSLATE);
            fill(level,origin,3,y,6,3,y,69,Blocks.CHISELED_DEEPSLATE);
            fill(level,origin,61,y,6,61,y,69,Blocks.CHISELED_DEEPSLATE);
        }
        // 入口宽阶让玩家自然进入战斗平台，避免高台只能攀爬。
        for(int step=0;step<5;step++) {
            fill(level,origin,25,0,step,39,step,step,Blocks.DEEPSLATE_BRICKS);
            for(int x=25;x<=39;x++) set(level,origin,x,step,step,Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));
        }
        fill(level,origin,25,4,5,39,4,9,Blocks.POLISHED_DEEPSLATE);
        // 四角高塔用实体角柱围出空心内部，塔冠沿蓝金配色逐层收束。
        for(int x:new int[]{9,55}) for(int z:new int[]{13,62}) {
            fill(level,origin,x-4,5,z-4,x+4,20,z+4,Blocks.DEEPSLATE_TILES);
            fill(level,origin,x-2,5,z-2,x+2,19,z+2,Blocks.AIR);
            for(int dx:new int[]{-4,4}) for(int dz:new int[]{-4,4}) fill(level,origin,x+dx,5,z+dz,x+dx,23,z+dz,Blocks.POLISHED_BASALT);
            for(int y:new int[]{8,15,21}) {
                fill(level,origin,x-5,y,z-5,x+5,y,z+5,Blocks.POLISHED_DEEPSLATE);
                if(y<21)fill(level,origin,x-2,y,z-2,x+2,y,z+2,Blocks.AIR);
            }
            fill(level,origin,x-3,22,z-3,x+3,24,z+3,Blocks.DEEPSLATE_BRICKS);
            fill(level,origin,x-2,25,z-2,x+2,25,z+2,Blocks.CUT_COPPER);
            fill(level,origin,x-1,26,z-1,x+1,27,z+1,Blocks.DEEPSLATE_TILES);
            fill(level,origin,x,28,z,x,31,z,Blocks.LIGHTNING_ROD);
            // 两面的竖窗和门洞打破实心盒状轮廓。
            for(int y=10;y<=13;y++) {
                set(level,origin,x,y,z-4,Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                set(level,origin,x,y,z+4,Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                set(level,origin,x-4,y,z,Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                set(level,origin,x+4,y,z,Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
            }
            fill(level,origin,x-1,5,z-4,x+1,8,z-3,Blocks.AIR);
        }
        // 两侧回廊只占边缘，连续拱口允许横向移动和躲避预警。
        for(int x:new int[]{9,55}) {
            fill(level,origin,x-3,11,20,x+3,12,55,Blocks.DEEPSLATE_TILES);
            for(int z=21;z<=53;z+=8) {
                fill(level,origin,x-3,5,z,x-3,10,z,Blocks.POLISHED_BASALT);
                fill(level,origin,x+3,5,z,x+3,10,z,Blocks.POLISHED_BASALT);
                fill(level,origin,x-3,10,z,x+3,10,z,Blocks.CHISELED_DEEPSLATE);
            }
        }
        // 后厅的敞开正立面面向战场，三层屋顶向内递进。
        fill(level,origin,19,5,60,45,16,68,Blocks.DEEPSLATE_BRICKS);
        fill(level,origin,21,5,60,43,14,66,Blocks.AIR);
        for(int y=17;y<=19;y++)fill(level,origin,18+y-17,y,59+y-17,46-y+17,y,69-y+17,Blocks.DEEPSLATE_TILES);
        // 四根高柱限定后厅节奏，入口拱楣带有雷霆王冠的三叉轮廓。
        for(int x:new int[]{20,26,38,44})fill(level,origin,x,5,60,x,15,60,Blocks.POLISHED_BASALT);
        fill(level,origin,27,20,64,37,20,64,Blocks.CUT_COPPER);
        for(int x:new int[]{28,32,36})fill(level,origin,x,21,64,x,x==32?25:23,64,Blocks.CHISELED_DEEPSLATE);
        // 低矮围栏保留天空视线，不把雷霆战斗做成封闭房间。
        for(int z=21;z<=54;z++)for(int x:new int[]{4,60})set(level,origin,x,5,z,Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
        for(int x=15;x<=49;x++)if(x<25||x>39)set(level,origin,x,5,7,Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
    }

    // 内饰先划分战场与轴线，再设置王座、祭坛、灯具和回廊陈设。
    public static void decorate(ServerLevel level, BlockPos origin) {
        // 圆形战斗徽记以连续同心结构绘制，不用随机杂色堆砌。
        for(int x=13;x<=51;x++)for(int z=17;z<=55;z++) {
            double r=Math.hypot(x-32,z-36);
            Block block=Blocks.POLISHED_DEEPSLATE;
            if(r<18.7 && r>17.4 || r<12.7 && r>12)block=Blocks.CHISELED_DEEPSLATE;
            if(r<18.2 && r>17.8 || r<6.7 && r>5.8)block=Blocks.WAXED_EXPOSED_COPPER;
            if(r<5)block=Blocks.DEEPSLATE_TILES;
            if(Math.abs(x-32)<=1 || Math.abs(z-36)<=1)block=Blocks.DEEPSLATE_BRICKS;
            set(level,origin,x,4,z,block.defaultBlockState());
        }
        // 战场四方的蓝色能量槽嵌入地板，覆盖玻璃后依旧可以自由走动。
        for(int z=14;z<60;z++)for(int x:new int[]{22,42})if(z<19||z>53) {
            set(level,origin,x,3,z,Blocks.SEA_LANTERN.defaultBlockState());
            set(level,origin,x,4,z,Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
        }
        for(int z=9;z<=61;z++)if(z<18||z>55) for(int x=30;x<=34;x++)set(level,origin,x,5,z,Blocks.PURPLE_CARPET.defaultBlockState());
        // 中心雷纹由金属镶嵌和冷光点交代，保持脚下预警辨识度。
        for(int z=31;z<=41;z++) {
            int x=32+(z<36?1:-1);
            set(level,origin,x,4,z,Blocks.WAXED_EXPOSED_COPPER.defaultBlockState());
        }
        for(int x:new int[]{28,36})for(int z:new int[]{32,40})set(level,origin,x,4,z,Blocks.SEA_LANTERN.defaultBlockState());
        // 王座三级台阶、蓝色靠背和金色扶手，背后悬挂三叉雷纹。
        fill(level,origin,27,5,62,37,5,67,Blocks.POLISHED_DEEPSLATE);
        fill(level,origin,29,6,63,35,6,67,Blocks.CHISELED_DEEPSLATE);
        fill(level,origin,30,7,65,34,8,67,Blocks.DEEPSLATE_TILES);
        fill(level,origin,31,9,67,33,12,67,Blocks.BLUE_TERRACOTTA);
        for(int x:new int[]{30,34})fill(level,origin,x,9,65,x,10,67,Blocks.CUT_COPPER);
        set(level,origin,32,13,67,Blocks.SEA_LANTERN.defaultBlockState());
        for(int x=29;x<=35;x++)set(level,origin,x,14,67,Blocks.WAXED_EXPOSED_COPPER.defaultBlockState());
        // 回廊祭台与长凳位于外围，中央不摆阻碍躲闪的大型家具。
        for(int x:new int[]{9,55})for(int z:new int[]{24,40,52}) {
            fill(level,origin,x-1,5,z,x+1,5,z+1,Blocks.CHISELED_DEEPSLATE);
            set(level,origin,x,6,z,Blocks.SOUL_LANTERN.defaultBlockState());
            for(int dz=3;dz<=5;dz++)set(level,origin,x,5,z+dz,Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,x<32?Direction.EAST:Direction.WEST));
        }
        // 塔冠灯环与铜箍点亮远景轮廓，塔楼内补光让门洞可以辨认。
        for(int x:new int[]{9,55})for(int z:new int[]{13,62}) {
            set(level,origin,x,5,z,Blocks.SOUL_LANTERN.defaultBlockState());
            for(int d=-2;d<=2;d++)for(int sign:new int[]{-1,1}) {
                set(level,origin,x+d,23,z+sign*3,Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                set(level,origin,x+sign*3,23,z+d,Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
            }
            set(level,origin,x,23,z,Blocks.SEA_LANTERN.defaultBlockState());
        }
        // 前厅两侧的礼器台与深色帷幕给入口提供停留空间。
        for(int x:new int[]{19,45}) {
            fill(level,origin,x-1,5,11,x+1,6,13,Blocks.CHISELED_DEEPSLATE);
            set(level,origin,x,7,12,Blocks.SOUL_LANTERN.defaultBlockState());
        }
        // 首轮雨天实机轮廓偏暗，补充塔身冷光竖线与檐口的铜色边缘。
        for(int x:new int[]{9,55})for(int z:new int[]{13,62}) {
            for(int y=10;y<=13;y++)for(int sign:new int[]{-1,1}) {
                set(level,origin,x,y,z+sign*3,Blocks.SEA_LANTERN.defaultBlockState());
                set(level,origin,x+sign*3,y,z,Blocks.SEA_LANTERN.defaultBlockState());
            }
            for(int d=-4;d<=4;d++)for(int sign:new int[]{-1,1}) {
                set(level,origin,x+d,20,z+sign*4,Blocks.WAXED_EXPOSED_COPPER.defaultBlockState());
                set(level,origin,x+sign*4,20,z+d,Blocks.WAXED_EXPOSED_COPPER.defaultBlockState());
            }
        }
        // 两面深蓝金纹挂毯衬托王座，边侧隐藏灯源照亮大厅。
        for(int x:new int[]{23,39}) {
            fill(level,origin,x,9,66,x+2,13,66,Blocks.BLUE_WOOL);
            for(int y=9;y<=13;y++)set(level,origin,x+(y%3),y,66,Blocks.WAXED_EXPOSED_COPPER.defaultBlockState());
            set(level,origin,x+1,12,65,Blocks.SOUL_LANTERN.defaultBlockState());
        }
        // 回廊柱头和门槛明确通行边界，夜间也能区分走廊与战场。
        for(int x:new int[]{9,55})for(int z=21;z<=53;z+=8) {
            set(level,origin,x,10,z,Blocks.SEA_LANTERN.defaultBlockState());
            set(level,origin,x,9,z,Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
        }
    }

    // 保存包含一名持久 Boss 的完整结构，标准结构文件不依赖制作辅助类。
    public static void export(ServerLevel level, BlockPos origin, Path output) throws Exception {
        ThunderKingEntity boss = new ThunderKingEntity(ModEntities.THUNDER_KING.get(),level);
        boss.moveTo(origin.getX()+32.5,origin.getY()+5,origin.getZ()+53.5,180,0);
        boss.setPersistenceRequired();
        level.addFreshEntity(boss);
        StructureTemplate template=new StructureTemplate();
        template.fillFromWorld(level,origin,SIZE,true,Blocks.STRUCTURE_VOID);
        Files.createDirectories(output.getParent());
        // 导出时只保留此次创建的 Boss，排除测试靶和偶然经过的其他实体。
        CompoundTag saved=template.save(new CompoundTag());
        var entities=saved.getList("entities",10);
        for(int i=entities.size()-1;i>=0;i--) {
            CompoundTag entity=entities.getCompound(i).getCompound("nbt");
            if(!entity.hasUUID("UUID") || !entity.getUUID("UUID").equals(boss.getUUID()))entities.remove(i);
        }
        NbtIo.writeCompressed(saved,output);
    }

    // 所有坐标均相对已确认的结构原点，框架和内饰共享同一放置范围。
    private static void fill(ServerLevel level,BlockPos origin,int x1,int y1,int z1,int x2,int y2,int z2,Block block) {
        BlockState state=block.defaultBlockState();
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++)for(int y=y1;y<=y2;y++)set(level,origin,x,y,z,state);
    }
    private static void set(ServerLevel level,BlockPos origin,int x,int y,int z,BlockState state) { level.setBlock(origin.offset(x,y,z),state,2); }
}



