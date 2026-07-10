package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.LevelFilter;
import boraheeb.util.logging.LogFilter;
import boraheeb.util.logging.LogLevel;
import boraheeb.util.logging.LogRecord;
import boraheeb.util.logging.LoggerNameFilter;
// -- FilterBenchmark Class: ---------------------------------------------
/**
* Standalone benchmark measuring {@link LogFilter}/{@link LevelFilter}/{@link LoggerNameFilter}
* {@code accept(LogRecord)} throughput using a fixed pre-built {@link LogRecord}.
*
* <p>Run directly: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.FilterBenchmark}</p>
**/
public final class FilterBenchmark{
    // -- Constants: --------------------------------------------------------
    private static final int WARMUP_ITERATIONS = 1_000_000;
    private static final int MAIN_ITERATIONS = 10_000_000;
    // -- Constructors: -----------------------------------------------------
    private FilterBenchmark(){}
    // -- Entry Point: --------------------------------------------------------
    public static void main(String[] args){
        BenchSupport.printHeader("FilterBenchmark");

        LogRecord record = LogRecord.builder()
            .level(LogLevel.WARN)
            .loggerName("boraheeb.ui.dialog.Confirm")
            .sourceName("Confirm.java")
            .message("record used for filter throughput benchmarking")
            .field("requestId", "REQ-8821")
            .build();

        BenchSupport.printSection("LevelFilter");
        runFilterScenario("atLeast(WARN)", LevelFilter.atLeast(LogLevel.WARN), record);
        runFilterScenario("atMost(WARN)", LevelFilter.atMost(LogLevel.WARN), record);
        runFilterScenario("exactly(WARN)", LevelFilter.exactly(LogLevel.WARN), record);
        runFilterScenario("between(DEBUG, ERROR)", LevelFilter.between(LogLevel.DEBUG, LogLevel.ERROR), record);

        BenchSupport.printSection("LoggerNameFilter");
        runFilterScenario("include(...)", LoggerNameFilter.include("boraheeb.ui.dialog.Confirm", "other"), record);
        runFilterScenario("prefix(...)", LoggerNameFilter.prefix("boraheeb.ui", "boraheeb.net"), record);
        runFilterScenario("exclude(...)", LoggerNameFilter.exclude("noisy.lib"), record);

        BenchSupport.printSection("Composed filter (and/or/negate chain)");
        LogFilter composed = LevelFilter.atLeast(LogLevel.INFO)
            .and(LoggerNameFilter.prefix("boraheeb.ui"))
            .or(LevelFilter.exactly(LogLevel.CRITICAL))
            .negate()
            .negate();
        runFilterScenario("composed chain", composed, record);

        BenchSupport.printSection("Done");
        System.out.println();
    }
    // -- Helpers: --------------------------------------------------------
    private static void runFilterScenario(String label, LogFilter filter, LogRecord record){
        boolean sink = false;
        for(int i = 0; i < WARMUP_ITERATIONS; i++)
            sink ^= filter.accept(record);

        long acceptedCount = 0;
        long start = System.nanoTime();
        for(int i = 0; i < MAIN_ITERATIONS; i++)
            if(filter.accept(record)) acceptedCount++;
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput(label, MAIN_ITERATIONS, elapsed);
        BenchSupport.metric(label + " accepted", acceptedCount);
        if(sink) BenchSupport.metric(label + " sink", "true"); // prevents dead-code elimination of the warmup loop
    }
}
