package net.moriis.moriisexpansions.client.midi;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Client-only Java Sound input. This class does not access Minecraft from MIDI threads. */
public final class MidiInput implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(MidiInput.class.getName());
    public record Port(String label, MidiDevice.Info info) {}
    public record Message(int command, int channel, int data1, int data2) {}
    public record ScanResult(List<Port> ports, List<String> errors) {}

    private MidiDevice device;
    private Transmitter transmitter;
    private InputReceiver receiver;
    private boolean ownsDevice;

    public static ScanResult scan() {
        List<Port> ports = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice candidate = MidiSystem.getMidiDevice(info);
                if (candidate instanceof Sequencer || candidate instanceof Synthesizer) continue;
                // A hardware MIDI INPUT delivers messages through a Java Transmitter.
                if (candidate.getMaxTransmitters() == 0) continue;
                ports.add(new Port((ports.size() + 1) + ": " + info.getName()
                        + " (" + info.getVendor() + ")", info));
            } catch (MidiUnavailableException | RuntimeException failure) {
                errors.add(info.getName() + ": " + failure);
                LOGGER.log(Level.WARNING, "MIDI port unavailable: " + info.getName(), failure);
            }
        }
        return new ScanResult(List.copyOf(ports), List.copyOf(errors));
    }

    public void connect(Port port, Consumer<Message> callback) throws MidiUnavailableException {
        close();
        try {
            device = MidiSystem.getMidiDevice(port.info());
            ownsDevice = !device.isOpen();
            if (ownsDevice) device.open();
            receiver = new InputReceiver(callback);
            transmitter = device.getTransmitter();
            transmitter.setReceiver(receiver);
            LOGGER.info("MIDI connected: " + port.label());
        } catch (MidiUnavailableException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "MIDI connection failed: " + port.label(), failure);
            close();
            throw failure;
        }
    }

    public boolean isOpen() {
        return device != null && device.isOpen();
    }

    @Override
    public void close() {
        if (receiver != null) receiver.close();
        if (transmitter != null) transmitter.close();
        if (device != null && ownsDevice) device.close();
        receiver = null;
        transmitter = null;
        device = null;
        ownsDevice = false;
    }

    private static final class InputReceiver implements Receiver {
        private final Consumer<Message> callback;
        private volatile boolean closed;

        private InputReceiver(Consumer<Message> callback) {
            this.callback = callback;
        }

        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (closed || !(message instanceof ShortMessage shortMessage)) return;
            int command = shortMessage.getCommand();
            if (command != ShortMessage.NOTE_ON && command != ShortMessage.NOTE_OFF
                    && command != ShortMessage.CONTROL_CHANGE) return;
            callback.accept(new Message(command, shortMessage.getChannel(),
                    shortMessage.getData1(), shortMessage.getData2()));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
