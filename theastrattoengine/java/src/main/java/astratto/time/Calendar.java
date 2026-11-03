package astratto.time;

/**
 * The Weekly System: the engine's authoritative calendar and clock.
 *
 * <p>It keeps its own year, month, day, hour, minute and second and moves them on by calendar arithmetic (carrying
 * seconds into minutes, minutes into hours, hours into days, days into months by each month's length, months into
 * years) - never by elapsed play time. The weekday is a zero-indexed cycle, 0 to 6, the first day of the week being 0,
 * and advances as the date changes (across midnight, a sleep, a skip or a load alike). The epoch is set by the game
 * ({@link #EPOCH_YEAR} etc. are the defaults: the 5th of the third month of year 500, 08:00, weekday 0).
 *
 * <p>The clock runs at a nominal {@link #BASE_MS_PER_MINUTE} real milliseconds per game minute (about 48 real minutes
 * a day), slower at midday and quicker at night: from {@link #MIDNIGHT_MS} a minute at midnight to {@link #NOON_MS} at
 * noon along a cosine, which averages out to the nominal rate over a day. The rate can be scaled, the clock paused
 * and restored, driven by the distance a traveller covers instead of by real time, or run on to an arrival time over a
 * set real duration (a scripted transition); the date and time can also be set outright.
 *
 * <p>Event scheduling reads this; the look of the sky and weather is sampled separately from the time of day.
 */
public final class Calendar {
    public static final int EPOCH_YEAR = 500, EPOCH_MONTH = 3, EPOCH_DAY = 5, EPOCH_HOUR = 8, EPOCH_WEEKDAY = 0;
    public static final double BASE_MS_PER_MINUTE = 2000, MIDNIGHT_MS = 1000, NOON_MS = 3000;
    /** Days in each month (index 1..12); the second month gains a day in a leap year. */
    private static final int[] MONTH_DAYS = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    public enum Mode { RUNNING, PAUSED, DISTANCE, TRANSITION }

    private int year = EPOCH_YEAR, month = EPOCH_MONTH, day = EPOCH_DAY, hour = EPOCH_HOUR, minute, weekday = EPOCH_WEEKDAY;
    private double second;
    /** Game minutes since the epoch's midnight (the scalar every other system reads), kept in step with the fields. */
    private double total = EPOCH_HOUR * 60;
    private Mode mode = Mode.RUNNING, before = Mode.RUNNING;
    /** A multiplier on the clock's rate (1: as designed; 0 stops it without pausing). */
    private double scale = 1;
    /** Distance mode: game minutes per metre travelled. */
    private double minutesPerMetre;
    /** A transition: the total to arrive at, and the real seconds left to get there. */
    private double arriveAt, arriveIn;

    // ------------------------------------------------------------------ reading
    public int year() { return year; }
    public int month() { return month; }
    public int day() { return day; }
    public int hour() { return hour; }
    public int minute() { return minute; }
    public double second() { return second; }
    /** 0..6; 0 is the first day of the week. */
    public int weekday() { return weekday; }
    /** The hour as a fraction (14.5 = half past two in the afternoon). */
    public double hourOfDay() { return hour + minute / 60.0 + second / 3600.0; }
    public double totalMinutes() { return total; }
    /** Whole days since the epoch's date (0 on the first day). */
    public long dayIndex() { return (long) Math.floor(total / 1440); }
    public Mode mode() { return mode; }
    public double scale() { return scale; }
    public static boolean leap(int year) { return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0); }
    public static int daysIn(int month, int year) { return MONTH_DAYS[month] + (month == 2 && leap(year) ? 1 : 0); }

    /** Real milliseconds a game minute takes at an hour of the day (1 s at midnight, 3 s at noon, 2 s on average). */
    public static double msPerMinute(double hour) {
        double mid = (MIDNIGHT_MS + NOON_MS) / 2, swing = (NOON_MS - MIDNIGHT_MS) / 2;
        return mid - swing * Math.cos(hour / 24.0 * Math.PI * 2);
    }
    /** Game minutes per real second now (0 while paused). */
    public double minutesPerSecond() { return mode == Mode.PAUSED || mode == Mode.DISTANCE ? 0 : 1000.0 / msPerMinute(hourOfDay()) * scale; }

    // ------------------------------------------------------------------ event windows
    /** Whether the day is one of these weekdays. */
    public boolean on(int... weekdays) { for (int w : weekdays) if (w == weekday) return true; return false; }
    /** Whether the hour is within [from, to) - a window across midnight (22 to 4) is understood. */
    public boolean between(double from, double to) { double h = hourOfDay(); return from <= to ? h >= from && h < to : h >= from || h < to; }
    /** A weekday mask (bit n: weekday n) and an hour window together. */
    public boolean window(int weekdayMask, double from, double to) { return (weekdayMask >> weekday & 1) != 0 && between(from, to); }

    // ------------------------------------------------------------------ running
    /** One frame of real time (seconds): returns the game minutes it moved on (none while paused or travelling by distance). */
    public double tick(double realSeconds) {
        if (realSeconds <= 0) return 0;
        switch (mode) {
            case PAUSED, DISTANCE -> { return 0; }
            case TRANSITION -> {
                double left = arriveAt - total, step = arriveIn <= realSeconds + 1e-6 ? left : left * realSeconds / arriveIn;
                arriveIn -= realSeconds;
                advance(step);
                if (arriveIn <= 0 || Math.abs(arriveAt - total) < 1e-6) mode = before;
                return step;
            }
            default -> {
                // integrated in small slices: the rate changes with the hour itself
                double moved = 0, t = realSeconds;
                while (t > 1e-9) {
                    double slice = Math.min(t, 0.25);
                    double minutes = slice * 1000.0 / msPerMinute(hourOfDay()) * scale;
                    advance(minutes); moved += minutes; t -= slice;
                }
                return moved;
            }
        }
    }

    /** Distance mode: a traveller covered this many metres - the clock goes on by that much. Returns minutes moved. */
    public double travelled(double metres) {
        if (mode != Mode.DISTANCE || metres <= 0) return 0;
        double minutes = metres * minutesPerMetre;
        advance(minutes);
        return minutes;
    }

    public void pause() { if (mode != Mode.PAUSED) { before = mode == Mode.TRANSITION ? Mode.RUNNING : mode; mode = Mode.PAUSED; } }
    /** Back to running at the designed rate (from a pause, distance mode or a transition). */
    public void restore() { mode = Mode.RUNNING; before = Mode.RUNNING; scale = 1; }
    public void resume() { if (mode == Mode.PAUSED) mode = before; }
    public void scale(double s) { scale = Math.max(0, s); }
    /** The clock follows the traveller's steps rather than real time (minutes per metre). */
    public void syncToDistance(double minutesPerMetre) { this.minutesPerMetre = Math.max(0, minutesPerMetre); mode = Mode.DISTANCE; }
    /** Runs on to a game time (minutes since the epoch's midnight, never backward) over this many real seconds. */
    public void transitionTo(double targetTotal, double realSeconds) {
        if (targetTotal <= total) return;
        if (mode != Mode.TRANSITION) before = mode == Mode.PAUSED ? Mode.RUNNING : mode;
        arriveAt = targetTotal; arriveIn = Math.max(0.01, realSeconds); mode = Mode.TRANSITION;
    }
    /** Runs on to the next time the clock reads this hour (today, or tomorrow if it has passed), over real seconds. */
    public void transitionToHour(double hourOfDay, double realSeconds) {
        double today = Math.floor(total / 1440) * 1440 + hourOfDay * 60;
        transitionTo(today > total ? today : today + 1440, realSeconds);
    }

    /** Moves the date and time on by game minutes (negative is not allowed: the calendar only goes forward this way). */
    public void advance(double minutes) {
        if (!(minutes > 0)) return;
        total += minutes;
        second += minutes * 60;
        if (second < 60 - 1e-6) return;
        // (a hair under the minute is the minute: the fractions of many small steps must not leave the clock at :59)
        long carryMin = (long) Math.floor((second + 1e-6) / 60); second = Math.max(0, second - carryMin * 60);
        long m = minute + carryMin; long carryHour = m / 60; minute = (int) (m % 60);
        long h = hour + carryHour; long carryDay = h / 24; hour = (int) (h % 24);
        for (long d = 0; d < carryDay; d++) nextDay();
    }

    private void nextDay() {
        weekday = (weekday + 1) % 7;
        if (++day > daysIn(month, year)) { day = 1; if (++month > 12) { month = 1; year++; } }
    }

    // ------------------------------------------------------------------ setting
    /** Puts the calendar at a total (minutes since the epoch's midnight): every field recomputed from the epoch. */
    public void setTotal(double minutes) {
        year = EPOCH_YEAR; month = EPOCH_MONTH; day = EPOCH_DAY; hour = 0; minute = 0; second = 0; weekday = EPOCH_WEEKDAY; total = 0;
        if (minutes < 0) minutes = 0;
        long days = (long) Math.floor(minutes / 1440);
        for (long d = 0; d < days; d++) nextDay();
        total = days * 1440;
        advance(minutes - total);
    }

    /** Sets the date and time outright (on or after the epoch); returns the new total in minutes. */
    public double set(int year, int month, int day, int hour, int minute) {
        long days = 0;
        int y = EPOCH_YEAR, mo = EPOCH_MONTH, d = EPOCH_DAY;
        if (year < EPOCH_YEAR || year == EPOCH_YEAR && (month < EPOCH_MONTH || month == EPOCH_MONTH && day < EPOCH_DAY)) throw new IllegalArgumentException("before the calendar's epoch");
        while (y != year || mo != month || d != day) {
            days++;
            if (++d > daysIn(mo, y)) { d = 1; if (++mo > 12) { mo = 1; y++; } }
            if (days > 400L * 366) throw new IllegalArgumentException("date out of range");
        }
        setTotal(days * 1440 + Math.max(0, Math.min(23, hour)) * 60 + Math.max(0, Math.min(59, minute)));
        return total;
    }

    /** Sets only the time of day, keeping the date (it may go back within the day: an override, not the clock running). */
    public double setTimeOfDay(int hour, int minute) { setTotal(Math.floor(total / 1440) * 1440 + hour * 60 + minute); return total; }

    // ------------------------------------------------------------------ persistence
    public java.util.Map<String, Object> save() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("total", total); m.put("mode", (mode == Mode.TRANSITION ? before : mode).name()); m.put("scale", scale); m.put("perMetre", minutesPerMetre);
        return m;
    }
    public void load(java.util.Map<String, Object> m, double fallbackTotal) {
        double t = m == null ? fallbackTotal : astratto.util.Json.num(m, "total", fallbackTotal);
        setTotal(t);
        mode = Mode.RUNNING; before = Mode.RUNNING; scale = 1;
        if (m != null) {
            try { mode = Mode.valueOf(astratto.util.Json.str(m, "mode", "RUNNING")); } catch (IllegalArgumentException e) { mode = Mode.RUNNING; }
            if (mode == Mode.TRANSITION) mode = Mode.RUNNING;
            scale = astratto.util.Json.num(m, "scale", 1); minutesPerMetre = astratto.util.Json.num(m, "perMetre", 0);
        }
    }

    @Override public String toString() { return String.format("%d-%02d-%02d %02d:%02d (weekday %d)", year, month, day, hour, minute, weekday); }
}
