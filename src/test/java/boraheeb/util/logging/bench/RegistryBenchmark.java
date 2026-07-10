package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.Logger;
import boraheeb.util.logging.LoggerRegistry;
import java.util.concurrent.CountDownLatch;
// -- RegistryBenchmark Class: ---------------------------------------------
/**
* Standalone benchmark measuring {@link LoggerRegistry#getLogger(String)} throughput
* using dedicated, isolated {@code new LoggerRegistry()} instances — never the global
* singleton returned by {@link LoggerRegistry#getInstance()}, since that installs a JVM
* shutdown hook and is shared process-wide.
*
* <p>Run directly: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.RegistryBenchmark}</p>
**/
public final class RegistryBenchmark{
    // -- Constants: --------------------------------------------------------
    private static final int WARMUP_ITERATIONS = 1_000_000;
    private static final int MAIN_ITERATIONS = 10_000_000;
    private static final int THREAD_COUNT = 8;
    private static final int PER_THREAD_ITERATIONS = 1_500_000;
    private static final String STEADY_STATE_NAME = "bench.steady-state-logger";
    // -- Constructors: -----------------------------------------------------
    private RegistryBenchmark(){}
    // -- Entry Point: --------------------------------------------------------
    public static void main(String[] args) throws InterruptedException{
        BenchSupport.printHeader("RegistryBenchmark");

        benchmarkSteadyStateLookup();
        benchmarkConcurrentLookup();
        benchmarkRegister();
        benchmarkRemove();

        BenchSupport.printSection("Done");
        System.out.println();
    }
    // -- Scenarios: ----------------------------------------------------------
    private static void benchmarkSteadyStateLookup(){
        BenchSupport.printSection("Single-thread steady-state getLogger(String) — same name, already registered");
        LoggerRegistry registry = new LoggerRegistry();
        registry.getLogger(STEADY_STATE_NAME); // pre-register so all timed lookups hit the fast path

        for(int i = 0; i < WARMUP_ITERATIONS; i++)
            registry.getLogger(STEADY_STATE_NAME);

        long start = System.nanoTime();
        for(int i = 0; i < MAIN_ITERATIONS; i++)
            registry.getLogger(STEADY_STATE_NAME);
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("getLogger(String) steady-state lookups", MAIN_ITERATIONS, elapsed);
        BenchSupport.metric("Registry size after run", registry.size());
    }

    private static void benchmarkConcurrentLookup() throws InterruptedException{
        BenchSupport.printSection("Concurrent getLogger(String) — " + THREAD_COUNT + " threads, same name (contention)");
        LoggerRegistry registry = new LoggerRegistry();
        registry.getLogger(STEADY_STATE_NAME);

        Thread[] threads = new Thread[THREAD_COUNT];
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        for(int t = 0; t < THREAD_COUNT; t++){
            threads[t] = new Thread(() -> {
                readyLatch.countDown();
                try{
                    startLatch.await();
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();
                    return;
                }
                for(int i = 0; i < PER_THREAD_ITERATIONS; i++)
                    registry.getLogger(STEADY_STATE_NAME);
                doneLatch.countDown();
            }, "registry-bench-" + t);
            threads[t].start();
        }

        readyLatch.await();
        long start = System.nanoTime();
        startLatch.countDown();
        doneLatch.await();
        long elapsed = System.nanoTime() - start;

        for(Thread thread : threads) thread.join();

        long totalOps = (long) THREAD_COUNT * PER_THREAD_ITERATIONS;
        BenchSupport.metricThroughput("Concurrent getLogger(String) lookups (aggregate)", totalOps, elapsed);
        BenchSupport.metric("Threads", THREAD_COUNT);
        BenchSupport.metric("Registry size after run", registry.size());
    }

    private static void benchmarkRegister(){
        BenchSupport.printSection("register(Logger) throughput");
        LoggerRegistry registry = new LoggerRegistry();
        int warmupNames = WARMUP_ITERATIONS / 10;
        for(int i = 0; i < warmupNames; i++)
            registry.register(Logger.builder("warmup-logger-" + i).build());

        LoggerRegistry timedRegistry = new LoggerRegistry();
        int registerCount = MAIN_ITERATIONS / 10;
        Logger[] loggers = new Logger[registerCount];
        for(int i = 0; i < registerCount; i++)
            loggers[i] = Logger.builder("register-bench-logger-" + i).build();

        long start = System.nanoTime();
        for(int i = 0; i < registerCount; i++)
            timedRegistry.register(loggers[i]);
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("register(Logger) calls (distinct names)", registerCount, elapsed);
        BenchSupport.metric("Registry size after run", timedRegistry.size());
    }

    private static void benchmarkRemove(){
        BenchSupport.printSection("remove(String) throughput");
        LoggerRegistry registry = new LoggerRegistry();
        int removeCount = MAIN_ITERATIONS / 10;
        for(int i = 0; i < removeCount; i++)
            registry.register(Logger.builder("remove-bench-logger-" + i).build());

        long start = System.nanoTime();
        for(int i = 0; i < removeCount; i++)
            registry.remove("remove-bench-logger-" + i);
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("remove(String) calls", removeCount, elapsed);
        BenchSupport.metric("Registry size after run", registry.size());
    }
}
