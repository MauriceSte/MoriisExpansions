package net.moriis.moriisexpansions.client.sound;

import net.moriis.moriisexpansions.MoriisExpansions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = MoriisExpansions.MOD_ID, value = Dist.CLIENT)
public final class OrganAudioEvents {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientOrganAudio.tick();
        RemoteOrganPlayback.pump();
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) { RemoteOrganPlayback.pump(); }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RemoteOrganPlayback.clear();
        ClientOrganAudio.clear();
    }
}
