package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.LogLevel;
import boraheeb.util.logging.Logger;
import boraheeb.util.logging.MemoryLogOutput;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
// -- LongRunningSoakTest Class: -------------------------------------------
/**
* Standalone soak test that logs continuously on multiple threads for a
* configurable duration, then reports heap usage before/after and GC pause
* time observed during the run — corresponding to the README's "300-second
* soak" claim, but rerunnable at a much shorter default duration.
*
* <p>
*   Duration is configurable via the {@code soakSeconds} system property or
*   the first CLI argument (in seconds), defaulting to a short 20 seconds so
*   this can be rerun casually. Pass a larger value (for example, 300) to
*   reproduce the README's original claim at full scale:
* </p>
* <pre>
*   java -cp target/classes;target/test-classes -DsoakSeconds=300 boraheeb.util.logging.bench.LongRunningSoakTest
*   java -cp target/classes;target/test-classes boraheeb.util.logging.bench.LongRunningSoakTest 300
* </pre>
*
* <p>Run directly: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.LongRunningSoakTest}</p>
**/
public final class LongRunningSoakTest{
    // -- Constants: --------------------------------------------------------
    private static final int DEFAULT_SOAK_SECONDS = 20;
    private static final int PRODUCER_THREADS = 8;
    private static final int MEMORY_CAPACITY = 2048;
    // -- Constructors: -----------------------------------------------------
    private LongRunningSoakTest(){}
    // -- Entry Point: --------------------------------------------------------
    public static void main(String[] args) throws Exception{
        BenchSupport.printHeader("LongRunningSoakTest");

        long soakSeconds = resolveSoakSeconds(args);
        BenchSupport.metric("Configured soak duration", soakSeconds + " s");
        BenchSupport.metric("Producer threads", PRODUCER_THREADS);
        if(soakSeconds < 300)
            BenchSupport.metric("NOTE", "shortened from README's 300s soak for rerunnability; pass a larger " +
                "soakSeconds (e.g. 300) to reproduce the original claim at full scale");

        runScenario(soakSeconds);

        BenchSupport.printSection("Done");
        System.out.println();
    }
    // -- Scenario: -----------------------------------------------------------
    private static void runScenario(long soakSeconds) throws Exception{
        MemoryLogOutput memoryOutput = MemoryLogOutput.builder()
            .capacity(MEMORY_CAPACITY)
            .minLevel(LogLevel.TRACE)
            .build();

        Logger logger = Logger.builder("LongRunningSoakTest")
            .minLevel(LogLevel.TRACE)
            .addOutput(memoryOutput)
            .build();

        BenchSupport.printSection("Warming up JIT");
        for(int i = 0; i < 200_000; i++)
            logger.info("warmup record {}", i);

        BenchSupport.settleGc();
        BenchSupport.Snapshot before = BenchSupport.Snapshot.capture();

        AtomicLong totalLogged = new AtomicLong();
        AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(PRODUCER_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(PRODUCER_THREADS);

        for(int t = 0; t < PRODUCER_THREADS; t++){
            final int threadId = t;
            pool.submit(() -> {
                try{
                    startLatch.await();
                    long local = 0;
                    while(!stop.get()){
                        logger.info("soak record thread={} seq={}", threadId, local++);
                        totalLogged.incrementAndGet();
                    }
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();
                }finally{
                    doneLatch.countDown();
                }
            });
        }

        BenchSupport.printSection("Running soak for " + soakSeconds + " seconds");
        long start = System.nanoTime();
        startLatch.countDown();
        Thread.sleep(TimeUnit.SECONDS.toMillis(soakSeconds));
        stop.set(true);
        doneLatch.await();
        long elapsed = System.nanoTime() - start;
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        int memorySizeBeforeClose = memoryOutput.size();
        logger.close();

        BenchSupport.settleGc();
        BenchSupport.Snapshot after = BenchSupport.Snapshot.capture();

        BenchSupport.printSection("Results");
        BenchSupport.metricThroughput("Total records logged", totalLogged.get(), elapsed);
        BenchSupport.metric("MemoryLogOutput size before close", memorySizeBeforeClose);
        after.printDeltaFrom(before);
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Resolves the configured soak duration in seconds: the {@code soakSeconds}
    * system property takes precedence, then the first CLI argument, then a
    * short default so the suite remains easy to rerun.
    *
    * @param args CLI arguments passed to {@code main}
    * @return the resolved soak duration in seconds, always positive
    **/
    private static long resolveSoakSeconds(String[] args){
        String property = System.getProperty("soakSeconds");
        if(property != null && !property.isBlank()){
            try{
                long parsed = Long.parseLong(property.trim());
                if(parsed > 0) return parsed;
            }catch(NumberFormatException ignore){}
        }
        if(args != null && args.length > 0){
            try{
                long parsed = Long.parseLong(args[0].trim());
                if(parsed > 0) return parsed;
            }catch(NumberFormatException ignore){}
        }
        return DEFAULT_SOAK_SECONDS;
    }
}
