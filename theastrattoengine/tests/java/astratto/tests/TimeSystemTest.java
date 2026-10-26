package astratto.tests;

import astratto.weather.TimeSystem;
import astratto.weather.Weather;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeSystemTest {
    @Test void saveNumbersPreserveEveryBitOfNativeIdsAndSentinelEpochs() {
        long id=Long.MIN_VALUE | 104L<<40 | 12L<<20 | 74;
        var data=astratto.util.Json.parseObject(astratto.util.Json.write(java.util.Map.of("id",id,"epoch",Long.MIN_VALUE,"fraction",0.125)));
        assertEquals(id,astratto.util.Json.lng(data,"id",0));assertEquals(Long.MIN_VALUE,astratto.util.Json.lng(data,"epoch",0));
        assertEquals(0.125,astratto.util.Json.num(data,"fraction",0));
    }
    @Test void adjacentRegionalFiltersBlendContinuouslyAndNeverDoubleTheirBrightness() {
        var time=new TimeSystem();
        time.regions.add(new TimeSystem.Region("west",0,0,100,100,10,new TimeSystem.Profile(1,0.5f,0.5f,0.5f,0.25f,0.25f,1)));
        time.regions.add(new TimeSystem.Region("east",100,0,200,100,10,new TimeSystem.Profile(0.5f,0.5f,1,0.25f,0.25f,0.5f,2)));
        time.update(720,20,100,0,50);assertEquals(0.75f,time.tintR,1e-4f);assertEquals(0.75f,time.tintB,1e-4f);assertEquals(1.5f,time.fogScale,1e-4f);
        time.update(720,20,99.99f,0,50);float left=time.tintR;
        time.update(720,20,100.01f,0,50);assertTrue(Math.abs(left-time.tintR)<0.002f);
        time.update(720,20,400,0,400);assertEquals(1,time.tintR,1e-4f);assertEquals(1,time.fogScale,1e-4f);
    }
    @Test void oneClockDrivesDaylightAndSavedWeatherResumesWithoutAbandonedLightning() {
        assertEquals(TimeSystem.daylight(0),TimeSystem.daylight(24),1e-6f);assertTrue(TimeSystem.daylight(12)>TimeSystem.daylight(0));
        var source=new TimeSystem();source.update(720,0,0,0,0);source.weather.set(Weather.Kind.STORM);source.weather.snap();source.weather.time=12;
        var loaded=new TimeSystem();loaded.weather.lightning=1;loaded.load(source.save());loaded.update(720,0,0,0,0);
        assertEquals(Weather.Kind.STORM,loaded.weather.kind());assertEquals(1,loaded.weather.rain);assertEquals(12,loaded.weather.time);assertEquals(0,loaded.weather.lightning);
    }
}
