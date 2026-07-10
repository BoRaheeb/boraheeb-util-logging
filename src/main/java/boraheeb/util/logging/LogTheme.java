package boraheeb.util.logging;
// -- LogTheme Class: ---------------------------------------------------
/**
* Defines the ANSI color and style theme applied to each part of log output.
*
* <p>A {@code LogTheme} holds individual ANSI style strings for every part of a log record:</p>
* <ul>
*   <li>Timestamp</li>
*   <li>Level: styled per severity (TRACE, DEBUG, INFO, WARN, ERROR, CRITICAL)</li>
*   <li>Logger name</li>
*   <li>Source name</li>
*   <li>Line number</li>
*   <li>Thread name</li>
*   <li>Message</li>
*   <li>Fields</li>
*   <li>Throwable</li>
*   <li>
*      Terminal background: a persistent background color applied once to the
*      entire terminal session and never reset automatically between log entries
*   </li>
* </ul>
*
* <p>
*   Instances are created via the {@link Builder} or from built-in presets
*   such as {@link #NONE}, {@link #DEFAULT_THEME}, {@link #BLUE_ICED}, {@link #DRACULA},
*   {@link #FOREST}, {@link #SUNSET}, {@link #MIDNIGHT}, {@link #CHERRY},
*   {@link #MONOKAI}, {@link #NORD}, {@link #GRUVBOX}, {@link #SOLARIZED},
*   {@link #GITHUB}, and {@link #CATPPUCCIN}.
* </p>
*
* <p>This class is immutable and thread-safe.</p>
*
* @author BoRaheeb
**/
public final class LogTheme{
    // -- Constants: --------------------------------------------------------
    /** Default theme name assigned when no name is provided or the name is null or blank. **/
    private static final String DEFAULT_THEME_NAME = "New Theme";
    /** Theme with no styling — all style strings are empty and no terminal background is set. **/
    public static final LogTheme NONE = builder().themeName("None").build();
    /** Default color theme — assigns distinct colors to the level badge and each log record part. **/
    public static final LogTheme DEFAULT_THEME = builder()
        .themeName("Default Theme")
        .timestampStyle(Ansi.FG_256_GRAY)
        .traceStyle(Ansi.FG_256_WHITE)
        .debugStyle(Ansi.FG_256_CYAN)
        .infoStyle(Ansi.FG_256_GREEN)
        .warnStyle(Ansi.FG_256_YELLOW)
        .errorStyle(Ansi.FG_256_RED)
        .criticalStyle(Ansi.BOLD + Ansi.FG_256_CRIMSON)
        .loggerNameStyle(Ansi.FG_256_CYAN)
        .sourceNameStyle(Ansi.FG_256_GRAY)
        .lineNumberStyle(Ansi.FG_256_GRAY)
        .threadNameStyle(Ansi.FG_256_PURPLE)
        .messageStyle("")
        .fieldsStyle(Ansi.FG_256_YELLOW)
        .throwableStyle(Ansi.FG_256_RED)
        .terminalBackground("")
        .build();
    /** Blue-iced dark theme — deep navy terminal background with icy blue accents, warm level badges, and vivid structured field colors. **/
    public static final LogTheme BLUE_ICED = builder()
        .themeName("Blue Iced")
        .timestampStyle(Ansi.FG_256_GRAY)
        .traceStyle(Ansi.FG_RGB_TEAL)
        .debugStyle(Ansi.FG_RGB_DARK_GOLD)
        .infoStyle(Ansi.FG_RGB_SKY_BLUE)
        .warnStyle(Ansi.FG_RGB_ORANGE)
        .errorStyle(Ansi.FG_256_RED)
        .criticalStyle(Ansi.BG_256_RED + Ansi.FG_256_WHITE + Ansi.BOLD)
        .loggerNameStyle(Ansi.FG_RGB_FOREST_GREEN)
        .sourceNameStyle(Ansi.FG_256_YELLOW)
        .lineNumberStyle(Ansi.FG_RGB_LIME)
        .threadNameStyle(Ansi.FG_RGB_BROWN)
        .messageStyle(Ansi.FG_256_WHITE)
        .fieldsStyle(Ansi.FG_RGB_SALMON)
        .throwableStyle(Ansi.FG_RGB_CRIMSON)
        .terminalBackground(Ansi.bgRgb(2, 10, 109) + Ansi.BOLD)
        .build();
    /** Dracula dark theme — dark purple terminal background with violet, pink, and mint accents inspired by the Dracula color palette. **/
    public static final LogTheme DRACULA = builder()
        .themeName("Dracula")
        .timestampStyle(Ansi.FG_256_GRAY)
        .traceStyle(Ansi.FG_RGB_LAVENDER)
        .debugStyle(Ansi.FG_RGB_VIOLET)
        .infoStyle(Ansi.FG_RGB_MINT)
        .warnStyle(Ansi.FG_256_ORANGE)
        .errorStyle(Ansi.FG_RGB_SALMON)
        .criticalStyle(Ansi.BOLD + Ansi.FG_RGB_HOT_PINK)
        .loggerNameStyle(Ansi.FG_RGB_LIME)
        .sourceNameStyle(Ansi.FG_RGB_PLUM)
        .lineNumberStyle(Ansi.FG_256_VIOLET)
        .threadNameStyle(Ansi.FG_RGB_PINK)
        .messageStyle(Ansi.FG_RGB_SNOW)
        .fieldsStyle(Ansi.FG_256_GOLD)
        .throwableStyle(Ansi.FG_RGB_CRIMSON)
        .terminalBackground(Ansi.bgRgb(40, 42, 54))
        .build();
    /** Forest dark theme — deep green terminal background with earthy tones, teal accents, and warm gold level badges. **/
    public static final LogTheme FOREST = builder()
        .themeName("Forest")
        .timestampStyle(Ansi.FG_256_GRAY)
        .traceStyle(Ansi.FG_256_TEAL)
        .debugStyle(Ansi.FG_256_LIME)
        .infoStyle(Ansi.FG_RGB_MINT)
        .warnStyle(Ansi.FG_256_GOLD)
        .errorStyle(Ansi.FG_RGB_CORAL)
        .criticalStyle(Ansi.BOLD + Ansi.FG_RGB_MAROON)
        .loggerNameStyle(Ansi.FG_RGB_FOREST_GREEN)
        .sourceNameStyle(Ansi.FG_256_GRAY)
        .lineNumberStyle(Ansi.FG_RGB_TEAL)
        .threadNameStyle(Ansi.FG_RGB_OLIVE)
        .messageStyle(Ansi.FG_RGB_LIGHT_GRAY)
        .fieldsStyle(Ansi.FG_256_GOLD)
        .throwableStyle(Ansi.FG_RGB_CRIMSON)
        .terminalBackground(Ansi.bgRgb(10, 30, 10))
        .build();
    /** Sunset dark theme — warm near-black terminal background with coral, gold, and beige tones evoking a glowing horizon. **/
    public static final LogTheme SUNSET = builder()
        .themeName("Sunset")
        .timestampStyle(Ansi.FG_256_GRAY)
        .traceStyle(Ansi.FG_RGB_TAN)
        .debugStyle(Ansi.FG_RGB_CHOCOLATE)
        .infoStyle(Ansi.FG_RGB_CORAL)
        .warnStyle(Ansi.FG_256_GOLD)
        .errorStyle(Ansi.FG_RGB_CRIMSON)
        .criticalStyle(Ansi.BOLD + Ansi.FG_RGB_MAROON)
        .loggerNameStyle(Ansi.FG_RGB_ORANGE)
        .sourceNameStyle(Ansi.FG_RGB_BROWN)
        .lineNumberStyle(Ansi.FG_RGB_DARK_GOLD)
        .threadNameStyle(Ansi.FG_RGB_SALMON)
        .messageStyle(Ansi.FG_RGB_BEIGE)
        .fieldsStyle(Ansi.FG_RGB_DARK_ORANGE)
        .throwableStyle(Ansi.FG_RGB_DEEP_PINK)
        .terminalBackground(Ansi.bgRgb(20, 10, 5))
        .build();
    /** Midnight dark theme — deep navy terminal background with cool blue and steel accents for a clean, focused look. **/
    public static final LogTheme MIDNIGHT = builder()
        .themeName("Midnight")
        .timestampStyle(Ansi.FG_RGB_GRAY)
        .traceStyle(Ansi.FG_256_SKY_BLUE)
        .debugStyle(Ansi.FG_RGB_STEEL_BLUE)
        .infoStyle(Ansi.FG_RGB_DEEP_SKY_BLUE)
        .warnStyle(Ansi.FG_256_GOLD)
        .errorStyle(Ansi.FG_256_CRIMSON)
        .criticalStyle(Ansi.BOLD + Ansi.BG_256_RED + Ansi.FG_256_WHITE)
        .loggerNameStyle(Ansi.FG_RGB_SKY_BLUE)
        .sourceNameStyle(Ansi.FG_256_GRAY)
        .lineNumberStyle(Ansi.FG_RGB_TURQUOISE)
        .threadNameStyle(Ansi.FG_256_VIOLET)
        .messageStyle(Ansi.FG_RGB_LIGHT_GRAY)
        .fieldsStyle(Ansi.FG_256_YELLOW)
        .throwableStyle(Ansi.FG_256_RED)
        .terminalBackground(Ansi.bgRgb(5, 10, 30))
        .build();
    /** Cherry dark theme — deep magenta terminal background with pink, violet, and lavender tones for a rich, elegant look. **/
    public static final LogTheme CHERRY = builder()
        .themeName("Cherry")
        .timestampStyle(Ansi.FG_256_GRAY)
        .traceStyle(Ansi.FG_RGB_PLUM)
        .debugStyle(Ansi.FG_RGB_LAVENDER)
        .infoStyle(Ansi.FG_RGB_PINK)
        .warnStyle(Ansi.FG_RGB_HOT_PINK)
        .errorStyle(Ansi.FG_256_CRIMSON)
        .criticalStyle(Ansi.BOLD + Ansi.FG_RGB_DEEP_PINK)
        .loggerNameStyle(Ansi.FG_256_MAGENTA)
        .sourceNameStyle(Ansi.FG_RGB_LAVENDER)
        .lineNumberStyle(Ansi.FG_256_VIOLET)
        .threadNameStyle(Ansi.FG_RGB_PLUM)
        .messageStyle(Ansi.FG_RGB_SNOW)
        .fieldsStyle(Ansi.FG_RGB_LAVENDER)
        .throwableStyle(Ansi.FG_RGB_CRIMSON)
        .terminalBackground(Ansi.bgRgb(25, 5, 25))
        .build();
    /** Monokai theme — dark olive-black terminal background with vivid pink, green, cyan, orange, and purple accents from the iconic Monokai palette. **/
    public static final LogTheme MONOKAI = builder()
        .themeName("Monokai")
        .timestampStyle(Ansi.fgRgb(117, 113, 94))
        .traceStyle(Ansi.FG_RGB_KHAKI)
        .debugStyle(Ansi.fgRgb(102, 217, 232))
        .infoStyle(Ansi.fgRgb(166, 226, 46))
        .warnStyle(Ansi.fgRgb(253, 151, 31))
        .errorStyle(Ansi.fgRgb(249, 38, 114))
        .criticalStyle(Ansi.BOLD + Ansi.fgRgb(249, 38, 114))
        .loggerNameStyle(Ansi.fgRgb(102, 217, 232))
        .sourceNameStyle(Ansi.FG_RGB_TAN)
        .lineNumberStyle(Ansi.fgRgb(174, 129, 255))
        .threadNameStyle(Ansi.fgRgb(253, 151, 31))
        .messageStyle(Ansi.fgRgb(248, 248, 242))
        .fieldsStyle(Ansi.fgRgb(230, 219, 116))
        .throwableStyle(Ansi.fgRgb(249, 38, 114))
        .terminalBackground(Ansi.bgRgb(39, 40, 34))
        .build();
    /** Nord theme — deep blue-grey terminal background with arctic frost blues, aurora greens, yellows, and reds inspired by the Nord color palette. **/
    public static final LogTheme NORD = builder()
        .themeName("Nord")
        .timestampStyle(Ansi.FG_RGB_GRAY)
        .traceStyle(Ansi.FG_RGB_TURQUOISE)
        .debugStyle(Ansi.fgRgb(136, 192, 208))
        .infoStyle(Ansi.fgRgb(163, 190, 140))
        .warnStyle(Ansi.fgRgb(235, 203, 139))
        .errorStyle(Ansi.fgRgb(191, 97, 106))
        .criticalStyle(Ansi.BOLD + Ansi.fgRgb(191, 97, 106))
        .loggerNameStyle(Ansi.fgRgb(129, 161, 193))
        .sourceNameStyle(Ansi.FG_256_GRAY)
        .lineNumberStyle(Ansi.fgRgb(143, 188, 187))
        .threadNameStyle(Ansi.fgRgb(180, 142, 173))
        .messageStyle(Ansi.fgRgb(236, 239, 244))
        .fieldsStyle(Ansi.fgRgb(235, 203, 139))
        .throwableStyle(Ansi.fgRgb(191, 97, 106))
        .terminalBackground(Ansi.bgRgb(46, 52, 64))
        .build();
    /** Gruvbox theme — warm dark terminal background with retro earthy tones: muted greens, aqua, gold, orange, and soft red from the Gruvbox palette. **/
    public static final LogTheme GRUVBOX = builder()
        .themeName("Gruvbox")
        .timestampStyle(Ansi.fgRgb(146, 131, 116))
        .traceStyle(Ansi.FG_RGB_KHAKI)
        .debugStyle(Ansi.fgRgb(131, 165, 152))
        .infoStyle(Ansi.fgRgb(184, 187, 38))
        .warnStyle(Ansi.fgRgb(250, 189, 47))
        .errorStyle(Ansi.fgRgb(251, 73, 52))
        .criticalStyle(Ansi.BOLD + Ansi.fgRgb(251, 73, 52))
        .loggerNameStyle(Ansi.fgRgb(142, 192, 124))
        .sourceNameStyle(Ansi.FG_RGB_TAN)
        .lineNumberStyle(Ansi.fgRgb(211, 134, 155))
        .threadNameStyle(Ansi.fgRgb(254, 128, 25))
        .messageStyle(Ansi.fgRgb(235, 219, 178))
        .fieldsStyle(Ansi.fgRgb(250, 189, 47))
        .throwableStyle(Ansi.fgRgb(251, 73, 52))
        .terminalBackground(Ansi.bgRgb(40, 40, 40))
        .build();
    /** Solarized theme — deep teal terminal background with the timeless Solarized palette: cyan, green, blue, violet, orange, and muted body text. **/
    public static final LogTheme SOLARIZED = builder()
        .themeName("Solarized")
        .timestampStyle(Ansi.fgRgb(88, 110, 117))
        .traceStyle(Ansi.FG_RGB_LIGHT_GRAY)
        .debugStyle(Ansi.fgRgb(42, 161, 152))
        .infoStyle(Ansi.fgRgb(133, 153, 0))
        .warnStyle(Ansi.fgRgb(181, 137, 0))
        .errorStyle(Ansi.fgRgb(220, 50, 47))
        .criticalStyle(Ansi.BOLD + Ansi.fgRgb(211, 54, 130))
        .loggerNameStyle(Ansi.fgRgb(38, 139, 210))
        .sourceNameStyle(Ansi.FG_256_GRAY)
        .lineNumberStyle(Ansi.fgRgb(108, 113, 196))
        .threadNameStyle(Ansi.fgRgb(203, 75, 22))
        .messageStyle(Ansi.fgRgb(147, 161, 161))
        .fieldsStyle(Ansi.fgRgb(181, 137, 0))
        .throwableStyle(Ansi.fgRgb(220, 50, 47))
        .terminalBackground(Ansi.bgRgb(0, 43, 54))
        .build();
    /** GitHub dark theme — near-black terminal background with GitHub's Primer palette: accent blue, success green, attention yellow, danger red, and done purple. **/
    public static final LogTheme GITHUB = builder()
        .themeName("GitHub")
        .timestampStyle(Ansi.fgRgb(110, 118, 129))
        .traceStyle(Ansi.FG_RGB_LIGHT_GRAY)
        .debugStyle(Ansi.fgRgb(88, 166, 255))
        .infoStyle(Ansi.fgRgb(63, 185, 80))
        .warnStyle(Ansi.fgRgb(210, 153, 34))
        .errorStyle(Ansi.fgRgb(248, 81, 73))
        .criticalStyle(Ansi.BOLD + Ansi.fgRgb(248, 81, 73))
        .loggerNameStyle(Ansi.fgRgb(88, 166, 255))
        .sourceNameStyle(Ansi.FG_256_GRAY)
        .lineNumberStyle(Ansi.fgRgb(163, 113, 247))
        .threadNameStyle(Ansi.fgRgb(219, 109, 40))
        .messageStyle(Ansi.fgRgb(230, 237, 243))
        .fieldsStyle(Ansi.fgRgb(210, 153, 34))
        .throwableStyle(Ansi.fgRgb(248, 81, 73))
        .terminalBackground(Ansi.bgRgb(13, 17, 23))
        .build();
    /** Catppuccin theme — deep indigo terminal background with soft pastel accents from the Catppuccin Mocha palette: sky, green, peach, mauve, and lavender tones. **/
    public static final LogTheme CATPPUCCIN = builder()
        .themeName("Catppuccin")
        .timestampStyle(Ansi.fgRgb(108, 112, 134))
        .traceStyle(Ansi.FG_RGB_LAVENDER)
        .debugStyle(Ansi.fgRgb(137, 220, 235))
        .infoStyle(Ansi.fgRgb(166, 227, 161))
        .warnStyle(Ansi.fgRgb(249, 226, 175))
        .errorStyle(Ansi.fgRgb(243, 139, 168))
        .criticalStyle(Ansi.BOLD + Ansi.fgRgb(235, 160, 172))
        .loggerNameStyle(Ansi.fgRgb(116, 199, 236))
        .sourceNameStyle(Ansi.FG_RGB_PLUM)
        .lineNumberStyle(Ansi.fgRgb(203, 166, 247))
        .threadNameStyle(Ansi.fgRgb(245, 194, 231))
        .messageStyle(Ansi.fgRgb(205, 214, 244))
        .fieldsStyle(Ansi.fgRgb(249, 226, 175))
        .throwableStyle(Ansi.fgRgb(243, 139, 168))
        .terminalBackground(Ansi.bgRgb(30, 30, 46))
        .build();
    /** All supported log themes. **/
    private static final LogTheme[] VALUES = {
        NONE, DEFAULT_THEME, BLUE_ICED, DRACULA, FOREST, SUNSET, MIDNIGHT, CHERRY,
        MONOKAI, NORD, GRUVBOX, SOLARIZED, GITHUB, CATPPUCCIN
    };
    // -- Fields: -----------------------------------------------------------
    /** Display name of this theme (for example, {@code "Dracula"}, {@code "Nord"}). **/
    private final String themeName;
    /** ANSI style string applied to the timestamp part. **/
    private final String timestampStyle;
    /** ANSI style string applied to the TRACE level badge. **/
    private final String traceStyle;
    /** ANSI style string applied to the DEBUG level badge. **/
    private final String debugStyle;
    /** ANSI style string applied to the INFO level badge. **/
    private final String infoStyle;
    /** ANSI style string applied to the WARN level badge. **/
    private final String warnStyle;
    /** ANSI style string applied to the ERROR level badge. **/
    private final String errorStyle;
    /** ANSI style string applied to the CRITICAL level badge. **/
    private final String criticalStyle;
    /** ANSI style string applied to the logger name part. **/
    private final String loggerNameStyle;
    /** ANSI style string applied to the source name part. **/
    private final String sourceNameStyle;
    /** ANSI style string applied to the line number part. **/
    private final String lineNumberStyle;
    /** ANSI style string applied to the thread name part. **/
    private final String threadNameStyle;
    /** ANSI style string applied to the message part. **/
    private final String messageStyle;
    /** ANSI style string applied to the structured fields part. **/
    private final String fieldsStyle;
    /** ANSI style string applied to the throwable part. **/
    private final String throwableStyle;
    /**
    * Optional persistent terminal background color.
    *
    * <p>
    *   Unlike per-part styles, which wrap individual parts of a log entry and are
    *   followed by a reset, this background is applied once and remains active
    *   for the entire terminal session. It is never reset automatically between
    *   log entries. The developer is responsible for clearing it
    *   (for example, by printing {@link Ansi#RESET} directly to the terminal).
    * </p>
    *
    * <p>Set to {@code null} or empty string for no persistent background.</p>
    **/
    private final String terminalBackground;
    // -- Constructors: -----------------------------------------------------
    /**
    * Creates a log theme from the given builder.
    *
    * @param builder the builder containing theme values
    **/
    private LogTheme(Builder builder){
        this.themeName = builder.themeName;
        this.timestampStyle = builder.timestampStyle;
        this.traceStyle = builder.traceStyle;
        this.debugStyle = builder.debugStyle;
        this.infoStyle = builder.infoStyle;
        this.warnStyle = builder.warnStyle;
        this.errorStyle = builder.errorStyle;
        this.criticalStyle = builder.criticalStyle;
        this.loggerNameStyle = builder.loggerNameStyle;
        this.sourceNameStyle = builder.sourceNameStyle;
        this.lineNumberStyle = builder.lineNumberStyle;
        this.threadNameStyle = builder.threadNameStyle;
        this.messageStyle = builder.messageStyle;
        this.fieldsStyle = builder.fieldsStyle;
        this.throwableStyle = builder.throwableStyle;
        this.terminalBackground = ((builder.terminalBackground == null || builder.terminalBackground.isBlank())?
            null : builder.terminalBackground
        );
    }
    // -- Accessor Methods: -------------------------------------------------
    /**
    * Returns the display name of this theme.
    *
    * @return the theme name, never {@code null}
    **/
    public String getThemeName(){
        return themeName;
    }
    /**
    * Returns the ANSI style string for the timestamp part.
    *
    * @return the ANSI style string, never {@code null}
    **/
    public String getTimestampStyle(){
        return timestampStyle;
    }
    /**
    * Returns the ANSI style string for the given log level badge.
    *
    * <p>
    *   The style is selected by identity against the known {@link LogLevel} constants
    *   (TRACE, DEBUG, INFO, WARN, ERROR). Any level that is none of those maps to
    *   the critical style. If {@code level} is {@code null}, an internal diagnostic warning
    *   is emitted and the default level is used.
    * </p>
    *
    * @param level the log level to look up
    * @return the ANSI style string, never {@code null}
    **/
    public String getLevelStyle(LogLevel level){
        if(level == null){
            InternalDiagnostic.warn("LogTheme.getLevelStyle: level is null -> using DEFAULT_LEVEL=" + LogLevel.DEFAULT_LEVEL.getLabel());
            level = LogLevel.DEFAULT_LEVEL;
        }
        if(level == LogLevel.TRACE) return traceStyle;
        if(level == LogLevel.DEBUG) return debugStyle;
        if(level == LogLevel.INFO) return infoStyle;
        if(level == LogLevel.WARN) return warnStyle;
        if(level == LogLevel.ERROR) return errorStyle;
        return criticalStyle;
    }
    /**
    * Returns the ANSI style string for the logger name part.
    *
    * @return the ANSI style string, never {@code null}
    **/
    public String getLoggerNameStyle(){
        return loggerNameStyle;
    }
    /**
    * Returns the ANSI style string for the source name part.
    *
    * @return the ANSI style string, never {@code null}
    **/
    public String getSourceNameStyle(){
        return sourceNameStyle;
    }
    /**
    * Returns the ANSI style string for the line number part.
    *
    * @return the ANSI style string, never {@code null}
    **/
    public String getLineNumberStyle(){
        return lineNumberStyle;
    }
    /**
    * Returns the ANSI style string for the thread name part.
    *
    * @return the ANSI style string, never {@code null}
    **/
    public String getThreadNameStyle(){
        return threadNameStyle;
    }
    /**
    * Returns the ANSI style string for the message part.
    *
    * @return the ANSI style string, never {@code null}
    **/
    public String getMessageStyle(){
        return messageStyle;
    }
    /**
    * Returns the ANSI style string for the structured fields part.
    *
    * @return the ANSI style string, never {@code null}
    **/
    public String getFieldsStyle(){
        return fieldsStyle;
    }
    /**
    * Returns the ANSI style string for the throwable part.
    *
    * @return the ANSI style string, never {@code null}
    **/
    public String getThrowableStyle(){
        return throwableStyle;
    }
    /**
    * Returns the persistent terminal background color string, or {@code null} if none is set.
    *
    * <p>
    *   This value is meant to be written once to the terminal and left active.
    *   It is never reset automatically between log entries.
    * </p>
    *
    * @return the ANSI background string, or {@code null} if no persistent background is configured
    **/
    public String getTerminalBackground(){
        return terminalBackground;
    }
    /**
    * Returns {@code true} if a persistent terminal background color is configured.
    *
    * @return {@code true} if a persistent terminal background is set, otherwise {@code false}
    **/
    public boolean hasTerminalBackground(){
        return (terminalBackground != null);
    }
    /**
    * Returns a string representation of this theme.
    *
    * @return a string in the form {@code LogTheme[themeName=..., hasTerminalBackground=...]}
    **/
    @Override
    public String toString(){
        return "LogTheme[themeName=" + themeName + ", hasTerminalBackground=" + hasTerminalBackground() + "]";
    }
    // -- Factory Methods: --------------------------------------------------
    /**
    * Creates a new builder for constructing a {@link LogTheme}.
    *
    * @return a new log theme builder
    **/
    public static Builder builder(){
        return new Builder();
    }
    /**
    * Creates a new builder pre-populated with all style values from this theme.
    *
    * <p>
    *   Use this to create a modified copy of an existing theme without
    *   re-specifying every field from scratch. The original theme is not modified.
    * </p>
    *
    * @return a new builder initialized from this theme's values
    **/
    public Builder toBuilder(){
        return new Builder()
            .themeName(themeName)
            .timestampStyle(timestampStyle)
            .traceStyle(traceStyle)
            .debugStyle(debugStyle)
            .infoStyle(infoStyle)
            .warnStyle(warnStyle)
            .errorStyle(errorStyle)
            .criticalStyle(criticalStyle)
            .loggerNameStyle(loggerNameStyle)
            .sourceNameStyle(sourceNameStyle)
            .lineNumberStyle(lineNumberStyle)
            .threadNameStyle(threadNameStyle)
            .messageStyle(messageStyle)
            .fieldsStyle(fieldsStyle)
            .throwableStyle(throwableStyle)
            .terminalBackground(terminalBackground);
    }
    // -- Utility Methods: --------------------------------------------------
    /**
    * Returns a copy of all supported log themes.
    *
    * @return a copy of all supported log themes, never {@code null}
    **/
    public static LogTheme[] getValues(){
        return VALUES.clone();
    }
    // -- Builder: ----------------------------------------------------------
    /**
    * Builder for creating {@link LogTheme} instances.
    *
    * <p>
    *   All style fields default to empty strings (no styling).
    *   The theme name defaults to {@link LogTheme#DEFAULT_THEME_NAME}.
    *   The terminal background defaults to {@code null} (no persistent background).
    * </p>
    **/
    public static final class Builder{
        // -- Builder Fields: ---------------------------------------------------
        /** Display name assigned to the theme being built. **/
        private String themeName = DEFAULT_THEME_NAME;
        /** ANSI style assigned to the timestamp part. **/
        private String timestampStyle = "";
        /** ANSI style assigned to TRACE-level badges. **/
        private String traceStyle = "";
        /** ANSI style assigned to DEBUG-level badges. **/
        private String debugStyle = "";
        /** ANSI style assigned to INFO-level badges. **/
        private String infoStyle = "";
        /** ANSI style assigned to WARN-level badges. **/
        private String warnStyle = "";
        /** ANSI style assigned to ERROR-level badges. **/
        private String errorStyle = "";
        /** ANSI style assigned to CRITICAL-level badges. **/
        private String criticalStyle = "";
        /** ANSI style assigned to the logger name part. **/
        private String loggerNameStyle = "";
        /** ANSI style assigned to the source name part. **/
        private String sourceNameStyle = "";
        /** ANSI style assigned to the line number part. **/
        private String lineNumberStyle = "";
        /** ANSI style assigned to the thread name part. **/
        private String threadNameStyle = "";
        /** ANSI style assigned to the message part. **/
        private String messageStyle = "";
        /** ANSI style assigned to the structured fields part. **/
        private String fieldsStyle = "";
        /** ANSI style assigned to the throwable part. **/
        private String throwableStyle = "";
        /** Persistent terminal background; {@code null} means no background. **/
        private String terminalBackground = null;
        // -- Builder Constructors: ---------------------------------------------
        /**
        * Creates a builder with default values.
        **/
        private Builder(){}
        // -- Builder Methods: --------------------------------------------------
        /**
        * Sets the display name of the theme.
        *
        * <p>
        *   If the given name is {@code null} or blank, an internal diagnostic warning is emitted
        *   and the default theme name is used.
        * </p>
        *
        * @param name the theme name, or {@code null} or blank to use the default theme name
        * @return this builder
        **/
        public Builder themeName(String name){
            if(name == null || name.isBlank()){
                InternalDiagnostic.warn("LogTheme.Builder.themeName: name is null/blank -> using DEFAULT_THEME_NAME=" + DEFAULT_THEME_NAME);
                name = DEFAULT_THEME_NAME;
            }
            themeName = name.trim();
            return this;
        }
        /**
        * Sets the ANSI style for the timestamp part.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder timestampStyle(String style){
            timestampStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the TRACE level badge.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder traceStyle(String style){
            traceStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the DEBUG level badge.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder debugStyle(String style){
            debugStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the INFO level badge.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder infoStyle(String style){
            infoStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the WARN level badge.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder warnStyle(String style){
            warnStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the ERROR level badge.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder errorStyle(String style){
            errorStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the CRITICAL level badge.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder criticalStyle(String style){
            criticalStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the logger name part.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder loggerNameStyle(String style){
            loggerNameStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the source name part.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder sourceNameStyle(String style){
            sourceNameStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the line number part.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder lineNumberStyle(String style){
            lineNumberStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the thread name part.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder threadNameStyle(String style){
            threadNameStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the message part.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder messageStyle(String style){
            messageStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the structured fields part.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder fieldsStyle(String style){
            fieldsStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the ANSI style for the throwable part.
        *
        * @param style the ANSI style string, or {@code null} for no style
        * @return this builder
        **/
        public Builder throwableStyle(String style){
            throwableStyle = ((style == null)? "" : style);
            return this;
        }
        /**
        * Sets the persistent terminal background color.
        *
        * <p>
        *   Unlike per-part styles — which wrap individual parts of a log entry and are
        *   followed by a reset — this background is applied once and remains active
        *   for the entire terminal session. It is never reset automatically between
        *   log entries. The developer is responsible for clearing it
        *   (for example, by printing {@link Ansi#RESET} directly to the terminal).
        * </p>
        *
        * <p>Pass {@code null} or an empty string to disable the persistent background.</p>
        *
        * @param background the ANSI background color string, or {@code null} for none
        * @return this builder
        **/
        public Builder terminalBackground(String background){
            terminalBackground = background;
            return this;
        }
        /**
        * Builds a new immutable {@link LogTheme}.
        *
        * @return a new immutable log theme
        **/
        public LogTheme build(){
            return new LogTheme(this);
        }
    }
}