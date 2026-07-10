package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.LogLevel;
import boraheeb.util.logging.LogOutput;
import boraheeb.util.logging.LogRecord;
import boraheeb.util.logging.Logger;
import java.util.concurrent.atomic.AtomicLong;
// -- LoggerBenchmark Class: ---------------------------------------------
/**
* Standalone benchmark measuring core {@link Logger} call-path throughput,
* isolated from I/O by using a tiny no-op/counting {@link LogOutput}.
*
* <p>Run directly: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.LoggerBenchmark}</p>
**/
public final class LoggerBenchmark{
    // -- Constants: --------------------------------------------------------
    private static final int WARMUP_ITERATIONS = 750_000;
    private static final int MAIN_ITERATIONS = 5_000_000;
    // -- Constructors: -----------------------------------------------------
    private LoggerBenchmark(){}
    // -- Entry Point: --------------------------------------------------------
    public static void main(String[] args){
        BenchSupport.printHeader("LoggerBenchmark");

        benchmarkFilteredCall();
        benchmarkAcceptedNoOutput();
        benchmarkAcceptedParameterized();

        BenchSupport.printSection("Done");
        System.out.println();
    }
    // -- Scenarios: ----------------------------------------------------------
    private static void benchmarkFilteredCall(){
        BenchSupport.printSection("Level-filtered call (below threshold, no record built)");
        CountingOutput output = new CountingOutput();
        Logger logger = Logger.builder("bench.filtered")
            .minLevel(LogLevel.ERROR)
            .addOutput(output)
            .build();

        for(int i = 0; i < WARMUP_ITERATIONS; i++)
            logger.info("warmup message {}", i);

        long start = System.nanoTime();
        for(int i = 0; i < MAIN_ITERATIONS; i++)
            logger.info("filtered message {}", i);
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("Filtered info() calls", MAIN_ITERATIONS, elapsed);
        BenchSupport.metric("Records actually published", output.count());
        logger.close();
    }

    private static void benchmarkAcceptedNoOutput(){
        BenchSupport.printSection("Accepted call, plain message, no-op output");
        CountingOutput output = new CountingOutput();
        Logger logger = Logger.builder("bench.plain")
            .minLevel(LogLevel.TRACE)
            .addOutput(output)
            .build();

        for(int i = 0; i < WARMUP_ITERATIONS; i++)
            logger.info("plain warmup message");

        long start = System.nanoTime();
        for(int i = 0; i < MAIN_ITERATIONS; i++)
            logger.info("plain accepted message");
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("Accepted info() calls", MAIN_ITERATIONS, elapsed);
        BenchSupport.metric("Records published", output.count());
        logger.close();
    }

    private static void benchmarkAcceptedParameterized(){
        BenchSupport.printSection("Accepted call, parameterized message with {} placeholders");
        CountingOutput output = new CountingOutput();
        Logger logger = Logger.builder("bench.parameterized")
            .minLevel(LogLevel.TRACE)
            .addOutput(output)
            .build();

        for(int i = 0; i < WARMUP_ITERATIONS; i++)
            logger.info("user {} did {} at {}ms", "user-" + i, "action", i);

        long start = System.nanoTime();
        for(int i = 0; i < MAIN_ITERATIONS; i++)
            logger.info("user {} did {} at {}ms", "user-" + i, "action", i);
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput("Accepted parameterized calls", MAIN_ITERATIONS, elapsed);
        BenchSupport.metric("Records published", output.count());
        logger.close();
    }
    // -- Support Types: --------------------------------------------------------
    /** A {@link LogOutput} that does nothing but count published records — isolates Logger overhead from I/O. **/
    private static final class CountingOutput implements LogOutput{
        private final AtomicLong published = new AtomicLong();
        private volatile boolean open = true;

        @Override
        public void publish(LogRecord record){
            published.incrementAndGet();
        }

        @Override
        public void flush(){}

        @Override
        public void close(){
            open = false;
        }

        @Override
        public boolean isOpen(){
            return open;
        }

        long count(){
            return published.get();
        }
    }
}
