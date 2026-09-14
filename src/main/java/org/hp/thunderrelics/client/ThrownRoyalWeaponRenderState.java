package org.hp.thunderrelics.client;

import java.util.HashMap;
import java.util.Map;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

// 为 GeckoLib 5 补充新版实体渲染状态所需的数据表。
public final class ThrownRoyalWeaponRenderState extends EntityRenderState implements GeoRenderState {
    // 弹体插值后的偏航和仰角。
    public float yaw;
    public float pitch;
    private final Map<DataTicket<?>, Object> geckolibData = new HashMap<>();

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
