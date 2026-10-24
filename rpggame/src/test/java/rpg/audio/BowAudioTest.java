package rpg.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BowAudioTest {
    @Test void releasesHaveHeadroomAndFadeToSilenceWithoutNonFiniteSamples() {
        for (boolean crossbow : new boolean[]{false, true}) {
            float[] wave = BowAudio.release(44100, crossbow, 72);
            double energy = 0; float peak = 0;
            for (float sample : wave) { assertTrue(Float.isFinite(sample)); peak = Math.max(peak, Math.abs(sample)); energy += sample * sample; }
            assertTrue(peak > 0.05f && peak < 0.98f, "audible release with mixer headroom");
            assertTrue(energy / wave.length > 0.00001);
            assertEquals(0, wave[0], 0.000001f); assertEquals(0, wave[wave.length - 1], 0.000001f);
        }
        assertFalse(java.util.Arrays.equals(BowAudio.release(44100, false, 72), BowAudio.release(44100, true, 72)));
    }
}
