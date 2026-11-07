package net.moriis.moriisexpansions.client.sound;

import net.moriis.moriisexpansions.MoriisExpansions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = MoriisExpansions.MOD_ID, value = Dist.CLIENT)
public final class PianoAudioEvents {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientPianoAudio.tick();
        RemotePianoPlayback.pump();
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) { RemotePianoPlayback.pump(); }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RemotePianoPlayback.clear();
        ClientPianoAudio.clear();
    }
}
