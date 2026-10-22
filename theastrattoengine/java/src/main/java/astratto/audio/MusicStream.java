package astratto.audio;

import astratto.bridge.NativeEngine;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.function.Supplier;
import org.lwjgl.system.MemoryUtil;

/**
 * UltraEngine (2.3): music minutes long, never decoded whole. A track is an engine stream - a ring of a few seconds at
 * the mixer's rate - that a decoder thread of its own keeps topped up from the file as the mixer drains it, so a
 * fourteen-minute MP3 costs a few hundred kilobytes instead of hundreds of megabytes, and nothing is decoded on the
 * game's thread. Play {@link #sound()} with one voice on the music bus; when the voice has ended, {@link #close()} the
 * stream (on the game's thread: it releases the engine's sound).
 *
 * <p>MP3 (any rate, mono or stereo) is decoded frame by frame; other formats the engine reads ({@link Codecs}) are
 * decoded whole first - they are short. A looping track starts again from its source at its end with no gap.
 */
public final class MusicStream implements AutoCloseable {
    private static final int RING_SECONDS = 4;
    private final NativeEngine engine;
    private final long sound;
    private final String name;
    private final Supplier<InputStream> source;
    private final boolean loop;
    private final Thread thread;
    private volatile boolean stop, done;
    private volatile long written;
    private volatile String failure;

    private MusicStream(NativeEngine engine, long sound, String name, Supplier<InputStream> source, boolean loop) {
        this.engine = engine;
        this.sound = sound;
        this.name = name;
        this.source = source;
        this.loop = loop;
        this.thread = new Thread(this::decode, "astratto-music-" + name);
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY + 1);
    }

    /** Opens a track and starts decoding it ahead. Null when the engine has no audio or the stream was refused. Game thread. */
    public static MusicStream open(NativeEngine engine, String name, Supplier<InputStream> source, boolean loop) {
        long s = engine.createStream(2, UltraEngine.RATE * RING_SECONDS);
        if (s == 0) return null;
        MusicStream m = new MusicStream(engine, s, name, source, loop);
        m.thread.start();
        return m;
    }

    /** The engine's sound to play (one voice, any pitch is ignored). */
    public long sound() { return sound; }
    public String name() { return name; }
    /** Seconds decoded into the ring so far (a voice started once this is a fraction of a second never begins dry). */
    public double buffered() { return written / (double) UltraEngine.RATE; }
    /** The decoder has handed over the whole track (and marked its end), or failed. */
    public boolean decoded() { return done; }
    /** Why decoding stopped early, or null. */
    public String failure() { return failure; }

    /** Stops the decoder and releases the engine's sound (a voice still playing it keeps what it has). Game thread. */
    @Override public void close() {
        stop = true;
        thread.interrupt();
        try { thread.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        engine.release(sound);
    }

    // ------------------------------------------------------------------ the decoder thread
    private void decode() {
        FloatBuffer out = MemoryUtil.memAllocFloat(8192 * 2);
        try {
            do {
                try (InputStream raw = source.get()) {
                    if (raw == null) { failure = "no such track"; break; }
                    InputStream in = new BufferedInputStream(raw, 1 << 16);
                    in.mark(4);
                    byte[] head = in.readNBytes(4);
                    in.reset();
                    boolean mp3 = head.length >= 3 && (head[0] == 'I' && head[1] == 'D' && head[2] == '3' || (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0);
                    if (mp3) mp3(in, out);
                    else whole(in, out);
                }
            } while (loop && !stop && failure == null);
            if (!stop) push(out, 0, true);
        } catch (IOException | RuntimeException e) {
            failure = e.toString();
        } finally {
            MemoryUtil.memFree(out);
            done = true;
        }
    }

    private void mp3(InputStream in, FloatBuffer out) {
        javazoom.jl.decoder.Bitstream bs = new javazoom.jl.decoder.Bitstream(in);
        javazoom.jl.decoder.Decoder dec = new javazoom.jl.decoder.Decoder();
        Resampler rs = null;
        int bad = 0;
        try {
            while (!stop) {
                javazoom.jl.decoder.Header h;
                try { h = bs.readFrame(); } catch (javazoom.jl.decoder.BitstreamException e) { if (++bad > 64) break; continue; }
                if (h == null) break;
                javazoom.jl.decoder.SampleBuffer sb;
                try { sb = (javazoom.jl.decoder.SampleBuffer) dec.decodeFrame(h, bs); }
                catch (javazoom.jl.decoder.DecoderException | ArrayIndexOutOfBoundsException e) { bs.closeFrame(); if (++bad > 64) break; continue; }
                int ch = Math.max(1, sb.getChannelCount()), frames = sb.getBufferLength() / ch, rate = sb.getSampleFrequency();
                if (rs == null || rs.from != rate) rs = new Resampler(rate);
                short[] b = sb.getBuffer();
                out.clear();
                int n = rs.run(b, ch, frames, out);
                bs.closeFrame();
                push(out, n, false);
            }
        } finally {
            try { bs.close(); } catch (javazoom.jl.decoder.BitstreamException ignored) { }
        }
    }

    private void whole(InputStream in, FloatBuffer out) throws IOException {
        float[] mono;
        try { mono = Codecs.decode(in.readAllBytes(), name).mono(); }
        catch (Codecs.Failure f) { failure = f.getMessage(); return; }
        for (int at = 0; at < mono.length && !stop; ) {
            int n = Math.min(8192, mono.length - at);
            out.clear();
            for (int i = 0; i < n; i++) { out.put(i * 2, mono[at + i]); out.put(i * 2 + 1, mono[at + i]); }
            push(out, n, false);
            at += n;
        }
    }

    /** Hands frames to the ring, waiting (off the game's thread) while it is full. */
    private void push(FloatBuffer out, int frames, boolean end) {
        int at = 0;
        while (!stop) {
            FloatBuffer view = frames == 0 ? out : MemoryUtil.memSlice(out, at * 2, (frames - at) * 2);
            int took = engine.writeStream(sound, view, frames - at, end);
            if (took < 0) { stop = true; failure = "the engine refused the stream"; return; }
            at += took;
            written += took;
            if (at >= frames) return;
            try { Thread.sleep(15); } catch (InterruptedException e) { return; }
        }
    }

    /** Interleaved 16-bit frames at any rate to stereo float at the mixer's, linearly, carrying its phase across MP3 frames. */
    private static final class Resampler {
        final int from;
        final double step;
        double pos;
        float lastL, lastR;
        boolean primed;
        Resampler(int from) { this.from = from; this.step = from / (double) UltraEngine.RATE; }

        int run(short[] b, int ch, int frames, FloatBuffer out) {
            int n = 0, cap = out.capacity() / 2;
            if (from == UltraEngine.RATE) {
                for (int i = 0; i < frames && n < cap; i++, n++) {
                    float l = b[i * ch] / 32768f, r = ch > 1 ? b[i * ch + 1] / 32768f : l;
                    out.put(n * 2, l); out.put(n * 2 + 1, r);
                }
                return n;
            }
            // pos runs over [-1, frames): -1 is the last frame of the previous block
            while (pos < frames - 1 && n < cap) {
                int i0 = (int) Math.floor(pos);
                float fr = (float) (pos - i0);
                float l0, r0;
                if (i0 < 0) { l0 = primed ? lastL : b[0] / 32768f; r0 = primed ? lastR : (ch > 1 ? b[1] : b[0]) / 32768f; }
                else { l0 = b[i0 * ch] / 32768f; r0 = ch > 1 ? b[i0 * ch + 1] / 32768f : l0; }
                int i1 = i0 + 1;
                float l1 = b[i1 * ch] / 32768f, r1 = ch > 1 ? b[i1 * ch + 1] / 32768f : l1;
                out.put(n * 2, l0 + (l1 - l0) * fr); out.put(n * 2 + 1, r0 + (r1 - r0) * fr);
                n++;
                pos += step;
            }
            pos -= frames;
            lastL = b[(frames - 1) * ch] / 32768f; lastR = ch > 1 ? b[(frames - 1) * ch + 1] / 32768f : lastL;
            primed = true;
            return n;
        }
    }
}
