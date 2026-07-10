package boraheeb.util.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class LogLevelTest{

    @Test
    void severitiesAreOrderedTraceToCritical(){
        assertEquals(0, LogLevel.TRACE.getSeverity());
        assertEquals(1, LogLevel.DEBUG.getSeverity());
        assertEquals(2, LogLevel.INFO.getSeverity());
        assertEquals(3, LogLevel.WARN.getSeverity());
        assertEquals(4, LogLevel.ERROR.getSeverity());
        assertEquals(5, LogLevel.CRITICAL.getSeverity());
    }

    @Test
    void defaultLevelIsInfo(){
        assertSame(LogLevel.INFO, LogLevel.DEFAULT_LEVEL);
    }

    @Test
    void labelsMatchConstantNames(){
        assertEquals("TRACE", LogLevel.TRACE.getLabel());
        assertEquals("DEBUG", LogLevel.DEBUG.getLabel());
        assertEquals("INFO", LogLevel.INFO.getLabel());
        assertEquals("WARN", LogLevel.WARN.getLabel());
        assertEquals("ERROR", LogLevel.ERROR.getLabel());
        assertEquals("CRITICAL", LogLevel.CRITICAL.getLabel());
    }

    @Test
    void toStringReturnsLabel(){
        assertEquals(LogLevel.WARN.getLabel(), LogLevel.WARN.toString());
    }

    @Test
    void getValuesReturnsAllSixLevelsInOrder(){
        LogLevel[] values = LogLevel.getValues();
        assertEquals(6, values.length);
        assertSame(LogLevel.TRACE, values[0]);
        assertSame(LogLevel.CRITICAL, values[5]);
    }

    @Test
    void getValuesReturnsADefensiveCopy(){
        LogLevel[] values = LogLevel.getValues();
        values[0] = LogLevel.CRITICAL;
        assertSame(LogLevel.TRACE, LogLevel.getValues()[0]);
    }

    @ParameterizedTest
    @CsvSource({"trace,TRACE", "Debug,DEBUG", "  INFO  ,INFO", "WaRn,WARN", "error,ERROR", "CRITICAL,CRITICAL"})
    void fromLabelIsCaseInsensitiveAndTrims(String input, String expectedLabel){
        assertEquals(expectedLabel, LogLevel.fromLabel(input).getLabel());
    }

    @Test
    void fromLabelWithNullReturnsDefault(){
        assertSame(LogLevel.DEFAULT_LEVEL, LogLevel.fromLabel(null));
    }

    @Test
    void fromLabelWithBlankReturnsDefault(){
        assertSame(LogLevel.DEFAULT_LEVEL, LogLevel.fromLabel("   "));
    }

    @Test
    void fromLabelWithUnknownNameReturnsDefault(){
        assertSame(LogLevel.DEFAULT_LEVEL, LogLevel.fromLabel("NOT_A_LEVEL"));
    }

    @Test
    void fromSeverityReturnsMatchingLevel(){
        for(LogLevel level : LogLevel.getValues())
            assertSame(level, LogLevel.fromSeverity(level.getSeverity()));
    }

    @Test
    void fromSeverityBelowRangeReturnsDefault(){
        assertSame(LogLevel.DEFAULT_LEVEL, LogLevel.fromSeverity(-1));
    }

    @Test
    void fromSeverityAboveRangeReturnsDefault(){
        assertSame(LogLevel.DEFAULT_LEVEL, LogLevel.fromSeverity(6));
    }

    @Test
    void isAtLeastTrueForEqualLevel(){
        assertTrue(LogLevel.WARN.isAtLeast(LogLevel.WARN));
    }

    @Test
    void isAtLeastTrueForHigherLevel(){
        assertTrue(LogLevel.ERROR.isAtLeast(LogLevel.WARN));
    }

    @Test
    void isAtLeastFalseForLowerLevel(){
        assertFalse(LogLevel.DEBUG.isAtLeast(LogLevel.WARN));
    }

    @Test
    void isAtLeastWithNullThresholdUsesDefaultLevel(){
        assertTrue(LogLevel.WARN.isAtLeast(null));
        assertFalse(LogLevel.TRACE.isAtLeast(null));
    }

    @Test
    void isAtMostTrueForEqualLevel(){
        assertTrue(LogLevel.WARN.isAtMost(LogLevel.WARN));
    }

    @Test
    void isAtMostTrueForLowerLevel(){
        assertTrue(LogLevel.DEBUG.isAtMost(LogLevel.WARN));
    }

    @Test
    void isAtMostFalseForHigherLevel(){
        assertFalse(LogLevel.ERROR.isAtMost(LogLevel.WARN));
    }

    @Test
    void isAtMostWithNullThresholdUsesDefaultLevel(){
        assertTrue(LogLevel.DEBUG.isAtMost(null));
        assertFalse(LogLevel.ERROR.isAtMost(null));
    }

    @Test
    void levelsAreSingletonsAcrossLookups(){
        assertSame(LogLevel.fromLabel("WARN"), LogLevel.fromSeverity(3));
    }
}
