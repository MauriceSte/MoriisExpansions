package net.moriis.moriisexpansions.client.screen;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.moriis.moriisexpansions.block.custom.OrganConsoleBlock;
import net.moriis.moriisexpansions.client.midi.MidiInput;
import net.moriis.moriisexpansions.client.midi.OrganPerformance;
import net.moriis.moriisexpansions.client.sound.ClientOrganAudio;
import net.moriis.moriisexpansions.network.OrganNotePayload;
import net.moriis.moriisexpansions.network.OrganSoundPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

public class OrganScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FIRST_NOTE = 36;
    private static final int LAST_NOTE = 96;
    private static final int WHITE_KEYS = 36;
    private static final String[] NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    private final BlockPos organPos;
    private final MidiInput midi = new MidiInput();
    private final OrganPerformance performance;
    private final UUID performer;
    private final List<OrganKey> whites = new ArrayList<>();
    private final List<OrganKey> blacks = new ArrayList<>();
    private final MidiInput.Port off = new MidiInput.Port("", null);

    private List<MidiInput.Port> ports;
    private MidiInput.Port selected = off;
    private Component status = tr("midi_off");
    private Component midiMonitor = tr("midi_waiting");
    private Component networkError;
    private long midiMessages;
    private boolean loggedNetworkError;
    private int statusColor = 0xFFB4BAC8;
    private int transpose;
    private int mouseNote = -1;
    private boolean mouseCaptured;
    private long midiGeneration;
    private boolean active = true;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int keyboardX;
    private int keyboardY;
    private int keyboardWidth;
    private int keyHeight;

    public OrganScreen(BlockPos organPos) {
        super(tr("title"));
        this.organPos = organPos.immutable();
        var player = Minecraft.getInstance().player;
        this.performer = player == null ? new UUID(0, 0) : player.getUUID();
        this.performance = new OrganPerformance(ClientOrganAudio::nextVoice,
                new OrganPerformance.Output() {
                    @Override public void start(int voice, int note, int velocity) {
                        startVoice(voice, note, velocity);
                    }
                    @Override public void stop(int voice) { stopVoice(voice); }
                    @Override public void finished(int voice) {
                        // Same local cleanup as before; never damp a listener on natural completion.
                        ClientOrganAudio.stop(performer, voice);
                        sendAction(OrganNotePayload.FINISHED, voice, 0, 0, ClientOrganAudio.timeMicros());
                    }
                });
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.moriisexpansions.organ." + key, args);
    }

    @Override
    protected void init() {
        if (ports == null) scanPorts();
        if (mouseNote >= 0) performance.noteOff(OrganPerformance.MOUSE_CHANNEL, mouseNote);
        whites.clear();
        blacks.clear();
        mouseCaptured = false;
        mouseNote = -1;

        int whiteWidth = Math.max(2, Math.min(20, (width - 32) / WHITE_KEYS));
        keyboardWidth = whiteWidth * WHITE_KEYS;
        panelWidth = keyboardWidth + 16;
        panelHeight = Math.min(302, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        keyboardX = panelX + 8;
        keyboardY = panelY + 126;
        keyHeight = Math.max(24, Math.min(110, panelHeight - 170));

        int blackWidth = Math.max(2, (int) (whiteWidth * 0.62));
        int blackHeight = (int) (keyHeight * 0.62);
        int whiteIndex = 0;
        for (int note = FIRST_NOTE; note <= LAST_NOTE; note++) {
            if (isBlack(note)) {
                blacks.add(new OrganKey(note,
                        keyboardX + whiteIndex * whiteWidth - blackWidth / 2,
                        keyboardY, blackWidth, blackHeight, true));
            } else {
                whites.add(new OrganKey(note,
                        keyboardX + whiteIndex++ * whiteWidth,
                        keyboardY, whiteWidth, keyHeight, false));
            }
        }

        addRenderableWidget(CycleButton.<MidiInput.Port>builder(port ->
                        port.info() == null ? tr("off") : Component.literal(port.label()))
                .withValues(ports)
                .withInitialValue(selected)
                .create(keyboardX, panelY + 27, keyboardWidth - 92, 20,
                        tr("midi"), (button, port) -> selectPort(port, button)));

        addRenderableWidget(Button.builder(tr("refresh"), button -> {
            disconnectMidi();
            scanPorts();
            rebuildWidgets();
        }).bounds(keyboardX + keyboardWidth - 88, panelY + 27, 88, 20).build());

        addRenderableWidget(Checkbox.builder(tr("let_ring"), font)
                .pos(keyboardX, panelY + 101)
                .maxWidth(keyboardWidth)
                .selected(performance.letRing())
                .tooltip(Tooltip.create(tr("let_ring_help")))
                .onValueChange((checkbox, checked) -> performance.setLetRing(checked))
                .build());

        int sliderWidth = Math.min(260, keyboardWidth);
        addRenderableWidget(new TransposeSlider((width - sliderWidth) / 2,
                keyboardY + keyHeight + 12, sliderWidth));
    }

    private void scanPorts() {
        List<MidiInput.Port> found = new ArrayList<>();
        found.add(off);
        try {
            MidiInput.ScanResult result = MidiInput.scan();
            found.addAll(result.ports());
            if (result.errors().isEmpty()) {
                status = tr("ports_found", found.size() - 1);
                statusColor = 0xFFB4BAC8;
            } else {
                status = tr("scan_warning", found.size() - 1, result.errors().size());
                statusColor = 0xFFFFCB80;
            }
        } catch (RuntimeException failure) {
            status = tr("error", errorText(failure));
            statusColor = 0xFFFF8888;
        }
        ports = List.copyOf(found);
    }

    private void selectPort(MidiInput.Port port, CycleButton<MidiInput.Port> button) {
        disconnectMidi();
        if (port.info() == null) return;

        final long generation = midiGeneration;
        final Minecraft client = Minecraft.getInstance();
        try {
            midi.connect(port, message -> client.execute(() -> {
                if (active && client.screen == this && generation == midiGeneration) {
                    handleMidi(message);
                }
            }));
            selected = port;
            status = tr("connected", port.info().getName());
            statusColor = 0xFF91D8AD;
        } catch (Exception failure) {
            disconnectMidi();
            button.setValue(off);
            status = tr("error", errorText(failure));
            statusColor = 0xFFFF8888;
        }
    }

    private static String errorText(Exception failure) {
        String text = failure.getMessage();
        return text == null || text.isBlank() ? failure.getClass().getSimpleName() : text;
    }

    private void disconnectMidi() {
        ++midiGeneration;
        midi.close();
        selected = off;
        performance.disconnectMidi();
        midiMessages = 0;
        midiMonitor = tr("midi_waiting");
        status = tr("midi_off");
        statusColor = 0xFFB4BAC8;
    }

    private void handleMidi(MidiInput.Message message) {
        int channel = message.channel();
        int note = message.data1();
        if (channel < 0 || channel >= 16 || note < 0 || note > 127) return;
        ++midiMessages;
        if (message.command() == ShortMessage.CONTROL_CHANGE) {
            midiMonitor = tr("midi_control", channel + 1, note, message.data2(), midiMessages);
            switch (note) {
                case 64 -> performance.pedal(channel, message.data2() >= 64);
                case 120 -> performance.allSoundOff(channel);
                case 121 -> performance.pedal(channel, false);
                case 123 -> performance.allNotesOff(channel);
                default -> { }
            }
            return;
        }
        boolean down = message.command() == ShortMessage.NOTE_ON && message.data2() > 0;
        midiMonitor = tr("midi_received", down ? tr("note_on") : tr("note_off"),
                noteName(note), note, channel + 1, message.data2(), midiMessages);
        // Do not silently discard controllers with a different base octave.
        if (down && (note + transpose < 24 || note + transpose > 108)) {
            midiMonitor = tr("midi_out_of_range", noteName(note), note);
            return;
        }
        if (down) performance.noteOn(channel, note, transpose, message.data2());
        else performance.noteOff(channel, note);
    }

    private void startVoice(int voice, int note, int velocity) {
        if (!networkReady()) {
            networkError = tr("network_missing");
            return;
        }
        // Immediate local prediction: no server tick or network round trip before sound.
        long timeMicros = ClientOrganAudio.timeMicros();
        ClientOrganAudio.start(performer, voice, organPos, note, velocity);
        if (!sendAction(OrganNotePayload.START, voice, note, velocity, timeMicros)) {
            ClientOrganAudio.stop(performer, voice);
        }
    }

    private void stopVoice(int voice) {
        long timeMicros = ClientOrganAudio.timeMicros();
        ClientOrganAudio.stop(performer, voice);
        sendAction(OrganNotePayload.STOP, voice, 0, 0, timeMicros);
    }

    private boolean sendAction(int action, int voice, int note, int velocity, long timeMicros) {
        if (!networkReady()) {
            networkError = tr("network_missing");
            return false;
        }
        try {
            PacketDistributor.sendToServer(new OrganNotePayload(
                    organPos, action, voice, note, velocity, timeMicros));
            networkError = null;
            loggedNetworkError = false;
            return true;
        } catch (RuntimeException failure) {
            networkError = tr("network_error", errorText(failure));
            if (!loggedNetworkError) {
                LOGGER.error("Could not send organ voice action", failure);
                loggedNetworkError = true;
            }
            return false;
        }
    }

    private boolean networkReady() {
        return minecraft != null && minecraft.getConnection() != null
                && minecraft.getConnection().hasChannel(OrganNotePayload.TYPE)
                && minecraft.getConnection().hasChannel(OrganSoundPayload.TYPE);
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null
                || !minecraft.player.isAlive()
                || minecraft.player.distanceToSqr(Vec3.atCenterOf(organPos)) > 64.0
                || !OrganConsoleBlock.isPlayableAnchor(minecraft.level, organPos)) {
            onClose();
            return;
        }
        performance.discardFinished(id -> ClientOrganAudio.isAlive(performer, id));
        if (selected.info() != null && !midi.isOpen()) {
            disconnectMidi();
            status = tr("disconnected");
            statusColor = 0xFFFF8888;
            rebuildWidgets();
        }
    }

    private static boolean isBlack(int note) {
        return switch (note % 12) {
            case 1, 3, 6, 8, 10 -> true;
            default -> false;
        };
    }

    private static String noteName(int note) {
        return NAMES[note % 12] + (note / 12 - 1);
    }

    private OrganKey findKey(double x, double y) {
        for (OrganKey key : blacks) if (key.contains(x, y)) return key;
        for (OrganKey key : whites) if (key.contains(x, y)) return key;
        return null;
    }

    private boolean isHeld(int note) {
        return performance.isHeld(note);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            OrganKey key = findKey(x, y);
            if (key != null) {
                mouseCaptured = true;
                mouseNote = key.note();
                performance.noteOn(OrganPerformance.MOUSE_CHANNEL, mouseNote, transpose, 100);
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && mouseCaptured) {
            OrganKey key = findKey(x, y);
            int next = key == null ? -1 : key.note();
            if (next != mouseNote) {
                if (mouseNote >= 0) performance.noteOff(OrganPerformance.MOUSE_CHANNEL, mouseNote);
                mouseNote = next;
                if (next >= 0) performance.noteOn(OrganPerformance.MOUSE_CHANNEL, next, transpose, 100);
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && mouseCaptured) {
            if (mouseNote >= 0) performance.noteOff(OrganPerformance.MOUSE_CHANNEL, mouseNote);
            mouseCaptured = false;
            mouseNote = -1;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    // Do not call Minecraft's blur pass. The GUI is drawn with integer pixel coordinates.
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    protected void renderBlurredBackground(float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA010131A);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF202633);
        graphics.drawCenteredString(font, title, width / 2, panelY + 10, 0xFFF0D48B);
        Component net = !networkReady() ? tr("network_missing")
                : networkError != null ? networkError : tr("network_ready");
        drawStatusLine(graphics, status, panelY + 51, statusColor);
        drawStatusLine(graphics, net, panelY + 63,
                networkReady() && networkError == null ? 0xFF91D8AD : 0xFFFF8888);
        drawStatusLine(graphics, midiMonitor, panelY + 75, 0xFFE3C991);
        drawStatusLine(graphics, tr("pedal_status", performance.pedalDown()
                ? tr("pedal_down") : tr("pedal_up")), panelY + 87, 0xFFABB4C8);

        OrganKey hovered = findKey(mouseX, mouseY);
        for (OrganKey key : whites) drawKey(graphics, key, hovered);
        for (OrganKey key : blacks) drawKey(graphics, key, hovered);

        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) {
            graphics.renderTooltip(font,
                    Component.literal(noteName(hovered.note() + transpose)), mouseX, mouseY);
        } else if (mouseX >= keyboardX && mouseX < keyboardX + keyboardWidth) {
            Component detail = mouseY >= panelY + 51 && mouseY < panelY + 61 ? status
                    : mouseY >= panelY + 63 && mouseY < panelY + 73 ? net
                    : mouseY >= panelY + 75 && mouseY < panelY + 85 ? midiMonitor : null;
            if (detail != null) graphics.renderTooltip(font,
                    font.split(detail, Math.max(100, Math.min(360, width - 24))), mouseX, mouseY);
        }
    }

    private void drawStatusLine(GuiGraphics graphics, Component text, int y, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), keyboardWidth),
                keyboardX, y, color, false);
    }

    private void drawKey(GuiGraphics graphics, OrganKey key, OrganKey hovered) {
        int color = isHeld(key.note()) ? 0xFFE1B65F
                : key == hovered ? (key.black() ? 0xFF4C5970 : 0xFFD9E6F5)
                : key.black() ? 0xFF10141D : 0xFFF3F0E8;
        graphics.fill(key.x(), key.y(), key.x() + key.width(),
                key.y() + key.height(), 0xFF080A10);
        graphics.fill(key.x() + 1, key.y() + 1, key.x() + key.width(),
                key.y() + key.height() - 1, color);
        if (!key.black() && key.note() % 12 == 0 && key.width() >= 12) {
            graphics.drawString(font, noteName(key.note()), key.x() + 2,
                    key.y() + key.height() - 12, 0xFF424957, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        active = false;
        disconnectMidi();
        performance.stopAll();
        mouseNote = -1;
        mouseCaptured = false;
        super.removed();
    }

    private record OrganKey(int note, int x, int y, int width, int height, boolean black) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }

    private final class TransposeSlider extends AbstractSliderButton {
        private TransposeSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), (transpose + 12) / 24.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(tr("transpose", transpose > 0 ? "+" + transpose : "" + transpose));
        }

        @Override
        protected void applyValue() {
            transpose = Mth.clamp((int) Math.round(value * 24.0) - 12, -12, 12);
            value = (transpose + 12) / 24.0;
            updateMessage();
        }
    }
}
