package net.moriis.moriisexpansions.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.moriis.moriisexpansions.MoriisExpansions;
import net.moriis.moriisexpansions.item.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MoriisExpansions.MOD_ID);

    public static final DeferredBlock<Block> CANNABIS_BLOCK = registerBlock("cannabis_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(0.11f)
                    .sound(SoundType.MOSS)
                    .pushReaction(PushReaction.NORMAL)));

    public static final DeferredBlock<Block> DRIED_CANNABIS_BLOCK = registerBlock("dried_cannabis_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PODZOL)
                    .strength(0.8f)
                    .sound(SoundType.MANGROVE_ROOTS)
                    .pushReaction(PushReaction.DESTROY)));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block){
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
