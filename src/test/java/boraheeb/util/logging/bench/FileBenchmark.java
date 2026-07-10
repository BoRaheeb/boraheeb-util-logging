package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.FileLogOutput;
import boraheeb.util.logging.LogLevel;
import boraheeb.util.logging.LogRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
// -- FileBenchmark Class: -----------------------------------------------
/**
* Standalone benchmark measuring {@link FileLogOutput} throughput against a
* real temp-directory file: single-threaded buffered-write latency/throughput,
* and 16-thread concurrent throughput with a zero-loss line-count check.
*
* <p>Run directly: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.FileBenchmark}</p>
**/
public final class FileBenchmark{
    // -- Constants: --------------------------------------------------------
    private static final int SINGLE_WARMUP = 50_000;
    private static final int SINGLE_MAIN = 550_000;
    private static final int CONCURRENT_THREADS = 16;
    private static final int CONCURRENT_PER_THREAD = 50_000;
    // -- Constructors: -----------------------------------------------------
    private FileBenchmark(){}
    // -- Entry Point: --------------------------------------------------------
    public static void main(String[] args) throws Exception{
        BenchSupport.printHeader("FileBenchmark");

        Path tempDir = Files.createTempDirectory("boraheeb-file-bench-");
        try{
            benchmarkSingleThreadBuffered(tempDir);
            benchmarkConcurrentWrites(tempDir);
        }finally{
            deleteRecursively(tempDir);
        }

        BenchSupport.printSection("Done");
        System.out.println();
    }
    // -- Scenarios: ----------------------------------------------------------
    private static void benchmarkSingleThreadBuffered(Path tempDir) throws IOException{
        BenchSupport.printSection("Single-thread buffered write (autoFlush=false)");
        Path path = tempDir.resolve("single-thread.log");
        FileLogOutput output = FileLogOutput.builder()
            .path(path)
            .autoFlush(false)
            .minLevel(LogLevel.TRACE)
            .build();

        for(int i = 0; i < SINGLE_WARMUP; i++)
            output.publish(buildRecord(i));

        long[] latencies = new long[SINGLE_MAIN];
        long start = System.nanoTime();
        for(int i = 0; i < SINGLE_MAIN; i++){
            LogRecord record = buildRecord(i);
            long t0 = System.nanoTime();
            output.publish(record);
            latencies[i] = System.nanoTime() - t0;
        }
        long elapsed = System.nanoTime() - start;
        output.flush();
        output.close();

        BenchSupport.metricThroughput("Buffered publish() calls", SINGLE_MAIN, elapsed);
        BenchSupport.printLatencyPercentiles("publish() latency", latencies);

        long totalWritten = SINGLE_WARMUP + SINGLE_MAIN;
        long lineCount = countLines(path);
        BenchSupport.metric("Records published (warmup+main)", totalWritten);
        BenchSupport.metric("Lines found in file", lineCount);
        BenchSupport.metric("Zero-loss check", (lineCount == totalWritten)? "PASS" : "FAIL (mismatch)");
        BenchSupport.metric("Final file size", BenchSupport.formatBytes(Files.size(path)));
    }

    private static void benchmarkConcurrentWrites(Path tempDir) throws Exception{
        BenchSupport.printSection(CONCURRENT_THREADS + "-thread concurrent write to a single FileLogOutput");
        Path path = tempDir.resolve("concurrent.log");
        FileLogOutput output = FileLogOutput.builder()
            .path(path)
            .autoFlush(false)
            .minLevel(LogLevel.TRACE)
            .build();

        AtomicLong publishedCount = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_THREADS);

        for(int t = 0; t < CONCURRENT_THREADS; t++){
            final int threadId = t;
            pool.submit(() -> {
                try{
                    startLatch.await();
                    for(int i = 0; i < CONCURRENT_PER_THREAD; i++){
                        output.publish(buildRecord(threadId * CONCURRENT_PER_THREAD + i));
                        publishedCount.incrementAndGet();
                    }
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();
                }finally{
                    doneLatch.countDown();
                }
            });
        }

        long start = System.nanoTime();
        startLatch.countDown();
        doneLatch.await();
        long elapsed = System.nanoTime() - start;
        pool.shutdown();

        output.flush();
        output.close();

        long expectedTotal = (long) CONCURRENT_THREADS * CONCURRENT_PER_THREAD;
        long lineCount = countLines(path);

        BenchSupport.metricThroughput("Concurrent publish() calls", publishedCount.get(), elapsed);
        BenchSupport.metric("Expected total records", expectedTotal);
        BenchSupport.metric("Actual published (counter)", publishedCount.get());
        BenchSupport.metric("Lines found in file", lineCount);
        BenchSupport.metric("Final file size", BenchSupport.formatBytes(Files.size(path)));

        if(lineCount != expectedTotal || publishedCount.get() != expectedTotal){
            throw new IllegalStateException(
                "FileBenchmark: zero-loss check FAILED -> expected " + expectedTotal +
                " records but found " + lineCount + " lines in file (published counter=" + publishedCount.get() + ")"
            );
        }
        BenchSupport.metric("Zero-loss check", "PASS");
    }
    // -- Private Helper Methods: -------------------------------------------
    private static LogRecord buildRecord(int i){
        return LogRecord.builder()
            .level(LogLevel.INFO)
            .loggerName("FileBenchmark")
            .sourceName("FileBenchmark")
            .message("benchmark record #" + i + " with a moderately sized payload to be representative")
            .build();
    }

    private static long countLines(Path path) throws IOException{
        try(var lines = Files.lines(path)){
            return lines.count();
        }
    }

    private static void deleteRecursively(Path root){
        try{
            if(!Files.exists(root)) return;
            try(var walk = Files.walk(root)){
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try{
                        Files.deleteIfExists(p);
                    }catch(IOException ignore){}
                });
            }
        }catch(IOException ignore){}
    }
}
