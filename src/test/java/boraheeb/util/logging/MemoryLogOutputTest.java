package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MemoryLogOutputTest{

    private LogRecord record(String message){
        return LogRecord.builder().level(LogLevel.INFO).message(message).build();
    }

    private static final class RecordingOutput implements LogOutput{
        final List<LogRecord> published = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record){
            published.add(record);
        }

        @Override
        public void flush(){}

        @Override
        public void close(){}

        @Override
        public boolean isOpen(){
            return true;
        }
    }

    @Test
    void builderDefaultsHaveCapacity512(){
        MemoryLogOutput output = MemoryLogOutput.builder().build();
        assertEquals(512, output.getCapacity());
        assertEquals(LogLevel.TRACE, output.getMinLevel());
        assertTrue(output.isEmpty());
        output.close();
    }

    @Test
    void publishRetainsRecordInSnapshot(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(10).build();
        output.publish(record("hello"));
        assertEquals(1, output.size());
        assertEquals("hello", output.snapshot().get(0).getMessage());
        output.close();
    }

    @Test
    void snapshotOrderIsOldestToNewest(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(10).build();
        output.publish(record("a"));
        output.publish(record("b"));
        output.publish(record("c"));

        List<LogRecord> snapshot = output.snapshot();
        assertEquals(List.of("a", "b", "c"), snapshot.stream().map(LogRecord::getMessage).toList());
        output.close();
    }

    @Test
    void evictsOldestRecordWhenCapacityExceeded(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(3).build();
        output.publish(record("a"));
        output.publish(record("b"));
        output.publish(record("c"));
        output.publish(record("d"));

        List<LogRecord> snapshot = output.snapshot();
        assertEquals(3, snapshot.size());
        assertEquals(List.of("b", "c", "d"), snapshot.stream().map(LogRecord::getMessage).toList());
        output.close();
    }

    @Test
    void capacityOneKeepsOnlyLatestRecord(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(1).build();
        output.publish(record("a"));
        output.publish(record("b"));

        assertEquals(1, output.size());
        assertEquals("b", output.snapshot().get(0).getMessage());
        output.close();
    }

    @Test
    void capacityBelowMinimumFallsBackToOne(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(0).build();
        assertEquals(1, output.getCapacity());
        output.close();
    }

    @Test
    void capacityNegativeFallsBackToOne(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(-5).build();
        assertEquals(1, output.getCapacity());
        output.close();
    }

    @Test
    void recordsBelowMinLevelAreDropped(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(10).minLevel(LogLevel.ERROR).build();
        output.publish(record("filtered"));
        assertTrue(output.isEmpty());
        output.close();
    }

    @Test
    void filterDropsNonMatchingRecords(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(10).filter(LogFilter.REJECT_ALL).build();
        output.publish(record("filtered"));
        assertTrue(output.isEmpty());
        output.close();
    }

    @Test
    void nullRecordIsIgnored(){
        MemoryLogOutput output = MemoryLogOutput.builder().build();
        assertDoesNotThrow(() -> output.publish(null));
        assertTrue(output.isEmpty());
        output.close();
    }

    @Test
    void setMinLevelWithNullIsIgnored(){
        MemoryLogOutput output = MemoryLogOutput.builder().minLevel(LogLevel.WARN).build();
        output.setMinLevel(null);
        assertEquals(LogLevel.WARN, output.getMinLevel());
        output.close();
    }

    @Test
    void clearEmptiesTheBuffer(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(10).build();
        output.publish(record("a"));
        output.clear();
        assertTrue(output.isEmpty());
        assertEquals(0, output.size());
        output.close();
    }

    @Test
    void snapshotIsUnmodifiable(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(10).build();
        output.publish(record("a"));
        assertThrows(UnsupportedOperationException.class, () -> output.snapshot().add(record("b")));
        output.close();
    }

    @Test
    void snapshotIsACopyNotALiveView(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(10).build();
        output.publish(record("a"));
        List<LogRecord> snapshot = output.snapshot();
        output.publish(record("b"));
        assertEquals(1, snapshot.size());
    }

    @Test
    void dumpToForwardsSnapshotWithoutClearingBuffer(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(10).build();
        output.publish(record("a"));
        output.publish(record("b"));
        RecordingOutput target = new RecordingOutput();

        output.dumpTo(target);

        assertEquals(2, target.published.size());
        assertEquals(2, output.size());
        output.close();
    }

    @Test
    void dumpToWithNullTargetIsIgnored(){
        MemoryLogOutput output = MemoryLogOutput.builder().build();
        output.publish(record("a"));
        assertDoesNotThrow(() -> output.dumpTo(null));
        output.close();
    }

    @Test
    void flushIsNoop(){
        MemoryLogOutput output = MemoryLogOutput.builder().build();
        assertDoesNotThrow(output::flush);
        output.close();
    }

    @Test
    void closeClearsBufferAndRejectsFurtherRecords(){
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(10).build();
        output.publish(record("a"));

        output.close();

        assertTrue(output.snapshot().isEmpty());
        assertFalse(output.isOpen());
        output.publish(record("b"));
        assertTrue(output.snapshot().isEmpty());
    }

    @Test
    void closeIsIdempotent(){
        MemoryLogOutput output = MemoryLogOutput.builder().build();
        output.close();
        assertDoesNotThrow(output::close);
    }

    @Test
    void concurrentPublishesDoNotLoseTrackOfCountOrCapacity() throws InterruptedException{
        MemoryLogOutput output = MemoryLogOutput.builder().capacity(50).build();
        int threadCount = 8;
        int perThread = 100;
        Thread[] threads = new Thread[threadCount];
        for(int t = 0; t < threadCount; t++){
            threads[t] = new Thread(() -> {
                for(int i = 0; i < perThread; i++)
                    output.publish(record("x"));
            });
            threads[t].start();
        }
        for(Thread thread : threads) thread.join();

        assertEquals(50, output.size());
        assertEquals(50, output.snapshot().size());
        output.close();
    }
}
