package boraheeb.util.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MDCTest{

    @AfterEach
    void clearContext(){
        MDC.clear();
    }

    @Test
    void putAndGetRoundTrip(){
        MDC.put("requestId", "REQ-1");
        assertEquals("REQ-1", MDC.get("requestId"));
    }

    @Test
    void getMissingKeyReturnsNull(){
        assertNull(MDC.get("missing"));
    }

    @Test
    void getWithNullKeyReturnsNull(){
        assertNull(MDC.get(null));
    }

    @Test
    void getWithBlankKeyReturnsNull(){
        assertNull(MDC.get("   "));
    }

    @Test
    void putWithNullKeyIsIgnored(){
        MDC.put(null, "value");
        assertTrue(MDC.isEmpty());
    }

    @Test
    void putWithBlankKeyIsIgnored(){
        MDC.put("   ", "value");
        assertTrue(MDC.isEmpty());
    }

    @Test
    void putWithNullValueStoresLiteralNullString(){
        MDC.put("key", null);
        assertEquals("null", MDC.get("key"));
    }

    @Test
    void putKeyIsTrimmed(){
        MDC.put("  key  ", "v");
        assertEquals("v", MDC.get("key"));
    }

    @Test
    void putReplacesExistingValue(){
        MDC.put("k", "first");
        MDC.put("k", "second");
        assertEquals("second", MDC.get("k"));
    }

    @Test
    void containsKeyReflectsPresence(){
        assertFalse(MDC.containsKey("k"));
        MDC.put("k", "v");
        assertTrue(MDC.containsKey("k"));
    }

    @Test
    void containsKeyWithNullOrBlankReturnsFalse(){
        assertFalse(MDC.containsKey(null));
        assertFalse(MDC.containsKey("  "));
    }

    @Test
    void removeDeletesEntry(){
        MDC.put("k", "v");
        MDC.remove("k");
        assertFalse(MDC.containsKey("k"));
    }

    @Test
    void removeMissingKeyIsNoop(){
        assertDoesNotThrow(() -> MDC.remove("missing"));
    }

    @Test
    void removeWithNullOrBlankKeyIsIgnored(){
        MDC.put("k", "v");
        MDC.remove(null);
        MDC.remove("  ");
        assertTrue(MDC.containsKey("k"));
    }

    @Test
    void clearRemovesAllEntries(){
        MDC.put("a", "1");
        MDC.put("b", "2");
        MDC.clear();
        assertTrue(MDC.isEmpty());
        assertEquals(0, MDC.size());
    }

    @Test
    void sizeReflectsEntryCount(){
        assertEquals(0, MDC.size());
        MDC.put("a", "1");
        MDC.put("b", "2");
        assertEquals(2, MDC.size());
    }

    @Test
    void getContextReturnsSnapshotThatDoesNotReflectFutureChanges(){
        MDC.put("a", "1");
        Map<String, String> snapshot = MDC.getContext();
        MDC.put("b", "2");
        assertEquals(1, snapshot.size());
        assertFalse(snapshot.containsKey("b"));
    }

    @Test
    void getContextIsUnmodifiable(){
        MDC.put("a", "1");
        Map<String, String> snapshot = MDC.getContext();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", "y"));
    }

    @Test
    void setContextReplacesExistingEntries(){
        MDC.put("stale", "x");
        MDC.setContext(Map.of("fresh", "y"));
        assertFalse(MDC.containsKey("stale"));
        assertEquals("y", MDC.get("fresh"));
    }

    @Test
    void setContextWithNullClearsContext(){
        MDC.put("a", "1");
        MDC.setContext(null);
        assertTrue(MDC.isEmpty());
    }

    @Test
    void setContextSkipsInvalidKeys(){
        Map<String, String> input = new java.util.LinkedHashMap<>();
        input.put("ok", "1");
        input.put("", "ignored");
        MDC.setContext(input);
        assertEquals(1, MDC.size());
        assertTrue(MDC.containsKey("ok"));
    }

    @Test
    void setContextWithNullValueStoresLiteralNullString(){
        Map<String, String> input = new java.util.HashMap<>();
        input.put("k", null);
        MDC.setContext(input);
        assertEquals("null", MDC.get("k"));
    }

    @Test
    void getContextThenSetContextRoundTrips(){
        MDC.put("a", "1");
        MDC.put("b", "2");
        Map<String, String> saved = MDC.getContext();
        MDC.clear();
        MDC.setContext(saved);
        assertEquals(saved, MDC.getContext());
    }

    @Test
    void contextIsIsolatedPerThread() throws Exception{
        MDC.put("main", "value");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try{
            Future<Boolean> future = executor.submit(() -> !MDC.containsKey("main"));
            assertTrue(future.get(5, TimeUnit.SECONDS));
        }finally{
            executor.shutdown();
        }
    }

    @Test
    void wrapRunnableCarriesCapturedContextToWorkerThread() throws Exception{
        MDC.put("requestId", "REQ-99");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<String> seen = new AtomicReference<>();
        try{
            Runnable task = MDC.wrap(() -> seen.set(MDC.get("requestId")));
            executor.submit(task).get(5, TimeUnit.SECONDS);
        }finally{
            executor.shutdown();
        }
        assertEquals("REQ-99", seen.get());
    }

    @Test
    void wrapRunnableRestoresWorkerThreadPreviousContextAfterward() throws Exception{
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try{
            executor.submit(() -> MDC.put("workerOwn", "keepme")).get(5, TimeUnit.SECONDS);

            MDC.put("captured", "fromMain");
            Runnable task = MDC.wrap(() -> {});
            executor.submit(task).get(5, TimeUnit.SECONDS);

            Future<Boolean> check = executor.submit(() -> "keepme".equals(MDC.get("workerOwn")) && !MDC.containsKey("captured"));
            assertTrue(check.get(5, TimeUnit.SECONDS));
        }finally{
            executor.shutdown();
        }
    }

    @Test
    void wrapRunnableCapturesContextEagerlyAtCallTime() throws Exception{
        MDC.put("k", "original");
        Runnable task = MDC.wrap(() -> {});
        MDC.put("k", "changedAfterWrap");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<String> seen = new AtomicReference<>();
        try{
            Runnable inner = MDC.wrap(() -> seen.set(MDC.get("k")));
            executor.submit(inner).get(5, TimeUnit.SECONDS);
        }finally{
            executor.shutdown();
        }
        assertEquals("changedAfterWrap", seen.get());
    }

    @Test
    void wrapRunnableWithNullTaskReturnsNoOp(){
        Runnable task = MDC.wrap((Runnable) null);
        assertDoesNotThrow(task::run);
    }

    @Test
    void wrapCallablePreservesReturnValue() throws Exception{
        MDC.put("k", "v");
        Callable<String> task = MDC.wrap(() -> MDC.get("k"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try{
            Future<String> future = executor.submit(task);
            assertEquals("v", future.get(5, TimeUnit.SECONDS));
        }finally{
            executor.shutdown();
        }
    }

    @Test
    void wrapCallableWithNullTaskYieldsNull() throws Exception{
        Callable<String> task = MDC.wrap((Callable<String>) null);
        assertNull(task.call());
    }

    @Test
    void wrapCallablePropagatesCheckedException(){
        Callable<String> task = MDC.wrap(() -> {
            throw new java.io.IOException("boom");
        });
        assertThrows(java.io.IOException.class, task::call);
    }
}
