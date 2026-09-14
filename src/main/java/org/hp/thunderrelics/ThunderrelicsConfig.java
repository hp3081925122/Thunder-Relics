package org.hp.thunderrelics;

import net.minecraftforge.common.ForgeConfigSpec;

// 集中保存雷霆君王的可配置战斗属性。
public final class ThunderrelicsConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue THUNDER_KING_MAX_HEALTH;
    public static final ForgeConfigSpec.IntValue THUNDER_KING_ARMOR;

    static {
        // 使用独立分组，让配置文件中的 Boss 属性更容易查找。
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("雷霆君王属性", "Thunder King attributes").push("thunder_king");

        // 扩大配置输入范围，方便整合包预留更高的 Boss 数值。
        THUNDER_KING_MAX_HEALTH = builder
                .comment("最大生命值，修改后重启游戏生效。原版 generic.max_health 实际上限为 1024，超过部分会被属性系统截断。",
                        "Maximum health; restart the game after changing it. Vanilla generic.max_health is capped at 1024, so higher values are clamped by the attribute system.")
                .defineInRange("max_health", 350, 1, 1_000_000);

        // 扩大配置输入范围，同时保留原版护甲属性的实际限制说明。
        THUNDER_KING_ARMOR = builder
                .comment("护甲值，修改后重启游戏生效。原版 generic.armor 实际上限为 30，超过部分会被属性系统截断。",
                        "Armor value; restart the game after changing it. Vanilla generic.armor is capped at 30, so higher values are clamped by the attribute system.")
                .defineInRange("armor", 10, 0, 1_000);

        // 结束分组并生成供 Forge 注册的配置规格。
        builder.pop();
        SPEC = builder.build();
    }

    private ThunderrelicsConfig() {
    }
}
