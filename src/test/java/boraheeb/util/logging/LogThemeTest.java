package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogThemeTest{

    @Test
    void builderDefaultsProduceEmptyStylesAndNoBackground(){
        LogTheme theme = LogTheme.builder().build();
        assertEquals("", theme.getTimestampStyle());
        assertEquals("", theme.getLoggerNameStyle());
        assertEquals("", theme.getSourceNameStyle());
        assertEquals("", theme.getLineNumberStyle());
        assertEquals("", theme.getThreadNameStyle());
        assertEquals("", theme.getMessageStyle());
        assertEquals("", theme.getFieldsStyle());
        assertEquals("", theme.getThrowableStyle());
        assertNull(theme.getTerminalBackground());
        assertFalse(theme.hasTerminalBackground());
    }

    @Test
    void builderDefaultThemeNameIsUsedWhenNotSet(){
        LogTheme theme = LogTheme.builder().build();
        assertEquals("New Theme", theme.getThemeName());
    }

    @Test
    void themeNameNullFallsBackToDefault(){
        LogTheme theme = LogTheme.builder().themeName(null).build();
        assertEquals("New Theme", theme.getThemeName());
    }

    @Test
    void themeNameBlankFallsBackToDefault(){
        LogTheme theme = LogTheme.builder().themeName("   ").build();
        assertEquals("New Theme", theme.getThemeName());
    }

    @Test
    void themeNameIsTrimmed(){
        LogTheme theme = LogTheme.builder().themeName("  Custom Name  ").build();
        assertEquals("Custom Name", theme.getThemeName());
    }

    @Test
    void nullStylesAreStoredAsEmptyStrings(){
        LogTheme theme = LogTheme.builder()
            .timestampStyle(null)
            .traceStyle(null)
            .debugStyle(null)
            .infoStyle(null)
            .warnStyle(null)
            .errorStyle(null)
            .criticalStyle(null)
            .loggerNameStyle(null)
            .sourceNameStyle(null)
            .lineNumberStyle(null)
            .threadNameStyle(null)
            .messageStyle(null)
            .fieldsStyle(null)
            .throwableStyle(null)
            .build();
        assertEquals("", theme.getTimestampStyle());
        assertEquals("", theme.getLevelStyle(LogLevel.TRACE));
        assertEquals("", theme.getLevelStyle(LogLevel.CRITICAL));
        assertEquals("", theme.getLoggerNameStyle());
        assertEquals("", theme.getSourceNameStyle());
        assertEquals("", theme.getLineNumberStyle());
        assertEquals("", theme.getThreadNameStyle());
        assertEquals("", theme.getMessageStyle());
        assertEquals("", theme.getFieldsStyle());
        assertEquals("", theme.getThrowableStyle());
    }

    @Test
    void terminalBackgroundNullMeansNoBackground(){
        LogTheme theme = LogTheme.builder().terminalBackground(null).build();
        assertNull(theme.getTerminalBackground());
        assertFalse(theme.hasTerminalBackground());
    }

    @Test
    void terminalBackgroundBlankMeansNoBackground(){
        LogTheme theme = LogTheme.builder().terminalBackground("   ").build();
        assertNull(theme.getTerminalBackground());
        assertFalse(theme.hasTerminalBackground());
    }

    @Test
    void terminalBackgroundSetIsStoredAsIs(){
        LogTheme theme = LogTheme.builder().terminalBackground(Ansi.BG_BLUE).build();
        assertEquals(Ansi.BG_BLUE, theme.getTerminalBackground());
        assertTrue(theme.hasTerminalBackground());
    }

    @Test
    void getLevelStyleMapsEachLevelCorrectly(){
        LogTheme theme = LogTheme.builder()
            .traceStyle("trace")
            .debugStyle("debug")
            .infoStyle("info")
            .warnStyle("warn")
            .errorStyle("error")
            .criticalStyle("critical")
            .build();
        assertEquals("trace", theme.getLevelStyle(LogLevel.TRACE));
        assertEquals("debug", theme.getLevelStyle(LogLevel.DEBUG));
        assertEquals("info", theme.getLevelStyle(LogLevel.INFO));
        assertEquals("warn", theme.getLevelStyle(LogLevel.WARN));
        assertEquals("error", theme.getLevelStyle(LogLevel.ERROR));
        assertEquals("critical", theme.getLevelStyle(LogLevel.CRITICAL));
    }

    @Test
    void getLevelStyleWithNullLevelFallsBackToDefaultLevelStyle(){
        LogTheme theme = LogTheme.builder().infoStyle("info").build();
        assertEquals("info", theme.getLevelStyle(null));
    }

    @Test
    void toStringContainsThemeNameAndBackgroundFlag(){
        LogTheme theme = LogTheme.builder().themeName("MyTheme").terminalBackground(Ansi.BG_BLUE).build();
        String result = theme.toString();
        assertTrue(result.contains("MyTheme"));
        assertTrue(result.contains("hasTerminalBackground=true"));
    }

    @Test
    void toBuilderPreservesAllValues(){
        LogTheme original = LogTheme.builder()
            .themeName("Original")
            .timestampStyle("ts")
            .traceStyle("tr")
            .debugStyle("de")
            .infoStyle("in")
            .warnStyle("wa")
            .errorStyle("er")
            .criticalStyle("cr")
            .loggerNameStyle("lg")
            .sourceNameStyle("sn")
            .lineNumberStyle("ln")
            .threadNameStyle("tn")
            .messageStyle("ms")
            .fieldsStyle("fl")
            .throwableStyle("th")
            .terminalBackground("bg")
            .build();
        LogTheme copy = original.toBuilder().build();
        assertEquals(original.getThemeName(), copy.getThemeName());
        assertEquals(original.getTimestampStyle(), copy.getTimestampStyle());
        assertEquals(original.getLevelStyle(LogLevel.TRACE), copy.getLevelStyle(LogLevel.TRACE));
        assertEquals(original.getLoggerNameStyle(), copy.getLoggerNameStyle());
        assertEquals(original.getSourceNameStyle(), copy.getSourceNameStyle());
        assertEquals(original.getLineNumberStyle(), copy.getLineNumberStyle());
        assertEquals(original.getThreadNameStyle(), copy.getThreadNameStyle());
        assertEquals(original.getMessageStyle(), copy.getMessageStyle());
        assertEquals(original.getFieldsStyle(), copy.getFieldsStyle());
        assertEquals(original.getThrowableStyle(), copy.getThrowableStyle());
        assertEquals(original.getTerminalBackground(), copy.getTerminalBackground());
    }

    @Test
    void toBuilderCreatesIndependentCopy(){
        LogTheme original = LogTheme.builder().themeName("Original").build();
        LogTheme modified = original.toBuilder().themeName("Modified").build();
        assertEquals("Original", original.getThemeName());
        assertEquals("Modified", modified.getThemeName());
    }

    @Test
    void noneThemeHasNoStylesAndNoBackground(){
        assertEquals("", LogTheme.NONE.getTimestampStyle());
        assertEquals("", LogTheme.NONE.getLevelStyle(LogLevel.ERROR));
        assertFalse(LogTheme.NONE.hasTerminalBackground());
    }

    @Test
    void defaultThemeHasNonEmptyLevelStyles(){
        assertFalse(LogTheme.DEFAULT_THEME.getLevelStyle(LogLevel.ERROR).isEmpty());
        assertFalse(LogTheme.DEFAULT_THEME.getLevelStyle(LogLevel.CRITICAL).isEmpty());
    }

    @Test
    void builtInThemesWithBackgroundReportHasBackground(){
        assertTrue(LogTheme.DRACULA.hasTerminalBackground());
        assertTrue(LogTheme.NORD.hasTerminalBackground());
        assertTrue(LogTheme.GITHUB.hasTerminalBackground());
    }

    @Test
    void getValuesContainsAllBuiltInThemes(){
        LogTheme[] values = LogTheme.getValues();
        assertEquals(14, values.length);
        assertTrue(containsByIdentity(values, LogTheme.NONE));
        assertTrue(containsByIdentity(values, LogTheme.DEFAULT_THEME));
        assertTrue(containsByIdentity(values, LogTheme.CATPPUCCIN));
    }

    @Test
    void getValuesReturnsDefensiveCopy(){
        LogTheme[] first = LogTheme.getValues();
        first[0] = null;
        LogTheme[] second = LogTheme.getValues();
        assertNotNull(second[0]);
    }

    private static boolean containsByIdentity(LogTheme[] values, LogTheme target){
        for(LogTheme value : values)
            if(value == target) return true;
        return false;
    }
}
