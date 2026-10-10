package org.hp.thunderrelics.client;

import java.util.HashMap;
import java.util.Map;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

// 为 GeckoLib 5 补充新版实体渲染状态所需的数据表，并携带特效需要的招式快照。
public final class ThunderKingRenderState extends LivingEntityRenderState implements GeoRenderState {
    private final Map<DataTicket<?>, Object> geckolibData = new HashMap<>();
    // 实体编号用于查找骨骼缓存，招式编号与招式年龄驱动特效时序。
    public int entityId;
    public int skill = -1;
    public double skillAge = -1.0D;
    public double worldTime;
    public boolean alive;

    // 覆盖 NeoForge 混入的写入入口，确保 GeckoLib 父类写入的数据与读取使用同一张表。
    @Override
    public <D> void addGeckolibData(DataTicket<D> ticket, D value) {
        geckolibData.put(ticket, value);
    }

    @Override
    public Map<DataTicket<?>, Object> getDataMap() {
        return geckolibData;
    }
}
