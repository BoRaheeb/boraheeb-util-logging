package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.LogLevel;
import boraheeb.util.logging.LogRecord;
import boraheeb.util.logging.SocketLogOutput;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
// -- SocketBenchmark Class: ----------------------------------------------
/**
* Standalone benchmark measuring {@link SocketLogOutput} throughput against a
* local {@link ServerSocket} listener bound to an ephemeral port on localhost.
* A background thread reads and counts incoming lines so the publisher is
* never blocked by the listener.
*
* <p>Run directly: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.SocketBenchmark}</p>
**/
public final class SocketBenchmark{
    // -- Constants: --------------------------------------------------------
    private static final int RECORD_COUNT = 120_000;
    private static final long STABILIZE_TIMEOUT_MS = 15_000;
    private static final long STABILIZE_POLL_MS = 100;
    // -- Constructors: -----------------------------------------------------
    private SocketBenchmark(){}
    // -- Entry Point: --------------------------------------------------------
    public static void main(String[] args) throws Exception{
        BenchSupport.printHeader("SocketBenchmark");

        ServerSocket serverSocket = new ServerSocket(0, 64, java.net.InetAddress.getLoopbackAddress());
        AtomicLong receivedLines = new AtomicLong();
        AtomicBoolean listenerFailed = new AtomicBoolean(false);
        Thread listenerThread = new Thread(() -> runListener(serverSocket, receivedLines, listenerFailed), "socket-bench-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        try{
            runScenario(serverSocket.getLocalPort(), receivedLines);
        }finally{
            try{
                serverSocket.close();
            }catch(IOException ignore){}
            listenerThread.join(5000);
        }

        BenchSupport.printSection("Done");
        System.out.println();
    }
    // -- Scenario: -----------------------------------------------------------
    private static void runScenario(int port, AtomicLong receivedLines) throws InterruptedException{
        BenchSupport.printSection("Publish " + RECORD_COUNT + " records over TCP to localhost:" + port);

        SocketLogOutput output = SocketLogOutput.builder()
            .host("localhost")
            .port(port)
            .autoFlush(false)
            .connectTimeoutMs(2000)
            .reconnectDelayMs(1000)
            .minLevel(LogLevel.TRACE)
            .build();

        BenchSupport.metric("Connected on construction", String.valueOf(output.isConnected()));

        long start = System.nanoTime();
        for(int i = 0; i < RECORD_COUNT; i++){
            LogRecord record = LogRecord.builder()
                .level(LogLevel.INFO)
                .loggerName("SocketBenchmark")
                .sourceName("SocketBenchmark")
                .message("socket benchmark record #" + i)
                .build();
            output.publish(record);
        }
        output.flush();
        long elapsed = System.nanoTime() - start;
        output.close();

        BenchSupport.metricThroughput("publish() calls (send side)", RECORD_COUNT, elapsed);

        long stableCount = waitForStable(receivedLines);
        BenchSupport.metric("Records published (sent)", RECORD_COUNT);
        BenchSupport.metric("Lines received by listener", stableCount);
        BenchSupport.metric(
            "Delivery consistency check",
            (stableCount == RECORD_COUNT)? "PASS (all delivered)" : "PARTIAL (" + stableCount + "/" + RECORD_COUNT + " delivered)"
        );
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Polls the receive counter until it stops changing (delivery has caught up
    * and quiesced) or a timeout elapses, since socket delivery is asynchronous
    * relative to the publisher having returned from its last publish() call.
    **/
    private static long waitForStable(AtomicLong receivedLines){
        long deadline = System.currentTimeMillis() + STABILIZE_TIMEOUT_MS;
        long last = -1;
        int stableStreak = 0;
        while(System.currentTimeMillis() < deadline){
            long current = receivedLines.get();
            if(current == last){
                stableStreak++;
                if(stableStreak >= 3 || current >= RECORD_COUNT) return current;
            }else{
                stableStreak = 0;
            }
            last = current;
            try{
                Thread.sleep(STABILIZE_POLL_MS);
            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
                return current;
            }
        }
        return receivedLines.get();
    }

    private static void runListener(ServerSocket serverSocket, AtomicLong receivedLines, AtomicBoolean failed){
        try(Socket client = serverSocket.accept();
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))){
            while(reader.readLine() != null) receivedLines.incrementAndGet();
        }catch(IOException ex){
            // Expected once the server socket is closed at the end of the run; only a real
            // mid-run failure would be unexpected, and we have no clean way to distinguish
            // them here, so just record that the listener exited via an exception path.
            failed.set(true);
        }
    }
}
