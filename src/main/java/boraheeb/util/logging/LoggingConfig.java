package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
// -- LoggingConfig Class: ----------------------------------------------
/**
* A declarative, {@code .properties}-based source of logger minimum levels.
*
* <p>
*   {@code LoggingConfig} is the third configuration tier alongside programmatic
*   builders and runtime setters. In this phase it does exactly one thing: it
*   reads level rules from a {@code .properties} file and applies them to existing
*   loggers through {@link Logger#setMinLevel(LogLevel)}. It does not construct
*   outputs, formatters, or themes, and it never creates loggers.
* </p>
*
* <p>All keys live under the {@code log.level.} namespace; any other key is ignored:</p>
* <pre>
*   # fallback for any logger with no exact or prefix match (optional)
*   log.level.root = INFO
*
*   # exact logger name
*   log.level.boraheeb.ui.MainWindow = DEBUG
*
*   # prefix rule: a trailing ".*" matches that segment and everything below it
*   log.level.boraheeb.ui.*  = DEBUG
*   log.level.boraheeb.net.* = WARN
* </pre>
*
* <p>
*   Level values are parsed case-insensitively against {@link LogLevel#getValues()}.
*   An unknown value causes that single rule to be ignored with an internal
*   diagnostic warning — it is never coerced to a default.
* </p>
*
* <p>The resolved level for a logger name is chosen by this precedence:</p>
* <ol>
*   <li>an <b>exact</b> rule whose key equals the logger name;</li>
*   <li>otherwise the <b>longest matching prefix</b> rule;</li>
*   <li>otherwise the <b>root</b> rule;</li>
*   <li>otherwise {@code null} — meaning "no opinion", and the logger is left untouched.</li>
* </ol>
*
* <p>
*   Prefix matching is segment-aware: the rule {@code log.level.boraheeb.ui.*}
*   matches {@code "boraheeb.ui.MainWindow"} and {@code "boraheeb.ui.dialog.Alert"},
*   but not {@code "boraheeb.uiteam.SomeClass"}. This is because the matched prefix
*   keeps its trailing dot ({@code "boraheeb.ui."}), so a match requires a real
*   segment boundary. To set a level on the boundary node itself
*   ({@code "boraheeb.ui"}), add an exact rule for it.
* </p>
*
* <p>
*   This class only modifies loggers that already exist when {@link #applyTo(LoggerRegistry)}
*   runs — it never creates loggers. Because resolution is one-shot string matching,
*   a logger registered after {@code applyTo} is not affected until the config is
*   applied again. There is no file watching or automatic reload; to reload, call
*   {@code load} and {@code applyTo} again — {@link #applyTo(LoggerRegistry)} is idempotent.
* </p>
*
* <p>
*   {@code LoggingConfig} targets the logger's own minimum level — the front gate.
*   A record must still clear each output's own minimum level, so leave outputs
*   permissive (for example, {@link LogLevel#TRACE}) if config-driven logger levels
*   should be authoritative.
* </p>
*
* <p>This class is immutable and thread-safe.</p>
*
* @author BoRaheeb
* @see Logger
* @see LoggerRegistry
* @see LogLevel
**/
public final class LoggingConfig{
    // -- Constants: --------------------------------------------------------
    /** Namespace prefix that marks a level rule key. **/
    private static final String KEY_PREFIX = "log.level.";
    /** Reserved key suffix denoting the root (fallback) rule. **/
    private static final String ROOT_KEY = "root";
    /** Key suffix marking a prefix rule. **/
    private static final String PREFIX_SUFFIX = ".*";
    // -- Fields: -----------------------------------------------------------
    /** Root fallback level, or {@code null} if no root rule is configured. **/
    private final LogLevel rootLevel;
    /** Exact logger-name rules, mapping logger name to level. **/
    private final Map<String, LogLevel> exactRules;
    /** Prefix rules, mapping a dot-terminated prefix (for example, {@code "boraheeb.ui."}) to level. **/
    private final Map<String, LogLevel> prefixRules;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a logging config from already-parsed rules.
    *
    * @param rootLevel the root fallback level, or {@code null} for none
    * @param exactRules the exact logger-name rules
    * @param prefixRules the dot-terminated prefix rules
    **/
    private LoggingConfig(LogLevel rootLevel, Map<String, LogLevel> exactRules, Map<String, LogLevel> prefixRules){
        this.rootLevel = rootLevel;
        this.exactRules = Collections.unmodifiableMap(new LinkedHashMap<>(exactRules));
        this.prefixRules = Collections.unmodifiableMap(new LinkedHashMap<>(prefixRules));
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the root fallback level applied to loggers with no exact or prefix match.
    *
    * @return the root level, or {@code null} if no root rule is configured
    **/
    public LogLevel getRootLevel(){
        return rootLevel;
    }
    /**
    * Returns an unmodifiable view of the exact logger-name rules.
    *
    * @return the exact rules, mapping logger name to level, never {@code null}
    **/
    public Map<String, LogLevel> getExactRules(){
        return exactRules;
    }
    /**
    * Returns an unmodifiable view of the prefix rules.
    *
    * <p>Keys are dot-terminated prefixes — for example, {@code "boraheeb.ui."} for the rule {@code log.level.boraheeb.ui.*}.</p>
    *
    * @return the prefix rules, mapping a dot-terminated prefix to level, never {@code null}
    **/
    public Map<String, LogLevel> getPrefixRules(){
        return prefixRules;
    }
    /**
    * Resolves the configured level for the given logger name.
    *
    * <p>
    *   Resolution precedence: an exact rule, otherwise the longest matching prefix
    *   rule, otherwise the root rule, otherwise {@code null}. A {@code null} result
    *   means the config has no opinion and the logger should be left untouched.
    * </p>
    *
    * <p>If {@code loggerName} is {@code null} or blank, an internal diagnostic warning is emitted and {@code null} is returned.</p>
    *
    * @param loggerName the logger name to resolve, or {@code null} or blank to return {@code null}
    * @return the resolved level, or {@code null} if no rule applies
    **/
    public LogLevel levelFor(String loggerName){
        if(loggerName == null || loggerName.isBlank()){
            InternalDiagnostic.warn("LoggingConfig.levelFor: loggerName is null/blank -> returning null");
            return null;
        }
        String name = loggerName.trim();
        LogLevel exact = exactRules.get(name);
        if(exact != null) return exact;
        LogLevel best = null;
        int bestLength = -1;
        for(Map.Entry<String, LogLevel> rule : prefixRules.entrySet()){
            String prefix = rule.getKey();
            if(name.startsWith(prefix) && prefix.length() > bestLength){
                best = rule.getValue();
                bestLength = prefix.length();
            }
        }
        if(best != null) return best;
        return rootLevel;
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Loads a config from a {@code .properties} file.
    *
    * <p>If the file is {@code null} or cannot be read, an internal diagnostic message is emitted and an empty config is returned.</p>
    *
    * @param file the properties file to read, or {@code null} to return an empty config
    * @return a config parsed from the file, or an empty config on failure, never {@code null}
    **/
    public static LoggingConfig load(Path file){
        if(file == null){
            InternalDiagnostic.warn("LoggingConfig.load: file is null -> returning empty config");
            return empty();
        }
        try(InputStream in = Files.newInputStream(file)){
            Properties properties = new Properties();
            properties.load(in);
            return parse(properties);
        }catch(IOException ex){
            InternalDiagnostic.error(
                "LoggingConfig.load: failed to read \"" + file + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> returning empty config"
            );
            return empty();
        }
    }
    /**
    * Loads a config from a {@code .properties} input stream.
    *
    * <p>
    *   The stream is read but not closed — the caller retains ownership. If the
    *   stream is {@code null} or cannot be read, an internal diagnostic message is
    *   emitted and an empty config is returned.
    * </p>
    *
    * @param in the properties input stream, or {@code null} to return an empty config
    * @return a config parsed from the stream, or an empty config on failure, never {@code null}
    **/
    public static LoggingConfig load(InputStream in){
        if(in == null){
            InternalDiagnostic.warn("LoggingConfig.load: input stream is null -> returning empty config");
            return empty();
        }
        Properties properties = new Properties();
        try{
            properties.load(in);
        }catch(IOException ex){
            InternalDiagnostic.error(
                "LoggingConfig.load: failed to read properties stream -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> returning empty config"
            );
            return empty();
        }
        return parse(properties);
    }
    /**
    * Loads a config from an already-populated {@link Properties} object.
    *
    * <p>If {@code properties} is {@code null}, an internal diagnostic warning is emitted and an empty config is returned.</p>
    *
    * @param properties the properties to read, or {@code null} to return an empty config
    * @return a config parsed from the properties, never {@code null}
    **/
    public static LoggingConfig load(Properties properties){
        if(properties == null){
            InternalDiagnostic.warn("LoggingConfig.load: properties is null -> returning empty config");
            return empty();
        }
        return parse(properties);
    }
    // -- Application Methods: ----------------------------------------------
    /**
    * Applies the configured levels to every logger currently registered in the
    * given registry.
    *
    * <p>
    *   For each registered logger, the level resolved by {@link #levelFor(String)}
    *   is applied through {@link Logger#setMinLevel(LogLevel)}. Loggers for which
    *   no rule applies are left untouched. This method does not create loggers and
    *   does not affect loggers registered after it runs. It is idempotent.
    * </p>
    *
    * <p>If {@code registry} is {@code null}, an internal diagnostic warning is emitted and the call is ignored.</p>
    *
    * @param registry the registry whose loggers are updated; ignored if {@code null}
    **/
    public void applyTo(LoggerRegistry registry){
        if(registry == null){
            InternalDiagnostic.warn("LoggingConfig.applyTo: registry is null -> ignored");
            return;
        }
        for(Logger logger : registry.getAll()){
            LogLevel level = levelFor(logger.getName());
            if(level != null) logger.setMinLevel(level);
        }
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Parses {@code log.level.*} rules out of the given properties.
    *
    * <p>
    *   Keys outside the {@code log.level.} namespace are ignored. A rule with an
    *   unknown level value or an empty logger name is skipped with an internal
    *   diagnostic warning.
    * </p>
    *
    * @param properties the properties to parse
    * @return a config built from the parsed rules
    **/
    private static LoggingConfig parse(Properties properties){
        LogLevel root = null;
        Map<String, LogLevel> exact = new LinkedHashMap<>();
        Map<String, LogLevel> prefix = new LinkedHashMap<>();
        for(String key : properties.stringPropertyNames()){
            if(!key.startsWith(KEY_PREFIX)) continue;
            String suffix = key.substring(KEY_PREFIX.length()).trim();
            String rawValue = properties.getProperty(key);
            LogLevel level = parseLevel(rawValue);
            if(level == null){
                InternalDiagnostic.warn("LoggingConfig.parse: invalid level \"" + rawValue + "\" for key \"" + key + "\" -> rule ignored");
                continue;
            }
            if(suffix.isEmpty()){
                InternalDiagnostic.warn("LoggingConfig.parse: empty logger name in key \"" + key + "\" -> rule ignored");
                continue;
            }
            if(suffix.equals(ROOT_KEY))
                root = level;
            else if(suffix.endsWith(PREFIX_SUFFIX) && suffix.length() > PREFIX_SUFFIX.length())
                // strip the trailing '*' but keep the dot: "boraheeb.ui.*" -> "boraheeb.ui."
                prefix.put(suffix.substring(0, suffix.length() - 1), level);
            else exact.put(suffix, level);
        }
        return new LoggingConfig(root, exact, prefix);
    }
    /**
    * Parses a level label case-insensitively against the known levels.
    *
    * @param raw the raw value, possibly {@code null}
    * @return the matching level, or {@code null} if the value is null, blank, or unknown
    **/
    private static LogLevel parseLevel(String raw){
        if(raw == null) return null;
        String label = raw.trim();
        if(label.isEmpty()) return null;
        for(LogLevel level : LogLevel.getValues())
            if(level.getLabel().equalsIgnoreCase(label)) return level;
        return null;
    }
    /**
    * Returns an empty config with no rules.
    *
    * @return an empty config, never {@code null}
    **/
    private static LoggingConfig empty(){
        return new LoggingConfig(null, new LinkedHashMap<>(), new LinkedHashMap<>());
    }
}