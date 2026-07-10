package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
// -- LoggerRegistry Class: ---------------------------------------------
/**
* A thread-safe registry of named {@link Logger} instances.
*
* <p>
*   The registry acts as a central store that maps logger names to their
*   {@link Logger} instances. Two access patterns are supported:
* </p>
* <ul>
*   <li>
*       {@link #register(Logger)}: stores a pre-built logger under its own
*       name. Emits an internal diagnostic warning if a different logger with
*       that name is already registered.
*   </li>
*   <li>
*       {@link #getLogger(String)} or {@link #getLogger(Class)}: returns the existing logger for the
*       given name, or creates and registers a new default logger (no outputs,
*       minimum level {@link Logger#DEFAULT_MIN_LEVEL})
*       if none exists yet.
*   </li>
* </ul>
*
* <p>
*   A global singleton is available via {@link #getInstance()}.
*   The singleton automatically attempts to register a JVM shutdown hook that
*   calls {@link #closeAll()} during normal JVM shutdown so registered outputs
*   are flushed and closed. If hook registration fails, an internal diagnostic
*   error is emitted and the singleton remains available without automatic
*   shutdown. Custom instances constructed directly do not get a shutdown hook.
* </p>
*
* <p>
*   Logger names are case-sensitive and are trimmed before lookup.
*   Use {@link #getLogger(Class)} for Java classes.
*   Use {@link #getLogger(String)} for custom domains, modules, resources, or non-Java sources.
* </p>
*
* <p>
*   {@link #closeAll()} closes every registered logger and clears the registry.
*   {@link #remove(String)} or {@link #remove(Class)} removes a logger by name without closing it —
*   the caller is responsible for closing it when no longer needed. Use
*   {@link #close(String)} or {@link #close(Class)} to remove and close a single
*   logger in one call; the outcome is reported as a {@link LoggerCloseResult}.
* </p>
*
* <p>This class is thread-safe.</p>
*
* @author BoRaheeb
**/
public final class LoggerRegistry{
    // -- Constants: --------------------------------------------------------
    /** Name assigned to the singleton's JVM shutdown-hook thread. **/
    private static final String SHUTDOWN_HOOK_THREAD_NAME = "boraheeb-log-shutdown";
    /** Global singleton instance. **/
    private static final LoggerRegistry INSTANCE;
    // -- LoggerCloseResult Enum: -------------------------------------------
    /** Outcome of a single-logger {@link #close(String)} or {@link #close(Class)} call. **/
    public enum LoggerCloseResult{
        /** A logger was found, removed, and closed successfully. **/
        CLOSED,
        /** No logger was registered under the given name. **/
        NOT_FOUND,
        /** A logger was found and removed, but its {@link Logger#close()} call threw an unchecked exception. **/
        CLOSE_FAILED,
        /** The given name was {@code null} or blank, or the given class was {@code null} or anonymous. **/
        INVALID_NAME
    }
    // -- Fields: -----------------------------------------------------------
    /** Map of logger name to logger instance. **/
    private final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a new empty logger registry.
    *
    * <p>
    *   Use {@link #getInstance()} for the global registry, or construct
    *   a new instance for isolated use.
    * </p>
    **/
    public LoggerRegistry(){}
    // -- Static Initializer: -----------------------------------------------
    static{
        INSTANCE = new LoggerRegistry();
        try{
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try{
                    INSTANCE.closeAll();
                }catch(RuntimeException ex){
                    InternalDiagnostic.error(
                        "LoggerRegistry.shutdownHook: failed to close all loggers -> cause: " +
                        ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> shutdown continues"
                    );
                }
            }, SHUTDOWN_HOOK_THREAD_NAME));
        }catch(IllegalStateException | SecurityException ex){
            InternalDiagnostic.error(
                "LoggerRegistry.staticInitializer: failed to register shutdown hook -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() +
                " -> singleton remains available without automatic shutdown"
            );
        }
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the logger registered under the given name, creating and
    * registering a default logger if none exists yet.
    *
    * <p>
    *   The default logger has no registered outputs and a minimum level of
    *   {@link Logger#DEFAULT_MIN_LEVEL}. It can be replaced at any time by calling
    *   {@link #register(Logger)} with a fully configured logger of the same name.
    * </p>
    *
    * <p>
    *   If {@code name} is {@code null} or blank, an internal diagnostic
    *   warning is emitted and {@code null} is returned.
    * </p>
    *
    * @param name the logger name, or {@code null} or blank to return {@code null}
    * @return the existing or newly created logger, or {@code null} if {@code name} is invalid
    **/
    public Logger getLogger(String name){
        if(name == null || name.isBlank()){
            InternalDiagnostic.warn("LoggerRegistry.getLogger: name is null/blank -> returning null");
            return null;
        }
        String key = name.trim();
        return loggers.computeIfAbsent(key, k -> Logger.builder(k).build());
    }
    /**
    * Returns the logger registered under the fully qualified name of the given
    * class, creating and registering a default logger if none exists yet.
    *
    * <p>
    *   Equivalent to {@code getLogger(clazz.getName())}.
    *   Use {@link #getLogger(String)} when the display name should differ
    *   from the class name (for example, {@code "Firebase"}, {@code "back-end"}).
    * </p>
    *
    * <p>
    *   If {@code clazz} is {@code null} or anonymous, an internal diagnostic
    *   warning is emitted and {@code null} is returned.
    * </p>
    *
    * @param clazz the class whose name is used, or {@code null} or anonymous to return {@code null}
    * @return the existing or newly created logger, or {@code null} if {@code clazz} is invalid
    **/
    public Logger getLogger(Class<?> clazz){
        if(clazz == null){
            InternalDiagnostic.warn("LoggerRegistry.getLogger: clazz is null -> returning null");
            return null;
        }
        if(clazz.isAnonymousClass()){
            InternalDiagnostic.warn("LoggerRegistry.getLogger: clazz is anonymous -> returning null");
            return null;
        }
        return getLogger(clazz.getName());
    }
    /**
    * Returns an unmodifiable view of all loggers currently registered.
    *
    * <p>
    *   This is a live view backed by the registry — it reflects subsequent
    *   registrations and removals, and iterating it is weakly consistent
    *   (safe under concurrent modification, but not a frozen snapshot).
    * </p>
    *
    * @return an unmodifiable live collection of registered loggers, never {@code null}
    **/
    public Collection<Logger> getAll(){
        return Collections.unmodifiableCollection(loggers.values());
    }
    /**
    * Returns {@code true} if a logger is registered under the given name.
    *
    * <p>
    *   If {@code name} is {@code null} or blank, an internal diagnostic
    *   warning is emitted and {@code false} is returned.
    * </p>
    *
    * @param name the logger name to check, or {@code null} or blank to return {@code false}
    * @return {@code true} if the name is registered, otherwise {@code false}
    **/
    public boolean contains(String name){
        if(name == null || name.isBlank()){
            InternalDiagnostic.warn("LoggerRegistry.contains: name is null/blank -> returning false");
            return false;
        }
        return loggers.containsKey(name.trim());
    }
    /**
    * Returns {@code true} if a logger is registered under the fully qualified name of the given class.
    *
    * <p>
    *   Equivalent to {@code contains(clazz.getName())}. If {@code clazz} is
    *   {@code null} or anonymous, an internal diagnostic warning is emitted
    *   and {@code false} is returned.
    * </p>
    *
    * @param clazz the class whose name is checked, or {@code null} or anonymous to return {@code false}
    * @return {@code true} if the class's name is registered, otherwise {@code false}
    **/
    public boolean contains(Class<?> clazz){
        if(clazz == null){
            InternalDiagnostic.warn("LoggerRegistry.contains: clazz is null -> returning false");
            return false;
        }
        if(clazz.isAnonymousClass()){
            InternalDiagnostic.warn("LoggerRegistry.contains: clazz is anonymous -> returning false");
            return false;
        }
        return contains(clazz.getName());
    }
    /**
    * Returns the number of loggers currently registered.
    *
    * @return the number of registered loggers
    **/
    public int size(){
        return loggers.size();
    }
    // -- Mutator Methods: --------------------------------------------------
    /**
    * Registers a pre-built logger under its own name, replacing any
    * existing logger registered under the same name.
    *
    * <p>
    *   If a different logger with the same name is already registered, an
    *   internal diagnostic warning is emitted before the replacement occurs.
    *   Registering the same instance again has no diagnostic side effect. A
    *   replaced logger is not closed — the caller is responsible for closing
    *   it if needed.
    * </p>
    *
    * <p>A {@code null} logger is ignored with an internal diagnostic warning.</p>
    *
    * @param logger the logger to register, or {@code null} to ignore
    **/
    public void register(Logger logger){
        if(logger == null){
            InternalDiagnostic.warn("LoggerRegistry.register: logger is null -> ignored");
            return;
        }
        Logger previous = loggers.put(logger.getName(), logger);
        if(previous != null && previous != logger)
            InternalDiagnostic.warn(
                "LoggerRegistry.register: replaced existing logger \"" + logger.getName() + "\" -> previous instance not closed"
            );
    }
    /**
    * Removes and returns the logger registered under the given name.
    *
    * <p>
    *   The logger is removed from the registry but is not closed.
    *   Returns {@code null} if no logger is registered under that name.
    *   If {@code name} is {@code null} or blank, an internal diagnostic
    *   warning is emitted and {@code null} is returned.
    * </p>
    *
    * @param name the logger name to remove, or {@code null} or blank to return {@code null}
    * @return the removed logger, or {@code null} if not found
    **/
    public Logger remove(String name){
        if(name == null || name.isBlank()){
            InternalDiagnostic.warn("LoggerRegistry.remove: name is null/blank -> returning null");
            return null;
        }
        return loggers.remove(name.trim());
    }
    /**
    * Removes and returns the logger registered under the fully qualified name of the given class.
    *
    * <p>
    *   Equivalent to {@code remove(clazz.getName())}. If {@code clazz} is
    *   {@code null} or anonymous, an internal diagnostic warning is emitted
    *   and {@code null} is returned.
    * </p>
    *
    * @param clazz the class whose name is removed, or {@code null} or anonymous to return {@code null}
    * @return the removed logger, or {@code null} if not found
    **/
    public Logger remove(Class<?> clazz){
        if(clazz == null){
            InternalDiagnostic.warn("LoggerRegistry.remove: clazz is null -> returning null");
            return null;
        }
        if(clazz.isAnonymousClass()){
            InternalDiagnostic.warn("LoggerRegistry.remove: clazz is anonymous -> returning null");
            return null;
        }
        return remove(clazz.getName());
    }
    /**
    * Closes and removes the logger registered under the given name.
    *
    * <p>
    *   The logger is removed from the registry first, then {@link Logger#close()}
    *   is called on it. If {@code close()} throws an unchecked exception, it is
    *   caught and reported through an internal diagnostic error — the logger
    *   stays removed from the registry either way. Does nothing if no logger is
    *   registered under that name. If {@code name} is {@code null} or blank,
    *   an internal diagnostic warning is emitted and
    *   {@link LoggerCloseResult#INVALID_NAME} is returned.
    * </p>
    *
    * <p>
    *   {@link LoggerCloseResult#CLOSE_FAILED} is only returned if {@link Logger#close()}
    *   itself throws unexpectedly. In normal operation this does not happen: each
    *   registered {@link LogOutput}'s own {@code close()} failure is already isolated
    *   inside {@link Logger#close()} and reported through an internal diagnostic error,
    *   so a single failing output does not propagate out of {@code Logger#close()} and
    *   does not cause this method to return {@code CLOSE_FAILED}.
    * </p>
    *
    * @param name the logger name to close and remove
    * @return {@link LoggerCloseResult#CLOSED} if a logger was found and closed;
    *         {@link LoggerCloseResult#NOT_FOUND} if no logger was registered under that name;
    *         {@link LoggerCloseResult#CLOSE_FAILED} if a logger was found and removed but its {@code close()} call threw unexpectedly;
    *         {@link LoggerCloseResult#INVALID_NAME} if {@code name} is {@code null} or blank
    **/
    public LoggerCloseResult close(String name){
        if(name == null || name.isBlank()){
            InternalDiagnostic.warn("LoggerRegistry.close: name is null/blank -> returning INVALID_NAME");
            return LoggerCloseResult.INVALID_NAME;
        }
        Logger logger = remove(name);
        if(logger == null) return LoggerCloseResult.NOT_FOUND;
        try{
            logger.close();
            return LoggerCloseResult.CLOSED;
        }catch(RuntimeException ex){
            InternalDiagnostic.error(
                "LoggerRegistry.close: removed logger \"" + logger.getName() + "\" but failed to close it -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> returning CLOSE_FAILED"
            );
            return LoggerCloseResult.CLOSE_FAILED;
        }
    }
    /**
    * Closes and removes the logger registered under the fully qualified
    * name of the given class.
    *
    * <p>
    *   Equivalent to {@code close(clazz.getName())}. If {@code clazz} is
    *   {@code null} or anonymous, an internal diagnostic warning is emitted
    *   and {@link LoggerCloseResult#INVALID_NAME} is returned.
    * </p>
    *
    * <p>
    *   As with {@link #close(String)}, {@link LoggerCloseResult#CLOSE_FAILED} is only
    *   returned if {@link Logger#close()} itself throws unexpectedly — output-level
    *   close failures are isolated inside {@link Logger#close()} and reported through
    *   an internal diagnostic error, so they do not normally cause this method to
    *   return {@code CLOSE_FAILED}.
    * </p>
    *
    * @param clazz the class whose logger is closed and removed, or {@code null} or anonymous to return {@link LoggerCloseResult#INVALID_NAME}
    * @return the result of the close attempt
    **/
    public LoggerCloseResult close(Class<?> clazz){
        if(clazz == null){
            InternalDiagnostic.warn("LoggerRegistry.close: clazz is null -> returning INVALID_NAME");
            return LoggerCloseResult.INVALID_NAME;
        }
        if(clazz.isAnonymousClass()){
            InternalDiagnostic.warn("LoggerRegistry.close: clazz is anonymous -> returning INVALID_NAME");
            return LoggerCloseResult.INVALID_NAME;
        }
        return close(clazz.getName());
    }
    /**
    * Closes all registered loggers and clears the registry.
    *
    * <p>
    *   Each logger's {@link Logger#close()} is called in an unspecified order.
    *   If a logger's {@code close()} call throws an unchecked exception, it is
    *   caught and reported through an internal diagnostic error so that
    *   remaining loggers are still closed.
    * </p>
    **/
    public void closeAll(){
        for(Logger logger : loggers.values()){
            try{
                logger.close();
            }catch(RuntimeException ex){
                InternalDiagnostic.error(
                    "LoggerRegistry.closeAll: failed to close logger \"" + logger.getName() + "\" -> cause: " +
                    ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> closeAll continues"
                );
            }
        }
        loggers.clear();
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Returns the global application-wide registry.
    *
    * <p>
    *   This singleton is intended for final applications that want one shared
    *   logging registry across the whole JVM.
    * </p>
    *
    * <p>
    *   Libraries, frameworks, plugins, and reusable APIs should prefer creating
    *   their own {@link LoggerRegistry} instance to avoid sharing logger state
    *   with the host application or other libraries running in the same JVM.
    * </p>
    *
    * @return the global logger registry, never {@code null}
    **/
    public static LoggerRegistry getInstance(){
        return INSTANCE;
    }
}