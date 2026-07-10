package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TextLogFormatterTest{

    private static final char ESC_CHAR = 0x1B;

    private static LogRecord.Builder baseRecord(){
        return LogRecord.builder()
            .level(LogLevel.INFO)
            .loggerName("my.logger")
            .sourceName("MyClass.java")
            .lineNumber(42)
            .message("hello world")
            .timestamp(Instant.parse("2026-01-13T20:45:12.000Z"));
    }

    private static boolean containsEsc(String text){
        return text.indexOf(ESC_CHAR) >= 0;
    }

    @Test
    void formatNullRecordReturnsEmptyString(){
        assertEquals("", TextLogFormatter.DEFAULT.format(null));
    }

    @Test
    void plainPresetEmitsNoAnsiEscapeCodes(){
        LogRecord record = baseRecord().build();
        String result = TextLogFormatter.PLAIN.format(record);
        assertFalse(containsEsc(result));
    }

    @Test
    void defaultPresetEmitsAnsiEscapeCodes(){
        LogRecord record = baseRecord().build();
        String result = TextLogFormatter.DEFAULT.format(record);
        assertTrue(containsEsc(result));
    }

    @Test
    void minimalPresetShowsOnlyLevelAndMessage(){
        LogRecord record = baseRecord().build();
        String result = TextLogFormatter.MINIMAL.format(record);
        assertEquals("> [INFO] hello world", result);
        assertFalse(containsEsc(result));
    }

    @Test
    void compactPresetShowsTimestampLevelAndMessageOnly(){
        LogRecord record = baseRecord().build();
        String result = TextLogFormatter.COMPACT.format(record);
        assertTrue(result.contains("INFO"));
        assertTrue(result.contains("hello world"));
        assertFalse(result.contains("my.logger"));
        assertFalse(result.contains("MyClass.java"));
    }

    @Test
    void fullPresetShowsSourceNameLineNumberAndThreadName(){
        LogRecord record = baseRecord().build();
        String result = TextLogFormatter.FULL.format(record);
        assertTrue(result.contains("MyClass.java"));
        assertTrue(result.contains("42"));
        assertTrue(result.contains(Thread.currentThread().getName()));
    }

    @Test
    void defaultBuilderHidesSourceLineAndThreadByDefault(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).build();
        LogRecord record = baseRecord().build();
        String result = formatter.format(record);
        assertFalse(result.contains("MyClass.java"));
        assertFalse(result.contains(Thread.currentThread().getName()));
    }

    @Test
    void showSourceNameTrueIncludesSourceName(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).showSourceName(true).build();
        String result = formatter.format(baseRecord().build());
        assertTrue(result.contains("MyClass.java"));
    }

    @Test
    void showLineNumberTrueWithoutSourceNameShowsBareLineNumber(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).showLineNumber(true).build();
        String result = formatter.format(baseRecord().build());
        assertTrue(result.contains("[42]"));
    }

    @Test
    void showSourceNameAndLineNumberTogetherAreColonSeparated(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE)
            .showSourceName(true).showLineNumber(true).build();
        String result = formatter.format(baseRecord().build());
        assertTrue(result.contains("[MyClass.java:42]"));
    }

    @Test
    void showLineNumberOmittedWhenLineNumberUnknown(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).showLineNumber(true).build();
        LogRecord record = baseRecord().lineNumber(-1).build();
        String result = formatter.format(record);
        assertFalse(result.contains("42"));
    }

    @Test
    void showThreadNameTrueIncludesThreadName(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).showThreadName(true).build();
        String result = formatter.format(baseRecord().build());
        assertTrue(result.contains(Thread.currentThread().getName()));
    }

    @Test
    void showMessageFalseHidesMessage(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).showMessage(false).build();
        String result = formatter.format(baseRecord().build());
        assertFalse(result.contains("hello world"));
    }

    @Test
    void showFieldsTrueAppendsFieldsOnNewLine(){
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userId", "9021");
        fields.put("role", "admin");
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).build();
        LogRecord record = baseRecord().fields(fields).build();
        String result = formatter.format(record);
        assertTrue(result.contains("userId=9021, role=admin"));
        assertTrue(result.contains("\n"));
    }

    @Test
    void showFieldsFalseOmitsFields(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).showFields(false).build();
        LogRecord record = baseRecord().field("a", "b").build();
        String result = formatter.format(record);
        assertFalse(result.contains("a=b"));
    }

    @Test
    void showThrowableTrueAppendsStackTrace(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).build();
        LogRecord record = baseRecord().throwable(new RuntimeException("boom")).build();
        String result = formatter.format(record);
        assertTrue(result.contains("java.lang.RuntimeException: boom"));
    }

    @Test
    void showThrowableFalseOmitsStackTrace(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).showThrowable(false).build();
        LogRecord record = baseRecord().throwable(new RuntimeException("boom")).build();
        String result = formatter.format(record);
        assertFalse(result.contains("java.lang.RuntimeException"));
    }

    @Test
    void controlCharsInMessageAreEscapedByDefault(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).build();
        LogRecord record = baseRecord().message("line1\nline2").build();
        String result = formatter.format(record);
        assertTrue(result.contains("line1\\nline2"));
        assertEquals(1, result.lines().count());
    }

    @Test
    void controlCharsInLoggerNameAreEscapedByDefault(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).build();
        LogRecord record = baseRecord().loggerName("evil\nlogger").build();
        String result = formatter.format(record);
        assertTrue(result.contains("evil\\nlogger"));
    }

    @Test
    void escapeControlCharsFalseWritesVerbatim(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).escapeControlChars(false).build();
        LogRecord record = baseRecord().message("line1\nline2").build();
        String result = formatter.format(record);
        assertTrue(result.contains("line1\nline2"));
    }

    @Test
    void throwableStackTraceIsNeverEscapedEvenWhenEscapingEnabled(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).build();
        LogRecord record = baseRecord().throwable(new RuntimeException("boom")).build();
        String result = formatter.format(record);
        assertTrue(result.contains("\tat"));
    }

    @Test
    void terminalBackgroundReturnsNullForThemeWithoutBackground(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).build();
        assertNull(formatter.terminalBackground());
    }

    @Test
    void terminalBackgroundReturnsConfiguredBackground(){
        LogTheme theme = LogTheme.builder().terminalBackground(Ansi.BG_BLUE).build();
        TextLogFormatter formatter = TextLogFormatter.builder().theme(theme).build();
        assertEquals(Ansi.BG_BLUE, formatter.terminalBackground());
    }

    @Test
    void appendStyledReappliesTerminalBackgroundAfterReset(){
        LogTheme theme = LogTheme.builder()
            .messageStyle(Ansi.FG_RED)
            .terminalBackground(Ansi.BG_BLUE)
            .build();
        TextLogFormatter formatter = TextLogFormatter.builder().theme(theme).showTimestamp(false)
            .showLevel(false).showLoggerName(false).build();
        String result = formatter.format(baseRecord().build());
        assertTrue(result.contains(Ansi.RESET + Ansi.BG_BLUE));
    }

    @Test
    void setDateTimeNullIsIgnored(){
        TextLogFormatter formatter = TextLogFormatter.builder().build();
        LogDateTime original = formatter.getDateTime();
        formatter.setDateTime(null);
        assertSame(original, formatter.getDateTime());
    }

    @Test
    void setDateTimeReplacesFormatterAtRuntime(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).build();
        LogDateTime custom = LogDateTime.ofPattern("yyyy", Locale.ENGLISH, ZoneOffset.UTC);
        formatter.setDateTime(custom);
        String result = formatter.format(baseRecord().build());
        assertTrue(result.contains("[2026]"));
    }

    @Test
    void setThemeNullIsIgnored(){
        TextLogFormatter formatter = TextLogFormatter.builder().build();
        LogTheme original = formatter.getTheme();
        formatter.setTheme(null);
        assertSame(original, formatter.getTheme());
    }

    @Test
    void setThemeReplacesThemeAtRuntime(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.DEFAULT_THEME).build();
        formatter.setTheme(LogTheme.NONE);
        assertSame(LogTheme.NONE, formatter.getTheme());
        String result = formatter.format(baseRecord().build());
        assertFalse(containsEsc(result));
    }

    @Test
    void builderThemeNullFallsBackToDefaultTheme(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(null).build();
        assertSame(LogTheme.DEFAULT_THEME, formatter.getTheme());
    }

    @Test
    void toBuilderPreservesAllValues(){
        TextLogFormatter original = TextLogFormatter.builder()
            .showTimestamp(false)
            .showLevel(false)
            .showLoggerName(false)
            .showSourceName(true)
            .showLineNumber(true)
            .showThreadName(true)
            .showMessage(false)
            .showFields(false)
            .showThrowable(false)
            .theme(LogTheme.NONE)
            .escapeControlChars(false)
            .build();
        TextLogFormatter copy = original.toBuilder().build();
        assertFalse(copy.isShowTimestamp());
        assertFalse(copy.isShowLevel());
        assertFalse(copy.isShowLoggerName());
        assertTrue(copy.isShowSourceName());
        assertTrue(copy.isShowLineNumber());
        assertTrue(copy.isShowThreadName());
        assertFalse(copy.isShowMessage());
        assertFalse(copy.isShowFields());
        assertFalse(copy.isShowThrowable());
        assertSame(LogTheme.NONE, copy.getTheme());
        assertFalse(copy.isEscapeControlChars());
    }

    @Test
    void mutatorsToggleShowFlags(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).build();
        formatter.setShowTimestamp(false);
        formatter.setShowLevel(false);
        formatter.setShowLoggerName(false);
        formatter.setShowSourceName(true);
        formatter.setShowLineNumber(true);
        formatter.setShowThreadName(true);
        formatter.setShowMessage(false);
        formatter.setShowFields(false);
        formatter.setShowThrowable(false);
        formatter.setEscapeControlChars(false);
        assertFalse(formatter.isShowTimestamp());
        assertFalse(formatter.isShowLevel());
        assertFalse(formatter.isShowLoggerName());
        assertTrue(formatter.isShowSourceName());
        assertTrue(formatter.isShowLineNumber());
        assertTrue(formatter.isShowThreadName());
        assertFalse(formatter.isShowMessage());
        assertFalse(formatter.isShowFields());
        assertFalse(formatter.isShowThrowable());
        assertFalse(formatter.isEscapeControlChars());
    }

    @Test
    void toStringContainsThemeName(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.DRACULA).build();
        assertTrue(formatter.toString().contains("Dracula"));
    }

    @Test
    void formatStartsWithPromptPrefix(){
        TextLogFormatter formatter = TextLogFormatter.builder().theme(LogTheme.NONE).build();
        String result = formatter.format(baseRecord().build());
        assertTrue(result.startsWith("> "));
    }
}
