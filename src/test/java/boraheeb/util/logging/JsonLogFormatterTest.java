package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonLogFormatterTest{

    private static LogRecord.Builder baseRecord(){
        return LogRecord.builder()
            .level(LogLevel.INFO)
            .loggerName("my.logger")
            .sourceName("MyClass.java")
            .lineNumber(42)
            .message("hello world")
            .timestamp(Instant.parse("2026-01-13T20:45:12.000Z"));
    }

    @Test
    void formatNullRecordReturnsEmptyString(){
        assertEquals("", JsonLogFormatter.DEFAULT.format(null));
    }

    @Test
    void formatProducesWellFormedJsonObject(){
        String json = JsonLogFormatter.DEFAULT.format(baseRecord().build());
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertEquals(1, json.lines().count());
    }

    @Test
    void formatIncludesAllDefaultFields(){
        LogRecord record = baseRecord().build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertTrue(json.contains("\"level\":\"INFO\""));
        assertTrue(json.contains("\"logger\":\"my.logger\""));
        assertTrue(json.contains("\"source\":\"MyClass.java\""));
        assertTrue(json.contains("\"line\":42"));
        assertTrue(json.contains("\"message\":\"hello world\""));
        assertTrue(json.contains("\"timestamp\""));
        assertTrue(json.contains("\"thread\""));
    }

    @Test
    void formatOmitsLineNumberWhenUnknown(){
        LogRecord record = baseRecord().lineNumber(-1).build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertFalse(json.contains("\"line\""));
    }

    @Test
    void minimalPresetIncludesOnlyLevelAndMessage(){
        LogRecord record = baseRecord().build();
        String json = JsonLogFormatter.MINIMAL.format(record);
        assertTrue(json.contains("\"level\":\"INFO\""));
        assertTrue(json.contains("\"message\":\"hello world\""));
        assertFalse(json.contains("\"timestamp\""));
        assertFalse(json.contains("\"logger\""));
        assertFalse(json.contains("\"source\""));
        assertFalse(json.contains("\"line\""));
        assertFalse(json.contains("\"thread\""));
        assertFalse(json.contains("\"fields\""));
        assertFalse(json.contains("\"throwable\""));
    }

    @Test
    void formatEscapesQuotesAndBackslashes(){
        LogRecord record = baseRecord().message("say \"hi\" \\ ok").build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertTrue(json.contains("\"message\":\"say \\\"hi\\\" \\\\ ok\""));
    }

    @Test
    void formatEscapesNewlineCarriageReturnAndTab(){
        LogRecord record = baseRecord().message("line1\nline2\rline3\ttab").build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertTrue(json.contains("line1\\nline2\\rline3\\ttab"));
        assertEquals(1, json.lines().count());
    }

    @Test
    void formatEscapesControlCharacters(){
        LogRecord record = baseRecord().message("bellend").build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertTrue(json.contains("bell\\u0007end"));
    }

    @Test
    void formatPreservesUnicodeCharacters(){
        LogRecord record = baseRecord().message("héllo wörld 日本語 😀").build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertTrue(json.contains("héllo wörld 日本語 😀"));
    }

    @Test
    void formatWithEmptyMessageUsesDefaultMessageFallback(){
        LogRecord record = baseRecord().message("").build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertTrue(json.contains("\"message\":\"" + LogRecord.DEFAULT_MESSAGE + "\""));
    }

    @Test
    void formatWithoutThrowableOmitsThrowableField(){
        LogRecord record = baseRecord().build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertFalse(json.contains("\"throwable\""));
    }

    @Test
    void formatWithThrowableEmbedsStackTraceAsSingleLineString(){
        LogRecord record = baseRecord().throwable(new RuntimeException("boom")).build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertTrue(json.contains("\"throwable\""));
        assertTrue(json.contains("java.lang.RuntimeException: boom"));
        assertTrue(json.contains("\\n"));
        assertEquals(1, json.lines().count());
    }

    @Test
    void formatWithThrowableDisabledOmitsThrowableEvenWhenPresent(){
        LogRecord record = baseRecord().throwable(new RuntimeException("boom")).build();
        JsonLogFormatter formatter = JsonLogFormatter.builder().includeThrowable(false).build();
        String json = formatter.format(record);
        assertFalse(json.contains("\"throwable\""));
    }

    @Test
    void formatWithEmptyFieldsOmitsFieldsObject(){
        LogRecord record = baseRecord().build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertFalse(json.contains("\"fields\""));
    }

    @Test
    void formatWithFieldsEmbedsFieldsAsNestedObject(){
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userId", "9021");
        fields.put("role", "admin");
        LogRecord record = baseRecord().fields(fields).build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertTrue(json.contains("\"fields\":{\"userId\":\"9021\",\"role\":\"admin\"}"));
    }

    @Test
    void formatEscapesFieldKeysAndValues(){
        LogRecord record = baseRecord().field("key\"with\"quotes", "val\nue").build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        assertTrue(json.contains("\"key\\\"with\\\"quotes\":\"val\\nue\""));
    }

    @Test
    void formatIsValidJsonStructureWithBalancedBraces(){
        LogRecord record = baseRecord().field("a", "b").throwable(new RuntimeException("x")).build();
        String json = JsonLogFormatter.DEFAULT.format(record);
        int open = 0;
        for(char c : json.toCharArray()){
            if(c == '{') open++;
            if(c == '}') open--;
            assertTrue(open >= 0);
        }
        assertEquals(0, open);
    }

    @Test
    void setDateTimeNullIsIgnored(){
        JsonLogFormatter formatter = JsonLogFormatter.builder().build();
        LogDateTime original = formatter.getDateTime();
        formatter.setDateTime(null);
        assertSame(original, formatter.getDateTime());
    }

    @Test
    void setDateTimeReplacesFormatterAtRuntime(){
        JsonLogFormatter formatter = JsonLogFormatter.builder().build();
        LogDateTime custom = LogDateTime.ofPattern("yyyy", java.util.Locale.ENGLISH, ZoneOffset.UTC);
        formatter.setDateTime(custom);
        assertSame(custom, formatter.getDateTime());
        String json = formatter.format(baseRecord().build());
        assertTrue(json.contains("\"timestamp\":\"2026\""));
    }

    @Test
    void builderDateTimeNullFallsBackToDefault(){
        JsonLogFormatter formatter = JsonLogFormatter.builder().dateTime(null).build();
        assertNotNull(formatter.getDateTime());
    }

    @Test
    void toBuilderPreservesAllFlags(){
        JsonLogFormatter original = JsonLogFormatter.builder()
            .includeTimestamp(false)
            .includeLevel(false)
            .includeLoggerName(false)
            .includeSourceName(false)
            .includeLineNumber(false)
            .includeThreadName(false)
            .includeMessage(false)
            .includeFields(false)
            .includeThrowable(false)
            .build();
        JsonLogFormatter copy = original.toBuilder().build();
        assertFalse(copy.isIncludeTimestamp());
        assertFalse(copy.isIncludeLevel());
        assertFalse(copy.isIncludeLoggerName());
        assertFalse(copy.isIncludeSourceName());
        assertFalse(copy.isIncludeLineNumber());
        assertFalse(copy.isIncludeThreadName());
        assertFalse(copy.isIncludeMessage());
        assertFalse(copy.isIncludeFields());
        assertFalse(copy.isIncludeThrowable());
    }

    @Test
    void mutatorsToggleIncludeFlags(){
        JsonLogFormatter formatter = JsonLogFormatter.builder().build();
        formatter.setIncludeTimestamp(false);
        formatter.setIncludeLevel(false);
        formatter.setIncludeLoggerName(false);
        formatter.setIncludeSourceName(false);
        formatter.setIncludeLineNumber(false);
        formatter.setIncludeThreadName(false);
        formatter.setIncludeMessage(false);
        formatter.setIncludeFields(false);
        formatter.setIncludeThrowable(false);
        assertFalse(formatter.isIncludeTimestamp());
        assertFalse(formatter.isIncludeLevel());
        assertFalse(formatter.isIncludeLoggerName());
        assertFalse(formatter.isIncludeSourceName());
        assertFalse(formatter.isIncludeLineNumber());
        assertFalse(formatter.isIncludeThreadName());
        assertFalse(formatter.isIncludeMessage());
        assertFalse(formatter.isIncludeFields());
        assertFalse(formatter.isIncludeThrowable());
        assertEquals("{}", formatter.format(baseRecord().build()));
    }

    @Test
    void toStringContainsDateTimePattern(){
        JsonLogFormatter formatter = JsonLogFormatter.builder().build();
        assertTrue(formatter.toString().contains(formatter.getDateTime().getPattern()));
    }
}
