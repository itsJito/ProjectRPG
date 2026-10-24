package rpg.audio;

import java.util.Random;

/** Original plucked-string release layers. Rendered once per sound bank, never on the audio callback. */
final class BowAudio {
    private BowAudio() { }
    static float[] release(int rate, boolean crossbow, long seed) {
        Random random = new Random(seed);
        float[] wave = new float[(int) (rate * 0.55f)];
        float frequency = (crossbow ? 185 : 118) + random.nextFloat() * 16;
        float[] string = new float[Math.max(2, (int) (rate / frequency))];
        for (int i = 0; i < string.length; i++) string[i] = random.nextFloat() * 2 - 1;
        float body = 0, air = 0;
        for (int i = 0; i < wave.length; i++) {
            int at = i % string.length;
            float sample = string[at];
            string[at] = (sample + string[(at + 1) % string.length]) * (crossbow ? 0.485f : 0.493f);
            double time = i / (double) rate;
            float noise = random.nextFloat() * 2 - 1;
            body += (sample - body) * 0.12f;
            air += (noise - air) * 0.32f;
            double attack = Math.min(1, time / 0.0015);
            double wood = Math.sin(time * 2 * Math.PI * (crossbow ? 570 : 340)) * Math.exp(-time * 65) * 0.18;
            double snap = (noise - air) * Math.exp(-time * 110) * (crossbow ? 0.26 : 0.15);
            double tail = Math.min(1, (wave.length - 1 - i) / (rate * 0.045));
            wave[i] = (float) Math.tanh((sample * 0.48 + body * 0.65 + wood + snap) * attack * tail);
        }
        return wave;
    }
}
