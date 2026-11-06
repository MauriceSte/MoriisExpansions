package net.moriis.moriisexpansions.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.moriis.moriisexpansions.MoriisExpansions;
import net.moriis.moriisexpansions.block.ModBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.ItemNameBlockItem;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MoriisExpansions.MOD_ID);

    public static final DeferredItem<Item> CANNABIS_BUD = ITEMS.register("cannabis_bud",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> DRIED_CANNABIS_BUD = ITEMS.register("dried_cannabis_bud",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> CANNABIS_LEAF = ITEMS.register("cannabis_leaf",
            () -> new Item(new Item.Properties()));

    //Special BlockItem for Seed
    public static final DeferredItem<ItemNameBlockItem> CANNABIS_SEEDS =
            ITEMS.register("cannabis_seeds",
                    () -> new ItemNameBlockItem(ModBlocks.CROP.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
