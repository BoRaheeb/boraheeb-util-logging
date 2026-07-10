package boraheeb.util.logging;
// -- Libraries: --------------------------------------------------------
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
// -- LogDateTime Class: ------------------------------------------------
/**
* Date-time formatting utility for logging.
*
* <p>
*   Supports built-in presets and custom patterns with safe fallbacks.
*   Also provides helpers to print and download the bundled date-time symbols documentation file for developers.
* </p>
*
* <p>This class is immutable and thread-safe.</p>
*
* @author BoRaheeb
**/
public final class LogDateTime{
    // -- Constants: --------------------------------------------------------
    /** Default locale used when no locale is provided or when locale input is invalid. **/
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    /**
    * Default zone used when no zone is provided or when zone input is invalid.
    *
    * <p>
    *   A zone is required to format {@link java.time.Instant} values, which are
    *   timezone-agnostic. Without a zone, formatting an {@code Instant} would throw
    *   {@link java.time.temporal.UnsupportedTemporalTypeException}. Defaulting to
    *   the system zone gives the most natural result for local logging.
    * </p>
    **/
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    /** Classpath resource path for the bundled date-time symbols documentation file. **/
    private static final String DATE_FORMATTING_SYMBOLS_RESOURCE = "/docs/date-formatting-symbols.txt";
    /** Output file name used when downloading the date-time symbols documentation file. **/
    private static final String DATE_FORMATTING_SYMBOLS_FILE_NAME = "date-formatting-symbols.txt";
    /** System property key for the current working directory. **/
    private static final String WORKING_DIR_PROPERTY = "user.dir";
    // -- Fields: -----------------------------------------------------------
    /** Active date-time pattern used by this formatter instance. **/
    private final String pattern;
    /** Active locale used by this formatter instance. **/
    private final Locale locale;
    /** Active zone used by this formatter instance. **/
    private final ZoneId zone;
    /**
    * Precompiled formatter created from pattern, locale, and zone for efficient formatting.
    *
    * <p>
    *   The zone is applied via {@link DateTimeFormatter#withZone(ZoneId)} so that
    *   {@link java.time.Instant} values can be formatted directly without manual conversion.
    * </p>
    **/
    private final DateTimeFormatter formatter;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a LogDateTime instance with a validated pattern, locale, zone, and formatter.
    *
    * @param pattern the active date-time pattern
    * @param locale the active locale
    * @param zone the active zone
    * @param formatter the precompiled formatter with zone already applied
    **/
    private LogDateTime(String pattern, Locale locale, ZoneId zone, DateTimeFormatter formatter){
        this.pattern = pattern;
        this.locale = locale;
        this.zone = zone;
        this.formatter = formatter;
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the active date-time pattern of this instance.
    *
    * @return the active date-time pattern, never {@code null}
    **/
    public String getPattern(){
        return pattern;
    }
    /**
    * Returns the active locale of this instance.
    *
    * @return the active locale, never {@code null}
    **/
    public Locale getLocale(){
        return locale;
    }
    /**
    * Returns the active zone of this instance.
    *
    * @return the active zone, never {@code null}
    **/
    public ZoneId getZone(){
        return zone;
    }
    /**
    * Returns the precompiled {@link DateTimeFormatter} of this instance.
    * The zone is already applied — {@link java.time.Instant} values can be
    * passed directly to {@link #format(TemporalAccessor)}.
    *
    * @return the precompiled formatter, never {@code null}
    **/
    public DateTimeFormatter getFormatter(){
        return formatter;
    }
    /**
    * Returns a string representation of this instance.
    *
    * @return a string in the form {@code LogDateTime[pattern="...", locale=..., zone=...]}
    **/
    @Override
    public String toString(){
        return "LogDateTime[pattern=\"" + pattern + "\", locale=" + locale + ", zone=" + zone + "]";
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a LogDateTime instance from a built-in preset using the default locale and default zone.
    *
    * @param preset the date-time preset, or {@code null} to use the default preset
    * @return a configured LogDateTime instance, never {@code null}
    **/
    public static LogDateTime ofPreset(LogDateTimePreset preset){
        return ofPreset(preset, DEFAULT_LOCALE, DEFAULT_ZONE);
    }
    /**
    * Creates a LogDateTime instance from a built-in preset and locale using the default zone.
    *
    * @param preset the date-time preset, or {@code null} to use the default preset
    * @param locale the locale to apply, or {@code null} to use the default locale
    * @return a configured LogDateTime instance, never {@code null}
    **/
    public static LogDateTime ofPreset(LogDateTimePreset preset, Locale locale){
        return ofPreset(preset, locale, DEFAULT_ZONE);
    }
    /**
    * Creates a LogDateTime instance from a built-in preset, locale, and zone.
    *
    * <p>
    *   If the date-time preset is {@code null}, an internal diagnostic warning is emitted
    *   and the default preset is used.
    * </p>
    *
    * @param preset the date-time preset, or {@code null} to use the default preset
    * @param locale the locale to apply, or {@code null} to use the default locale
    * @param zone the zone to apply, or {@code null} to use the default zone
    * @return a configured LogDateTime instance, never {@code null}
    **/
    public static LogDateTime ofPreset(LogDateTimePreset preset, Locale locale, ZoneId zone){
        if(preset == null){
            InternalDiagnostic.warn(
                "LogDateTime.ofPreset: preset is null -> using DEFAULT_PRESET=" + LogDateTimePreset.DEFAULT_PRESET.getLabel()
            );
            preset = LogDateTimePreset.DEFAULT_PRESET;
        }
        return ofPattern(preset.getPattern(), locale, zone);
    }
    /**
    * Creates a LogDateTime instance from a custom pattern using the default locale and default zone.
    *
    * @param pattern the custom date-time pattern, or {@code null} or blank to use the default preset
    * @return a configured LogDateTime instance, never {@code null}
    **/
    public static LogDateTime ofPattern(String pattern){
        return ofPattern(pattern, DEFAULT_LOCALE, DEFAULT_ZONE);
    }
    /**
    * Creates a LogDateTime instance from a custom pattern and locale using the default zone.
    *
    * @param pattern the custom date-time pattern, or {@code null} or blank to use the default preset
    * @param locale the locale to apply, or {@code null} to use the default locale
    * @return a configured LogDateTime instance, never {@code null}
    **/
    public static LogDateTime ofPattern(String pattern, Locale locale){
        return ofPattern(pattern, locale, DEFAULT_ZONE);
    }
    /**
    * Creates a LogDateTime instance from a custom pattern, locale, and zone.
    *
    * <p>
    *   This is the base factory method. All other factory methods delegate here.
    *   If the pattern is invalid, or the locale or zone are {@code null},
    *   an internal diagnostic warning is emitted and the defaults are used.
    * </p>
    *
    * @param pattern the custom date-time pattern, or {@code null} or blank to use the default preset
    * @param locale the locale to apply, or {@code null} to use the default locale
    * @param zone the zone to apply, or {@code null} to use the default zone
    * @return a configured LogDateTime instance, never {@code null}
    **/
    public static LogDateTime ofPattern(String pattern, Locale locale, ZoneId zone){
        if(locale == null){
            InternalDiagnostic.warn("LogDateTime.ofPattern: locale is null -> using DEFAULT_LOCALE=" + DEFAULT_LOCALE);
            locale = DEFAULT_LOCALE;
        }
        if(zone == null){
            InternalDiagnostic.warn("LogDateTime.ofPattern: zone is null -> using DEFAULT_ZONE=" + DEFAULT_ZONE);
            zone = DEFAULT_ZONE;
        }
        if(pattern == null || pattern.isBlank()){
            InternalDiagnostic.warn(
                "LogDateTime.ofPattern: pattern is null/blank -> returning DEFAULT_PRESET=" + LogDateTimePreset.DEFAULT_PRESET.getLabel()
            );
            return ofPattern(LogDateTimePreset.DEFAULT_PRESET.getPattern(), locale, zone);
        }
        pattern = pattern.trim();
        try{
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, locale).withZone(zone);
            return new LogDateTime(pattern, locale, zone, formatter);
        }catch(IllegalArgumentException ex){
            InternalDiagnostic.warn(
                "LogDateTime.ofPattern: pattern is invalid \"" + pattern + "\" -> returning DEFAULT_PRESET=" + LogDateTimePreset.DEFAULT_PRESET.getLabel()
            );
            InternalDiagnostic.info(
                "LogDateTime.ofPattern: for supported symbols, use LogDateTime.printPatternSymbols() or LogDateTime.downloadPatternSymbols(...)"
            );
            return ofPattern(LogDateTimePreset.DEFAULT_PRESET.getPattern(), locale, zone);
        }
    }
    // -- Utility Methods: --------------------------------------------------
    /**
    * Formats the given temporal value using the active pattern, locale, and zone.
    *
    * <p>
    *   {@link java.time.Instant} values are supported directly because the zone
    *   is pre-applied to the internal formatter via {@link DateTimeFormatter#withZone(ZoneId)}.
    *   If {@code temporal} is {@code null}, an internal diagnostic warning is emitted and an empty string is returned.
    *   If the temporal value is incompatible with the active pattern, an internal diagnostic error is emitted and an empty string is returned.
    * </p>
    *
    * @param temporal the date-time value to format, or {@code null} or incompatible with the active pattern to return an empty string
    * @return the formatted string, or an empty string if {@code temporal} is {@code null} or incompatible with the active pattern
    **/
    public String format(TemporalAccessor temporal){
        if(temporal == null){
            InternalDiagnostic.warn("LogDateTime.format: temporal is null -> returning empty string");
            return "";
        }
        try{
            return formatter.format(temporal);
        }catch(DateTimeException ex){
            InternalDiagnostic.error(
                "LogDateTime.format: temporal value is incompatible with pattern \"" + pattern + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> returning empty string"
            );
            return "";
        }
    }
    /**
    * Returns {@code true} if the given temporal value can be formatted using
    * the active pattern, locale, and zone without throwing an exception.
    *
    * <p>
    *   Useful for pre-validating a temporal value before passing it to
    *   {@link #format(TemporalAccessor)}, for example, when the temporal type
    *   is not known ahead of time.
    * </p>
    *
    * @param temporal the date-time value to test, or {@code null} to return {@code false}
    * @return {@code true} if the temporal is non-null and compatible with the active pattern, otherwise {@code false}
    **/
    public boolean isCompatibleWith(TemporalAccessor temporal){
        if(temporal == null) return false;
        try{
            formatter.format(temporal);
            return true;
        }catch(DateTimeException ex){
            return false;
        }
    }
    /**
    * Prints the bundled date-time symbols documentation content to {@code System.out}.
    *
    * <p>
    *   This is useful for developers to reference the supported symbols when creating custom patterns.
    *   If the resource is missing or the print fails, an internal diagnostic error is emitted and {@code false} is returned.
    *   The resource is expected to be bundled in the original JAR under {@code docs/date-formatting-symbols.txt}.
    *   If missing, the JAR may have been modified or tampered with — re-download the original JAR from the source.
    * </p>
    *
    * @return {@code true} if printed successfully, otherwise {@code false}
    **/
    public static boolean printPatternSymbols(){
        try(InputStream in = LogDateTime.class.getResourceAsStream(DATE_FORMATTING_SYMBOLS_RESOURCE)){
            if(in == null){
                reportMissingSymbolsResource("LogDateTime.printPatternSymbols");
                return false;
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println(content);
            InternalDiagnostic.info("LogDateTime.printPatternSymbols: printed the content successfully -> returning true");
            return true;
        }catch(IOException ex){
            InternalDiagnostic.error(
                "LogDateTime.printPatternSymbols: failed to print pattern symbols content -> " +
                "cause: " + ex.getClass().getSimpleName() + ": " + ex.getMessage() +
                " -> returning false"
            );
            return false;
        }
    }
    /**
    * Copies the bundled date-time symbols documentation file to the current working directory.
    *
    * <p>
    *   This is useful for developers to reference the supported symbols when creating custom patterns.
    *   If the resource is missing or the copy fails, an internal diagnostic error is emitted and {@code false} is returned.
    *   The resource is expected to be bundled in the original JAR under {@code docs/date-formatting-symbols.txt}.
    *   If missing, the JAR may have been modified or tampered with — re-download the original JAR from the source.
    * </p>
    * 
    * @return {@code true} if copied successfully, otherwise {@code false}
    **/
    public static boolean downloadPatternSymbols(){
        String userDir;
        try{
            userDir = System.getProperty(WORKING_DIR_PROPERTY);
        }catch(SecurityException ex){
            InternalDiagnostic.error(
                "LogDateTime.downloadPatternSymbols: access to system property \"" + WORKING_DIR_PROPERTY + "\" was denied by SecurityManager -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> returning false"
            );
            return false;
        }
        Path targetFile = Path.of(userDir, DATE_FORMATTING_SYMBOLS_FILE_NAME);
        return downloadPatternSymbols(targetFile);
    }
    /**
    * Copies the bundled date-time symbols documentation file to the target path.
    *
    * <p>
    *   This is useful for developers to reference the supported symbols when creating custom patterns.
    *   If the target file is {@code null}, an internal diagnostic warning is emitted and {@code false} is returned.
    *   If the resource is missing or the copy fails, an internal diagnostic error is emitted and {@code false} is returned.
    *   The resource is expected to be bundled in the original JAR under {@code docs/date-formatting-symbols.txt}.
    *   If missing, the JAR may have been modified or tampered with — re-download the original JAR from the source.
    * </p>
    *
    * <p>
    *   If {@code targetFile} does not already exist, the resource is saved normally and an
    *   internal diagnostic info message reports the file was saved. If {@code targetFile}
    *   already exists, it is replaced and the internal diagnostic info message instead
    *   reports that the existing file was overwritten.
    * </p>
    *
    * @param targetFile the destination file path, or {@code null} to return {@code false}
    * @return {@code true} if copied successfully, otherwise {@code false}
    **/
    public static boolean downloadPatternSymbols(Path targetFile){
        if(targetFile == null){
            InternalDiagnostic.warn("LogDateTime.downloadPatternSymbols: targetFile is null -> returning false");
            return false;
        }
        try(InputStream in = LogDateTime.class.getResourceAsStream(DATE_FORMATTING_SYMBOLS_RESOURCE)){
            if(in == null){
                reportMissingSymbolsResource("LogDateTime.downloadPatternSymbols");
                return false;
            }
            Path parent = targetFile.getParent();
            if(parent != null) Files.createDirectories(parent);
            boolean existedBefore = Files.exists(targetFile);
            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            if(existedBefore)
                InternalDiagnostic.info("LogDateTime.downloadPatternSymbols: existing file at \"" + targetFile + "\" was overwritten -> returning true");
            else
                InternalDiagnostic.info("LogDateTime.downloadPatternSymbols: file saved to \"" + targetFile + "\" -> returning true");
            return true;
        }catch(SecurityException ex){
            InternalDiagnostic.error(
                "LogDateTime.downloadPatternSymbols: file system access to \"" + targetFile + "\" was denied by SecurityManager -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> returning false"
            );
            return false;
        }catch(IOException ex){
            InternalDiagnostic.error(
                "LogDateTime.downloadPatternSymbols: failed to download pattern symbols file to \"" + targetFile + "\" -> cause: " +
                ex.getClass().getSimpleName() + ": " + ex.getMessage() + " -> returning false"
            );
            return false;
        }
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Reports a missing bundled date-time symbols resource.
    *
    * @param callerName the method reporting the missing resource
    **/
    private static void reportMissingSymbolsResource(String callerName){
        InternalDiagnostic.error(
            callerName + ": resource not found: DATE_FORMATTING_SYMBOLS_RESOURCE=\"" +
            DATE_FORMATTING_SYMBOLS_RESOURCE + "\" (expected under \"docs/\") -> returning false"
        );
        InternalDiagnostic.warn(
            callerName + ": this resource is bundled in the original JAR - if missing, the JAR may have been " +
            "modified or tampered with -> re-download the original JAR from the source"
        );
    }
}