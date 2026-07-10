package boraheeb;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.*;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
// -- Application Class (API Usage Examples): ---------------------------
/**
* Runnable collection of {@code boraheeb.util.logging} API usage examples.
*
* <p>
*   Each public static method below is a self-contained, independently runnable
*   demonstration of one library feature: theme preview, console/file/rolling/
*   async/memory/socket outputs, MDC, filters, the JSON formatter, {@code .properties}
*   configuration, and the logger registry. {@link #main(String[])} runs all of
*   them in sequence with clear console separators, but any single method can be
*   called on its own by a developer who only wants to see one feature in isolation
*   — just call that one method instead of {@code main}.
* </p>
*
* <p>This class is a runnable demo only — it is not part of the library's public API.</p>
*
* <p>To run: java -cp target/classes;target/test-classes boraheeb.Application</p>
**/
public final class Application{
    // -- Constructors: -----------------------------------------------------
    private Application(){}
    // -- Main Method: ------------------------------------------------------
    /**
    * Runs every example in this class, in sequence, with a header printed before each.
    *
    * @param args unused
    * @throws Exception if a demo using a worker thread ({@link #mdcDemo()}) is interrupted
    **/
    public static void main(String[] args) throws Exception{
        themePreviewDemo();
        // consoleOutputDemo();
        // fileOutputDemo();
        // rollingFileOutputDemo();
        // asyncOutputDemo();
        // memoryOutputDemo();
        // socketOutputDemo();
        // mdcDemo();
        // filterDemo();
        // jsonFormatterDemo();
        // loggingConfigDemo();
        // loggerRegistryDemo();
    }
    // -- Examples: ---------------------------------------------------------
    /**
    * Prints the same set of sample records once per built-in {@link LogTheme},
    * with the theme's name printed as a header before it — a quick visual way
    * to preview every bundled color theme side by side.
    **/
    public static void themePreviewDemo(){
        String brand = ""
            + "===========================================================================\n"
            + "     __            ______      ______      _               _               \n"
            + "     \\ \\           | ___ \\     | ___ \\    | |             | |              \n"
            + "      \\ \\          | |_/ / ___ | |_/ /__ _| |__   ___  ___| |__            \n"
            + "      / /          | ___ \\/ _ \\|    // _` | '_ \\ / _ \\/ _ \\ '_ \\           \n"
            + "     /_/   ______  | |_/ / (_) | |\\ \\ (_| | | | |  __/  __/ |_) |          \n"
            + "          /_____/  \\____/ \\___/\\_| \\_\\__,_|_| |_|\\___|\\___|_.__/           \n"
            + "===========================================================================";
        System.out.println("\n=== Theme Preview ===");
        LogRecord[] sampleRecords = buildSampleRecords();
        for(LogTheme theme : LogTheme.getValues()){
            // Clear the previous theme's background first, then apply this theme's own
            // background (if it has one) before printing the header and banner, so both
            // render inside the new theme's background instead of the old one or the
            // terminal's default. ConsoleLogOutput re-applies the same background code
            // when it is constructed below — a harmless, idempotent repeat.
            System.out.print(Ansi.RESET);
            if(theme.hasTerminalBackground()) System.out.print(theme.getTerminalBackground());
            System.out.println("\n--- Theme: " + theme.getThemeName() + " ---");
            System.out.println(brand);
            Logger logger = Logger.builder("Demo")
                .addOutput(
                    ConsoleLogOutput.builder()
                        .formatter(
                            TextLogFormatter.builder()
                                .dateTime(LogDateTime.ofPreset(LogDateTimePreset.LOG_WITH_NANOS))
                                .showSourceName(true)
                                .showLineNumber(true)
                                .showThreadName(true)
                                .theme(theme)
                                .build()
                        )
                        .build()
                )
                .build();
            for(LogRecord record : sampleRecords) logger.log(record);
            logger.close();
        }
        // Leave the terminal in its default state — otherwise the last theme's
        // background color stays active even after this method returns.
        System.out.print(Ansi.RESET);
    }
    /**
    * The simplest possible setup: one {@link Logger}, one {@link ConsoleLogOutput}
    * with default styling, and the SLF4J-style {@code "{}"} placeholder syntax.
    **/
    public static void consoleOutputDemo(){
        System.out.println("\n=== Console Output ===");
        Logger logger = Logger.builder("app")
            .addOutput(ConsoleLogOutput.builder().build())
            .build();
        logger.info("Hello {}", "world");
        logger.warn("low disk: {} MB left", 42);
        logger.error("request failed", new IllegalStateException("boom"));
        logger.close();
    }
    /**
    * Writes plain-text records to a file. {@link FileLogOutput} grows without
    * bound — see {@link #rollingFileOutputDemo()} for a bounded alternative.
    **/
    public static void fileOutputDemo(){
        System.out.println("\n=== File Output ===");
        LogOutput file = FileLogOutput.builder()
            .path("logs/app.log")
            .build();
        Logger logger = Logger.builder("app.file").addOutput(file).build();
        logger.info("Writing to logs/app.log");
        logger.close();
        System.out.println("Wrote a record to logs/app.log");
    }
    /**
    * Rolls the log file once it crosses a small size threshold (intentionally
    * tiny here so a roll is visible within this short demo) and keeps only the
    * most recent 3 rolled copies.
    **/
    public static void rollingFileOutputDemo(){
        System.out.println("\n=== Rolling File Output ===");
        LogOutput rolling = RollingFileLogOutput.builder()
            .path("logs/rolling-demo.log")
            .maxSizeBytes(10 * 1024)
            .maxFiles(3)
            .build();
        Logger logger = Logger.builder("app.rolling").addOutput(rolling).build();
        for(int i = 0; i < 200; i++)
            logger.info("Rolling demo record #{} with some padding text to grow the file faster", i);
        logger.close();
        System.out.println("Wrote demo records to logs/rolling-demo.log (rolls at 10 KB, keeps 3 files)");
    }
    /**
    * Wraps a {@link ConsoleLogOutput} in an {@link AsyncLogOutput} so publishing
    * returns immediately on the calling thread. {@link Logger#close()} drains the
    * queue before returning, and {@link AsyncLogOutput#stats()} exposes what happened.
    **/
    public static void asyncOutputDemo(){
        System.out.println("\n=== Async Output ===");
        LogOutput console = ConsoleLogOutput.builder().build();
        AsyncLogOutput async = AsyncLogOutput.builder(console)
            .queueCapacity(1024)
            .overflowPolicy(AsyncLogOutput.OverflowPolicy.DROP_OLDEST)
            .build();
        Logger logger = Logger.builder("app.async").addOutput(async).build();
        for(int i = 0; i < 5; i++) logger.info("Async record #{}", i);
        logger.close();
        System.out.println("Stats: " + async.stats());
    }
    /**
    * Buffers the last N records in memory with no I/O, then dumps a snapshot to
    * the console — the "flight recorder" pattern for capturing recent context
    * cheaply, without writing every record to disk.
    **/
    public static void memoryOutputDemo(){
        System.out.println("\n=== In-Memory Flight Recorder ===");
        MemoryLogOutput recent = MemoryLogOutput.builder().capacity(50).build();
        Logger logger = Logger.builder("app.memory").addOutput(recent).build();
        for(int i = 0; i < 5; i++) logger.debug("Buffered record #{}", i);

        System.out.println("Snapshot has " + recent.snapshot().size() + " records");
        recent.dumpTo(ConsoleLogOutput.builder().build());
        logger.close();
    }
    /**
    * Connects to a port nothing is listening on, on purpose — demonstrates that
    * {@link SocketLogOutput} never throws even when the connection fails; it just
    * fails soft and reports {@link SocketLogOutput#isConnected()} as {@code false}.
    **/
    public static void socketOutputDemo(){
        System.out.println("\n=== Socket Output (fail-soft demo, no server required) ===");
        SocketLogOutput socket = SocketLogOutput.builder()
            .host("localhost")
            .port(65000)
            .reconnectDelayMs(1000)
            .build();
        Logger logger = Logger.builder("app.socket").addOutput(socket).build();
        logger.info("This record is dropped silently since no server is listening");
        System.out.println("isConnected() = " + socket.isConnected() + " (expected: false, no server running)");
        logger.close();
    }
    /**
    * Shows {@link MDC} context merged automatically into records on the same
    * thread, then propagated to a worker thread via {@link MDC#wrap(Runnable)}.
    *
    * @throws Exception if the worker thread task is interrupted while waiting for its result
    **/
    public static void mdcDemo() throws Exception{
        System.out.println("\n=== MDC (contextual fields) ===");
        Logger logger = Logger.builder("app.mdc")
            .addOutput(ConsoleLogOutput.builder().build())
            .build();

        MDC.put("requestId", "REQ-8821");
        try{
            logger.info("Handling request on the calling thread");
        }finally{
            MDC.clear();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try{
            MDC.put("requestId", "REQ-9000");
            executor.submit(MDC.wrap(() -> logger.info("Runs on a worker thread with the caller's MDC context"))).get();
        }finally{
            MDC.clear();
            executor.shutdown();
        }
        logger.close();
    }
    /**
    * Composes a level filter and a logger-name filter with {@link LogFilter#and(LogFilter)}
    * and attaches the result to an output, so only matching records reach it.
    **/
    public static void filterDemo(){
        System.out.println("\n=== Filters ===");
        LogFilter warnAndAbove = LevelFilter.atLeast(LogLevel.WARN);
        LogFilter dbOrAuth = LoggerNameFilter.include("db", "auth");
        LogFilter combined = warnAndAbove.and(dbOrAuth);

        Logger logger = Logger.builder("db")
            .addOutput(
                ConsoleLogOutput.builder()
                    .filter(combined)
                    .build()
            )
            .build();

        logger.info("This INFO record is filtered out (below WARN)");
        logger.warn("This WARN record passes (WARN+ and logger name \"db\")");
        logger.close();
    }
    /**
    * Same {@link Logger}, different {@link LogFormatter} — {@link JsonLogFormatter#DEFAULT}
    * renders each record as one JSON object per line instead of styled text.
    **/
    public static void jsonFormatterDemo(){
        System.out.println("\n=== JSON Formatter ===");
        Logger logger = Logger.builder("app.json")
            .addOutput(
                ConsoleLogOutput.builder()
                    .formatter(JsonLogFormatter.DEFAULT)
                    .build()
            )
            .build();
        logger.info("user {} logged in", "ali");
        logger.close();
    }
    /**
    * Registers a logger, then uses {@link LoggingConfig} to resolve and apply a
    * minimum level to it from a {@code .properties}-style prefix rule.
    **/
    public static void loggingConfigDemo(){
        System.out.println("\n=== LoggingConfig (.properties) ===");
        Properties props = new Properties();
        props.setProperty("log.level.root", "INFO");
        props.setProperty("log.level.app.verbose.*", "DEBUG");

        LoggerRegistry registry = new LoggerRegistry();
        Logger verbose = registry.getLogger("app.verbose.Worker");
        verbose.setMinLevel(LogLevel.ERROR);

        LoggingConfig config = LoggingConfig.load(props);
        config.applyTo(registry);

        System.out.println("Resolved level for app.verbose.Worker: " + verbose.getMinLevel());
        registry.closeAll();
    }
    /**
    * Registers a logger with the global {@link LoggerRegistry} singleton, looks
    * it up by name from elsewhere, then closes and removes just that one logger.
    **/
    public static void loggerRegistryDemo(){
        System.out.println("\n=== LoggerRegistry ===");
        LoggerRegistry registry = LoggerRegistry.getInstance();
        registry.register(
            Logger.builder("app.registry-demo")
                .addOutput(ConsoleLogOutput.builder().build())
                .build()
        );
        Logger logger = registry.getLogger("app.registry-demo");
        logger.info("Retrieved from the registry by name");
        registry.close("app.registry-demo");
    }
    // -- Private Helper Methods: ---------------------------------------------
    /**
    * Builds a fixed set of sample records spanning every {@link LogLevel}, used
    * by {@link #themePreviewDemo()} so every theme is previewed with identical data.
    **/
    private static LogRecord[] buildSampleRecords(){
        return new LogRecord[]{
            LogRecord.builder()
                .level(LogLevel.TRACE)
                .loggerName("Demo")
                .sourceName("DatabaseConnection.java")
                .lineNumber(112)
                .message("Entering fetchUserById - scanning connection pool")
                .field("userId", 9021)
                .field("poolSize", 10)
                .field("activeConnections", 3)
                .field("method", "fetchUserById")
                .build(),
            LogRecord.builder()
                .level(LogLevel.DEBUG)
                .loggerName("Demo")
                .sourceName("QueryBuilder.java")
                .lineNumber(87)
                .message("SQL query built successfully")
                .field("query", "SELECT * FROM users WHERE id = ?")
                .field("params", "[9021]")
                .field("estimatedRows", 1)
                .field("cached", false)
                .build(),
            LogRecord.builder()
                .level(LogLevel.INFO)
                .loggerName("Demo")
                .sourceName("UserService.java")
                .lineNumber(204)
                .message("User loaded successfully")
                .field("userId", 9021)
                .field("username", "ali.yahya")
                .field("role", "admin")
                .field("durationMs", 14)
                .build(),
            LogRecord.builder()
                .level(LogLevel.WARN)
                .loggerName("Demo")
                .sourceName("CacheManager.java")
                .lineNumber(318)
                .message("Cache miss: falling back to database")
                .field("cacheKey", "user:9021")
                .field("ttlSeconds", 300)
                .field("fallback", "database")
                .field("missCount", 47)
                .throwable(new IllegalStateException("Cache entry expired before TTL"))
                .build(),
            LogRecord.builder()
                .level(LogLevel.ERROR)
                .loggerName("Demo")
                .sourceName("PaymentGateway.java")
                .lineNumber(456)
                .message("Payment transaction failed: user not charged")
                .field("transactionId", "TXN-20240520-8821")
                .field("userId", 9021)
                .field("amount", "149.99")
                .field("currency", "USD")
                .field("gateway", "Stripe")
                .field("retryable", true)
                .throwable(new RuntimeException("Connection timeout after 5000ms"))
                .build(),
            LogRecord.builder()
                .level(LogLevel.CRITICAL)
                .loggerName("Demo")
                .sourceName("DatabasePool.java")
                .lineNumber(91)
                .message("All database connections exhausted: service unavailable")
                .field("poolSize", 10)
                .field("activeConnections", 10)
                .field("queuedRequests", 238)
                .field("uptime", "4d 17h 32m")
                .field("host", "db-primary.internal")
                .throwable(new OutOfMemoryError("Unable to allocate connection: heap exhausted"))
                .build()
        };
    }
}