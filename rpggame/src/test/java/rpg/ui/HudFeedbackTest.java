package rpg.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudFeedbackTest {
    @Test void honorOnlyWakesForAChangeAndFinishesFadingWithinSixSeconds() {
        Fragment toast = new HudFragments.HonorToast(null);
        assertFalse(toast.wanted(0), "Honor must not display just because the world HUD is open");
        toast.woken(1);
        assertTrue(toast.wanted(1.1));
        float alpha = 0;
        for (int frame = 0; frame <= 420; frame++) {
            double now = 1 + frame / 60.0;
            alpha = toast.wanted(now) ? Math.min(1, alpha + 1f / 60 / toast.fadeIn) : Math.max(0, alpha - 1f / 60 / toast.fadeOut);
            if (now >= 7) assertEquals(0, alpha, 1e-5f, "fade-out counts toward the six-second limit");
        }
        toast.woken(10);
        assertTrue(toast.wanted(10.1), "a later change can show a fresh toast");
    }
}
