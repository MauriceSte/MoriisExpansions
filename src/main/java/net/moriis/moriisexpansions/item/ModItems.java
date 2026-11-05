package net.moriis.moriisexpansions.item;

import net.minecraft.world.item.Item;
import net.moriis.moriisexpansions.MoriisExpansions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MoriisExpansions.MOD_ID);

    public static final DeferredItem<Item> CANNABIS_BUD = ITEMS.register("cannabis_bud",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> DRIED_CANNABIS_BUD = ITEMS.register("dried_cannabis_bud",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> CANNABIS_SEEDS = ITEMS.register("cannabis_seeds",
            () -> new Item(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

}
