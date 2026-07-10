package boraheeb.util.logging;
// -- Libraries: ----------------------------------------------------------
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
// -- AsyncLogOutputTest Class: ---------------------------------------------
class AsyncLogOutputTest{
    // -- Fields: -------------------------------------------------------------
    private final List<AsyncLogOutput> opened = new ArrayList<>();
    // -- Teardown: -------------------------------------------------------------
    @AfterEach
    void closeAll(){
        for(AsyncLogOutput output : opened) output.close();
        opened.clear();
    }
    private AsyncLogOutput track(AsyncLogOutput.Builder builder){
        AsyncLogOutput output = builder.build();
        opened.add(output);
        return output;
    }
    private static LogRecord record(String message){
        return LogRecord.builder().level(LogLevel.INFO).message(message).build();
    }
    private static LogRecord record(LogLevel level, String message){
        return LogRecord.builder().level(level).message(message).build();
    }
    private static void await(java.util.function.BooleanSupplier condition, long timeoutMs){
        long deadline = System.currentTimeMillis() + timeoutMs;
        while(System.currentTimeMillis() < deadline){
            if(condition.getAsBoolean()) return;
            try{
                Thread.sleep(20);
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertTrue(condition.getAsBoolean(), "condition not met within " + timeoutMs + "ms");
    }
    // -- Fake Delegate: -------------------------------------------------------------
    /** Local fake {@link LogOutput} used to exercise backpressure and failure paths deterministically. **/
    private static final class FakeOutput implements LogOutput{
        private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger publishCount = new AtomicInteger();
        private final AtomicInteger flushCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();
        private final AtomicInteger throwRemaining = new AtomicInteger(0);
        private volatile CountDownLatch blockLatch;
        @Override
        public void publish(LogRecord record){
            publishCount.incrementAndGet();
            CountDownLatch latch = blockLatch;
            if(latch != null){
                try{
                    latch.await();
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            int remaining = throwRemaining.getAndUpdate(v -> (v > 0)? v - 1 : v);
            if(remaining > 0) throw new RuntimeException("boom");
            records.add(record);
        }
        @Override
        public void flush(){
            flushCount.incrementAndGet();
        }
        @Override
        public void close(){
            closeCount.incrementAndGet();
        }
        @Override
        public boolean isOpen(){
            return true;
        }
        List<LogRecord> records(){
            return records;
        }
    }
    // -- Builder Validation Tests: -------------------------------------------------------------
    @Test
    void builderRejectsNullDelegate(){
        assertThrows(IllegalArgumentException.class, () -> AsyncLogOutput.builder(null));
    }
    @Test
    void builderDefaultsMatchDocumentedValues(){
        AsyncLogOutput output = track(AsyncLogOutput.builder(new FakeOutput()));
        assertEquals(LogLevel.TRACE, output.getMinLevel());
        assertNull(output.getFilter());
        assertEquals(1024, output.getQueueCapacity());
        assertEquals(AsyncLogOutput.OverflowPolicy.DROP_NEWEST, output.getOverflowPolicy());
        assertEquals(5000, output.getShutdownTimeoutMs());
        assertTrue(output.isDrainOnClose());
        assertTrue(output.isOpen());
    }
    @Test
    void queueCapacityBelowMinimumFallsBackToMinimum(){
        AsyncLogOutput output = track(AsyncLogOutput.builder(new FakeOutput()).queueCapacity(1));
        assertEquals(16, output.getQueueCapacity());
    }
    @Test
    void queueCapacityAtMinimumIsAccepted(){
        AsyncLogOutput output = track(AsyncLogOutput.builder(new FakeOutput()).queueCapacity(16));
        assertEquals(16, output.getQueueCapacity());
    }
    @Test
    void overflowPolicyNullFallsBackToDropNewest(){
        AsyncLogOutput output = track(AsyncLogOutput.builder(new FakeOutput()).overflowPolicy(null));
        assertEquals(AsyncLogOutput.OverflowPolicy.DROP_NEWEST, output.getOverflowPolicy());
    }
    @Test
    void shutdownTimeoutNegativeFallsBackToDefault(){
        AsyncLogOutput output = track(AsyncLogOutput.builder(new FakeOutput()).shutdownTimeoutMs(-1));
        assertEquals(5000, output.getShutdownTimeoutMs());
    }
    @Test
    void shutdownTimeoutZeroIsAccepted(){
        AsyncLogOutput output = track(AsyncLogOutput.builder(new FakeOutput()).shutdownTimeoutMs(0));
        assertEquals(0, output.getShutdownTimeoutMs());
    }
    @Test
    void minLevelNullFallsBackToDefault(){
        AsyncLogOutput output = track(AsyncLogOutput.builder(new FakeOutput()).minLevel(LogLevel.ERROR).minLevel(null));
        assertEquals(LogLevel.TRACE, output.getMinLevel());
    }
    @Test
    void drainOnCloseFalseIsHonored(){
        AsyncLogOutput output = track(AsyncLogOutput.builder(new FakeOutput()).drainOnClose(false));
        assertFalse(output.isDrainOnClose());
    }
    // -- Publish / Delivery Tests: -------------------------------------------------------------
    @Test
    void publishDeliversRecordToDelegate(){
        FakeOutput delegate = new FakeOutput();
        AsyncLogOutput output = track(AsyncLogOutput.builder(delegate));
        output.publish(record("hello"));
        await(() -> output.stats().getDelivered() == 1, 3000);
        assertEquals("hello", delegate.records().get(0).getMessage());
    }
    @Test
    void publishNullRecordIsIgnored(){
        FakeOutput delegate = new FakeOutput();
        AsyncLogOutput output = track(AsyncLogOutput.builder(delegate));
        assertDoesNotThrow(() -> output.publish(null));
        await(() -> output.getPendingCount() == 0 && delegate.publishCount.get() == 0, 500);
        assertEquals(0, output.stats().getAccepted());
    }
    @Test
    void publishBelowMinLevelIsDropped(){
        FakeOutput delegate = new FakeOutput();
        AsyncLogOutput output = track(AsyncLogOutput.builder(delegate).minLevel(LogLevel.WARN));
        output.publish(record(LogLevel.DEBUG, "too low"));
        output.publish(record(LogLevel.ERROR, "high enough"));
        await(() -> output.stats().getDelivered() == 1, 3000);
        assertEquals(0, output.stats().getAccepted() - 1);
        assertEquals("high enough", delegate.records().get(0).getMessage());
    }
    @Test
    void publishRejectedByFilterIsDropped(){
        FakeOutput delegate = new FakeOutput();
        LogFilter onlyA = r -> r.getMessage().startsWith("a");
        AsyncLogOutput output = track(AsyncLogOutput.builder(delegate).filter(onlyA));
        output.publish(record("bXXX"));
        output.publish(record("aXXX"));
        await(() -> output.stats().getDelivered() == 1, 3000);
        assertEquals("aXXX", delegate.records().get(0).getMessage());
    }
    @Test
    void setFilterNullAcceptsAllRecords(){
        FakeOutput delegate = new FakeOutput();
        AsyncLogOutput output = track(AsyncLogOutput.builder(delegate).filter(LogFilter.REJECT_ALL));
        output.setFilter(null);
        assertNull(output.getFilter());
        output.publish(record("through"));
        await(() -> output.stats().getDelivered() == 1, 3000);
    }
    @Test
    void setMinLevelNullIsIgnored(){
        AsyncLogOutput output = track(AsyncLogOutput.builder(new FakeOutput()).minLevel(LogLevel.WARN));
        output.setMinLevel(null);
        assertEquals(LogLevel.WARN, output.getMinLevel());
    }
    @Test
    void setMinLevelChangesActiveThreshold(){
        FakeOutput delegate = new FakeOutput();
        AsyncLogOutput output = track(AsyncLogOutput.builder(delegate).minLevel(LogLevel.ERROR));
        output.publish(record(LogLevel.INFO, "blocked"));
        output.setMinLevel(LogLevel.TRACE);
        output.publish(record(LogLevel.INFO, "allowed"));
        await(() -> output.stats().getDelivered() == 1, 3000);
        assertEquals("allowed", delegate.records().get(0).getMessage());
    }
    @Test
    void deliveryFailureIsCountedAndWorkerContinues(){
        FakeOutput delegate = new FakeOutput();
        delegate.throwRemaining.set(2);
        AsyncLogOutput output = track(AsyncLogOutput.builder(delegate));
        for(int i = 0; i < 5; i++) output.publish(record("msg-" + i));
        await(() -> output.stats().getDelivered() + output.stats().getDeliveryFailed() == 5, 3000);
        assertEquals(2, output.stats().getDeliveryFailed());
        assertEquals(3, output.stats().getDelivered());
    }
    // -- Overflow Policy Tests: -------------------------------------------------------------
    @Test
    void dropNewestPolicyDropsIncomingRecordsWhenQueueFull(){
        FakeOutput delegate = new FakeOutput();
        delegate.blockLatch = new CountDownLatch(1);
        AsyncLogOutput output = track(
            AsyncLogOutput.builder(delegate).queueCapacity(16).overflowPolicy(AsyncLogOutput.OverflowPolicy.DROP_NEWEST)
        );
        output.publish(record("blocker"));
        await(() -> delegate.publishCount.get() == 1, 3000);
        for(int i = 0; i < 20; i++) output.publish(record("msg-" + i));
        assertEquals(4, output.stats().getDropped());
        assertEquals(16, output.getPendingCount());
        delegate.blockLatch.countDown();
        await(() -> output.stats().getDelivered() == 17, 3000);
    }
    @Test
    void dropOldestPolicyEvictsOldestQueuedRecord(){
        FakeOutput delegate = new FakeOutput();
        delegate.blockLatch = new CountDownLatch(1);
        AsyncLogOutput output = track(
            AsyncLogOutput.builder(delegate).queueCapacity(16).overflowPolicy(AsyncLogOutput.OverflowPolicy.DROP_OLDEST)
        );
        output.publish(record("blocker"));
        await(() -> delegate.publishCount.get() == 1, 3000);
        for(int i = 0; i < 20; i++) output.publish(record("msg-" + i));
        assertEquals(4, output.stats().getDropped());
        assertEquals(16, output.getPendingCount());
        delegate.blockLatch.countDown();
        await(() -> output.stats().getDelivered() == 17, 3000);
        List<LogRecord> delivered = delegate.records();
        assertEquals("msg-19", delivered.get(delivered.size() - 1).getMessage());
        assertFalse(delivered.stream().anyMatch(r -> "msg-0".equals(r.getMessage())));
    }
    @Test
    void blockPolicyBlocksCallerUntilSpaceAvailable() throws InterruptedException{
        FakeOutput delegate = new FakeOutput();
        delegate.blockLatch = new CountDownLatch(1);
        AsyncLogOutput output = track(
            AsyncLogOutput.builder(delegate).queueCapacity(16).overflowPolicy(AsyncLogOutput.OverflowPolicy.BLOCK)
        );
        output.publish(record("blocker"));
        await(() -> delegate.publishCount.get() == 1, 3000);
        for(int i = 0; i < 16; i++) output.publish(record("fill-" + i));
        assertEquals(16, output.getPendingCount());
        AtomicInteger finished = new AtomicInteger(0);
        Thread publisher = new Thread(() -> {
            output.publish(record("overflow"));
            finished.set(1);
        });
        publisher.start();
        Thread.sleep(300);
        assertEquals(0, finished.get(), "publish should still be blocked waiting for queue space");
        delegate.blockLatch.countDown();
        publisher.join(5000);
        assertFalse(publisher.isAlive());
        assertEquals(1, finished.get());
        assertTrue(output.stats().getBlocked() >= 1);
    }
    // -- Stats Tests: -------------------------------------------------------------
    @Test
    void peakPendingTracksHighWaterMark(){
        FakeOutput delegate = new FakeOutput();
        delegate.blockLatch = new CountDownLatch(1);
        AsyncLogOutput output = track(AsyncLogOutput.builder(delegate).queueCapacity(16));
        output.publish(record("blocker"));
        await(() -> delegate.publishCount.get() == 1, 3000);
        for(int i = 0; i < 10; i++) output.publish(record("msg-" + i));
        assertTrue(output.stats().getPeakPending() >= 10);
        delegate.blockLatch.countDown();
        await(() -> output.stats().getDelivered() == 11, 3000);
    }
    @Test
    void getPendingCountReflectsQueueSize(){
        FakeOutput delegate = new FakeOutput();
        delegate.blockLatch = new CountDownLatch(1);
        AsyncLogOutput output = track(AsyncLogOutput.builder(delegate).queueCapacity(16));
        output.publish(record("blocker"));
        await(() -> delegate.publishCount.get() == 1, 3000);
        output.publish(record("queued-1"));
        output.publish(record("queued-2"));
        assertEquals(2, output.getPendingCount());
        delegate.blockLatch.countDown();
        await(() -> output.getPendingCount() == 0, 3000);
    }
    // -- Close Tests: -------------------------------------------------------------
    @Test
    void closeDrainsPendingRecordsByDefault(){
        FakeOutput delegate = new FakeOutput();
        AsyncLogOutput output = AsyncLogOutput.builder(delegate).build();
        for(int i = 0; i < 5; i++) output.publish(record("msg-" + i));
        output.close();
        assertEquals(5, output.stats().getDelivered());
        assertEquals(0, output.getPendingCount());
        assertTrue(delegate.closeCount.get() >= 1);
    }
    @Test
    void closeIsIdempotent(){
        AsyncLogOutput output = AsyncLogOutput.builder(new FakeOutput()).build();
        output.close();
        assertDoesNotThrow(output::close);
        assertFalse(output.isOpen());
    }
    @Test
    void publishAfterCloseIsIgnored(){
        FakeOutput delegate = new FakeOutput();
        AsyncLogOutput output = AsyncLogOutput.builder(delegate).build();
        output.close();
        output.publish(record("after-close"));
        assertEquals(0, output.stats().getAccepted());
        assertFalse(output.isOpen());
    }
    @Test
    void flushAfterCloseIsNoop(){
        AsyncLogOutput output = AsyncLogOutput.builder(new FakeOutput()).build();
        output.close();
        assertDoesNotThrow(output::flush);
    }
    @Test
    void flushDelegatesDirectlyToDelegate(){
        FakeOutput delegate = new FakeOutput();
        AsyncLogOutput output = track(AsyncLogOutput.builder(delegate));
        output.flush();
        assertTrue(delegate.flushCount.get() >= 1);
    }
    @Test
    void closeWithDrainOnCloseFalseAbandonsRemainingRecords(){
        FakeOutput delegate = new FakeOutput();
        delegate.blockLatch = new CountDownLatch(1);
        AsyncLogOutput output = AsyncLogOutput.builder(delegate)
            .queueCapacity(16)
            .drainOnClose(false)
            .shutdownTimeoutMs(500)
            .build();
        output.publish(record("blocker"));
        await(() -> delegate.publishCount.get() == 1, 3000);
        for(int i = 0; i < 4; i++) output.publish(record("queued-" + i));
        assertEquals(4, output.getPendingCount());
        output.close();
        assertEquals(4, output.getPendingCount(), "records still queued at close time should be abandoned, not drained");
        assertFalse(output.isOpen());
        delegate.blockLatch.countDown();
    }
    @Test
    void isOpenReflectsClosedState(){
        AsyncLogOutput output = AsyncLogOutput.builder(new FakeOutput()).build();
        assertTrue(output.isOpen());
        output.close();
        assertFalse(output.isOpen());
    }
}
