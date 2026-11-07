package net.moriis.moriisexpansions.client.sound;

import net.moriis.moriisexpansions.MoriisExpansions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = MoriisExpansions.MOD_ID, value = Dist.CLIENT)
public final class PianoAudioEvents {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) { ClientPianoAudio.tick(); }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientPianoAudio.clear(); }
}
