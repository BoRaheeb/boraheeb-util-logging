package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
// -- MDC Class: --------------------------------------------------------
/**
* Mapped Diagnostic Context — thread-local key-value pairs automatically
* merged into every {@link LogRecord} produced by a {@link Logger} on the
* same thread.
*
* <p>
*   MDC provides a way to attach contextual information (such as a request
*   ID, session ID, or user ID) to all log records produced during the
*   processing of a request, without passing those values to every log call
*   explicitly.
* </p>
*
* <p>
*   MDC values are stored per-thread. Setting a value on one thread does
*   not affect other threads. Child threads do not inherit the parent
*   thread's MDC context.
* </p>
*
* <p>
*   Because the context is thread-local, it does not automatically flow to
*   worker threads. To carry the current thread's context into a task that runs
*   on another thread (an {@link java.util.concurrent.ExecutorService}, a thread
*   pool, a {@code SwingWorker}, or a plain {@link Thread}), wrap the task with
*   {@link #wrap(Runnable)} or {@link #wrap(Callable)}. The context is captured
*   when {@code wrap} is called and installed for the duration of the task, then
*   the worker thread's previous context is restored.
* </p>
*
* <p>
*   MDC context is merged automatically by {@link Logger} when using
*   shorthand methods ({@code debug}, {@code info}, {@code warn}, etc.) and
*   {@link Logger#log(LogLevel, String, Throwable)}. Records passed directly
*   to {@link Logger#log(LogRecord)} are published as-is — the caller owns
*   all fields in a pre-built record.
* </p>
*
* <p>
*   MDC fields are a baseline. If a record field and an MDC entry share the
*   same key, the record field takes priority.
* </p>
*
* <p>
*   Always call {@link #clear()} when the unit of work is complete —
*   typically in a {@code finally} block — to prevent stale values from
*   leaking into subsequent work on thread-pool threads.
* </p>
*
* <p>Example usage:</p>
* <pre>{@code
*   MDC.put("requestId", "REQ-8821");
*   MDC.put("userId", "9021");
*   try{
*       logger.info("payment started"); // fields: requestId=REQ-8821, userId=9021
*       logger.info("payment complete"); // fields: requestId=REQ-8821, userId=9021
*   }finally{
*       MDC.clear();
*   }
* }</pre>
*
* <p>
*   This class is a static utility class. All methods operate on the
*   current thread's context and are inherently thread-safe.
* </p>
*
* @author BoRaheeb
**/
public final class MDC{
    // -- Constants: --------------------------------------------------------
    /** Thread-local map of diagnostic context entries. **/
    private static final ThreadLocal<Map<String, String>> CONTEXT = ThreadLocal.withInitial(LinkedHashMap::new);
    // -- Constructors: -----------------------------------------------------
    /** Private constructor — This class is a static utility class and cannot be created. **/
    private MDC(){}
    // -- Context Methods: --------------------------------------------------
    /**
    * Adds or replaces a key-value pair in the current thread's context.
    *
    * <p>
    *   A {@code null} or blank key is ignored with an internal diagnostic
    *   warning. A {@code null} value is stored as the string {@code "null"}.
    * </p>
    *
    * @param key the context key, or {@code null} or blank to ignore the entry
    * @param value the context value
    **/
    public static void put(String key, String value){
        if(key == null || key.isBlank()){
            InternalDiagnostic.warn("MDC.put: key is null/blank -> ignored");
            return;
        }
        CONTEXT.get().put(key.trim(), ((value == null)? "null" : value));
    }
    /**
    * Returns the value associated with the given key in the current
    * thread's context, or {@code null} if the key is not present.
    *
    * <p>If the key is {@code null} or blank, an internal diagnostic warning is emitted and {@code null} is returned.</p>
    *
    * @param key the context key, or {@code null} or blank to return {@code null}
    * @return the associated value, or {@code null}
    **/
    public static String get(String key){
        if(key == null || key.isBlank()){
            InternalDiagnostic.warn("MDC.get: key is null/blank -> returning null");
            return null;
        }
        return CONTEXT.get().get(key.trim());
    }
    /**
    * Returns {@code true} if the current thread's context contains the given key.
    *
    * <p>If the key is {@code null} or blank, an internal diagnostic warning is emitted and {@code false} is returned.</p>
    *
    * @param key the context key to check, or {@code null} or blank to return {@code false}
    * @return {@code true} if the key is present, otherwise {@code false}
    **/
    public static boolean containsKey(String key){
        if(key == null || key.isBlank()){
            InternalDiagnostic.warn("MDC.containsKey: key is null/blank -> returning false");
            return false;
        }
        return CONTEXT.get().containsKey(key.trim());
    }
    /**
    * Removes the entry with the given key from the current thread's context.
    * Does nothing if the key is not present.
    *
    * <p>If the key is {@code null} or blank, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param key the context key to remove, or {@code null} or blank to ignore the entry
    **/
    public static void remove(String key){
        if(key == null || key.isBlank()){
            InternalDiagnostic.warn("MDC.remove: key is null/blank -> ignored");
            return;
        }
        CONTEXT.get().remove(key.trim());
    }
    /**
    * Removes all entries from the current thread's context.
    *
    * <p>
    *   Always call this in a {@code finally} block after the unit of work
    *   completes to prevent stale values from leaking across tasks on
    *   thread-pool threads.
    * </p>
    **/
    public static void clear(){
        CONTEXT.get().clear();
    }
    /**
    * Returns {@code true} if the current thread's context has no entries.
    *
    * @return {@code true} if the context is empty, otherwise {@code false}
    **/
    public static boolean isEmpty(){
        return CONTEXT.get().isEmpty();
    }
    /**
    * Returns the number of entries in the current thread's context.
    *
    * @return the number of context entries
    **/
    public static int size(){
        return CONTEXT.get().size();
    }
    /**
    * Returns an unmodifiable snapshot of the current thread's context.
    *
    * <p>
    *   The returned map reflects the context at the moment of the call.
    *   Subsequent changes to the MDC are not reflected in the returned map.
    * </p>
    *
    * @return an unmodifiable snapshot of the current thread's context, never {@code null}
    **/
    public static Map<String, String> getContext(){
        return Collections.unmodifiableMap(new LinkedHashMap<>(CONTEXT.get()));
    }
    /**
    * Replaces the current thread's context with a copy of the given map.
    *
    * <p>
    *   The inverse of {@link #getContext()} — {@code setContext(getContext())} is
    *   a clean round-trip. Entries are copied into the thread-local context, so
    *   the given map is not retained and later changes to it have no effect.
    *   Existing entries are discarded first.
    * </p>
    *
    * <p>
    *   The same validation as {@link #put(String, String)} is applied while
    *   copying: an entry with a {@code null} or blank key is skipped with an
    *   internal diagnostic warning, and a {@code null} value is stored as the
    *   string {@code "null"}.
    * </p>
    *
    * <p>Passing {@code null} clears the context, representing an empty context state.</p>
    *
    * @param context the entries to install, or {@code null} to clear the context
    **/
    public static void setContext(Map<String, String> context){
        Map<String, String> target = CONTEXT.get();
        target.clear();
        if(context == null) return;
        for(Map.Entry<String, String> entry : context.entrySet()){
            String key = entry.getKey();
            if(key == null || key.isBlank()){
                InternalDiagnostic.warn("MDC.setContext: key is null/blank -> entry ignored");
                continue;
            }
            target.put(key.trim(), ((entry.getValue() == null)? "null" : entry.getValue()));
        }
    }
    /**
    * Returns the live thread-local context map for internal merging by {@link Logger}.
    *
    * <p>
    *   Package-private and read-only by contract — callers must not mutate the
    *   returned map. This avoids the defensive copy made by {@link #getContext()}
    *   on the logging hot path. The map is thread-local, so only the calling
    *   thread ever accesses it.
    * </p>
    *
    * @return the live context map for the current thread, never {@code null}
    **/
    static Map<String, String> context(){
        return CONTEXT.get();
    }
    // -- Propagation Methods: ----------------------------------------------
    /**
    * Wraps the given task so it runs with the calling thread's current MDC
    * context, regardless of which thread later executes it.
    *
    * <p>
    *   The context is captured eagerly — at the moment {@code wrap} is called,
    *   not when the returned task runs — so it reflects the submitting thread's
    *   context even if that thread later changes or clears it. When the wrapped
    *   task runs, the executing thread's existing context is saved, the captured
    *   context is installed, the task runs, and the executing thread's previous
    *   context is restored in a {@code finally} block. This leaves a pooled
    *   worker thread exactly as it was found, so no context leaks between tasks.
    * </p>
    *
    * <p>Example usage with an executor:</p>
    * <pre>{@code
    *   MDC.put("requestId", "REQ-8821");
    *   executor.execute(MDC.wrap(() -> logger.info("runs with requestId=REQ-8821")));
    * }</pre>
    *
    * <p>If {@code task} is {@code null}, an internal diagnostic warning is emitted and a no-op {@link Runnable} is returned.</p>
    *
    * @param task the task to wrap, or {@code null} to return a no-op task
    * @return a runnable that installs the captured context around the task, never {@code null}
    **/
    public static Runnable wrap(Runnable task){
        if(task == null){
            InternalDiagnostic.warn("MDC.wrap: task is null -> using no-op Runnable");
            return () -> {};
        }
        Map<String, String> captured = getContext();
        return () -> {
            Map<String, String> previous = getContext();
            setContext(captured);
            try{
                task.run();
            }finally{
                setContext(previous);
            }
        };
    }
    /**
    * Wraps the given task so it runs with the calling thread's current MDC
    * context, regardless of which thread later executes it.
    *
    * <p>
    *   Behaves exactly like {@link #wrap(Runnable)} but for a {@link Callable},
    *   preserving the task's return value and checked exceptions — suitable for
    *   {@link java.util.concurrent.ExecutorService#submit(Callable)}. The context
    *   is captured eagerly when {@code wrap} is called, installed for the duration
    *   of the call, and the executing thread's previous context is restored in a
    *   {@code finally} block.
    * </p>
    *
    * <p>If {@code task} is {@code null}, an internal diagnostic warning is emitted and a {@link Callable} returning {@code null} is returned.</p>
    *
    * @param <V> the result type of the task
    * @param task the task to wrap, or {@code null} to return a task that yields {@code null}
    * @return a callable that installs the captured context around the task, never {@code null}
    **/
    public static <V> Callable<V> wrap(Callable<V> task){
        if(task == null){
            InternalDiagnostic.warn("MDC.wrap: task is null -> using no-op Callable");
            return () -> null;
        }
        Map<String, String> captured = getContext();
        return () -> {
            Map<String, String> previous = getContext();
            setContext(captured);
            try{
                return task.call();
            }finally{
                setContext(previous);
            }
        };
    }
}