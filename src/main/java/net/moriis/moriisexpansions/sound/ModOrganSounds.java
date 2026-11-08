package net.moriis.moriisexpansions.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.moriis.moriisexpansions.MoriisExpansions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.stream.IntStream;

@EventBusSubscriber(modid = MoriisExpansions.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModOrganSounds {
    public static final float HEARING_DISTANCE = 64.0F;

    public static final List<SoundEvent> NOTES = IntStream.rangeClosed(1, 8)
            .mapToObj(octave -> SoundEvent.createFixedRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(
                            MoriisExpansions.MOD_ID, "organ_c" + octave),
                    HEARING_DISTANCE)).toList();

    private ModOrganSounds() {}

    /** Compatibility with the previous package; no manual registration is needed now. */
    @Deprecated
    public static void register(IEventBus bus) {
        // Registration is performed automatically by the event subscriber below.
    }

    @SubscribeEvent
    public static void registerSounds(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, helper -> {
            for (int index = 0; index < NOTES.size(); index++) {
                helper.register(ResourceLocation.fromNamespaceAndPath(
                        MoriisExpansions.MOD_ID, "organ_c" + (index + 1)), NOTES.get(index));
            }
        });
    }
}
