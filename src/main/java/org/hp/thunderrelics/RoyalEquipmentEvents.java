package org.hp.thunderrelics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import com.mojang.logging.LogUtils;
import org.hp.thunderrelics.effect.DecorativeLightning;

// 王铠的被动效果集中在 NeoForge 事件总线上，服务端负责伤害与状态同步。
@EventBusSubscriber(modid = Thunderrelics.MOD_ID)
public final class RoyalEquipmentEvents {
    private static final String CROWN_TRIGGERED_KEY = "thunderrelics_crown_triggered";
    private static final Map<UUID, Long> CHEST_COOLDOWN = new ConcurrentHashMap<>();

    // 目标真正切换到戴王冠的玩家时，每个敌对实体只触发一次装饰雷霆。
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)
                || !(mob instanceof Monster) || !(event.getNewAboutToBeSetTarget() instanceof Player player)
                || mob.getTarget() == player || !hasItem(player, EquipmentSlot.HEAD, ModEntities.ROYAL_HELMET.get())
                || mob.isAlliedTo(player) || mob.getPersistentData().getBoolean(CROWN_TRIGGERED_KEY)) return;
        // 在结算前记录标记，防止同一实体重复切换目标时再次触发。
        mob.getPersistentData().putBoolean(CROWN_TRIGGERED_KEY, true);
        // 敌方生物是索敌事件的发起者，雷霆固定落在敌方生物脚下并由它承受反击。
        Vec3 enemyPosition = mob.position();
        DecorativeLightning.strike(player.level(), enemyPosition, player, mob, 10.0F, 10);
        LogUtils.getLogger().debug("Royal crown retaliation: enemy={}, enemyPos={}, player={}",
                mob.getType().builtInRegistryHolder().key().location(), enemyPosition, player.getUUID());
    }

    // 戴胸甲的玩家受伤后按每秒一次冷却概率反击攻击来源。
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Player player)
                || !hasItem(player, EquipmentSlot.CHEST, ModEntities.ROYAL_CHESTPLATE.get())) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity enemy) || enemy == player || player.isAlliedTo(enemy)) return;
        long now = player.level().getGameTime();
        long ready = CHEST_COOLDOWN.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now < ready || player.getRandom().nextFloat() >= 0.30F) return;
        CHEST_COOLDOWN.put(player.getUUID(), now + 20L);
        // 反击雷霆固定落在攻击者脚下，避免闪电漂浮到身体上方。
        DecorativeLightning.strike(player.level(), enemy.position(), player, enemy, 3.0F, 3);
    }

    // 护腿在雨中或水中刷新生命恢复 I 与力量 II，靴子刷新速度 II。
    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        if (hasItem(player, EquipmentSlot.LEGS, ModEntities.ROYAL_LEGGINGS.get())
                && (player.level().isRainingAt(player.blockPosition()) || player.isInWater())) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 1, true, false, true));
        }
        if (hasItem(player, EquipmentSlot.FEET, ModEntities.ROYAL_BOOTS.get())) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false, true));
        }
        if (CHEST_COOLDOWN.size() > 256 && player.tickCount % 200 == 0) {
            CHEST_COOLDOWN.entrySet().removeIf(entry -> entry.getValue() < player.level().getGameTime());
        }
    }

    // 只认物品注册对象本身，避免改名、染色或相似物品误触发技能。
    private static boolean hasItem(Player player, EquipmentSlot slot, net.minecraft.world.item.Item item) {
        return player.getItemBySlot(slot).is(item);
    }

    private RoyalEquipmentEvents() {
    }
}
