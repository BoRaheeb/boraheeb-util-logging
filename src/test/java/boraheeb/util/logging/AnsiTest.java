package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnsiTest{

    private static final char ESC_CHAR = 0x1B;
    private static final String ESC = String.valueOf(ESC_CHAR);

    @Test
    void resetIsWellFormedEscapeSequence(){
        assertEquals(ESC + "[0m", Ansi.RESET);
    }

    @Test
    void boldIsWellFormedEscapeSequence(){
        assertEquals(ESC + "[1m", Ansi.BOLD);
    }

    @Test
    void standardForegroundColorsAreWellFormed(){
        assertEquals(ESC + "[31m", Ansi.FG_RED);
        assertEquals(ESC + "[32m", Ansi.FG_GREEN);
        assertEquals(ESC + "[97m", Ansi.FG_BRIGHT_WHITE);
    }

    @Test
    void standardBackgroundColorsAreWellFormed(){
        assertEquals(ESC + "[41m", Ansi.BG_RED);
        assertEquals(ESC + "[107m", Ansi.BG_BRIGHT_WHITE);
    }

    @Test
    void fg256ProducesExpectedFormat(){
        assertEquals(ESC + "[38;5;100m", Ansi.fg256(100));
    }

    @Test
    void bg256ProducesExpectedFormat(){
        assertEquals(ESC + "[48;5;100m", Ansi.bg256(100));
    }

    @Test
    void fg256BoundaryValuesAreValid(){
        assertEquals(ESC + "[38;5;0m", Ansi.fg256(0));
        assertEquals(ESC + "[38;5;255m", Ansi.fg256(255));
    }

    @Test
    void fg256OutOfRangeFallsBackToDefault(){
        assertEquals(ESC + "[38;5;244m", Ansi.fg256(-1));
        assertEquals(ESC + "[38;5;244m", Ansi.fg256(256));
    }

    @Test
    void bg256OutOfRangeFallsBackToDefault(){
        assertEquals(ESC + "[48;5;244m", Ansi.bg256(-5));
        assertEquals(ESC + "[48;5;244m", Ansi.bg256(1000));
    }

    @Test
    void fgRgbProducesExpectedFormat(){
        assertEquals(ESC + "[38;2;10;20;30m", Ansi.fgRgb(10, 20, 30));
    }

    @Test
    void bgRgbProducesExpectedFormat(){
        assertEquals(ESC + "[48;2;10;20;30m", Ansi.bgRgb(10, 20, 30));
    }

    @Test
    void fgRgbBoundaryValuesAreValid(){
        assertEquals(ESC + "[38;2;0;0;0m", Ansi.fgRgb(0, 0, 0));
        assertEquals(ESC + "[38;2;255;255;255m", Ansi.fgRgb(255, 255, 255));
    }

    @Test
    void fgRgbOutOfRangeChannelFallsBackToDefault(){
        assertEquals(ESC + "[38;2;180;20;30m", Ansi.fgRgb(-1, 20, 30));
        assertEquals(ESC + "[38;2;10;180;30m", Ansi.fgRgb(10, 300, 30));
        assertEquals(ESC + "[38;2;10;20;180m", Ansi.fgRgb(10, 20, -100));
    }

    @Test
    void bgRgbOutOfRangeChannelFallsBackToDefault(){
        assertEquals(ESC + "[48;2;180;180;180m", Ansi.bgRgb(-1, 999, -50));
    }

    @Test
    void isValid256ReturnsTrueForInRangeValues(){
        assertTrue(Ansi.isValid256(0));
        assertTrue(Ansi.isValid256(255));
        assertTrue(Ansi.isValid256(128));
    }

    @Test
    void isValid256ReturnsFalseForOutOfRangeValues(){
        assertFalse(Ansi.isValid256(-1));
        assertFalse(Ansi.isValid256(256));
    }

    @Test
    void isValidRgbChannelReturnsTrueForInRangeValues(){
        assertTrue(Ansi.isValidRgbChannel(0));
        assertTrue(Ansi.isValidRgbChannel(255));
    }

    @Test
    void isValidRgbChannelReturnsFalseForOutOfRangeValues(){
        assertFalse(Ansi.isValidRgbChannel(-1));
        assertFalse(Ansi.isValidRgbChannel(256));
    }

    @Test
    void wrapAppliesStyleAndReset(){
        String result = Ansi.wrap(Ansi.FG_RED, "hello");
        assertEquals(Ansi.FG_RED + "hello" + Ansi.RESET, result);
    }

    @Test
    void wrapWithNullStyleOmitsStylePrefix(){
        String result = Ansi.wrap(null, "hello");
        assertEquals("hello" + Ansi.RESET, result);
    }

    @Test
    void wrapWithNullTextTreatsAsEmptyString(){
        String result = Ansi.wrap(Ansi.FG_RED, (String) null);
        assertEquals(Ansi.FG_RED + Ansi.RESET, result);
    }

    @Test
    void wrapWithComposedStylesAppliesBothBeforeReset(){
        String composed = Ansi.BOLD + Ansi.FG_RED;
        String result = Ansi.wrap(composed, "x");
        assertEquals(composed + "x" + Ansi.RESET, result);
    }

    @Test
    void wrapObjectValueUsesStringValueOf(){
        String result = Ansi.wrap(Ansi.FG_RED, (Object) 42);
        assertEquals(Ansi.FG_RED + "42" + Ansi.RESET, result);
    }

    @Test
    void wrapObjectNullValueTreatsAsEmptyString(){
        String result = Ansi.wrap(Ansi.FG_RED, (Object) null);
        assertEquals(Ansi.FG_RED + Ansi.RESET, result);
    }

    @Test
    void allEscapeSequencesStartWithEscAndEndWithM(){
        String[] sequences = {
            Ansi.RESET, Ansi.BOLD, Ansi.DIM, Ansi.ITALIC, Ansi.UNDERLINE,
            Ansi.FG_RED, Ansi.BG_BLUE, Ansi.FG_256_GRAY, Ansi.BG_256_GOLD,
            Ansi.FG_RGB_ORANGE, Ansi.BG_RGB_TEAL
        };
        for(String sequence : sequences){
            assertTrue(sequence.charAt(0) == ESC_CHAR, "expected escape prefix in: " + sequence);
            assertTrue(sequence.endsWith("m"), "expected 'm' suffix in: " + sequence);
        }
    }

    @Test
    void namedConstantsMatchManualConstruction(){
        assertEquals(Ansi.fg256(244), Ansi.FG_256_GRAY);
        assertEquals(Ansi.fgRgb(255, 165, 0), Ansi.FG_RGB_ORANGE);
        assertEquals(Ansi.bgRgb(0, 128, 128), Ansi.BG_RGB_TEAL);
    }
}
