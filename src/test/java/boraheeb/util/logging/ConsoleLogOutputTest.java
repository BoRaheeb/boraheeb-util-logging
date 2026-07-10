package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleLogOutputTest{

    private LogRecord record(LogLevel level, String message){
        return LogRecord.builder().level(level).loggerName("app").message(message).build();
    }

    @Test
    void builderDefaultsUseTextFormatterAndTraceMinLevel(){
        ConsoleLogOutput output = ConsoleLogOutput.builder().build();
        assertEquals(TextLogFormatter.DEFAULT, output.getFormatter());
        assertEquals(LogLevel.TRACE, output.getMinLevel());
        assertNull(output.getFilter());
        assertTrue(output.isOpen());
        output.close();
    }

    @Test
    void publishWritesFormattedLineToStream(){
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleLogOutput output = ConsoleLogOutput.builder()
            .stream(new PrintStream(buffer, true, StandardCharsets.UTF_8))
            .formatter(TextLogFormatter.PLAIN)
            .build();

        output.publish(record(LogLevel.INFO, "hello"));
        output.flush();

        String text = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("hello"));
        output.close();
    }

    @Test
    void recordsBelowMinLevelAreDropped(){
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleLogOutput output = ConsoleLogOutput.builder()
            .stream(new PrintStream(buffer, true, StandardCharsets.UTF_8))
            .formatter(TextLogFormatter.PLAIN)
            .minLevel(LogLevel.WARN)
            .build();

        output.publish(record(LogLevel.INFO, "filtered"));
        output.flush();

        assertEquals("", buffer.toString(StandardCharsets.UTF_8));
        output.close();
    }

    @Test
    void filterDropsNonMatchingRecords(){
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleLogOutput output = ConsoleLogOutput.builder()
            .stream(new PrintStream(buffer, true, StandardCharsets.UTF_8))
            .formatter(TextLogFormatter.PLAIN)
            .filter(LogFilter.REJECT_ALL)
            .build();

        output.publish(record(LogLevel.INFO, "filtered"));
        output.flush();

        assertEquals("", buffer.toString(StandardCharsets.UTF_8));
        output.close();
    }

    @Test
    void nullRecordIsIgnored(){
        ConsoleLogOutput output = ConsoleLogOutput.builder().build();
        assertDoesNotThrow(() -> output.publish(null));
        output.close();
    }

    @Test
    void builderNullFormatterFallsBackToDefault(){
        ConsoleLogOutput output = ConsoleLogOutput.builder().formatter(null).build();
        assertEquals(TextLogFormatter.DEFAULT, output.getFormatter());
        output.close();
    }

    @Test
    void builderNullMinLevelFallsBackToDefault(){
        ConsoleLogOutput output = ConsoleLogOutput.builder().minLevel(null).build();
        assertEquals(LogLevel.TRACE, output.getMinLevel());
        output.close();
    }

    @Test
    void setMinLevelChangesFilteringAtRuntime(){
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleLogOutput output = ConsoleLogOutput.builder()
            .stream(new PrintStream(buffer, true, StandardCharsets.UTF_8))
            .formatter(TextLogFormatter.PLAIN)
            .minLevel(LogLevel.ERROR)
            .build();

        output.publish(record(LogLevel.INFO, "first"));
        output.setMinLevel(LogLevel.TRACE);
        output.publish(record(LogLevel.INFO, "second"));
        output.flush();

        String text = buffer.toString(StandardCharsets.UTF_8);
        assertFalse(text.contains("first"));
        assertTrue(text.contains("second"));
        output.close();
    }

    @Test
    void setMinLevelWithNullIsIgnored(){
        ConsoleLogOutput output = ConsoleLogOutput.builder().minLevel(LogLevel.WARN).build();
        output.setMinLevel(null);
        assertEquals(LogLevel.WARN, output.getMinLevel());
        output.close();
    }

    @Test
    void setFilterAppliesToSubsequentPublishes(){
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleLogOutput output = ConsoleLogOutput.builder()
            .stream(new PrintStream(buffer, true, StandardCharsets.UTF_8))
            .formatter(TextLogFormatter.PLAIN)
            .build();

        output.setFilter(LogFilter.REJECT_ALL);
        output.publish(record(LogLevel.INFO, "dropped"));
        output.flush();

        assertEquals("", buffer.toString(StandardCharsets.UTF_8));
        output.close();
    }

    @Test
    void closeIsIdempotentAndDoesNotCloseUnderlyingStream(){
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        ConsoleLogOutput output = ConsoleLogOutput.builder().stream(stream).build();

        output.close();
        assertDoesNotThrow(output::close);
        assertFalse(output.isOpen());
        assertDoesNotThrow(() -> stream.println("still usable"));
    }

    @Test
    void publishAfterCloseIsIgnored(){
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleLogOutput output = ConsoleLogOutput.builder()
            .stream(new PrintStream(buffer, true, StandardCharsets.UTF_8))
            .formatter(TextLogFormatter.PLAIN)
            .build();
        output.close();

        output.publish(record(LogLevel.INFO, "after close"));

        assertEquals("", buffer.toString(StandardCharsets.UTF_8));
    }

    @Test
    void flushAfterCloseIsNoop(){
        ConsoleLogOutput output = ConsoleLogOutput.builder().build();
        output.close();
        assertDoesNotThrow(output::flush);
    }

    @Test
    void concurrentPublishesDoNotInterleaveWithinASingleLine() throws InterruptedException{
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleLogOutput output = ConsoleLogOutput.builder()
            .stream(new PrintStream(buffer, true, StandardCharsets.UTF_8))
            .formatter(TextLogFormatter.PLAIN)
            .build();

        int threadCount = 6;
        int perThread = 100;
        Thread[] threads = new Thread[threadCount];
        for(int t = 0; t < threadCount; t++){
            threads[t] = new Thread(() -> {
                for(int i = 0; i < perThread; i++)
                    output.publish(record(LogLevel.INFO, "line"));
            });
            threads[t].start();
        }
        for(Thread thread : threads) thread.join();
        output.flush();

        long lineCount = buffer.toString(StandardCharsets.UTF_8).lines().count();
        assertEquals(threadCount * perThread, lineCount);
        output.close();
    }
}
