package boraheeb.util.logging.bench;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

/**
* Shared helpers for the standalone benchmark/stress harnesses under this package.
*
* <p>These are plain {@code main()} programs, not JUnit tests — run each one directly,
* for example: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.LoggerBenchmark}</p>
**/
public final class BenchSupport{

    private BenchSupport(){}

    public static void printHeader(String title){
        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println(title);
        System.out.println("=".repeat(78));
    }

    public static void printSection(String title){
        System.out.println();
        System.out.println("--- " + title + " ---");
    }

    public static void metric(String label, String value){
        System.out.printf("  %-38s %s%n", label + ":", value);
    }

    public static void metric(String label, long value){
        metric(label, String.format("%,d", value));
    }

    public static void metricThroughput(String label, long count, long elapsedNanos){
        double seconds = elapsedNanos / 1_000_000_000.0;
        double perSec = (seconds <= 0)? 0 : count / seconds;
        metric(label, String.format("%,d records in %s (%,.0f rec/s)", count, formatDuration(elapsedNanos), perSec));
    }

    public static String formatDuration(long nanos){
        double ms = nanos / 1_000_000.0;
        if(ms < 1000) return String.format("%.2f ms", ms);
        return String.format("%.3f s", ms / 1000.0);
    }

    public static String formatBytes(long bytes){
        if(bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if(kb < 1024) return String.format("%.2f KB", kb);
        double mb = kb / 1024.0;
        if(mb < 1024) return String.format("%.2f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    /** Snapshot of heap usage and cumulative GC counters, for before/after comparisons. **/
    public record Snapshot(long heapUsedBytes, long gcCollections, long gcTimeMillis, long timestampNanos){

        public static Snapshot capture(){
            long gcCount = 0;
            long gcTime = 0;
            for(GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()){
                long count = bean.getCollectionCount();
                long time = bean.getCollectionTime();
                if(count > 0) gcCount += count;
                if(time > 0) gcTime += time;
            }
            Runtime runtime = Runtime.getRuntime();
            long heapUsed = runtime.totalMemory() - runtime.freeMemory();
            return new Snapshot(heapUsed, gcCount, gcTime, System.nanoTime());
        }

        public void printDeltaFrom(Snapshot before){
            metric("Heap used (before)", formatBytes(before.heapUsedBytes));
            metric("Heap used (after)", formatBytes(this.heapUsedBytes));
            long delta = this.heapUsedBytes - before.heapUsedBytes;
            metric("Heap delta", (delta >= 0? "+" : "") + formatBytes(delta));
            metric("GC collections during run", this.gcCollections - before.gcCollections);
            metric("GC time during run", (this.gcTimeMillis - before.gcTimeMillis) + " ms");
        }
    }

    /** Forces a best-effort GC and settles briefly, for cleaner before/after heap comparisons. **/
    public static void settleGc(){
        for(int i = 0; i < 3; i++){
            System.gc();
            try{
                Thread.sleep(50);
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Computes and prints p50/p90/p99/p99.9 latency percentiles from raw nanosecond samples. **/
    public static void printLatencyPercentiles(String label, long[] latenciesNanos){
        if(latenciesNanos.length == 0){
            metric(label, "no samples");
            return;
        }
        long[] sorted = latenciesNanos.clone();
        Arrays.sort(sorted);
        metric(label + " p50", formatNanos(percentile(sorted, 0.50)));
        metric(label + " p90", formatNanos(percentile(sorted, 0.90)));
        metric(label + " p99", formatNanos(percentile(sorted, 0.99)));
        metric(label + " p99.9", formatNanos(percentile(sorted, 0.999)));
        metric(label + " max", formatNanos(sorted[sorted.length - 1]));
    }

    private static long percentile(long[] sorted, double p){
        int index = (int) Math.ceil(p * sorted.length) - 1;
        if(index < 0) index = 0;
        if(index >= sorted.length) index = sorted.length - 1;
        return sorted[index];
    }

    private static String formatNanos(long nanos){
        if(nanos < 1_000) return nanos + " ns";
        if(nanos < 1_000_000) return String.format("%.2f us", nanos / 1_000.0);
        return String.format("%.2f ms", nanos / 1_000_000.0);
    }

    public static double mean(List<Long> values){
        if(values.isEmpty()) return 0;
        long sum = 0;
        for(long v : values) sum += v;
        return (double) sum / values.size();
    }
}
