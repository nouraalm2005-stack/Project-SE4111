package view;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.awt.Toolkit;

/**
 * A soft, gentle bell melody for the ringing alarm. The sound is synthesized in code
 * (sine waves with a soft attack and a smooth decay), so no audio files or libraries are needed.
 * If the computer has no audio output, it falls back to the system beep.
 */
final class Chime {

    private static final float SAMPLE_RATE = 44100f;

    // notes of the melody: frequency in Hz, start time in seconds, length in seconds
    private static final double[][] NOTES = {
        {659.25, 0.00, 1.4},   // E5
        {783.99, 0.28, 1.4},   // G5
        {880.00, 0.56, 1.4},   // A5
        {1046.50, 0.84, 1.6},  // C6
        {880.00, 1.30, 1.4},   // A5
        {783.99, 1.58, 1.4},   // G5
        {659.25, 1.86, 1.4},   // E5
        {783.99, 2.30, 1.6},   // G5
        {1046.50, 2.70, 2.4},  // C6 (long, last note)
    };
    private static final double LENGTH = 5.4;   // seconds, melody plus a little silence

    private final byte[] pcm = render();
    private volatile boolean playing;
    private Thread worker;

    /** Starts the melody and repeats it until stop() is called. */
    synchronized void start() {
        if (playing) return;
        playing = true;
        worker = new Thread(this::loop, "alarm-chime");
        worker.setDaemon(true);
        worker.start();
    }

    synchronized void stop() {
        playing = false;
        if (worker != null) worker.interrupt();
    }

    private void loop() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            while (playing) {
                int offset = 0;
                while (playing && offset < pcm.length) {
                    int n = Math.min(4096, pcm.length - offset);
                    line.write(pcm, offset, n);
                    offset += n;
                }
            }
            line.flush();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            beepFallback();
        }
    }

    private void beepFallback() {
        while (playing) {
            Toolkit.getDefaultToolkit().beep();
            try { Thread.sleep(2200); } catch (InterruptedException e) { return; }
        }
    }

    /** Builds 16-bit mono PCM audio (little endian) of the whole melody. */
    static byte[] render() {
        int total = (int) (LENGTH * SAMPLE_RATE);
        double[] mix = new double[total];
        for (double[] note : NOTES) {
            int start = (int) (note[1] * SAMPLE_RATE);
            int len = (int) (note[2] * SAMPLE_RATE);
            for (int i = 0; i < len && start + i < total; i++) {
                double t = i / SAMPLE_RATE;
                double attack = Math.min(1.0, t / 0.012);              // soft start, no click
                double decay = Math.exp(-3.2 * t / note[2]);            // smooth fade, like a bell
                double f = note[0];
                double wave = Math.sin(2 * Math.PI * f * t)
                            + 0.35 * Math.sin(2 * Math.PI * f * 2 * t) * Math.exp(-6 * t)
                            + 0.12 * Math.sin(2 * Math.PI * f * 3 * t) * Math.exp(-9 * t);
                mix[start + i] += wave * attack * decay * 0.22;
            }
        }
        byte[] out = new byte[total * 2];
        for (int i = 0; i < total; i++) {
            double s = Math.max(-1, Math.min(1, mix[i]));
            short v = (short) (s * 32767);
            out[2 * i] = (byte) (v & 0xFF);
            out[2 * i + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }
}
