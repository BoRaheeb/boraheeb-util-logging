package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogDateTimePresetTest{

    @Test
    void defaultPresetIsLogReadable(){
        assertSame(LogDateTimePreset.LOG_READABLE, LogDateTimePreset.DEFAULT_PRESET);
    }

    @Test
    void getLabelReturnsExpectedLabels(){
        assertEquals("DATE_SHORT", LogDateTimePreset.DATE_SHORT.getLabel());
        assertEquals("LOG_READABLE", LogDateTimePreset.LOG_READABLE.getLabel());
        assertEquals("FILENAME_FULL", LogDateTimePreset.FILENAME_FULL.getLabel());
    }

    @Test
    void getPatternReturnsExpectedPatterns(){
        assertEquals("yyyy/MM/dd", LogDateTimePreset.DATE_SHORT.getPattern());
        assertEquals("yyyy-MM-dd HH:mm:ss", LogDateTimePreset.LOG_READABLE.getPattern());
        assertEquals("HH:mm:ss.SSS", LogDateTimePreset.TIME_24H_WITH_MILLIS.getPattern());
    }

    @Test
    void toStringReturnsLabel(){
        assertEquals("ISO_LOCAL", LogDateTimePreset.ISO_LOCAL.toString());
    }

    @Test
    void getValuesContainsAllPresets(){
        LogDateTimePreset[] values = LogDateTimePreset.getValues();
        assertEquals(27, values.length);
        assertTrue(containsByIdentity(values, LogDateTimePreset.DATE_SHORT));
        assertTrue(containsByIdentity(values, LogDateTimePreset.FILENAME_FULL));
    }

    @Test
    void getValuesReturnsDefensiveCopy(){
        LogDateTimePreset[] first = LogDateTimePreset.getValues();
        first[0] = null;
        LogDateTimePreset[] second = LogDateTimePreset.getValues();
        assertNotNull(second[0]);
    }

    @Test
    void fromLabelFindsExactMatch(){
        assertSame(LogDateTimePreset.DATE_ISO, LogDateTimePreset.fromLabel("DATE_ISO"));
    }

    @Test
    void fromLabelIsCaseInsensitive(){
        assertSame(LogDateTimePreset.DATE_ISO, LogDateTimePreset.fromLabel("date_iso"));
        assertSame(LogDateTimePreset.DATE_ISO, LogDateTimePreset.fromLabel("Date_Iso"));
    }

    @Test
    void fromLabelTrimsWhitespace(){
        assertSame(LogDateTimePreset.DATE_ISO, LogDateTimePreset.fromLabel("  DATE_ISO  "));
    }

    @Test
    void fromLabelNullFallsBackToDefault(){
        assertSame(LogDateTimePreset.DEFAULT_PRESET, LogDateTimePreset.fromLabel(null));
    }

    @Test
    void fromLabelBlankFallsBackToDefault(){
        assertSame(LogDateTimePreset.DEFAULT_PRESET, LogDateTimePreset.fromLabel("   "));
    }

    @Test
    void fromLabelUnknownFallsBackToDefault(){
        assertSame(LogDateTimePreset.DEFAULT_PRESET, LogDateTimePreset.fromLabel("NOT_A_REAL_PRESET"));
    }

    @Test
    void fromPatternFindsExactMatch(){
        assertSame(LogDateTimePreset.DATE_ISO, LogDateTimePreset.fromPattern("yyyy-MM-dd"));
    }

    @Test
    void fromPatternTrimsWhitespace(){
        assertSame(LogDateTimePreset.DATE_ISO, LogDateTimePreset.fromPattern("  yyyy-MM-dd  "));
    }

    @Test
    void fromPatternIsCaseSensitive(){
        assertSame(LogDateTimePreset.TIME_24H, LogDateTimePreset.fromPattern("HH:mm"));
        assertNotSame(LogDateTimePreset.TIME_24H, LogDateTimePreset.fromPattern("hh:mm"));
    }

    @Test
    void fromPatternNullFallsBackToDefault(){
        assertSame(LogDateTimePreset.DEFAULT_PRESET, LogDateTimePreset.fromPattern(null));
    }

    @Test
    void fromPatternBlankFallsBackToDefault(){
        assertSame(LogDateTimePreset.DEFAULT_PRESET, LogDateTimePreset.fromPattern(""));
    }

    @Test
    void fromPatternUnknownFallsBackToDefault(){
        assertSame(LogDateTimePreset.DEFAULT_PRESET, LogDateTimePreset.fromPattern("not-a-real-pattern"));
    }

    private static boolean containsByIdentity(LogDateTimePreset[] values, LogDateTimePreset target){
        for(LogDateTimePreset value : values)
            if(value == target) return true;
        return false;
    }
}
