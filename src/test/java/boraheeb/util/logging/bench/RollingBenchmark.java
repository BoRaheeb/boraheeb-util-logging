package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.LogLevel;
import boraheeb.util.logging.LogRecord;
import boraheeb.util.logging.RollingFileLogOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
// -- RollingBenchmark Class: --------------------------------------------
/**
* Standalone benchmark measuring {@link RollingFileLogOutput} under sustained
* volume with size-based rolling and file-count pruning, verifying that
* on-disk usage stays bounded near {@code maxSizeBytes * maxFiles} rather
* than growing unbounded.
*
* <p>
*   The README's headline soak figure logs ~20 GB of rolled output; that is
*   too slow to rerun casually here, so this benchmark deliberately scales the
*   volume down to a few hundred MB while keeping the roll threshold small
*   enough that rolling triggers many times, exercising the same code paths
*   (roll, rename, prune) at a much faster, rerunnable scale.
* </p>
*
* <p>Run directly: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.RollingBenchmark}</p>
**/
public final class RollingBenchmark{
    // -- Constants: --------------------------------------------------------
    /** Scaled-down roll threshold (README's real-world scenario uses much larger files). **/
    private static final long MAX_SIZE_BYTES = 256L * 1024; // 256 KB
    private static final int MAX_FILES = 5;
    /** Scaled-down total volume target (README's soak scenario writes ~20 GB). **/
    private static final long TARGET_TOTAL_BYTES = 300L * 1024 * 1024; // 300 MB
    // -- Constructors: -----------------------------------------------------
    private RollingBenchmark(){}
    // -- Entry Point: --------------------------------------------------------
    public static void main(String[] args) throws Exception{
        BenchSupport.printHeader("RollingBenchmark");
        BenchSupport.metric("NOTE", "scaled down from README's ~20GB soak scenario to a " +
            BenchSupport.formatBytes(TARGET_TOTAL_BYTES) + " rerunnable run");
        BenchSupport.metric("maxSizeBytes (roll threshold)", BenchSupport.formatBytes(MAX_SIZE_BYTES));
        BenchSupport.metric("maxFiles (retained rolled files)", MAX_FILES);

        Path tempDir = Files.createTempDirectory("boraheeb-rolling-bench-");
        try{
            runScenario(tempDir);
        }finally{
            deleteRecursively(tempDir);
        }

        BenchSupport.printSection("Done");
        System.out.println();
    }
    // -- Scenario: -----------------------------------------------------------
    private static void runScenario(Path tempDir) throws IOException{
        Path baseName = Path.of("app.log");
        Path path = tempDir.resolve(baseName);

        RollingFileLogOutput output = RollingFileLogOutput.builder()
            .path(path)
            .autoFlush(false)
            .minLevel(LogLevel.TRACE)
            .maxSizeBytes(MAX_SIZE_BYTES)
            .rollingPeriod(null)
            .maxFiles(MAX_FILES)
            .build();

        String messageTemplate = "rolling benchmark record payload padding to a representative log line length #";
        long publishedBytes = 0;
        long publishedRecords = 0;

        long start = System.nanoTime();
        while(publishedBytes < TARGET_TOTAL_BYTES){
            LogRecord record = LogRecord.builder()
                .level(LogLevel.INFO)
                .loggerName("RollingBenchmark")
                .sourceName("RollingBenchmark")
                .message(messageTemplate + publishedRecords)
                .build();
            output.publish(record);
            // Approximate bytes published using the same accounting the production class uses:
            // UTF-8 encoded message length is close enough for a throughput/volume estimate here.
            publishedBytes += record.getMessage().getBytes(StandardCharsets.UTF_8).length + 64; // + rough formatter overhead
            publishedRecords++;
        }
        output.flush();
        output.close();
        long elapsed = System.nanoTime() - start;

        BenchSupport.printSection("Results");
        BenchSupport.metricThroughput("Rolling publish() calls", publishedRecords, elapsed);
        BenchSupport.metric("Approx. total bytes published", BenchSupport.formatBytes(publishedBytes));

        long onDiskBytes = 0;
        int fileCount = 0;
        if(Files.exists(tempDir)){
            try(var stream = Files.list(tempDir)){
                for(Path candidate : (Iterable<Path>) stream::iterator){
                    String name = candidate.getFileName().toString();
                    if(name.equals("app.log") || (name.startsWith("app-") && name.endsWith(".log"))){
                        onDiskBytes += Files.size(candidate);
                        fileCount++;
                    }
                }
            }
        }

        long boundEstimate = MAX_SIZE_BYTES * (MAX_FILES + 1) * 2; // generous margin: active file + retained rolled files
        BenchSupport.metric("Files retained on disk", fileCount);
        BenchSupport.metric("Total on-disk bytes retained", BenchSupport.formatBytes(onDiskBytes));
        BenchSupport.metric("Configured bound (maxSizeBytes * (maxFiles+1))", BenchSupport.formatBytes(MAX_SIZE_BYTES * (MAX_FILES + 1)));
        BenchSupport.metric("Bounded-disk check (generous margin)", (onDiskBytes <= boundEstimate)? "PASS" : "FAIL (unbounded growth?)");

        if(onDiskBytes > boundEstimate)
            throw new IllegalStateException(
                "RollingBenchmark: bounded-disk check FAILED -> on-disk bytes (" + onDiskBytes +
                ") exceeded generous bound (" + boundEstimate + ") for maxSizeBytes=" + MAX_SIZE_BYTES + ", maxFiles=" + MAX_FILES
            );
    }
    // -- Private Helper Methods: -------------------------------------------
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
