package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LoggerRegistryTest{

    @Test
    void newRegistryStartsEmpty(){
        LoggerRegistry registry = new LoggerRegistry();
        assertEquals(0, registry.size());
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void getLoggerCreatesDefaultLoggerWhenAbsent(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger logger = registry.getLogger("app");
        assertNotNull(logger);
        assertEquals("app", logger.getName());
        assertEquals(Logger.DEFAULT_MIN_LEVEL, logger.getMinLevel());
        assertTrue(logger.getOutputs().isEmpty());
    }

    @Test
    void getLoggerReturnsSameInstanceOnRepeatedCalls(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger first = registry.getLogger("app");
        Logger second = registry.getLogger("app");
        assertSame(first, second);
    }

    @Test
    void getLoggerWithNullNameReturnsNull(){
        LoggerRegistry registry = new LoggerRegistry();
        assertNull(registry.getLogger((String) null));
    }

    @Test
    void getLoggerWithBlankNameReturnsNull(){
        LoggerRegistry registry = new LoggerRegistry();
        assertNull(registry.getLogger("   "));
    }

    @Test
    void getLoggerByClassUsesFullyQualifiedName(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger logger = registry.getLogger(LoggerRegistryTest.class);
        assertEquals(LoggerRegistryTest.class.getName(), logger.getName());
    }

    @Test
    void getLoggerByNullClassReturnsNull(){
        LoggerRegistry registry = new LoggerRegistry();
        assertNull(registry.getLogger((Class<?>) null));
    }

    @Test
    void getLoggerByAnonymousClassReturnsNull(){
        LoggerRegistry registry = new LoggerRegistry();
        Runnable anon = new Runnable(){
            @Override
            public void run(){}
        };
        assertNull(registry.getLogger(anon.getClass()));
    }

    @Test
    void registerStoresLoggerUnderItsOwnName(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger logger = Logger.builder("custom").build();
        registry.register(logger);
        assertSame(logger, registry.getLogger("custom"));
    }

    @Test
    void registerReplacesExistingLoggerWithSameName(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger first = Logger.builder("app").build();
        Logger second = Logger.builder("app").build();
        registry.register(first);
        registry.register(second);
        assertSame(second, registry.getLogger("app"));
    }

    @Test
    void registerWithNullLoggerIsIgnored(){
        LoggerRegistry registry = new LoggerRegistry();
        assertDoesNotThrow(() -> registry.register(null));
        assertEquals(0, registry.size());
    }

    @Test
    void containsReflectsRegisteredNames(){
        LoggerRegistry registry = new LoggerRegistry();
        assertFalse(registry.contains("app"));
        registry.getLogger("app");
        assertTrue(registry.contains("app"));
    }

    @Test
    void containsWithNullOrBlankReturnsFalse(){
        LoggerRegistry registry = new LoggerRegistry();
        assertFalse(registry.contains((String) null));
        assertFalse(registry.contains("  "));
    }

    @Test
    void containsByClassChecksFullyQualifiedName(){
        LoggerRegistry registry = new LoggerRegistry();
        registry.getLogger(LoggerRegistryTest.class);
        assertTrue(registry.contains(LoggerRegistryTest.class));
    }

    @Test
    void containsByNullOrAnonymousClassReturnsFalse(){
        LoggerRegistry registry = new LoggerRegistry();
        assertFalse(registry.contains((Class<?>) null));
        Runnable anon = new Runnable(){
            @Override
            public void run(){}
        };
        assertFalse(registry.contains(anon.getClass()));
    }

    @Test
    void sizeReflectsRegisteredLoggerCount(){
        LoggerRegistry registry = new LoggerRegistry();
        registry.getLogger("a");
        registry.getLogger("b");
        assertEquals(2, registry.size());
    }

    @Test
    void getAllIsLiveView(){
        LoggerRegistry registry = new LoggerRegistry();
        registry.getLogger("a");
        var all = registry.getAll();
        assertEquals(1, all.size());
        registry.getLogger("b");
        assertEquals(2, all.size());
    }

    @Test
    void getAllIsUnmodifiable(){
        LoggerRegistry registry = new LoggerRegistry();
        assertThrows(UnsupportedOperationException.class, () -> registry.getAll().add(Logger.builder("x").build()));
    }

    @Test
    void removeReturnsAndDeletesLoggerWithoutClosingIt(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger logger = registry.getLogger("app");
        Logger removed = registry.remove("app");
        assertSame(logger, removed);
        assertFalse(registry.contains("app"));
        assertTrue(logger.isOpen());
    }

    @Test
    void removeMissingLoggerReturnsNull(){
        LoggerRegistry registry = new LoggerRegistry();
        assertNull(registry.remove("missing"));
    }

    @Test
    void removeWithNullOrBlankNameReturnsNull(){
        LoggerRegistry registry = new LoggerRegistry();
        assertNull(registry.remove((String) null));
        assertNull(registry.remove("  "));
    }

    @Test
    void removeByClassUsesFullyQualifiedName(){
        LoggerRegistry registry = new LoggerRegistry();
        registry.getLogger(LoggerRegistryTest.class);
        assertNotNull(registry.remove(LoggerRegistryTest.class));
        assertFalse(registry.contains(LoggerRegistryTest.class));
    }

    @Test
    void closeReturnsClosedWhenLoggerFoundAndClosesIt(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger logger = registry.getLogger("app");
        LoggerRegistry.LoggerCloseResult result = registry.close("app");
        assertEquals(LoggerRegistry.LoggerCloseResult.CLOSED, result);
        assertFalse(logger.isOpen());
        assertFalse(registry.contains("app"));
    }

    @Test
    void closeReturnsNotFoundWhenLoggerMissing(){
        LoggerRegistry registry = new LoggerRegistry();
        assertEquals(LoggerRegistry.LoggerCloseResult.NOT_FOUND, registry.close("missing"));
    }

    @Test
    void closeReturnsInvalidNameForNullOrBlank(){
        LoggerRegistry registry = new LoggerRegistry();
        assertEquals(LoggerRegistry.LoggerCloseResult.INVALID_NAME, registry.close((String) null));
        assertEquals(LoggerRegistry.LoggerCloseResult.INVALID_NAME, registry.close("  "));
    }

    @Test
    void closeByClassReturnsInvalidNameForNullOrAnonymousClass(){
        LoggerRegistry registry = new LoggerRegistry();
        assertEquals(LoggerRegistry.LoggerCloseResult.INVALID_NAME, registry.close((Class<?>) null));
        Runnable anon = new Runnable(){
            @Override
            public void run(){}
        };
        assertEquals(LoggerRegistry.LoggerCloseResult.INVALID_NAME, registry.close(anon.getClass()));
    }

    @Test
    void closeByClassClosesRegisteredLogger(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger logger = registry.getLogger(LoggerRegistryTest.class);
        assertEquals(LoggerRegistry.LoggerCloseResult.CLOSED, registry.close(LoggerRegistryTest.class));
        assertFalse(logger.isOpen());
    }

    @Test
    void closeAllClosesEveryLoggerAndClearsRegistry(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger a = registry.getLogger("a");
        Logger b = registry.getLogger("b");

        registry.closeAll();

        assertFalse(a.isOpen());
        assertFalse(b.isOpen());
        assertEquals(0, registry.size());
    }

    @Test
    void closeAllOnEmptyRegistryIsNoop(){
        LoggerRegistry registry = new LoggerRegistry();
        assertDoesNotThrow(registry::closeAll);
    }

    @Test
    void getInstanceReturnsSameSingletonAcrossCalls(){
        assertSame(LoggerRegistry.getInstance(), LoggerRegistry.getInstance());
    }

    @Test
    void newInstancesAreIndependentOfEachOtherAndTheSingleton(){
        LoggerRegistry a = new LoggerRegistry();
        LoggerRegistry b = new LoggerRegistry();
        a.getLogger("shared-name-for-isolation-test");
        assertFalse(b.contains("shared-name-for-isolation-test"));
        assertNotSame(a, LoggerRegistry.getInstance());
    }

    @Test
    void concurrentGetLoggerForSameNameCreatesExactlyOneLogger() throws InterruptedException{
        LoggerRegistry registry = new LoggerRegistry();
        int threadCount = 16;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger distinctInstances = new AtomicInteger();
        Logger[] seen = new Logger[threadCount];
        Thread[] threads = new Thread[threadCount];
        for(int i = 0; i < threadCount; i++){
            int idx = i;
            threads[i] = new Thread(() -> {
                ready.countDown();
                try{
                    go.await();
                }catch(InterruptedException ignored){}
                seen[idx] = registry.getLogger("concurrent-name");
            });
            threads[i].start();
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        for(Thread thread : threads) thread.join();

        Logger first = seen[0];
        for(Logger logger : seen)
            if(logger != first) distinctInstances.incrementAndGet();
        assertEquals(0, distinctInstances.get());
        assertEquals(1, registry.size());
    }
}
