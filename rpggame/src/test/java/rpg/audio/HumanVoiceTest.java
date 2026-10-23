package rpg.audio;

import astratto.audio.Codecs;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HumanVoiceTest {
    @Test
    void everyConfiguredVoiceDecodesAndHasUniqueAudioAcrossBothBanks() throws Exception {
        var owners = new HashMap<String, String>();
        for (HumanVoice voice : HumanVoice.values()) for (String path : voice.recordings) {
            try (var input = getClass().getResourceAsStream("/sounds/" + path)) {
                assertNotNull(input, "missing voice asset: " + path);
                var decoded = Codecs.decode(input.readAllBytes(), path);
                var pcm = ByteBuffer.allocate(decoded.mono().length * Float.BYTES);
                double energy = 0;
                for (float sample : decoded.mono()) {
                    assertTrue(Float.isFinite(sample), "invalid voice sample: " + path);
                    pcm.putFloat(sample);
                    energy += sample * sample;
                }
                assertTrue(energy > 0.001, "silent voice asset: " + path);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pcm.array()));
                String previous = owners.putIfAbsent(hash, path);
                assertNull(previous, "duplicate voice audio: " + previous + " and " + path);
            }
        }
    }
}
