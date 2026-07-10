package boraheeb.util.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LoggerTest{

    private static final class RecordingOutput implements LogOutput{
        final List<LogRecord> published = new CopyOnWriteArrayList<>();
        volatile boolean flushed = false;
        volatile boolean closed = false;

        @Override
        public void publish(LogRecord record){
            published.add(record);
        }

        @Override
        public void flush(){
            flushed = true;
        }

        @Override
        public void close(){
            closed = true;
        }

        @Override
        public boolean isOpen(){
            return !closed;
        }
    }

    private static final class ThrowingOutput implements LogOutput{
        @Override
        public void publish(LogRecord record){
            throw new RuntimeException("publish boom");
        }

        @Override
        public void flush(){
            throw new RuntimeException("flush boom");
        }

        @Override
        public void close(){
            throw new RuntimeException("close boom");
        }

        @Override
        public boolean isOpen(){
            return true;
        }
    }

    @AfterEach
    void clearMdc(){
        MDC.clear();
    }

    @Test
    void builderDefaultsNameSourceAndMinLevel(){
        Logger logger = Logger.builder("app").build();
        assertEquals("app", logger.getName());
        assertEquals("app", logger.getSourceName());
        assertEquals(Logger.DEFAULT_MIN_LEVEL, logger.getMinLevel());
        assertFalse(logger.isCaptureLocation());
        assertTrue(logger.getOutputs().isEmpty());
        assertTrue(logger.isOpen());
    }

    @Test
    void builderWithNullNameFallsBackToDefault(){
        Logger logger = Logger.builder((String) null).build();
        assertEquals(LogRecord.DEFAULT_LOGGER_NAME, logger.getName());
    }

    @Test
    void builderWithBlankNameFallsBackToDefault(){
        Logger logger = Logger.builder("   ").build();
        assertEquals(LogRecord.DEFAULT_LOGGER_NAME, logger.getName());
    }

    @Test
    void builderNameIsTrimmed(){
        Logger logger = Logger.builder("  app  ").build();
        assertEquals("app", logger.getName());
    }

    @Test
    void builderFromClassUsesFullyQualifiedName(){
        Logger logger = Logger.builder(LoggerTest.class).build();
        assertEquals(LoggerTest.class.getName(), logger.getName());
    }

    @Test
    void builderFromNullClassFallsBackToDefault(){
        Logger logger = Logger.builder((Class<?>) null).build();
        assertEquals(LogRecord.DEFAULT_LOGGER_NAME, logger.getName());
    }

    @Test
    void builderFromAnonymousClassFallsBackToDefault(){
        Runnable anon = new Runnable(){
            @Override
            public void run(){}
        };
        Logger logger = Logger.builder(anon.getClass()).build();
        assertEquals(LogRecord.DEFAULT_LOGGER_NAME, logger.getName());
    }

    @Test
    void builderSourceNameDefaultsToLoggerName(){
        Logger logger = Logger.builder("app").build();
        assertEquals("app", logger.getSourceName());
    }

    @Test
    void builderSourceNameCanBeOverridden(){
        Logger logger = Logger.builder("app").sourceName("core").build();
        assertEquals("core", logger.getSourceName());
    }

    @Test
    void builderSourceNameNullFallsBackToLoggerName(){
        Logger logger = Logger.builder("app").sourceName(null).build();
        assertEquals("app", logger.getSourceName());
    }

    @Test
    void builderMinLevelNullFallsBackToDefault(){
        Logger logger = Logger.builder("app").minLevel(null).build();
        assertEquals(Logger.DEFAULT_MIN_LEVEL, logger.getMinLevel());
    }

    @Test
    void builderAddOutputNullIsIgnored(){
        Logger logger = Logger.builder("app").addOutput(null).build();
        assertTrue(logger.getOutputs().isEmpty());
    }

    @Test
    void builderOutputsListIsUnmodifiable(){
        Logger logger = Logger.builder("app").addOutput(new RecordingOutput()).build();
        assertThrows(UnsupportedOperationException.class, () -> logger.getOutputs().add(new RecordingOutput()));
    }

    @Test
    void infoDispatchesRecordToAllOutputs(){
        RecordingOutput a = new RecordingOutput();
        RecordingOutput b = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(a).addOutput(b).build();

        logger.info("hello");

        assertEquals(1, a.published.size());
        assertEquals(1, b.published.size());
        assertEquals("hello", a.published.get(0).getMessage());
        assertEquals(LogLevel.INFO, a.published.get(0).getLevel());
    }

    @Test
    void recordCarriesLoggerNameAndSourceName(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").sourceName("core").addOutput(out).build();
        logger.warn("careful");
        LogRecord record = out.published.get(0);
        assertEquals("app", record.getLoggerName());
        assertEquals("core", record.getSourceName());
    }

    @Test
    void recordsBelowMinLevelAreNotDispatched(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").minLevel(LogLevel.WARN).addOutput(out).build();

        logger.info("filtered out");
        logger.warn("passes");

        assertEquals(1, out.published.size());
        assertEquals("passes", out.published.get(0).getMessage());
    }

    @Test
    void isEnabledReflectsMinLevel(){
        Logger logger = Logger.builder("app").minLevel(LogLevel.WARN).build();
        assertFalse(logger.isEnabled(LogLevel.INFO));
        assertTrue(logger.isEnabled(LogLevel.WARN));
        assertTrue(logger.isEnabled(LogLevel.ERROR));
    }

    @Test
    void isEnabledWithNullLevelReturnsFalse(){
        Logger logger = Logger.builder("app").build();
        assertFalse(logger.isEnabled(null));
    }

    @Test
    void isEnabledReturnsFalseAfterClose(){
        Logger logger = Logger.builder("app").build();
        logger.close();
        assertFalse(logger.isEnabled(LogLevel.CRITICAL));
    }

    @Test
    void setMinLevelChangesFilteringAtRuntime(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").minLevel(LogLevel.ERROR).addOutput(out).build();
        logger.warn("filtered");
        logger.setMinLevel(LogLevel.TRACE);
        logger.warn("passes now");
        assertEquals(1, out.published.size());
        assertEquals("passes now", out.published.get(0).getMessage());
    }

    @Test
    void setMinLevelWithNullIsIgnored(){
        Logger logger = Logger.builder("app").minLevel(LogLevel.WARN).build();
        logger.setMinLevel(null);
        assertEquals(LogLevel.WARN, logger.getMinLevel());
    }

    @Test
    void allSeverityShorthandsDispatchCorrectLevel(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();

        logger.trace("t");
        logger.debug("d");
        logger.info("i");
        logger.warn("w");
        logger.error("e");
        logger.critical("c");

        assertEquals(6, out.published.size());
        assertEquals(LogLevel.TRACE, out.published.get(0).getLevel());
        assertEquals(LogLevel.DEBUG, out.published.get(1).getLevel());
        assertEquals(LogLevel.INFO, out.published.get(2).getLevel());
        assertEquals(LogLevel.WARN, out.published.get(3).getLevel());
        assertEquals(LogLevel.ERROR, out.published.get(4).getLevel());
        assertEquals(LogLevel.CRITICAL, out.published.get(5).getLevel());
    }

    @Test
    void shorthandWithThrowableAttachesThrowable(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();
        RuntimeException ex = new RuntimeException("boom");

        logger.error("failed", ex);

        assertSame(ex, out.published.get(0).getThrowable());
    }

    @Test
    void parameterizedMessageSubstitutesPlaceholdersInOrder(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();

        logger.info("user {} did {} in {}ms", "alice", "login", 42);

        assertEquals("user alice did login in 42ms", out.published.get(0).getMessage());
    }

    @Test
    void parameterizedMessageWithTrailingThrowableAttachesItInsteadOfSubstituting(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();
        RuntimeException ex = new RuntimeException("boom");

        logger.error("request {} failed", "REQ-1", ex);

        LogRecord record = out.published.get(0);
        assertEquals("request REQ-1 failed", record.getMessage());
        assertSame(ex, record.getThrowable());
    }

    @Test
    void parameterizedMessageDoesNotSubstituteWhenLevelIsFiltered(){
        RecordingOutput out = new RecordingOutput();
        AtomicInteger toStringCalls = new AtomicInteger();
        Object expensiveArg = new Object(){
            @Override
            public String toString(){
                toStringCalls.incrementAndGet();
                return "expensive";
            }
        };
        Logger logger = Logger.builder("app").minLevel(LogLevel.ERROR).addOutput(out).build();

        logger.info("value is {}", expensiveArg);

        assertTrue(out.published.isEmpty());
        assertEquals(0, toStringCalls.get());
    }

    @Test
    void parameterizedMessageSurplusPlaceholdersAreLeftAsIs(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();

        logger.info("{} and {}", "only-one");

        assertEquals("only-one and {}", out.published.get(0).getMessage());
    }

    @Test
    void logWithLevelNullFallsBackToDefaultLevel(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();

        logger.log(null, "message");

        assertEquals(LogLevel.DEFAULT_LEVEL, out.published.get(0).getLevel());
    }

    @Test
    void logWithBlankMessageFallsBackToDefaultMessage(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();

        logger.log(LogLevel.INFO, "   ");

        assertEquals(LogRecord.DEFAULT_MESSAGE, out.published.get(0).getMessage());
    }

    @Test
    void logRecordDispatchesPrebuiltRecordAsIs(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("differentLogger").addOutput(out).build();
        LogRecord prebuilt = LogRecord.builder().loggerName("originalName").level(LogLevel.WARN).message("as-is").build();

        logger.log(prebuilt);

        assertSame(prebuilt, out.published.get(0));
        assertEquals("originalName", out.published.get(0).getLoggerName());
    }

    @Test
    void logRecordNullIsIgnored(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();
        assertDoesNotThrow(() -> logger.log((LogRecord) null));
        assertTrue(out.published.isEmpty());
    }

    @Test
    void logRecordBelowMinLevelIsDropped(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").minLevel(LogLevel.ERROR).addOutput(out).build();
        LogRecord prebuilt = LogRecord.builder().level(LogLevel.INFO).build();

        logger.log(prebuilt);

        assertTrue(out.published.isEmpty());
    }

    @Test
    void mdcFieldsAreMergedIntoDispatchedRecords(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();
        MDC.put("requestId", "REQ-1");
        try{
            logger.info("payment started");
        }finally{
            MDC.clear();
        }
        assertEquals("REQ-1", out.published.get(0).getFields().get("requestId"));
    }

    @Test
    void logRecordCallDoesNotMergeMdcAutomatically(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();
        MDC.put("requestId", "REQ-1");
        try{
            logger.log(LogRecord.builder().level(LogLevel.INFO).message("prebuilt").build());
        }finally{
            MDC.clear();
        }
        assertTrue(out.published.get(0).getFields().isEmpty());
    }

    @Test
    void captureLocationRecordsCallerClassAndLine(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").captureLocation(true).addOutput(out).build();

        logger.info("here");

        LogRecord record = out.published.get(0);
        assertEquals(LoggerTest.class.getName(), record.getSourceName());
        assertTrue(record.hasLineNumber());
    }

    @Test
    void captureLocationDisabledByDefaultKeepsConfiguredSourceName(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").sourceName("core").addOutput(out).build();

        logger.info("here");

        assertEquals("core", out.published.get(0).getSourceName());
        assertFalse(out.published.get(0).hasLineNumber());
    }

    @Test
    void closeMarksLoggerClosedAndClosesOutputs(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();

        logger.close();

        assertFalse(logger.isOpen());
        assertTrue(out.closed);
    }

    @Test
    void closeIsIdempotent(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();

        logger.close();
        assertDoesNotThrow(logger::close);
    }

    @Test
    void logCallsAfterCloseAreSilentlyIgnored(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();
        logger.close();

        logger.info("should not be dispatched");

        assertTrue(out.published.isEmpty());
    }

    @Test
    void flushForwardsToAllOutputs(){
        RecordingOutput a = new RecordingOutput();
        RecordingOutput b = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(a).addOutput(b).build();

        logger.flush();

        assertTrue(a.flushed);
        assertTrue(b.flushed);
    }

    @Test
    void flushAfterCloseIsNoop(){
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();
        logger.close();
        out.flushed = false;

        logger.flush();

        assertFalse(out.flushed);
    }

    @Test
    void throwingOutputDuringPublishDoesNotStopOtherOutputs(){
        RecordingOutput good = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(new ThrowingOutput()).addOutput(good).build();

        assertDoesNotThrow(() -> logger.info("still reaches good output"));

        assertEquals(1, good.published.size());
    }

    @Test
    void throwingOutputDuringFlushDoesNotStopOtherOutputs(){
        RecordingOutput good = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(new ThrowingOutput()).addOutput(good).build();

        assertDoesNotThrow(logger::flush);

        assertTrue(good.flushed);
    }

    @Test
    void throwingOutputDuringCloseDoesNotStopOtherOutputsFromClosing(){
        RecordingOutput good = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(new ThrowingOutput()).addOutput(good).build();

        assertDoesNotThrow(logger::close);

        assertTrue(good.closed);
    }

    @Test
    void concurrentLoggingFromMultipleThreadsDoesNotLoseOrCorruptRecords() throws InterruptedException{
        RecordingOutput out = new RecordingOutput();
        Logger logger = Logger.builder("app").addOutput(out).build();
        int threadCount = 8;
        int perThread = 200;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>();
        for(int t = 0; t < threadCount; t++){
            int threadId = t;
            Thread thread = new Thread(() -> {
                try{
                    for(int i = 0; i < perThread; i++)
                        logger.info("t{}-{}", threadId, i);
                }finally{
                    latch.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        for(Thread thread : threads) thread.join();

        assertEquals(threadCount * perThread, out.published.size());
    }
}
