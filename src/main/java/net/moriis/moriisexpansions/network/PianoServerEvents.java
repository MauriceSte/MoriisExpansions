package net.moriis.moriisexpansions.network;

import net.moriis.moriisexpansions.MoriisExpansions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = MoriisExpansions.MOD_ID)
public final class PianoServerEvents {
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) { PianoNetworking.tick(event.getServer()); }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { PianoNetworking.clear(); }
}
