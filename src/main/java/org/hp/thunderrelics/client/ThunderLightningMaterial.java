package org.hp.thunderrelics.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

// 共享雷电材质，让落雷、地面电弧和刀光使用同一套自定义着色器。
public final class ThunderLightningMaterial extends RenderType {
    // 着色器尚未完成资源加载时回退到原版雷电着色器，避免客户端启动失败。
    private static ShaderInstance loadedShader;
    private static final RenderStateShard.ShaderStateShard SHADER =
            new RenderStateShard.ShaderStateShard(ThunderLightningMaterial::getShader);

    // 共享发光材质只写颜色并关闭深度写入，避免半透明特效遮挡 Boss 和世界。
    public static final RenderType TYPE = create(
            "thunderrelics_lightning",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            65536,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(MAIN_TARGET)
                    .createCompositeState(false));

    // 资源重载完成后由 Forge 回调注入真正的自定义着色器实例。
    public static void setShader(ShaderInstance shader) {
        loadedShader = shader;
    }

    // 自定义着色器不可用时继续使用已确认存在的原版雷电着色器。
    private static ShaderInstance getShader() {
        ShaderInstance shader = loadedShader;
        return shader != null ? shader : GameRenderer.getRendertypeLightningShader();
    }

    private ThunderLightningMaterial() {
        super("thunderrelics_lightning_base", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 65536, false, true, () -> {}, () -> {});
    }
}
