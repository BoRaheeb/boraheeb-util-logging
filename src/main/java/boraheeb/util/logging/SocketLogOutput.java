package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
// -- SocketLogOutput Class: --------------------------------------------
/**
* A {@link LogOutput} that writes log records over a TCP socket connection,
* with automatic reconnection on failure.
*
* <p>
*   Each call to {@link #publish(LogRecord)} formats the record using the
*   configured {@link LogFormatter} and sends a single line followed by a
*   newline to the remote host. This is useful for shipping logs to a remote
*   aggregator or log server.
* </p>
*
* <p>
*   The default formatter is {@link TextLogFormatter#PLAIN}, so no ANSI escape
*   codes are written to the socket stream. Pass a custom formatter to override this.
* </p>
*
* <p>
*   The TCP connection is established once at construction time using the
*   configured connect timeout. If the connection fails, an internal diagnostic
*   error is emitted and subsequent calls to {@link #publish(LogRecord)} will
*   attempt to reconnect automatically, subject to the configured
*   {@link Builder#reconnectDelayMs(int) reconnect delay}.
* </p>
*
* <p>
*   When a write fails, the current record is dropped, the broken connection
*   is closed, and the next call to {@link #publish(LogRecord)} will attempt
*   a reconnect if the reconnect delay has elapsed. Setting the reconnect delay
*   to {@code 0} disables automatic reconnection entirely, restoring the
*   original connect-once behavior.
* </p>
*
* <p>
*   Calls are {@code synchronized} to prevent interleaved output from multiple
*   threads. {@link #close()} flushes, closes the writer, and closes the socket.
* </p>
*
* <p>
*   Because the connection is established synchronously — at construction and on
*   each reconnect — wrap this output in an {@link AsyncLogOutput} when publishing
*   from latency-sensitive threads, so connect and write latency never block callers.
* </p>
*
* <p>Instances are created via the {@link Builder}.</p>
*
* <p>This class is thread-safe.</p>
*
* @author BoRaheeb
**/
public final class SocketLogOutput implements LogOutput{
    // -- Constants: --------------------------------------------------------
    /** Default host used when none is provided. **/
    private static final String DEFAULT_HOST = "localhost";
    /** Minimum valid TCP port number. **/
    private static final int MIN_PORT = 1;
    /** Maximum valid TCP port number. **/
    private static final int MAX_PORT = 65535;
    /** Default TCP port used when none is provided. **/
    private static final int DEFAULT_PORT = 9000;
    /** Minimum valid connect timeout in milliseconds; {@code 0} means wait indefinitely. **/
    private static final int MIN_CONNECT_TIMEOUT_MS = 0;
    /** Default connect timeout in milliseconds. **/
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    /** Minimum valid reconnect delay in milliseconds; {@code 0} disables reconnection. **/
    private static final int MIN_RECONNECT_DELAY_MS = 0;
    /** Default minimum time in milliseconds between reconnect attempts. **/
    private static final int DEFAULT_RECONNECT_DELAY_MS = 5000;
    // -- Fields: -----------------------------------------------------------
    /** Formatter used to convert log records into plain-text strings. **/
    private final LogFormatter formatter;
    /** Remote host this output connects to. **/
    private final String host;
    /** Remote TCP port this output connects to. **/
    private final int port;
    /** Minimum log level required for a record to be published. **/
    private LogLevel minLevel;
    /** Optional filter applied after the level check; {@code null} means accept all. **/
    private LogFilter filter;
    /** Whether to flush after every published record. **/
    private boolean autoFlush;
    /** TCP connect timeout in milliseconds used for initial and reconnect attempts. **/
    private final int connectTimeoutMs;
    /** Minimum time in milliseconds between reconnect attempts; 0 disables reconnection. **/
    private int reconnectDelayMs;
    /** Whether a reconnect has been attempted yet (guards the monotonic timestamp sentinel). **/
    private boolean reconnectAttempted = false;
    /** Monotonic timestamp ({@link System#nanoTime}) of the last reconnect attempt. **/
    private long lastReconnectNanos = 0;
    /** Underlying TCP socket. {@code null} when disconnected. **/
    private Socket socket;
    /** Buffered writer over the socket output stream. {@code null} when disconnected. **/
    private BufferedWriter writer;
    /** Whether this output has been closed. **/
    private volatile boolean closed = false;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a socket log output from the given builder.
    *
    * @param builder the builder containing output values
    **/
    private SocketLogOutput(Builder builder){
        this.formatter = builder.formatter;
        this.host = builder.host;
        this.port = builder.port;
        this.minLevel = builder.minLevel;
        this.filter = builder.filter;
        this.autoFlush = builder.autoFlush;
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.reconnectDelayMs = builder.reconnectDelayMs;
        Socket initialSocket = null;
        BufferedWriter initialWriter = null;
        try{
            initialSocket = new Socket();
            initialSocket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            OutputStreamWriter streamWriter = new OutputStreamWriter(initialSocket.getOutputStream(), StandardCharsets.UTF_8);
            initialWriter = new BufferedWriter(streamWriter);
        }catch(IOException ex){
            InternalDiagnostic.error(
                "SocketLogOutput.constructor: failed to connect to " + host + ":" + port + " -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> starting with no connection and will attempt to reconnect on publish"
            );
            if(initialSocket != null){
                try{
                    initialSocket.close();
                }catch(IOException ignore){}
            }
            initialSocket = null;
            initialWriter = null;
        }
        this.socket = initialSocket;
        this.writer = initialWriter;
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the formatter used to convert log records into strings.
    *
    * @return the active formatter, never {@code null}
    **/
    public LogFormatter getFormatter(){
        return formatter;
    }
    /**
    * Returns the remote host this output connects to.
    *
    * @return the remote host name or IP address, never {@code null}
    **/
    public String getHost(){
        return host;
    }
    /**
    * Returns the remote TCP port this output connects to.
    *
    * @return the remote port number
    **/
    public int getPort(){
        return port;
    }
    /**
    * Returns the minimum log level required for a record to be published.
    *
    * @return the minimum log level, never {@code null}
    **/
    public LogLevel getMinLevel(){
        return minLevel;
    }
    /**
    * Returns the optional filter applied after the level check.
    *
    * @return the filter, or {@code null} if no filter is set
    **/
    public LogFilter getFilter(){
        return filter;
    }
    /**
    * Returns whether the writer is flushed after every published record.
    *
    * @return {@code true} if auto-flush is enabled, otherwise {@code false}
    **/
    public boolean isAutoFlush(){
        return autoFlush;
    }
    /**
    * Returns the TCP connect timeout in milliseconds.
    *
    * @return the connect timeout in milliseconds
    **/
    public int getConnectTimeoutMs(){
        return connectTimeoutMs;
    }
    /**
    * Returns the minimum delay in milliseconds between reconnect attempts,
    * or {@code 0} if automatic reconnection is disabled.
    *
    * @return the reconnect delay in milliseconds, or 0 if disabled
    **/
    public int getReconnectDelayMs(){
        return reconnectDelayMs;
    }
    /**
    * Returns {@code true} if the connection is currently established and
    * this output has not been closed.
    *
    * <p>
    *   The value may change over time — it returns {@code false} while
    *   disconnected and {@code true} again after a successful reconnect.
    * </p>
    *
    * @return {@code true} if the socket is open and available for writing, otherwise {@code false}
    **/
    public synchronized boolean isConnected(){
        return (!closed && writer != null);
    }
    /**
    * Returns {@code true} if this output is connected and has not been closed.
    * Delegates to {@link #isConnected()}.
    *
    * @return {@code true} if connected and available for writing, otherwise {@code false}
    **/
    @Override
    public synchronized boolean isOpen(){
        return isConnected();
    }
    // -- Mutator Methods: --------------------------------------------------
    /**
    * Replaces the active minimum log level at runtime.
    * Records below the new level are silently dropped on the next publish.
    *
    * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param minLevel the new minimum level; ignored if {@code null}
    **/
    public synchronized void setMinLevel(LogLevel minLevel){
        if(minLevel == null){
            InternalDiagnostic.warn("SocketLogOutput.setMinLevel: minLevel is null -> ignored");
            return;
        }
        this.minLevel = minLevel;
    }
    /**
    * Replaces the active filter at runtime.
    * Pass {@code null} to remove the filter and accept all records.
    *
    * @param filter the new filter, or {@code null} to accept all records
    **/
    public synchronized void setFilter(LogFilter filter){
        this.filter = filter;
    }
    /**
    * Sets whether to flush the writer after every published record at runtime.
    *
    * @param autoFlush {@code true} to flush after every record, {@code false} to buffer
    **/
    public synchronized void setAutoFlush(boolean autoFlush){
        this.autoFlush = autoFlush;
    }
    /**
    * Replaces the minimum delay between automatic reconnect attempts at runtime.
    * Pass {@code 0} to disable automatic reconnection entirely.
    *
    * <p>If a negative value is passed, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param ms the new minimum delay in milliseconds (0 to disable); ignored if negative
    **/
    public synchronized void setReconnectDelayMs(int ms){
        if(ms < MIN_RECONNECT_DELAY_MS){
            InternalDiagnostic.warn("SocketLogOutput.setReconnectDelayMs: ms (" + ms + ") is negative -> ignored");
            return;
        }
        reconnectDelayMs = ms;
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing a {@link SocketLogOutput}.
    *
    * @return a new socket log output builder
    **/
    public static Builder builder(){
        return new Builder();
    }
    // -- LogOutput Methods: ------------------------------------------------
    /**
    * Formats and sends the given log record over the TCP connection.
    *
    * <p>
    *   Records below the configured minimum level are silently dropped.
    *   A {@code null} record is ignored with an internal diagnostic warning.
    *   Does nothing if this output has been closed.
    * </p>
    *
    * <p>
    *   If the connection is not currently established, a reconnect is
    *   attempted before publishing, subject to the configured reconnect delay.
    *   If the reconnect fails or the delay has not elapsed, the record is
    *   dropped. If a write fails, the record is dropped and the connection is
    *   closed so that the next call can attempt a reconnect.
    * </p>
    *
    * <p>
    *   If {@code null} is passed for the record, an internal diagnostic warning is emitted and the call is ignored.
    *   If failed to send the record, an internal diagnostic error is emitted and the connection is closed so that the next call can attempt a reconnect.
    * </p>
    *
    * @param record the log record to publish
    **/
    @Override
    public synchronized void publish(LogRecord record){
        if(closed) return;
        if(record == null){
            InternalDiagnostic.warn("SocketLogOutput.publish: record is null -> ignored");
            return;
        }
        if(!record.getLevel().isAtLeast(minLevel)) return;
        if(filter != null && !filter.accept(record)) return;
        if(writer == null)
            if(reconnectDelayMs <= 0 || !reconnect()) return;
        try{
            writer.write(formatter.format(record));
            writer.newLine();
            if(autoFlush) writer.flush();
        }catch(IOException ex){
            InternalDiagnostic.error(
                "SocketLogOutput.publish: failed to send to " + host + ":" + port + " -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> closing connection and will attempt to reconnect on next publish"
            );
            closeConnection();
        }
    }
    /**
    * Flushes any buffered records to the remote socket.
    * Does nothing if this output has been closed or the connection is not established.
    *
    * <p>If failed to flush the writer, an internal diagnostic error is emitted and the connection is closed so that the next call can attempt a reconnect.</p>
    **/
    @Override
    public synchronized void flush(){
        if(closed || writer == null) return;
        try{
            writer.flush();
        }catch(IOException ex){
            InternalDiagnostic.error(
                "SocketLogOutput.flush: failed to flush to " + host + ":" + port + " -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> closing connection and will attempt to reconnect on next publish"
            );
            closeConnection();
        }
    }
    /**
    * Flushes, closes the writer, and closes the underlying socket.
    *
    * <p>
    *   Calls to {@link #publish(LogRecord)} and {@link #flush()} after closing
    *   are silently ignored. No further reconnect attempts are made.
    * </p>
    *
    * <p>If failed to close the writer or socket, an internal diagnostic error is emitted and resources may not be released properly.</p>
    **/
    @Override
    public synchronized void close(){
        if(closed) return;
        closed = true;
        if(writer != null){
            try{
                writer.flush();
                writer.close();
            }catch(IOException ex){
                InternalDiagnostic.error(
                    "SocketLogOutput.close: failed to close writer for " + host + ":" + port + " -> cause: " +
                    ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> closing connection and will attempt to reconnect on next publish"
                );
            }
            writer = null;
        }
        if(socket != null){
            try{
                socket.close();
            }catch(IOException ex){
                InternalDiagnostic.error(
                    "SocketLogOutput.close: failed to close socket for " + host + ":" + port + " -> cause: " +
                    ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> connection is closed but may not be cleanly released by the OS"
                );
            }
            socket = null;
        }
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Attempts to establish a new TCP connection to the configured host and port.
    *
    * <p>
    *   Returns {@code false} immediately if the reconnect delay has not elapsed
    *   since the last attempt, preventing repeated connection attempts on every
    *   publish call when the remote server is down.
    * </p>
    *
    * <p>
    *   If reconnected successfully, an internal diagnostic info message is emitted and publishing resumes.
    *   If failed to reconnect, an internal diagnostic error is emitted and the connection is closed so that the next call can attempt a reconnect.
    * </p>
    *
    * @return {@code true} if the connection was successfully established
    **/
    private boolean reconnect(){
        long now = System.nanoTime();
        if(reconnectAttempted && (now - lastReconnectNanos) < reconnectDelayMs * 1_000_000L) return false;
        reconnectAttempted = true;
        lastReconnectNanos = now;
        closeConnection();
        try{
            Socket newSocket = new Socket();
            newSocket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            OutputStreamWriter streamWriter = new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8);
            writer = new BufferedWriter(streamWriter);
            socket = newSocket;
            InternalDiagnostic.info("SocketLogOutput.reconnect: reconnected to " + host + ":" + port + " -> resuming publish");
            return true;
        }catch(IOException ex){
            InternalDiagnostic.error(
                "SocketLogOutput.reconnect: failed to connect to " + host + ":" + port + " -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> closing connection and will attempt to reconnect on next publish"
            );
            return false;
        }
    }
    /**
    * Silently closes the current writer and socket, setting both to {@code null}.
    * Called before a reconnect attempt and on write failure to reset connection state.
    **/
    private void closeConnection(){
        if(writer != null){
            try{
                writer.close();
            }catch(IOException ignore){}
            writer = null;
        }
        if(socket != null){
            try{
                socket.close();
            }catch(IOException ignore){}
            socket = null;
        }
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link SocketLogOutput} instances.
    *
    * <p>
    *   Defaults: plain-text formatter (no ANSI codes) {@link TextLogFormatter#PLAIN},
    *   {@code localhost:9000}, minimum level {@link LogLevel#TRACE}, no filter,
    *   auto-flush enabled, 5-second connect timeout, and 5-second reconnect delay.
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Formatter assigned to the output being built. **/
        private LogFormatter formatter = TextLogFormatter.PLAIN;
        /** Remote host assigned to the output being built. **/
        private String host = DEFAULT_HOST;
        /** Remote port assigned to the output being built. **/
        private int port = DEFAULT_PORT;
        /** Minimum level assigned to the output being built. **/
        private LogLevel minLevel = DEFAULT_MIN_LEVEL;
        /** Optional filter assigned to the output being built; {@code null} means accept all. **/
        private LogFilter filter = null;
        /** Whether to flush after every published record. **/
        private boolean autoFlush = true;
        /** Connect timeout in milliseconds assigned to the output being built. **/
        private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        /** Reconnect delay in milliseconds assigned to the output being built. **/
        private int reconnectDelayMs = DEFAULT_RECONNECT_DELAY_MS;
        // -- Builder Constructors: ---------------------------------------------
        /**
        * Creates a builder with default values.
        **/
        private Builder(){}
        // -- Builder Methods: --------------------------------------------------
        /**
        * Sets the formatter used to convert log records into strings.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default formatter is used.</p>
        *
        * @param formatter the log formatter, or {@code null} to use the default formatter
        * @return this builder
        **/
        public Builder formatter(LogFormatter formatter){
            if(formatter == null){
                InternalDiagnostic.warn("SocketLogOutput.Builder.formatter: formatter is null -> using TextLogFormatter.PLAIN");
                formatter = TextLogFormatter.PLAIN;
            }
            this.formatter = formatter;
            return this;
        }
        /**
        * Sets the remote host to connect to.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default host is used.</p>
        *
        * @param host the remote host name or IP address, or {@code null} or blank to use the default host
        * @return this builder
        **/
        public Builder host(String host){
            if(host == null || host.isBlank()){
                InternalDiagnostic.warn("SocketLogOutput.Builder.host: host is null/blank -> using DEFAULT_HOST=" + DEFAULT_HOST);
                host = DEFAULT_HOST;
            }
            this.host = host.trim();
            return this;
        }
        /**
        * Sets the remote TCP port to connect to.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default port is used.</p>
        *
        * @param port the port number (1–65535), or an invalid value to use the default port
        * @return this builder
        **/
        public Builder port(int port){
            if(port < MIN_PORT || port > MAX_PORT){
                InternalDiagnostic.warn(
                    "SocketLogOutput.Builder.port: port (" + port + ") out of range (1-65535) -> using DEFAULT_PORT=" + DEFAULT_PORT
                );
                port = DEFAULT_PORT;
            }
            this.port = port;
            return this;
        }
        /**
        * Sets the minimum log level required for a record to be published.
        * Records below this level are silently dropped.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default minimum level is used.</p>
        *
        * @param minLevel the minimum log level, or {@code null} to use the default minimum level
        * @return this builder
        **/
        public Builder minLevel(LogLevel minLevel){
            if(minLevel == null){
                InternalDiagnostic.warn("SocketLogOutput.Builder.minLevel: minLevel is null -> using DEFAULT_MIN_LEVEL=" + DEFAULT_MIN_LEVEL.getLabel());
                minLevel = DEFAULT_MIN_LEVEL;
            }
            this.minLevel = minLevel;
            return this;
        }
        /**
        * Sets an optional filter applied after the level check.
        * Records for which {@link LogFilter#accept(LogRecord)} returns {@code false}
        * are silently dropped. Pass {@code null} to remove a previously set filter.
        *
        * @param filter the filter to apply, or {@code null} to accept all records
        * @return this builder
        **/
        public Builder filter(LogFilter filter){
            this.filter = filter;
            return this;
        }
        /**
        * Sets whether to flush the writer after every published record.
        *
        * <p>
        *   When enabled, each record is immediately sent over the network.
        *   When disabled, records are buffered and only sent when the buffer
        *   is full or {@link SocketLogOutput#flush()} is called.
        * </p>
        *
        * @param autoFlush {@code true} to flush after every record, {@code false} to buffer
        * @return this builder
        **/
        public Builder autoFlush(boolean autoFlush){
            this.autoFlush = autoFlush;
            return this;
        }
        /**
        * Sets the TCP connect timeout used for the initial connection and all
        * subsequent reconnect attempts.
        * A value of {@code 0} means wait indefinitely.
        *
        * <p>If a negative value is passed, an internal diagnostic warning is emitted and the default connect timeout is used.</p>
        *
        * @param ms the connect timeout in milliseconds, or negative to use the default connect timeout
        * @return this builder
        **/
        public Builder connectTimeoutMs(int ms){
            if(ms < MIN_CONNECT_TIMEOUT_MS){
                InternalDiagnostic.warn(
                    "SocketLogOutput.Builder.connectTimeoutMs: ms (" + ms + ") is negative -> using DEFAULT_CONNECT_TIMEOUT_MS=" + DEFAULT_CONNECT_TIMEOUT_MS
                );
                ms = DEFAULT_CONNECT_TIMEOUT_MS;
            }
            connectTimeoutMs = ms;
            return this;
        }
        /**
        * Sets the minimum time to wait between automatic reconnect attempts.
        *
        * <p>
        *   When the connection is lost, a reconnect is attempted on the next
        *   {@link SocketLogOutput#publish(LogRecord)} call, but only if at least
        *   this many milliseconds have elapsed since the previous attempt.
        *   This prevents hammering a downed server on every log call.
        *   A value of {@code 0} disables automatic reconnection entirely.
        * </p>
        *
        * <p>If a negative value is passed, an internal diagnostic warning is emitted and the default reconnect delay is used.</p>
        *
        * @param ms the minimum delay in milliseconds between reconnect attempts (0 to disable), or negative to use the default reconnect delay
        * @return this builder
        **/
        public Builder reconnectDelayMs(int ms){
            if(ms < MIN_RECONNECT_DELAY_MS){
                InternalDiagnostic.warn(
                    "SocketLogOutput.Builder.reconnectDelayMs: ms (" + ms + ") is negative -> using DEFAULT_RECONNECT_DELAY_MS=" + DEFAULT_RECONNECT_DELAY_MS
                );
                ms = DEFAULT_RECONNECT_DELAY_MS;
            }
            reconnectDelayMs = ms;
            return this;
        }
        /**
        * Builds a new {@link SocketLogOutput} and establishes the TCP connection.
        *
        * @return a new socket log output
        **/
        public SocketLogOutput build(){
            return new SocketLogOutput(this);
        }
    }
}