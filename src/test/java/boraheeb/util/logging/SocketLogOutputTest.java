package boraheeb.util.logging;
// -- Libraries: ----------------------------------------------------------
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
// -- SocketLogOutputTest Class: ---------------------------------------------
class SocketLogOutputTest{
    // -- Fields: -------------------------------------------------------------
    private final List<SocketLogOutput> opened = new ArrayList<>();
    private final List<ServerSocket> servers = new ArrayList<>();
    // -- Teardown: -------------------------------------------------------------
    @AfterEach
    void closeAll(){
        for(SocketLogOutput output : opened) output.close();
        opened.clear();
        for(ServerSocket server : servers){
            try{
                server.close();
            }catch(IOException ignore){}
        }
        servers.clear();
    }
    private SocketLogOutput track(SocketLogOutput output){
        opened.add(output);
        return output;
    }
    private static LogRecord record(String message){
        return LogRecord.builder().level(LogLevel.INFO).message(message).build();
    }
    // -- Test Server Helpers: -------------------------------------------------------------
    /** Uses port 0 so the OS assigns a free ephemeral port, avoiding collisions between test runs. **/
    private ServerSocket newServer() throws IOException{
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress("localhost", 0));
        servers.add(server);
        return server;
    }
    /** Returns a port that had a listener but no longer does, so connections to it are refused quickly. **/
    private int closedPort() throws IOException{
        ServerSocket temp = new ServerSocket();
        temp.setReuseAddress(true);
        temp.bind(new InetSocketAddress("localhost", 0));
        int port = temp.getLocalPort();
        temp.close();
        return port;
    }
    private BlockingQueue<String> acceptAndCollectLines(ServerSocket server){
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread thread = new Thread(() -> {
            try(Socket client = server.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))){
                String line;
                while((line = reader.readLine()) != null) lines.offer(line);
            }catch(IOException ignore){}
        });
        thread.setDaemon(true);
        thread.start();
        return lines;
    }
    private static String poll(BlockingQueue<String> lines, long timeoutMs) throws InterruptedException{
        String line = lines.poll(timeoutMs, TimeUnit.MILLISECONDS);
        assertNotNull(line, "expected a line within " + timeoutMs + "ms");
        return line;
    }
    // -- Builder Default / Validation Tests: -------------------------------------------------------------
    @Test
    void builderDefaultsMatchDocumentedValues() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = track(SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).build());
        assertSame(TextLogFormatter.PLAIN, output.getFormatter());
        assertEquals(LogLevel.TRACE, output.getMinLevel());
        assertNull(output.getFilter());
        assertTrue(output.isAutoFlush());
        assertEquals(5000, output.getConnectTimeoutMs());
        assertEquals(5000, output.getReconnectDelayMs());
    }
    @Test
    void builderDefaultHostAndPort() throws IOException{
        SocketLogOutput output = track(SocketLogOutput.builder().connectTimeoutMs(300).build());
        assertEquals("localhost", output.getHost());
        assertEquals(9000, output.getPort());
    }
    @Test
    void hostNullFallsBackToDefaultHost() throws IOException{
        SocketLogOutput output = track(SocketLogOutput.builder().host(null).connectTimeoutMs(300).build());
        assertEquals("localhost", output.getHost());
    }
    @Test
    void hostBlankFallsBackToDefaultHost() throws IOException{
        SocketLogOutput output = track(SocketLogOutput.builder().host("   ").connectTimeoutMs(300).build());
        assertEquals("localhost", output.getHost());
    }
    @Test
    void hostIsTrimmed() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = track(SocketLogOutput.builder().host("  localhost  ").port(server.getLocalPort()).build());
        assertEquals("localhost", output.getHost());
    }
    @Test
    void portOutOfRangeFallsBackToDefaultPort() throws IOException{
        SocketLogOutput output = track(SocketLogOutput.builder().port(-1).connectTimeoutMs(300).build());
        assertEquals(9000, output.getPort());
        SocketLogOutput output2 = track(SocketLogOutput.builder().port(70000).connectTimeoutMs(300).build());
        assertEquals(9000, output2.getPort());
    }
    @Test
    void connectTimeoutNegativeFallsBackToDefault() throws IOException{
        SocketLogOutput output = track(SocketLogOutput.builder().connectTimeoutMs(-5).build());
        assertEquals(5000, output.getConnectTimeoutMs());
    }
    @Test
    void reconnectDelayNegativeFallsBackToDefault() throws IOException{
        SocketLogOutput output = track(SocketLogOutput.builder().reconnectDelayMs(-5).connectTimeoutMs(300).build());
        assertEquals(5000, output.getReconnectDelayMs());
    }
    @Test
    void reconnectDelayZeroIsAccepted() throws IOException{
        SocketLogOutput output = track(SocketLogOutput.builder().reconnectDelayMs(0).connectTimeoutMs(300).build());
        assertEquals(0, output.getReconnectDelayMs());
    }
    @Test
    void formatterNullFallsBackToPlain() throws IOException{
        SocketLogOutput output = track(SocketLogOutput.builder().formatter(null).connectTimeoutMs(300).build());
        assertSame(TextLogFormatter.PLAIN, output.getFormatter());
    }
    // -- Connection / Publish Tests: -------------------------------------------------------------
    @Test
    void constructionSucceedsWhenServerIsListening() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = track(SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).build());
        assertTrue(output.isConnected());
        assertTrue(output.isOpen());
    }
    @Test
    void constructionFailsWhenNothingIsListening() throws IOException{
        int port = closedPort();
        SocketLogOutput output = track(SocketLogOutput.builder().host("localhost").port(port).connectTimeoutMs(500).build());
        assertFalse(output.isConnected());
        assertFalse(output.isOpen());
    }
    @Test
    void publishSendsFormattedLineToServer() throws IOException, InterruptedException{
        ServerSocket server = newServer();
        BlockingQueue<String> lines = acceptAndCollectLines(server);
        SocketLogOutput output = track(SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).build());
        output.publish(record("hello-socket"));
        String line = poll(lines, 3000);
        assertTrue(line.contains("hello-socket"));
    }
    @Test
    void publishNullRecordDoesNotThrow() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = track(SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).build());
        assertDoesNotThrow(() -> output.publish(null));
    }
    @Test
    void publishBelowMinLevelIsDropped() throws IOException, InterruptedException{
        ServerSocket server = newServer();
        BlockingQueue<String> lines = acceptAndCollectLines(server);
        SocketLogOutput output = track(
            SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).minLevel(LogLevel.WARN).build()
        );
        output.publish(LogRecord.builder().level(LogLevel.DEBUG).message("too-low").build());
        output.publish(LogRecord.builder().level(LogLevel.ERROR).message("high-enough").build());
        String line = poll(lines, 3000);
        assertTrue(line.contains("high-enough"));
        assertNull(lines.poll(200, TimeUnit.MILLISECONDS));
    }
    @Test
    void publishRejectedByFilterIsDropped() throws IOException, InterruptedException{
        ServerSocket server = newServer();
        BlockingQueue<String> lines = acceptAndCollectLines(server);
        LogFilter onlyA = r -> r.getMessage().startsWith("a");
        SocketLogOutput output = track(
            SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).filter(onlyA).build()
        );
        output.publish(record("bXXX"));
        output.publish(record("aXXX"));
        String line = poll(lines, 3000);
        assertTrue(line.contains("aXXX"));
    }
    @Test
    void publishWhenDisconnectedAndReconnectDisabledIsDropped() throws IOException, InterruptedException{
        int port = closedPort();
        SocketLogOutput output = track(
            SocketLogOutput.builder().host("localhost").port(port).connectTimeoutMs(300).reconnectDelayMs(0).build()
        );
        assertFalse(output.isConnected());
        assertDoesNotThrow(() -> output.publish(record("dropped")));
        assertFalse(output.isConnected());
    }
    @Test
    void publishReconnectsAutomaticallyOnceServerBecomesAvailable() throws IOException, InterruptedException{
        int port = closedPort();
        SocketLogOutput output = track(
            SocketLogOutput.builder().host("localhost").port(port).connectTimeoutMs(300).reconnectDelayMs(100).build()
        );
        assertFalse(output.isConnected());
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress("localhost", port));
        servers.add(server);
        BlockingQueue<String> lines = acceptAndCollectLines(server);
        Thread.sleep(150);
        output.publish(record("after-reconnect"));
        String line = poll(lines, 3000);
        assertTrue(line.contains("after-reconnect"));
        assertTrue(output.isConnected());
    }
    // -- Runtime Mutator Tests: -------------------------------------------------------------
    @Test
    void setMinLevelNullIsIgnored() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = track(
            SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).minLevel(LogLevel.WARN).build()
        );
        output.setMinLevel(null);
        assertEquals(LogLevel.WARN, output.getMinLevel());
    }
    @Test
    void setFilterNullAcceptsAllRecords() throws IOException, InterruptedException{
        ServerSocket server = newServer();
        BlockingQueue<String> lines = acceptAndCollectLines(server);
        SocketLogOutput output = track(
            SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).filter(LogFilter.REJECT_ALL).build()
        );
        output.setFilter(null);
        assertNull(output.getFilter());
        output.publish(record("through"));
        String line = poll(lines, 3000);
        assertTrue(line.contains("through"));
    }
    @Test
    void setAutoFlushToggles() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = track(SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).build());
        assertTrue(output.isAutoFlush());
        output.setAutoFlush(false);
        assertFalse(output.isAutoFlush());
    }
    @Test
    void setReconnectDelayNegativeIsIgnored() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = track(
            SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).reconnectDelayMs(1000).build()
        );
        output.setReconnectDelayMs(-1);
        assertEquals(1000, output.getReconnectDelayMs());
    }
    @Test
    void setReconnectDelayZeroDisablesReconnection() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = track(SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).build());
        output.setReconnectDelayMs(0);
        assertEquals(0, output.getReconnectDelayMs());
    }
    // -- Close Tests: -------------------------------------------------------------
    @Test
    void closeIsIdempotent() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).build();
        output.close();
        assertDoesNotThrow(output::close);
        assertFalse(output.isOpen());
    }
    @Test
    void publishAfterCloseIsIgnored() throws IOException, InterruptedException{
        ServerSocket server = newServer();
        BlockingQueue<String> lines = acceptAndCollectLines(server);
        SocketLogOutput output = SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).build();
        output.publish(record("before-close"));
        poll(lines, 3000);
        output.close();
        output.publish(record("after-close"));
        assertNull(lines.poll(300, TimeUnit.MILLISECONDS));
        assertFalse(output.isOpen());
    }
    @Test
    void flushAfterCloseIsNoop() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).build();
        output.close();
        assertDoesNotThrow(output::flush);
    }
    @Test
    void flushWhenDisconnectedIsNoop() throws IOException{
        int port = closedPort();
        SocketLogOutput output = track(
            SocketLogOutput.builder().host("localhost").port(port).connectTimeoutMs(300).build()
        );
        assertFalse(output.isConnected());
        assertDoesNotThrow(output::flush);
    }
    @Test
    void isOpenReflectsConnectionState() throws IOException{
        ServerSocket server = newServer();
        acceptAndCollectLines(server);
        SocketLogOutput output = SocketLogOutput.builder().host("localhost").port(server.getLocalPort()).build();
        assertTrue(output.isOpen());
        output.close();
        assertFalse(output.isOpen());
    }
}
