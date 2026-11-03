package rpg;

import astratto.time.Calendar;
import org.junit.jupiter.api.Test;
import rpg.state.WorldState;
import static org.junit.jupiter.api.Assertions.*;

/** (0.3.0, gamefixes) The Weekly System: the engine calendar and its use by the game's clock. */
class WeeklySystemTest {
    @Test void itStartsOnSoldayTheFifthOfMarch500AtEight() {
        Calendar c = new Calendar();
        assertEquals(500, c.year()); assertEquals(3, c.month()); assertEquals(5, c.day());
        assertEquals(8, c.hour()); assertEquals(0, c.minute()); assertEquals(0, c.weekday());
        WorldState s = new WorldState();
        assertEquals("Solday", s.weekdayName());
        assertEquals("Solday, March 5, 500", s.dateText());
    }

    @Test void theWeekdayTurnsOverAtMidnightAndTheMonthAtItsEnd() {
        Calendar c = new Calendar();
        c.advance(16 * 60 - 1);                     // 23:59 Solday
        assertEquals(0, c.weekday()); assertEquals(23, c.hour()); assertEquals(59, c.minute());
        c.advance(1);
        assertEquals(1, c.weekday()); assertEquals(6, c.day()); assertEquals(0, c.hour());
        c.advance(26 * 1440);                       // March 6 + 26 days = April 1
        assertEquals(4, c.month()); assertEquals(1, c.day());
        assertEquals((1 + 26) % 7, c.weekday());
        // setting the total recomputes every field from the epoch: the same answer
        Calendar d = new Calendar(); d.setTotal(c.totalMinutes());
        assertEquals(c.toString(), d.toString());
    }

    @Test void aMinuteTakesOneSecondAtMidnightThreeAtNoonAndADayAboutFortyEightMinutes() {
        assertEquals(1000, Calendar.msPerMinute(0), 1e-6);
        assertEquals(3000, Calendar.msPerMinute(12), 1e-6);
        assertEquals(2000, Calendar.msPerMinute(6), 1e-6);
        Calendar c = new Calendar(); c.setTotal(0);
        double real = 0;
        while (c.totalMinutes() < 1440) { c.tick(0.05); real += 0.05; }
        assertEquals(48 * 60, real, 60, "a day takes about 48 real minutes: " + real / 60);
    }

    @Test void theClockPausesScalesTravelsAndArrives() {
        Calendar c = new Calendar();
        double t0 = c.totalMinutes();
        c.pause(); assertEquals(0, c.tick(10)); assertEquals(t0, c.totalMinutes());
        c.resume(); assertTrue(c.tick(10) > 0);
        c.syncToDistance(0.1); assertEquals(0, c.tick(10)); assertEquals(1.0, c.travelled(10), 1e-9);
        c.restore();
        double before = c.totalMinutes();
        c.transitionToHour(20, 2);
        for (int i = 0; i < 40; i++) c.tick(0.05);
        assertEquals(20, c.hour()); assertEquals(0, c.minute());
        assertEquals(Calendar.Mode.RUNNING, c.mode());
        assertTrue(c.totalMinutes() > before);
        c.set(501, 1, 1, 6, 30);
        assertEquals(501, c.year()); assertEquals(1, c.month()); assertEquals(1, c.day()); assertEquals(6, c.hour()); assertEquals(30, c.minute());
        assertThrows(IllegalArgumentException.class, () -> c.set(499, 1, 1, 0, 0));
    }

    @Test void theWorldsMinutesAndTheCalendarStayInStep() {
        WorldState s = new WorldState();
        s.minutes += 3 * 1440 + 60;
        assertEquals(3, s.weekday()); assertEquals("Merday", s.weekdayName()); assertEquals(9, s.cal().hour());
        s.minutes = 8 * 60;                         // a load or an override backward: recomputed
        assertEquals(0, s.weekday());
        assertTrue(s.cal().window(1, 7, 9));
    }
}
