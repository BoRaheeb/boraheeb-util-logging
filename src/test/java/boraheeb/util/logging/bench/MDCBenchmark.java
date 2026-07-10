package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.MDC;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
// -- MDCBenchmark Class: ---------------------------------------------
/**
* Standalone benchmark measuring {@link MDC} put/get/remove/clear throughput,
* {@link MDC#getContext()} snapshot cost, and {@link MDC#wrap(Runnable)} overhead
* (both the wrap call itself and running a wrapped no-op task on a background thread).
*
* <p>Run directly: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.MDCBenchmark}</p>
**/
public final class MDCBenchmark{
    // -- Constants: --------------------------------------------------------
    private static final int WARMUP_ITERATIONS = 500_000;
    private static final int MAIN_ITERATIONS = 5_000_000;
    private static final int WRAP_RUN_ITERATIONS = 200_000;
    // -- Constructors: -----------------------------------------------------
    private MDCBenchmark(){}
    // -- Entry Point: --------------------------------------------------------
    public static void main(String[] args) throws InterruptedException{
        BenchSupport.printHeader("MDCBenchmark");

        MDC.clear();
        benchmarkPut();
        benchmarkGet();
        benchmarkRemove();
        benchmarkClear();
        benchmarkGetContextSnapshot();
        benchmarkWrapCreation();
        benchmarkWrapExecution();

        MDC.clear();
        BenchSupport.printSection("Done");
        System.out.println();
    }
    // -- Scenarios: ----------------------------------------------------------
    private static void benchmarkPut(){
        BenchSupport.printSection("MDC.put");
        MDC.clear();
        for(int i = 0; i < WARMUP_ITERATIONS; i++)
            MDC.put("requestId", "REQ-" + (i % 64));

        long start = System.nanoTime();
        for(int i = 0; i < MAIN_ITERATIONS; i++)
            MDC.put("requestId", "REQ-" + (i % 64));
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("MDC.put ops", MAIN_ITERATIONS, elapsed);
    }

    private static void benchmarkGet(){
        BenchSupport.printSection("MDC.get");
        MDC.clear();
        MDC.put("requestId", "REQ-8821");
        MDC.put("userId", "9021");

        for(int i = 0; i < WARMUP_ITERATIONS; i++)
            MDC.get("requestId");

        long start = System.nanoTime();
        long hits = 0;
        for(int i = 0; i < MAIN_ITERATIONS; i++)
            if(MDC.get("requestId") != null) hits++;
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("MDC.get ops", MAIN_ITERATIONS, elapsed);
        BenchSupport.metric("MDC.get hits", hits);
    }

    private static void benchmarkRemove(){
        BenchSupport.printSection("MDC.remove");
        MDC.clear();

        for(int i = 0; i < WARMUP_ITERATIONS; i++){
            MDC.put("k", "v");
            MDC.remove("k");
        }

        long start = System.nanoTime();
        for(int i = 0; i < MAIN_ITERATIONS; i++){
            MDC.put("k", "v");
            MDC.remove("k");
        }
        long elapsed = System.nanoTime() - start;

        // Counts each put+remove pair as one op for a stable, comparable rate.
        BenchSupport.metricThroughput("MDC.put+remove pairs", MAIN_ITERATIONS, elapsed);
    }

    private static void benchmarkClear(){
        BenchSupport.printSection("MDC.clear");
        for(int i = 0; i < WARMUP_ITERATIONS; i++){
            MDC.put("a", "1");
            MDC.put("b", "2");
            MDC.clear();
        }

        long start = System.nanoTime();
        for(int i = 0; i < MAIN_ITERATIONS; i++){
            MDC.put("a", "1");
            MDC.put("b", "2");
            MDC.clear();
        }
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("MDC.clear ops (with 2 prior puts)", MAIN_ITERATIONS, elapsed);
    }

    private static void benchmarkGetContextSnapshot(){
        BenchSupport.printSection("MDC.getContext (defensive snapshot)");
        MDC.clear();
        MDC.put("requestId", "REQ-8821");
        MDC.put("userId", "9021");
        MDC.put("sessionId", "SESS-441");

        for(int i = 0; i < WARMUP_ITERATIONS / 5; i++)
            MDC.getContext();

        int iterations = MAIN_ITERATIONS / 5;
        long size = 0;
        long start = System.nanoTime();
        for(int i = 0; i < iterations; i++)
            size += MDC.getContext().size();
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("MDC.getContext snapshots", iterations, elapsed);
        BenchSupport.metric("Total entries observed", size);
        MDC.clear();
    }

    private static void benchmarkWrapCreation(){
        BenchSupport.printSection("MDC.wrap(Runnable) — wrap-call overhead only");
        MDC.clear();
        MDC.put("requestId", "REQ-8821");
        Runnable noop = () -> {};

        for(int i = 0; i < WARMUP_ITERATIONS / 5; i++)
            MDC.wrap(noop);

        int iterations = MAIN_ITERATIONS / 5;
        long start = System.nanoTime();
        for(int i = 0; i < iterations; i++)
            MDC.wrap(noop);
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("MDC.wrap() calls", iterations, elapsed);
        MDC.clear();
    }

    private static void benchmarkWrapExecution() throws InterruptedException{
        BenchSupport.printSection("MDC.wrap(Runnable) — running wrapped no-op task on 1 background thread");
        MDC.clear();
        MDC.put("requestId", "REQ-8821");
        MDC.put("userId", "9021");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try{
            Runnable noop = () -> {
                Map<String, String> ctx = MDC.getContext();
                if(ctx.isEmpty()) throw new IllegalStateException("expected MDC context to propagate into worker thread");
            };

            for(int i = 0; i < WRAP_RUN_ITERATIONS / 5; i++)
                executor.submit(MDC.wrap(noop)).get();

            long start = System.nanoTime();
            for(int i = 0; i < WRAP_RUN_ITERATIONS; i++)
                executor.submit(MDC.wrap(noop)).get();
            long elapsed = System.nanoTime() - start;

            BenchSupport.metricThroughput("Wrapped task submit+run (round-trip)", WRAP_RUN_ITERATIONS, elapsed);
        }catch(Exception ex){
            BenchSupport.metric("Wrap execution benchmark", "FAILED: " + ex);
        }finally{
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        MDC.clear();
    }
}
