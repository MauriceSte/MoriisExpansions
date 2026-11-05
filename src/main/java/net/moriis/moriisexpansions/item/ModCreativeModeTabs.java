package net.moriis.moriisexpansions.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.moriis.moriisexpansions.MoriisExpansions;
import net.moriis.moriisexpansions.block.ModBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MoriisExpansions.MOD_ID);

    public static final Supplier<CreativeModeTab> MOD_TAB = CREATIVE_MODE_TABS.register("mod_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.CANNABIS_BUD.get()))
                    .title(Component.translatable("creativetab.moriisexpansions.mod_tab"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModBlocks.CANNABIS_BLOCK);
                        output.accept(ModItems.CANNABIS_BUD);
                        output.accept(ModBlocks.DRIED_CANNABIS_BLOCK);
                        output.accept(ModItems.DRIED_CANNABIS_BUD);
                        output.accept(ModItems.CANNABIS_SEEDS);
                    }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
