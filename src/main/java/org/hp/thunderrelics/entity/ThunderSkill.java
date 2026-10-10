package org.hp.thunderrelics.entity;

// 雷霆君王招式表：动画名、播放倍速、动画时长与关键事件时刻（动画秒），服务端结算与客户端特效共用。
public enum ThunderSkill {
    CLEAVE("cleave", 1.25D, 1.65D, 0.75D),
    HEAVY_SLAM("heavy_slam", 1.25D, 2.05D, 1.0D),
    TWO_HANDED_SLAM("two_handed_slam", 1.25D, 2.3D, 1.1D),
    JAVELIN("javelin_throw", 1.25D, 2.6D, 1.2D, 2.2D),
    STORM("storm_ritual", 1.0D, 6.0D, 2.1D, 2.2D, 3.2D, 4.2D),
    RELIC_BARRAGE("relic_barrage", 1.0D, 2.8D, 0.95D, 1.1D, 1.25D, 1.4D),
    GROUND_PLANT("ground_plant", 1.0D, 2.6D, 0.7D, 2.1D);

    public final String animation;
    public final double speed;
    public final int duration;
    private final int[] events;

    ThunderSkill(String animation, double speed, double lengthSeconds, double... eventSeconds) {
        this.animation = animation;
        this.speed = speed;
        this.duration = Math.max(1, (int) Math.round(lengthSeconds * 20.0D / speed));
        this.events = new int[eventSeconds.length];
        // 动画秒按倍速换算为游戏刻。
        for (int i = 0; i < eventSeconds.length; i++) events[i] = toTicks(eventSeconds[i]);
    }

    // 动画秒换算为本招式的游戏刻。
    public int toTicks(double animationSeconds) {
        return (int) Math.round(animationSeconds * 20.0D / speed);
    }

    // 第几个关键事件的刻数。
    public int event(int index) {
        return events[index];
    }

    public int eventCount() {
        return events.length;
    }

    // 由同步编号取回招式，越界返回空。
    public static ThunderSkill byId(int id) {
        if (id < 0 || id >= values().length) return null;
        return values()[id];
    }
}
