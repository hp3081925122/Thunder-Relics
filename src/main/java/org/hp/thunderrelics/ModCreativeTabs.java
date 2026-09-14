package org.hp.thunderrelics;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

// 注册雷霆遗迹专属创造物品栏，并集中展示本模组的可获得物品。
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Thunderrelics.MOD_ID);

    public static final RegistryObject<CreativeModeTab> THUNDER_RELICS = TABS.register("thunder_relics", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.thunderrelics"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModEntities.THUNDER_KING_SPAWN_EGG.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(new ItemStack(ModEntities.THUNDER_KING_SPAWN_EGG.get()));
                        output.accept(new ItemStack(ModEntities.ROYAL_GLAIVE.get()));
                        output.accept(new ItemStack(ModEntities.ROYAL_HELMET.get()));
                        output.accept(new ItemStack(ModEntities.ROYAL_CHESTPLATE.get()));
                        output.accept(new ItemStack(ModEntities.ROYAL_LEGGINGS.get()));
                        output.accept(new ItemStack(ModEntities.ROYAL_BOOTS.get()));
                    })
                    .build());

    // 将创造栏注册器接入 Forge 模组事件总线。
    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }

    private ModCreativeTabs() {
    }
}
