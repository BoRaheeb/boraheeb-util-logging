package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogRecordTest{

    @Test
    void builderDefaultsProduceSaneRecord(){
        LogRecord record = LogRecord.builder().build();
        assertEquals(LogLevel.DEFAULT_LEVEL, record.getLevel());
        assertEquals(LogRecord.DEFAULT_LOGGER_NAME, record.getLoggerName());
        assertEquals(LogRecord.DEFAULT_SOURCE_NAME, record.getSourceName());
        assertEquals(LogRecord.DEFAULT_MESSAGE, record.getMessage());
        assertFalse(record.hasLineNumber());
        assertEquals(-1, record.getLineNumber());
        assertNotNull(record.getTimestamp());
        assertNotNull(record.getThreadName());
        assertTrue(record.getFields().isEmpty());
        assertNull(record.getThrowable());
    }

    @Test
    void timestampDefaultsToNowWhenUnset(){
        Instant before = Instant.now();
        LogRecord record = LogRecord.builder().build();
        Instant after = Instant.now();
        assertFalse(record.getTimestamp().isBefore(before));
        assertFalse(record.getTimestamp().isAfter(after));
    }

    @Test
    void explicitTimestampIsPreserved(){
        Instant fixed = Instant.parse("2020-01-01T00:00:00Z");
        LogRecord record = LogRecord.builder().timestamp(fixed).build();
        assertEquals(fixed, record.getTimestamp());
    }

    @Test
    void levelNullFallsBackToDefault(){
        LogRecord record = LogRecord.builder().level(null).build();
        assertEquals(LogLevel.DEFAULT_LEVEL, record.getLevel());
    }

    @Test
    void levelIsPreservedWhenSet(){
        LogRecord record = LogRecord.builder().level(LogLevel.ERROR).build();
        assertEquals(LogLevel.ERROR, record.getLevel());
    }

    @Test
    void loggerNameNullFallsBackToDefault(){
        LogRecord record = LogRecord.builder().loggerName(null).build();
        assertEquals(LogRecord.DEFAULT_LOGGER_NAME, record.getLoggerName());
    }

    @Test
    void loggerNameBlankFallsBackToDefault(){
        LogRecord record = LogRecord.builder().loggerName("   ").build();
        assertEquals(LogRecord.DEFAULT_LOGGER_NAME, record.getLoggerName());
    }

    @Test
    void loggerNameIsTrimmed(){
        LogRecord record = LogRecord.builder().loggerName("  app  ").build();
        assertEquals("app", record.getLoggerName());
    }

    @Test
    void sourceNameNullFallsBackToDefault(){
        LogRecord record = LogRecord.builder().sourceName((String) null).build();
        assertEquals(LogRecord.DEFAULT_SOURCE_NAME, record.getSourceName());
    }

    @Test
    void sourceNameFromClassUsesSimpleName(){
        LogRecord record = LogRecord.builder().sourceName(LogRecordTest.class).build();
        assertEquals("LogRecordTest", record.getSourceName());
    }

    @Test
    void sourceNameFromNullClassFallsBackToDefault(){
        LogRecord record = LogRecord.builder().sourceName((Class<?>) null).build();
        assertEquals(LogRecord.DEFAULT_SOURCE_NAME, record.getSourceName());
    }

    @Test
    void sourceNameFromAnonymousClassFallsBackToDefault(){
        Runnable anonymous = new Runnable(){
            @Override
            public void run(){}
        };
        LogRecord record = LogRecord.builder().sourceName(anonymous.getClass()).build();
        assertEquals(LogRecord.DEFAULT_SOURCE_NAME, record.getSourceName());
    }

    @Test
    void lineNumberValidValueIsPreserved(){
        LogRecord record = LogRecord.builder().lineNumber(42).build();
        assertEquals(42, record.getLineNumber());
        assertTrue(record.hasLineNumber());
    }

    @Test
    void lineNumberZeroIsInvalidAndBecomesUnknown(){
        LogRecord record = LogRecord.builder().lineNumber(0).build();
        assertEquals(-1, record.getLineNumber());
        assertFalse(record.hasLineNumber());
    }

    @Test
    void lineNumberNegativeOtherThanSentinelBecomesUnknown(){
        LogRecord record = LogRecord.builder().lineNumber(-5).build();
        assertEquals(-1, record.getLineNumber());
    }

    @Test
    void lineNumberSentinelMinusOneIsAcceptedAsUnknown(){
        LogRecord record = LogRecord.builder().lineNumber(-1).build();
        assertEquals(-1, record.getLineNumber());
        assertFalse(record.hasLineNumber());
    }

    @Test
    void threadNameDefaultsToCurrentThreadName(){
        LogRecord record = LogRecord.builder().build();
        assertEquals(Thread.currentThread().getName(), record.getThreadName());
    }

    @Test
    void threadNameExplicitValueIsPreserved(){
        LogRecord record = LogRecord.builder().threadName("worker-1").build();
        assertEquals("worker-1", record.getThreadName());
    }

    @Test
    void threadNameBlankFallsBackToCurrentThreadName(){
        LogRecord record = LogRecord.builder().threadName("   ").build();
        assertEquals(Thread.currentThread().getName(), record.getThreadName());
    }

    @Test
    void messageNullFallsBackToDefault(){
        LogRecord record = LogRecord.builder().message(null).build();
        assertEquals(LogRecord.DEFAULT_MESSAGE, record.getMessage());
    }

    @Test
    void messageBlankFallsBackToDefault(){
        LogRecord record = LogRecord.builder().message("  ").build();
        assertEquals(LogRecord.DEFAULT_MESSAGE, record.getMessage());
    }

    @Test
    void messageIsPreservedWhenSet(){
        LogRecord record = LogRecord.builder().message("hello world").build();
        assertEquals("hello world", record.getMessage());
    }

    @Test
    void singleFieldIsStoredAndStringified(){
        LogRecord record = LogRecord.builder().field("count", 42).build();
        assertEquals("42", record.getFields().get("count"));
    }

    @Test
    void fieldWithNullValueStoresLiteralNullString(){
        LogRecord record = LogRecord.builder().field("key", null).build();
        assertEquals("null", record.getFields().get("key"));
    }

    @Test
    void fieldWithNullKeyIsIgnored(){
        LogRecord record = LogRecord.builder().field(null, "value").build();
        assertTrue(record.getFields().isEmpty());
    }

    @Test
    void fieldWithBlankKeyIsIgnored(){
        LogRecord record = LogRecord.builder().field("  ", "value").build();
        assertTrue(record.getFields().isEmpty());
    }

    @Test
    void fieldKeyIsTrimmed(){
        LogRecord record = LogRecord.builder().field("  key  ", "v").build();
        assertTrue(record.getFields().containsKey("key"));
    }

    @Test
    void fieldsMapReplacesAllPriorFields(){
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        LogRecord record = LogRecord.builder().field("stale", "x").fields(map).build();
        assertEquals(2, record.getFields().size());
        assertFalse(record.getFields().containsKey("stale"));
    }

    @Test
    void fieldsMapNullClearsAllFields(){
        LogRecord record = LogRecord.builder().field("a", "1").fields(null).build();
        assertTrue(record.getFields().isEmpty());
    }

    @Test
    void fieldsMapSkipsInvalidKeys(){
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(null, "ignored");
        map.put("", "ignored");
        map.put("ok", "kept");
        LogRecord record = LogRecord.builder().fields(map).build();
        assertEquals(1, record.getFields().size());
        assertEquals("kept", record.getFields().get("ok"));
    }

    @Test
    void fieldsMapIsUnmodifiable(){
        LogRecord record = LogRecord.builder().field("a", "1").build();
        assertThrows(UnsupportedOperationException.class, () -> record.getFields().put("b", "2"));
    }

    @Test
    void noFieldsReturnsEmptyImmutableMap(){
        LogRecord record = LogRecord.builder().build();
        assertThrows(UnsupportedOperationException.class, () -> record.getFields().put("a", "1"));
    }

    @Test
    void throwableIsPreserved(){
        RuntimeException ex = new RuntimeException("boom");
        LogRecord record = LogRecord.builder().throwable(ex).build();
        assertSame(ex, record.getThrowable());
    }

    @Test
    void throwableDefaultsToNull(){
        LogRecord record = LogRecord.builder().build();
        assertNull(record.getThrowable());
    }

    @Test
    void toStringContainsLevelLoggerAndMessage(){
        LogRecord record = LogRecord.builder().level(LogLevel.WARN).loggerName("app").message("careful").build();
        String text = record.toString();
        assertTrue(text.contains("WARN"));
        assertTrue(text.contains("app"));
        assertTrue(text.contains("careful"));
    }

    @Test
    void toBuilderCopiesAllFieldsIntoNewRecord(){
        RuntimeException ex = new RuntimeException("boom");
        LogRecord original = LogRecord.builder()
            .level(LogLevel.ERROR)
            .loggerName("app")
            .sourceName("Source")
            .lineNumber(10)
            .threadName("t1")
            .message("msg")
            .field("k", "v")
            .throwable(ex)
            .build();

        LogRecord copy = original.toBuilder().build();

        assertEquals(original.getLevel(), copy.getLevel());
        assertEquals(original.getLoggerName(), copy.getLoggerName());
        assertEquals(original.getSourceName(), copy.getSourceName());
        assertEquals(original.getLineNumber(), copy.getLineNumber());
        assertEquals(original.getThreadName(), copy.getThreadName());
        assertEquals(original.getMessage(), copy.getMessage());
        assertEquals(original.getFields(), copy.getFields());
        assertSame(original.getThrowable(), copy.getThrowable());
        assertEquals(original.getTimestamp(), copy.getTimestamp());
    }

    @Test
    void toBuilderAllowsOverridingASingleField(){
        LogRecord original = LogRecord.builder().message("first").level(LogLevel.INFO).build();
        LogRecord modified = original.toBuilder().message("second").build();

        assertEquals("second", modified.getMessage());
        assertEquals(LogLevel.INFO, modified.getLevel());
        assertEquals("first", original.getMessage());
    }

    @Test
    void toBuilderDoesNotMutateOriginalRecordWhenAddingFields(){
        LogRecord original = LogRecord.builder().field("a", "1").build();
        LogRecord.Builder derivedBuilder = original.toBuilder().field("b", "2");
        LogRecord derived = derivedBuilder.build();

        assertEquals(1, original.getFields().size());
        assertEquals(2, derived.getFields().size());
    }

    @Test
    void recordIsImmutableAcrossBuilderReuse(){
        LogRecord.Builder builder = LogRecord.builder().message("original");
        LogRecord first = builder.build();
        builder.message("changed");
        LogRecord second = builder.build();

        assertEquals("original", first.getMessage());
        assertEquals("changed", second.getMessage());
    }
}
