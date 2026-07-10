package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
// -- RollingFileLogOutput Class: ---------------------------------------
/**
* A {@link LogOutput} that writes log records to a file and rolls it
* automatically when a size or time threshold is reached.
*
* <p>
*   The active file always keeps the configured base path (for example,
*   {@code logs/app.log}). When a roll is triggered, the current file is
*   renamed to a timestamped copy (for example, {@code logs/app-20260517-143022.log})
*   and a new file is opened at the base path.
* </p>
*
* <p>
*   Two rolling strategies are supported and can be used independently or
*   together, and whichever condition is satisfied first triggers a roll:
* </p>
* <ul>
*   <li>
*       <b>Size-based</b>: rolls when the file size reaches
*       {@link Builder#maxSizeBytes(long)}. A value of {@code 0} disables
*       size rolling. Size is tracked in bytes by encoding each formatted
*       record as UTF-8 before accumulating.
*   </li>
*   <li>
*       <b>Time-based</b>: rolls at the next {@link RollingPeriod} boundary
*       ({@code HOURLY} or {@code DAILY}). Set to {@code null} to disable
*       time rolling.
*   </li>
* </ul>
*
* <p>
*   The default formatter is {@link TextLogFormatter#PLAIN} — no ANSI escape
*   codes are written to the file. Pass a custom formatter to override this.
* </p>
*
* <p>
*   If the file cannot be opened at construction time, or a reopen after a roll fails,
*   an internal diagnostic error is emitted and {@link #flush()} becomes a no-op while
*   the writer is unavailable. {@link #publish(LogRecord)} retries opening the writer on
*   each call, rate-limited to once every 5 seconds, and resumes publishing automatically
*   once the retry succeeds — no restart is required.
* </p>
*
* <p>
*   When {@link Builder#maxFiles(int)} is greater than zero, the oldest rolled
*   files matching the base name pattern are deleted automatically after each
*   roll so that at most {@code maxFiles} rolled copies are retained.
* </p>
*
* <p>
*   Calls are {@code synchronized} to prevent interleaved output from
*   concurrent threads. {@link #close()} flushes and closes the writer.
* </p>
*
* <p>Instances are created via the {@link Builder}.</p>
*
* <p>This class is thread-safe.</p>
*
* @author BoRaheeb
**/
public final class RollingFileLogOutput implements LogOutput{
    // -- Constants: --------------------------------------------------------
    /** Default log file path used when none is configured. **/
    private static final Path DEFAULT_PATH = Path.of("logs", "app.log");
    /** Byte length of the platform line separator in UTF-8, used for accurate size tracking. **/
    private static final int LINE_SEPARATOR_BYTES = System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
    /** Default rolling period applied when none is specified. **/
    private static final RollingPeriod DEFAULT_ROLLING_PERIOD = RollingPeriod.DAILY;
    /** Zone used for both naming rolled files and computing time-based roll boundaries, so the two always agree. **/
    private static final ZoneId ROLL_ZONE = ZoneId.systemDefault();
    /** Timestamp format used to name rolled files. Zone pre-applied so {@link Instant} values format directly. **/
    private static final DateTimeFormatter ROLL_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ROLL_ZONE);
    /**
    * Shape of the timestamp segment a rolled file name must have between the stem
    * and extension to be recognized as one this output rolled — {@code yyyyMMdd-HHmmss},
    * optionally followed by a same-second counter suffix (see {@link #buildRolledPath()}).
    * Used to filter the directory glob in {@link #loadExistingRolledFiles()} so files
    * that merely share a name prefix (for example, another output's {@code app-errors.log})
    * are never mistaken for this output's rolled files and pruned.
    **/
    private static final Pattern ROLLED_TIMESTAMP_SHAPE = Pattern.compile("\\d{8}-\\d{6}(-\\d+)?");
    /** Default maximum number of rolled files to retain. **/
    private static final int DEFAULT_MAX_FILES = 7;
    /**
    * Minimum time in nanoseconds between automatic retries to reopen the writer after
    * a roll's reopen attempt failed. Rate-limits {@link #publish(LogRecord)} so a
    * persistently broken disk or directory is not retried on every single call.
    **/
    private static final long REOPEN_RETRY_INTERVAL_NANOS = 5_000_000_000L;
    // -- RollingPeriod Enum: -----------------------------------------------
    /** Time boundary at which a time-based roll is triggered. **/
    public enum RollingPeriod{
        /** Roll at the start of each new hour. **/
        HOURLY,
        /** Roll at the start of each new day. **/
        DAILY
    }
    // -- Fields: -----------------------------------------------------------
    /** Formatter used to convert log records into plain-text strings. **/
    private final LogFormatter formatter;
    /** Base path of the active log file. **/
    private final Path path;
    /** Minimum log level required for a record to be published. **/
    private LogLevel minLevel;
    /** Optional filter applied after the level check; {@code null} means accept all. **/
    private LogFilter filter;
    /** Whether to flush after every published record. **/
    private boolean autoFlush;
    /** Maximum file size in bytes before a size-based roll; 0 disables size rolling. **/
    private long maxSizeBytes;
    /** Time period for time-based rolling; {@code null} disables time rolling. **/
    private RollingPeriod rollingPeriod;
    /** Accumulated byte count of the current file, used for size-based rolling. **/
    private long currentSizeBytes;
    /** Time of the next time-based roll; {@code null} if time rolling is disabled. **/
    private Instant nextRollTime;
    /** Maximum number of rolled files to retain; 0 disables pruning (keep all). **/
    private int maxFiles;
    /**
    * In-memory queue of rolled file paths in chronological order (oldest first).
    * Used to track and prune rolled files without a directory scan on every roll.
    * Only populated when {@link #maxFiles} is greater than zero.
    **/
    private final Deque<Path> rolledFiles;
    /** Buffered writer to the active log file. {@code null} if the file could not be opened. **/
    private BufferedWriter writer;
    /**
    * Monotonic timestamp ({@link System#nanoTime}) of the last attempt to reopen the writer
    * after a reopen failure; {@code 0} means no attempt has been made yet. Used to rate-limit
    * retries in {@link #publish(LogRecord)} to {@link #REOPEN_RETRY_INTERVAL_NANOS}.
    **/
    private long lastReopenAttemptNanos = 0;
    /** Whether this output has been closed. **/
    private volatile boolean closed = false;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a rolling file log output from the given builder.
    *
    * @param builder the builder containing output values
    **/
    private RollingFileLogOutput(Builder builder){
        this.formatter = builder.formatter;
        this.path = builder.path;
        this.minLevel = builder.minLevel;
        this.filter = builder.filter;
        this.autoFlush = builder.autoFlush;
        this.maxSizeBytes = builder.maxSizeBytes;
        this.rollingPeriod = builder.rollingPeriod;
        this.nextRollTime = ((rollingPeriod != null)? computeNextRollTime(Instant.now()) : null);
        this.maxFiles = builder.maxFiles;
        this.rolledFiles = ((maxFiles > 0)? loadExistingRolledFiles() : new ArrayDeque<>(0));
        this.writer = openWriter(builder.append);
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
    * Returns the base path of the active log file.
    *
    * @return the log file path, never {@code null}
    **/
    public Path getPath(){
        return path;
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
    * Returns the maximum file size in bytes before a size-based roll,
    * or {@code 0} if size rolling is disabled.
    *
    * @return the maximum size in bytes, or 0
    **/
    public long getMaxSizeBytes(){
        return maxSizeBytes;
    }
    /**
    * Returns the time period used for time-based rolling,
    * or {@code null} if time rolling is disabled.
    *
    * @return the rolling period, or {@code null}
    **/
    public RollingPeriod getRollingPeriod(){
        return rollingPeriod;
    }
    /**
    * Returns the maximum number of rolled files retained after each roll,
    * or {@code 0} if pruning is disabled.
    *
    * @return the maximum number of retained files, or 0 if pruning is disabled
    **/
    public int getMaxFiles(){
        return maxFiles;
    }
    /**
    * Returns {@code true} if the file is open and this output has not been closed.
    *
    * @return {@code true} if the file is open and this output has not been closed, otherwise {@code false}
    **/
    public synchronized boolean isOpen(){
        return (!closed && writer != null);
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
            InternalDiagnostic.warn("RollingFileLogOutput.setMinLevel: minLevel is null -> ignored");
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
    * Replaces the maximum file size threshold for size-based rolling at runtime.
    * The new threshold takes effect on the next publish.
    * Pass {@code 0} to disable size-based rolling.
    *
    * <p>If a negative value is passed, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param max the new maximum size in bytes (0 to disable); ignored if negative
    **/
    public synchronized void setMaxSizeBytes(long max){
        if(max < 0){
            InternalDiagnostic.warn("RollingFileLogOutput.setMaxSizeBytes: max (" + max + ") is negative -> ignored");
            return;
        }
        maxSizeBytes = max;
    }
    /**
    * Replaces the time-based rolling period at runtime and immediately
    * recomputes the next roll boundary.
    * Pass {@code null} to disable time-based rolling.
    *
    * @param period the new rolling period, or {@code null} to disable
    **/
    public synchronized void setRollingPeriod(RollingPeriod period){
        rollingPeriod = period;
        nextRollTime = ((period != null)? computeNextRollTime(Instant.now()) : null);
    }
    /**
    * Replaces the maximum number of rolled files to retain at runtime.
    * The new limit takes effect on the next roll — no immediate pruning is performed.
    * Pass {@code 0} to disable pruning.
    *
    * <p>If a negative value is passed, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param max the new maximum number of retained files (0 to disable pruning); ignored if negative
    **/
    public synchronized void setMaxFiles(int max){
        if(max < 0){
            InternalDiagnostic.warn("RollingFileLogOutput.setMaxFiles: max (" + max + ") is negative -> ignored");
            return;
        }
        maxFiles = max;
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing a {@link RollingFileLogOutput}.
    *
    * @return a new rolling file log output builder
    **/
    public static Builder builder(){
        return new Builder();
    }
    // -- LogOutput Methods: ------------------------------------------------
    /**
    * Formats and appends the given log record to the active log file,
    * then rolls the file if a size or time threshold has been reached.
    *
    * <p>
    *   Records below the configured minimum level are silently dropped.
    *   A {@code null} record is ignored with an internal diagnostic warning.
    *   Does nothing if this output has been closed.
    * </p>
    *
    * <p>
    *   If the writer is unavailable — construction failed to open the file, or a
    *   previous roll's reopen attempt failed — this method retries opening the writer,
    *   rate-limited to once every {@link #REOPEN_RETRY_INTERVAL_NANOS} so a persistently
    *   broken disk or directory is not retried on every call. Once the writer reopens
    *   successfully, publishing resumes automatically with no restart required. Until
    *   then, the record is dropped.
    * </p>
    *
    * <p>If failed to write to the file, an internal diagnostic error is emitted and the record is dropped.</p>
    *
    * @param record the log record to publish
    **/
    @Override
    public synchronized void publish(LogRecord record){
        if(closed) return;
        if(writer == null){
            long now = System.nanoTime();
            if(now - lastReopenAttemptNanos < REOPEN_RETRY_INTERVAL_NANOS) return;
            lastReopenAttemptNanos = now;
            writer = openWriter(true);
            if(writer == null) return;
        }
        if(record == null){
            InternalDiagnostic.warn("RollingFileLogOutput.publish: record is null -> ignored");
            return;
        }
        if(!record.getLevel().isAtLeast(minLevel)) return;
        if(filter != null && !filter.accept(record)) return;
        try{
            String formatted = formatter.format(record);
            writer.write(formatted);
            writer.newLine();
            if(autoFlush) writer.flush();
            if(maxSizeBytes > 0)
                currentSizeBytes += formatted.getBytes(StandardCharsets.UTF_8).length + LINE_SEPARATOR_BYTES;
            if(shouldRollOnSize() || shouldRollOnTime()) roll();
        }catch(IOException ex){
            InternalDiagnostic.error(
                "RollingFileLogOutput.publish: failed to write to \"" + path + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> record dropped"
            );
        }
    }
    /**
    * Flushes any buffered records to the active log file.
    * Does nothing if this output has been closed or failed to open.
    *
    * <p>If failed to flush, an internal diagnostic error is emitted and pending records may be lost.</p>
    **/
    @Override
    public synchronized void flush(){
        if(closed || writer == null) return;
        try{
            writer.flush();
        }catch(IOException ex){
            InternalDiagnostic.error(
                "RollingFileLogOutput.flush: failed to flush \"" + path + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> pending records may be lost"
            );
        }
    }
    /**
    * Flushes and closes the active log file writer.
    *
    * <p>
    *   Calls to {@link #publish(LogRecord)} and {@link #flush()} after closing
    *   are silently ignored.
    * </p>
    *
    * <p>If failed to close, an internal diagnostic error is emitted and resources may not be released properly.</p>
    **/
    @Override
    public synchronized void close(){
        if(closed) return;
        closed = true;
        if(writer == null) return;
        try{
            writer.flush();
            writer.close();
        }catch(IOException ex){
            InternalDiagnostic.error(
                "RollingFileLogOutput.close: failed to close \"" + path + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> resources may not be released properly"
            );
        }
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Returns {@code true} if the accumulated file size has reached the configured limit, otherwise {@code false}.
    **/
    private boolean shouldRollOnSize(){
        return (maxSizeBytes > 0 && currentSizeBytes >= maxSizeBytes);
    }
    /**
    * Returns {@code true} if the current time has passed the next scheduled roll time, otherwise {@code false}.
    **/
    private boolean shouldRollOnTime(){
        return (rollingPeriod != null && nextRollTime != null && Instant.now().isAfter(nextRollTime));
    }
    /**
    * Computes the next roll time based on the configured {@link RollingPeriod}.
    *
    * <p>
    *   The boundary is computed in {@link #ROLL_ZONE} (the same zone used to name
    *   rolled files), so a daily roll fires at local midnight and an hourly roll
    *   at the local hour boundary — not at the UTC boundary that a raw
    *   {@link Instant} truncation would produce.
    * </p>
    *
    * @param from the reference time to compute from
    * @return the next roll boundary
    **/
    private Instant computeNextRollTime(Instant from){
        ZonedDateTime zoned = from.atZone(ROLL_ZONE);
        if(rollingPeriod == RollingPeriod.HOURLY)
            return zoned.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant();
        else
            return zoned.truncatedTo(ChronoUnit.DAYS).plusDays(1).toInstant();
    }
    /**
    * Builds the path for the rolled file by inserting a timestamp between
    * the stem and extension of the base file name.
    *
    * <p>
    *   If a file with the generated name already exists (same-second roll),
    *   a numeric counter suffix is appended until a unique name is found.
    * </p>
    *
    * @return a unique path for the rolled file
    **/
    private Path buildRolledPath(){
        String timestamp = ROLL_TIMESTAMP_FORMAT.format(Instant.now());
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = ((dot >= 0)? fileName.substring(0, dot) : fileName);
        String extension = ((dot >= 0)? fileName.substring(dot) : "");
        Path parent = ((path.getParent() != null)? path.getParent() : Path.of("."));
        Path candidate = parent.resolve(stem + "-" + timestamp + extension);
        int counter = 1;
        while(Files.exists(candidate)){
            candidate = parent.resolve(stem + "-" + timestamp + "-" + counter + extension);
            counter++;
        }
        return candidate;
    }
    /**
    * Scans the log directory once at construction time to populate the in-memory
    * rolled-file queue with any rolled files left from a previous run.
    *
    * <p>
    *   Files are sorted lexicographically before being added to the queue, which
    *   equals chronological order given the {@code yyyyMMdd-HHmmss} timestamp format.
    *   Any excess files beyond {@link #maxFiles} are pruned immediately so the
    *   queue never starts over-full.
    * </p>
    *
    * <p>
    *   If failed to list existing rolled files, an internal diagnostic error is emitted and the existing rolled files will not be tracked or pruned.
    *   If failed to prune excess files, an internal diagnostic error is emitted and excess files may not be pruned until the next roll.
    * </p>
    *
    * @return an ordered deque of existing rolled file paths, oldest first
    **/
    private Deque<Path> loadExistingRolledFiles(){
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = ((dot >= 0)? fileName.substring(0, dot) : fileName);
        String extension = ((dot >= 0)? fileName.substring(dot) : "");
        Path parent = ((path.getParent() != null)? path.getParent() : Path.of("."));
        List<Path> found = new ArrayList<>();
        if(Files.exists(parent)){
            try(DirectoryStream<Path> stream = Files.newDirectoryStream(parent, stem + "-*" + extension)){
                for(Path foundPath : stream){
                    String candidate = foundPath.getFileName().toString();
                    int middleStart = stem.length() + 1;
                    int middleEnd = candidate.length() - extension.length();
                    if(middleStart > middleEnd) continue;
                    String middle = candidate.substring(middleStart, middleEnd);
                    if(ROLLED_TIMESTAMP_SHAPE.matcher(middle).matches()) found.add(foundPath);
                }
            }catch(IOException ex){
                InternalDiagnostic.error(
                    "RollingFileLogOutput.loadExistingRolledFiles: failed to list existing rolled files in \"" + parent + "\" -> cause: " +
                    ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> existing rolled files will not be tracked or pruned"
                );
            }
        }
        Collections.sort(found);
        ArrayDeque<Path> deque = new ArrayDeque<>(found);
        while(deque.size() > maxFiles){
            Path oldest = deque.pollFirst();
            try{
                Files.deleteIfExists(oldest);
            }catch(IOException ex){
                InternalDiagnostic.error(
                    "RollingFileLogOutput.loadExistingRolledFiles: failed to prune excess rolled file \"" + oldest + "\" -> cause: " +
                    ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> excess rolled files may not be pruned until next roll"
                );
            }
        }
        return deque;
    }
    /**
    * Opens a buffered writer to {@link #path}, creating the file and any
    * missing parent directories as needed.
    *
    * <p>If the file cannot be opened, an internal diagnostic error is emitted and {@code null} is returned.</p>
    *
    * @param append {@code true} to append to an existing file; {@code false} to overwrite
    * @return the opened writer, or {@code null} if the file could not be opened
    **/
    private BufferedWriter openWriter(boolean append){
        try{
            Path parent = path.getParent();
            if(parent != null) Files.createDirectories(parent);
            if(append && Files.exists(path)){
                try{
                    currentSizeBytes = Files.size(path);
                }catch(IOException ignore){
                    currentSizeBytes = 0;
                }
                return Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
                );
            }else{
                currentSizeBytes = 0;
                return Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
                );
            }
        }catch(IOException ex){
            InternalDiagnostic.error(
                "RollingFileLogOutput.openWriter: failed to open file \"" + path + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> output will be disabled"
            );
            return null;
        }
    }
    /**
    * Performs a log roll: renames the current file to a timestamped copy,
    * prunes old files if needed, then closes the old writer and opens a new one.
    *
    * <p>If failed to delete old rolled files, an internal diagnostic error is emitted and excess files may not be pruned until the next roll.</p>
    *
    * <p>
    *   The rename is attempted before the writer is closed. If the rename fails,
    *   an internal diagnostic error is emitted and the existing writer is kept so
    *   that no records are lost. If the rename succeeds but the new writer cannot
    *   be opened, records are dropped until {@link #publish(LogRecord)} successfully
    *   retries opening the writer, rate-limited to once every
    *   {@link #REOPEN_RETRY_INTERVAL_NANOS} — no restart is required for logging to resume.
    * </p>
    *
    * <p>If failed to close the old writer, an internal diagnostic error is emitted and resources for the previous file may not be released properly.</p>
    **/
    private void roll(){
        if(Files.exists(path)){
            Path rolled = buildRolledPath();
            try{
                Files.move(path, rolled);
                if(maxFiles > 0){
                    rolledFiles.addLast(rolled);
                    while(rolledFiles.size() > maxFiles){
                        Path oldest = rolledFiles.pollFirst();
                        try{
                            Files.deleteIfExists(oldest);
                        }catch(IOException ex){
                            InternalDiagnostic.error(
                                "RollingFileLogOutput.roll: failed to delete \"" + oldest + "\" -> cause: " +
                                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> excess rolled files may not be pruned until next roll"
                            );
                        }
                    }
                }
            }catch(IOException ex){
                InternalDiagnostic.error(
                    "RollingFileLogOutput.roll: failed to rename \"" + path + "\" to \"" + rolled + "\" -> cause: " +
                    ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> roll canceled, current file will continue to be used"
                );
                return;
            }
        }
        if(writer != null){
            try{
                writer.flush();
                writer.close();
            }catch(IOException ex){
                InternalDiagnostic.error(
                    "RollingFileLogOutput.roll: failed to close writer after rename -> cause: " +
                    ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> resources for previous file may not be released properly"
                );
            }
            writer = null;
        }
        currentSizeBytes = 0;
        if(rollingPeriod != null)
            nextRollTime = computeNextRollTime(Instant.now());
        writer = openWriter(false);
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link RollingFileLogOutput} instances.
    *
    * <p>
    *   Defaults: plain-text formatter (no ANSI codes) {@link TextLogFormatter#PLAIN}, {@code logs/app.log} path,
    *   minimum level {@link LogLevel#TRACE}, no filter, auto-flush enabled, append mode enabled,
    *   size rolling disabled ({@code 0}), daily time rolling, and {@code 7} max retained files.
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Formatter assigned to the output being built. **/
        private LogFormatter formatter = TextLogFormatter.PLAIN;
        /** Log file path assigned to the output being built. **/
        private Path path = DEFAULT_PATH;
        /** Minimum level assigned to the output being built. **/
        private LogLevel minLevel = DEFAULT_MIN_LEVEL;
        /** Optional filter assigned to the output being built; {@code null} means accept all. **/
        private LogFilter filter = null;
        /** Whether to flush the writer after every published record. **/
        private boolean autoFlush = true;
        /** Whether to append to an existing file; {@code false} overwrites. **/
        private boolean append = true;
        /** Maximum file size in bytes before a size-based roll; 0 disables size rolling. **/
        private long maxSizeBytes = 0;
        /** Time period for time-based rolling; {@code null} disables time rolling. **/
        private RollingPeriod rollingPeriod = DEFAULT_ROLLING_PERIOD;
        /** Maximum number of rolled files to retain; 0 disables pruning (keep all). **/
        private int maxFiles = DEFAULT_MAX_FILES;
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
                InternalDiagnostic.warn("RollingFileLogOutput.Builder.formatter: formatter is null -> using TextLogFormatter.PLAIN");
                formatter = TextLogFormatter.PLAIN;
            }
            this.formatter = formatter;
            return this;
        }
        /**
        * Sets the path of the target log file.
        * Parent directories are created automatically if they do not exist.
        *
        * <p>If {@code null} is passed, an internal diagnostic warning is emitted and the default path is used.</p>
        *
        * @param path the log file path, or {@code null} to use the default path
        * @return this builder
        **/
        public Builder path(Path path){
            if(path == null){
                InternalDiagnostic.warn("RollingFileLogOutput.Builder.path: path is null -> using DEFAULT_PATH=" + DEFAULT_PATH);
                path = DEFAULT_PATH;
            }
            this.path = path;
            return this;
        }
        /**
        * Sets the path of the target log file from a string.
        * Parent directories are created automatically if they do not exist.
        *
        * <p>If {@code null} or blank is passed, an internal diagnostic warning is emitted and the default path is used.</p>
        *
        * @param path the log file path string, or {@code null} or blank to use the default path
        * @return this builder
        **/
        public Builder path(String path){
            if(path == null || path.isBlank()){
                InternalDiagnostic.warn("RollingFileLogOutput.Builder.path: path string is null/blank -> using DEFAULT_PATH=" + DEFAULT_PATH);
                this.path = DEFAULT_PATH;
            }else this.path = Path.of(path.trim());
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
                InternalDiagnostic.warn("RollingFileLogOutput.Builder.minLevel: minLevel is null -> using DEFAULT_MIN_LEVEL=" + DEFAULT_MIN_LEVEL.getLabel());
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
        *   When enabled, each record is immediately written to disk at the cost
        *   of reduced throughput. When disabled, records are buffered and only
        *   written when the buffer is full or {@link RollingFileLogOutput#flush()}
        *   is called.
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
        * Sets whether to append to an existing log file on open.
        * When {@code false}, the file is overwritten on open.
        *
        * @param append {@code true} to append, {@code false} to overwrite
        * @return this builder
        **/
        public Builder append(boolean append){
            this.append = append;
            return this;
        }
        /**
        * Sets the maximum file size in bytes before a size-based roll is triggered.
        *
        * <p>
        *   Size is tracked by encoding each formatted record as UTF-8 and accumulating
        *   the byte count. {@link java.nio.file.Files#size} is used to seed the count
        *   when appending to an existing file, so the two measures are always consistent.
        *   A value of {@code 0} disables size-based rolling entirely.
        * </p>
        *
        * <p>
        *   Common values: {@code 10 * 1024 * 1024} for 10 MB,
        *   {@code 50 * 1024 * 1024} for 50 MB.
        * </p>
        *
        * <p>If a negative value is passed, an internal diagnostic warning is emitted and size rolling is disabled.</p>
        *
        * @param max the maximum size in bytes (0 to disable), or negative to disable
        * @return this builder
        **/
        public Builder maxSizeBytes(long max){
            if(max < 0){
                InternalDiagnostic.warn(
                    "RollingFileLogOutput.Builder.maxSizeBytes: max (" + max + ") is negative -> using 0 (disabled)"
                );
                maxSizeBytes = 0;
            }else maxSizeBytes = max;
            return this;
        }
        /**
        * Sets the time period for time-based rolling.
        * Pass {@code null} to disable time-based rolling.
        *
        * <p>
        *   Rolling is evaluated lazily on {@link RollingFileLogOutput#publish(LogRecord)}:
        *   a roll fires on the first record published <em>after</em> the period
        *   boundary, not exactly at the boundary itself. A logger that stays
        *   idle across a boundary (for example, over midnight for {@link RollingPeriod#DAILY})
        *   therefore rolls on its next write rather than at the boundary time.
        * </p>
        *
        * @param period the rolling period, or {@code null} to disable
        * @return this builder
        **/
        public Builder rollingPeriod(RollingPeriod period){
            rollingPeriod = period;
            return this;
        }
        /**
        * Sets the maximum number of rolled files to retain after each roll.
        *
        * <p>
        *   When this limit is exceeded, the oldest rolled files matching the
        *   base file name pattern are deleted automatically. A value of {@code 0}
        *   disables pruning — all rolled files are kept indefinitely.
        * </p>
        *
        * <p>If a negative value is passed, an internal diagnostic warning is emitted and pruning is disabled.</p>
        *
        * @param max the maximum number of rolled files to retain (0 to disable pruning), or negative to disable
        * @return this builder
        **/
        public Builder maxFiles(int max){
            if(max < 0){
                InternalDiagnostic.warn(
                    "RollingFileLogOutput.Builder.maxFiles: max (" + max + ") is negative -> using 0 (pruning disabled)"
                );
                maxFiles = 0;
            }else maxFiles = max;
            return this;
        }
        /**
        * Builds a new {@link RollingFileLogOutput} and opens the log file.
        *
        * @return a new rolling file log output
        **/
        public RollingFileLogOutput build(){
            return new RollingFileLogOutput(this);
        }
    }
}