package boraheeb.util.logging;
// -- Ansi Class: -------------------------------------------------------
/**
* ANSI escape code constants and utilities for terminal text styling.
*
* <p>Provides three levels of color support:</p>
* <ul>
*   <li>Standard 16 Colors — {@code FG_RED}, {@code BG_BLUE}, etc.</li>
*   <li>256-Color Palette — {@link #fg256(int)}, {@link #bg256(int)}</li>
*   <li>True Color RGB — {@link #fgRgb(int, int, int)}, {@link #bgRgb(int, int, int)}</li>
* </ul>
*
* <p>Example usage:</p>
* <pre>{@code
*   System.out.println(Ansi.wrap(Ansi.FG_RED + Ansi.BOLD, "Error!"));
*   System.out.println(Ansi.wrap(Ansi.fg256(208), "Orange text"));
*   System.out.println(Ansi.wrap(Ansi.fgRgb(255, 165, 0), "True orange"));
* }</pre>
*
* <p>
*   ANSI codes have no effect on terminals that do not support them.
*   Use {@link LogTheme} to control whether colors are applied in log output.
* </p>
*
* <p>
*   Based on the ANSI/VT100 escape code standard (ECMA-48).
*   Reference: <a href="https://en.wikipedia.org/wiki/ANSI_escape_code">ANSI escape code — Wikipedia</a>
* </p>
*
* <p>This class is a static utility class and is thread-safe.</p>
*
* @author BoRaheeb
**/
public final class Ansi{
    // -- Constants: --------------------------------------------------------
    /** ANSI prefix for 256-color foreground codes. **/
    private static final String FG_256_PREFIX = "\u001B[38;5;";
    /** ANSI prefix for 256-color background codes. **/
    private static final String BG_256_PREFIX = "\u001B[48;5;";
    /** ANSI prefix for RGB (true color) foreground codes. **/
    private static final String FG_RGB_PREFIX = "\u001B[38;2;";
    /** ANSI prefix for RGB (true color) background codes. **/
    private static final String BG_RGB_PREFIX = "\u001B[48;2;";
    /** Separator used between RGB or 256-color values. **/
    private static final String SEMICOLON = ";";
    /** ANSI escape code suffix. **/
    private static final String SUFFIX = "m";
    /** Minimum valid value for 256-color codes. **/
    private static final int MIN_256 = 0;
    /** Maximum valid value for 256-color codes. **/
    private static final int MAX_256 = 255;
    /** Default 256-color code used as fallback when invalid input is provided. **/
    private static final int DEFAULT_256 = 244;
    /** Minimum valid value for RGB channels. **/
    private static final int MIN_RGB = 0;
    /** Maximum valid value for RGB channels. **/
    private static final int MAX_RGB = 255;
    /** Default RGB channel value used as fallback when invalid input is provided. **/
    private static final int DEFAULT_RGB = 180;
        // -- ANSI Basic Escape Codes: ------------------------------------------
            // -- ANSI Text Formatting Escape Codes: --------------------------------
                /** Resets all active styles and colors ({@code "U+001B[0m"}). **/
                public static final String RESET = "\u001B[0m";
                /** Bold text ({@code "U+001B[1m"}). **/
                public static final String BOLD = "\u001B[1m";
                /** Dim (faint) text ({@code "U+001B[2m"}). **/
                public static final String DIM = "\u001B[2m";
                /** Italic text ({@code "U+001B[3m"}). **/
                public static final String ITALIC = "\u001B[3m";
                /** Underlined text ({@code "U+001B[4m"}). **/
                public static final String UNDERLINE = "\u001B[4m";
                /** Slow blinking text ({@code "U+001B[5m"}). **/
                public static final String BLINK = "\u001B[5m";
                /** Rapid blinking text ({@code "U+001B[6m"}). **/
                public static final String RAPID_BLINK = "\u001B[6m";
                /** Inverted foreground and background colors ({@code "U+001B[7m"}). **/
                public static final String INVERT = "\u001B[7m";
                /** Hidden (concealed) text ({@code "U+001B[8m"}). **/
                public static final String HIDDEN = "\u001B[8m";
                /** Strikethrough text ({@code "U+001B[9m"}). **/
                public static final String STRIKETHROUGH = "\u001B[9m";
            // -- ANSI Standard Foreground Colors: ----------------------------------
                /** Black foreground ({@code "U+001B[30m"}). **/
                public static final String FG_BLACK = "\u001B[30m";
                /** Red foreground ({@code "U+001B[31m"}). **/
                public static final String FG_RED = "\u001B[31m";
                /** Green foreground ({@code "U+001B[32m"}). **/
                public static final String FG_GREEN = "\u001B[32m";
                /** Yellow foreground ({@code "U+001B[33m"}). **/
                public static final String FG_YELLOW = "\u001B[33m";
                /** Blue foreground ({@code "U+001B[34m"}). **/
                public static final String FG_BLUE = "\u001B[34m";
                /** Purple (magenta) foreground ({@code "U+001B[35m"}). **/
                public static final String FG_PURPLE = "\u001B[35m";
                /** Cyan foreground ({@code "U+001B[36m"}). **/
                public static final String FG_CYAN = "\u001B[36m";
                /** White foreground ({@code "U+001B[37m"}). **/
                public static final String FG_WHITE = "\u001B[37m";
                /** Bright black (dark gray) foreground ({@code "U+001B[90m"}). **/
                public static final String FG_BRIGHT_BLACK = "\u001B[90m";
                /** Bright red foreground ({@code "U+001B[91m"}). **/
                public static final String FG_BRIGHT_RED = "\u001B[91m";
                /** Bright green foreground ({@code "U+001B[92m"}). **/
                public static final String FG_BRIGHT_GREEN = "\u001B[92m";
                /** Bright yellow foreground ({@code "U+001B[93m"}). **/
                public static final String FG_BRIGHT_YELLOW = "\u001B[93m";
                /** Bright blue foreground ({@code "U+001B[94m"}). **/
                public static final String FG_BRIGHT_BLUE = "\u001B[94m";
                /** Bright purple (magenta) foreground ({@code "U+001B[95m"}). **/
                public static final String FG_BRIGHT_PURPLE = "\u001B[95m";
                /** Bright cyan foreground ({@code "U+001B[96m"}). **/
                public static final String FG_BRIGHT_CYAN = "\u001B[96m";
                /** Bright white foreground ({@code "U+001B[97m"}). **/
                public static final String FG_BRIGHT_WHITE = "\u001B[97m";
            // -- ANSI Standard Background Colors: ----------------------------------
                /** Black background ({@code "U+001B[40m"}). **/
                public static final String BG_BLACK = "\u001B[40m";
                /** Red background ({@code "U+001B[41m"}). **/
                public static final String BG_RED = "\u001B[41m";
                /** Green background ({@code "U+001B[42m"}). **/
                public static final String BG_GREEN = "\u001B[42m";
                /** Yellow background ({@code "U+001B[43m"}). **/
                public static final String BG_YELLOW = "\u001B[43m";
                /** Blue background ({@code "U+001B[44m"}). **/
                public static final String BG_BLUE = "\u001B[44m";
                /** Purple (magenta) background ({@code "U+001B[45m"}). **/
                public static final String BG_PURPLE = "\u001B[45m";
                /** Cyan background ({@code "U+001B[46m"}). **/
                public static final String BG_CYAN = "\u001B[46m";
                /** White background ({@code "U+001B[47m"}). **/
                public static final String BG_WHITE = "\u001B[47m";
                /** Bright black (dark gray) background ({@code "U+001B[100m"}). **/
                public static final String BG_BRIGHT_BLACK = "\u001B[100m";
                /** Bright red background ({@code "U+001B[101m"}). **/
                public static final String BG_BRIGHT_RED = "\u001B[101m";
                /** Bright green background ({@code "U+001B[102m"}). **/
                public static final String BG_BRIGHT_GREEN = "\u001B[102m";
                /** Bright yellow background ({@code "U+001B[103m"}). **/
                public static final String BG_BRIGHT_YELLOW = "\u001B[103m";
                /** Bright blue background ({@code "U+001B[104m"}). **/
                public static final String BG_BRIGHT_BLUE = "\u001B[104m";
                /** Bright purple (magenta) background ({@code "U+001B[105m"}). **/
                public static final String BG_BRIGHT_PURPLE = "\u001B[105m";
                /** Bright cyan background ({@code "U+001B[106m"}). **/
                public static final String BG_BRIGHT_CYAN = "\u001B[106m";
                /** Bright white background ({@code "U+001B[107m"}). **/
                public static final String BG_BRIGHT_WHITE = "\u001B[107m";
        // -- ANSI 256-Color Escape Codes: --------------------------------------
            // -- ANSI 256-Color Foreground Colors: ---------------------------------
                /** Black foreground, 256-color index 16. **/
                public static final String FG_256_BLACK = fg256(16);
                /** Dark gray foreground, 256-color index 236. **/
                public static final String FG_256_DARK_GRAY = fg256(236);
                /** Gray foreground, 256-color index 244. **/
                public static final String FG_256_GRAY = fg256(244);
                /** Light gray foreground, 256-color index 250. **/
                public static final String FG_256_LIGHT_GRAY = fg256(250);
                /** White foreground, 256-color index 255. **/
                public static final String FG_256_WHITE = fg256(255);
                /** Red foreground, 256-color index 196. **/
                public static final String FG_256_RED = fg256(196);
                /** Crimson foreground, 256-color index 197. **/
                public static final String FG_256_CRIMSON = fg256(197);
                /** Orange foreground, 256-color index 208. **/
                public static final String FG_256_ORANGE = fg256(208);
                /** Gold foreground, 256-color index 220. **/
                public static final String FG_256_GOLD = fg256(220);
                /** Yellow foreground, 256-color index 226. **/
                public static final String FG_256_YELLOW = fg256(226);
                /** Green foreground, 256-color index 46. **/
                public static final String FG_256_GREEN = fg256(46);
                /** Lime foreground, 256-color index 118. **/
                public static final String FG_256_LIME = fg256(118);
                /** Teal foreground, 256-color index 37. **/
                public static final String FG_256_TEAL = fg256(37);
                /** Cyan foreground, 256-color index 51. **/
                public static final String FG_256_CYAN = fg256(51);
                /** Blue foreground, 256-color index 33. **/
                public static final String FG_256_BLUE = fg256(33);
                /** Sky blue foreground, 256-color index 117. **/
                public static final String FG_256_SKY_BLUE = fg256(117);
                /** Navy foreground, 256-color index 18. **/
                public static final String FG_256_NAVY = fg256(18);
                /** Purple foreground, 256-color index 93. **/
                public static final String FG_256_PURPLE = fg256(93);
                /** Violet foreground, 256-color index 141. **/
                public static final String FG_256_VIOLET = fg256(141);
                /** Magenta foreground, 256-color index 201. **/
                public static final String FG_256_MAGENTA = fg256(201);
                /** Pink foreground, 256-color index 205. **/
                public static final String FG_256_PINK = fg256(205);
                /** Brown foreground, 256-color index 94. **/
                public static final String FG_256_BROWN = fg256(94);
            // -- ANSI 256-Color Background Colors: ---------------------------------
                /** Black background, 256-color index 16. **/
                public static final String BG_256_BLACK = bg256(16);
                /** Dark gray background, 256-color index 236. **/
                public static final String BG_256_DARK_GRAY = bg256(236);
                /** Gray background, 256-color index 244. **/
                public static final String BG_256_GRAY = bg256(244);
                /** Light gray background, 256-color index 250. **/
                public static final String BG_256_LIGHT_GRAY = bg256(250);
                /** White background, 256-color index 255. **/
                public static final String BG_256_WHITE = bg256(255);
                /** Red background, 256-color index 196. **/
                public static final String BG_256_RED = bg256(196);
                /** Crimson background, 256-color index 197. **/
                public static final String BG_256_CRIMSON = bg256(197);
                /** Orange background, 256-color index 208. **/
                public static final String BG_256_ORANGE = bg256(208);
                /** Gold background, 256-color index 220. **/
                public static final String BG_256_GOLD = bg256(220);
                /** Yellow background, 256-color index 226. **/
                public static final String BG_256_YELLOW = bg256(226);
                /** Green background, 256-color index 46. **/
                public static final String BG_256_GREEN = bg256(46);
                /** Lime background, 256-color index 118. **/
                public static final String BG_256_LIME = bg256(118);
                /** Teal background, 256-color index 37. **/
                public static final String BG_256_TEAL = bg256(37);
                /** Cyan background, 256-color index 51. **/
                public static final String BG_256_CYAN = bg256(51);
                /** Blue background, 256-color index 33. **/
                public static final String BG_256_BLUE = bg256(33);
                /** Sky blue background, 256-color index 117. **/
                public static final String BG_256_SKY_BLUE = bg256(117);
                /** Navy background, 256-color index 18. **/
                public static final String BG_256_NAVY = bg256(18);
                /** Purple background, 256-color index 93. **/
                public static final String BG_256_PURPLE = bg256(93);
                /** Violet background, 256-color index 141. **/
                public static final String BG_256_VIOLET = bg256(141);
                /** Magenta background, 256-color index 201. **/
                public static final String BG_256_MAGENTA = bg256(201);
                /** Pink background, 256-color index 205. **/
                public static final String BG_256_PINK = bg256(205);
                /** Brown background, 256-color index 94. **/
                public static final String BG_256_BROWN = bg256(94);
        // -- ANSI RGB Escape Codes: --------------------------------------------
            // -- ANSI RGB Foreground Colors: ---------------------------------------
                /** Orange foreground, rgb(255, 165, 0). **/
                public static final String FG_RGB_ORANGE = fgRgb(255, 165, 0);
                /** Dark orange foreground, rgb(255, 140, 0). **/
                public static final String FG_RGB_DARK_ORANGE = fgRgb(255, 140, 0);
                /** Gold foreground, rgb(255, 215, 0). **/
                public static final String FG_RGB_GOLD = fgRgb(255, 215, 0);
                /** Dark gold foreground, rgb(184, 134, 11). **/
                public static final String FG_RGB_DARK_GOLD = fgRgb(184, 134, 11);
                /** Brown foreground, rgb(139, 69, 19). **/
                public static final String FG_RGB_BROWN = fgRgb(139, 69, 19);
                /** Chocolate foreground, rgb(210, 105, 30). **/
                public static final String FG_RGB_CHOCOLATE = fgRgb(210, 105, 30);
                /** Tan foreground, rgb(210, 180, 140). **/
                public static final String FG_RGB_TAN = fgRgb(210, 180, 140);
                /** Pink foreground, rgb(255, 105, 180). **/
                public static final String FG_RGB_PINK = fgRgb(255, 105, 180);
                /** Hot pink foreground, rgb(255, 20, 147). **/
                public static final String FG_RGB_HOT_PINK = fgRgb(255, 20, 147);
                /** Deep pink foreground, rgb(199, 21, 133). **/
                public static final String FG_RGB_DEEP_PINK = fgRgb(199, 21, 133);
                /** Coral foreground, rgb(255, 127, 80). **/
                public static final String FG_RGB_CORAL = fgRgb(255, 127, 80);
                /** Salmon foreground, rgb(250, 128, 114). **/
                public static final String FG_RGB_SALMON = fgRgb(250, 128, 114);
                /** Crimson foreground, rgb(220, 20, 60). **/
                public static final String FG_RGB_CRIMSON = fgRgb(220, 20, 60);
                /** Maroon foreground, rgb(128, 0, 0). **/
                public static final String FG_RGB_MAROON = fgRgb(128, 0, 0);
                /** Olive foreground, rgb(128, 128, 0). **/
                public static final String FG_RGB_OLIVE = fgRgb(128, 128, 0);
                /** Lime foreground, rgb(50, 205, 50). **/
                public static final String FG_RGB_LIME = fgRgb(50, 205, 50);
                /** Forest green foreground, rgb(34, 139, 34). **/
                public static final String FG_RGB_FOREST_GREEN = fgRgb(34, 139, 34);
                /** Mint foreground, rgb(152, 255, 152). **/
                public static final String FG_RGB_MINT = fgRgb(152, 255, 152);
                /** Teal foreground, rgb(0, 128, 128). **/
                public static final String FG_RGB_TEAL = fgRgb(0, 128, 128);
                /** Turquoise foreground, rgb(64, 224, 208). **/
                public static final String FG_RGB_TURQUOISE = fgRgb(64, 224, 208);
                /** Sky blue foreground, rgb(135, 206, 235). **/
                public static final String FG_RGB_SKY_BLUE = fgRgb(135, 206, 235);
                /** Deep sky blue foreground, rgb(0, 191, 255). **/
                public static final String FG_RGB_DEEP_SKY_BLUE = fgRgb(0, 191, 255);
                /** Steel blue foreground, rgb(70, 130, 180). **/
                public static final String FG_RGB_STEEL_BLUE = fgRgb(70, 130, 180);
                /** Navy foreground, rgb(0, 0, 128). **/
                public static final String FG_RGB_NAVY = fgRgb(0, 0, 128);
                /** Indigo foreground, rgb(75, 0, 130). **/
                public static final String FG_RGB_INDIGO = fgRgb(75, 0, 130);
                /** Violet foreground, rgb(138, 43, 226). **/
                public static final String FG_RGB_VIOLET = fgRgb(138, 43, 226);
                /** Magenta foreground, rgb(255, 0, 255). **/
                public static final String FG_RGB_MAGENTA = fgRgb(255, 0, 255);
                /** Plum foreground, rgb(221, 160, 221). **/
                public static final String FG_RGB_PLUM = fgRgb(221, 160, 221);
                /** Lavender foreground, rgb(230, 230, 250). **/
                public static final String FG_RGB_LAVENDER = fgRgb(230, 230, 250);
                /** Beige foreground, rgb(245, 245, 220). **/
                public static final String FG_RGB_BEIGE = fgRgb(245, 245, 220);
                /** Khaki foreground, rgb(240, 230, 140). **/
                public static final String FG_RGB_KHAKI = fgRgb(240, 230, 140);
                /** Silver foreground, rgb(192, 192, 192). **/
                public static final String FG_RGB_SILVER = fgRgb(192, 192, 192);
                /** Gray foreground, rgb(128, 128, 128). **/
                public static final String FG_RGB_GRAY = fgRgb(128, 128, 128);
                /** Dark gray foreground, rgb(64, 64, 64). **/
                public static final String FG_RGB_DARK_GRAY = fgRgb(64, 64, 64);
                /** Light gray foreground, rgb(211, 211, 211). **/
                public static final String FG_RGB_LIGHT_GRAY = fgRgb(211, 211, 211);
                /** Charcoal foreground, rgb(54, 69, 79). **/
                public static final String FG_RGB_CHARCOAL = fgRgb(54, 69, 79);
                /** Snow foreground, rgb(255, 250, 250). **/
                public static final String FG_RGB_SNOW = fgRgb(255, 250, 250);
            // -- ANSI RGB Background Colors: ---------------------------------------
                /** Orange background, rgb(255, 165, 0). **/
                public static final String BG_RGB_ORANGE = bgRgb(255, 165, 0);
                /** Dark orange background, rgb(255, 140, 0). **/
                public static final String BG_RGB_DARK_ORANGE = bgRgb(255, 140, 0);
                /** Gold background, rgb(255, 215, 0). **/
                public static final String BG_RGB_GOLD = bgRgb(255, 215, 0);
                /** Dark gold background, rgb(184, 134, 11). **/
                public static final String BG_RGB_DARK_GOLD = bgRgb(184, 134, 11);
                /** Brown background, rgb(139, 69, 19). **/
                public static final String BG_RGB_BROWN = bgRgb(139, 69, 19);
                /** Chocolate background, rgb(210, 105, 30). **/
                public static final String BG_RGB_CHOCOLATE = bgRgb(210, 105, 30);
                /** Tan background, rgb(210, 180, 140). **/
                public static final String BG_RGB_TAN = bgRgb(210, 180, 140);
                /** Pink background, rgb(255, 105, 180). **/
                public static final String BG_RGB_PINK = bgRgb(255, 105, 180);
                /** Hot pink background, rgb(255, 20, 147). **/
                public static final String BG_RGB_HOT_PINK = bgRgb(255, 20, 147);
                /** Deep pink background, rgb(199, 21, 133). **/
                public static final String BG_RGB_DEEP_PINK = bgRgb(199, 21, 133);
                /** Coral background, rgb(255, 127, 80). **/
                public static final String BG_RGB_CORAL = bgRgb(255, 127, 80);
                /** Salmon background, rgb(250, 128, 114). **/
                public static final String BG_RGB_SALMON = bgRgb(250, 128, 114);
                /** Crimson background, rgb(220, 20, 60). **/
                public static final String BG_RGB_CRIMSON = bgRgb(220, 20, 60);
                /** Maroon background, rgb(128, 0, 0). **/
                public static final String BG_RGB_MAROON = bgRgb(128, 0, 0);
                /** Olive background, rgb(128, 128, 0). **/
                public static final String BG_RGB_OLIVE = bgRgb(128, 128, 0);
                /** Lime background, rgb(50, 205, 50). **/
                public static final String BG_RGB_LIME = bgRgb(50, 205, 50);
                /** Forest green background, rgb(34, 139, 34). **/
                public static final String BG_RGB_FOREST_GREEN = bgRgb(34, 139, 34);
                /** Mint background, rgb(152, 255, 152). **/
                public static final String BG_RGB_MINT = bgRgb(152, 255, 152);
                /** Teal background, rgb(0, 128, 128). **/
                public static final String BG_RGB_TEAL = bgRgb(0, 128, 128);
                /** Turquoise background, rgb(64, 224, 208). **/
                public static final String BG_RGB_TURQUOISE = bgRgb(64, 224, 208);
                /** Sky blue background, rgb(135, 206, 235). **/
                public static final String BG_RGB_SKY_BLUE = bgRgb(135, 206, 235);
                /** Deep sky blue background, rgb(0, 191, 255). **/
                public static final String BG_RGB_DEEP_SKY_BLUE = bgRgb(0, 191, 255);
                /** Steel blue background, rgb(70, 130, 180). **/
                public static final String BG_RGB_STEEL_BLUE = bgRgb(70, 130, 180);
                /** Navy background, rgb(0, 0, 128). **/
                public static final String BG_RGB_NAVY = bgRgb(0, 0, 128);
                /** Indigo background, rgb(75, 0, 130). **/
                public static final String BG_RGB_INDIGO = bgRgb(75, 0, 130);
                /** Violet background, rgb(138, 43, 226). **/
                public static final String BG_RGB_VIOLET = bgRgb(138, 43, 226);
                /** Magenta background, rgb(255, 0, 255). **/
                public static final String BG_RGB_MAGENTA = bgRgb(255, 0, 255);
                /** Plum background, rgb(221, 160, 221). **/
                public static final String BG_RGB_PLUM = bgRgb(221, 160, 221);
                /** Lavender background, rgb(230, 230, 250). **/
                public static final String BG_RGB_LAVENDER = bgRgb(230, 230, 250);
                /** Beige background, rgb(245, 245, 220). **/
                public static final String BG_RGB_BEIGE = bgRgb(245, 245, 220);
                /** Khaki background, rgb(240, 230, 140). **/
                public static final String BG_RGB_KHAKI = bgRgb(240, 230, 140);
                /** Silver background, rgb(192, 192, 192). **/
                public static final String BG_RGB_SILVER = bgRgb(192, 192, 192);
                /** Gray background, rgb(128, 128, 128). **/
                public static final String BG_RGB_GRAY = bgRgb(128, 128, 128);
                /** Dark gray background, rgb(64, 64, 64). **/
                public static final String BG_RGB_DARK_GRAY = bgRgb(64, 64, 64);
                /** Light gray background, rgb(211, 211, 211). **/
                public static final String BG_RGB_LIGHT_GRAY = bgRgb(211, 211, 211);
                /** Charcoal background, rgb(54, 69, 79). **/
                public static final String BG_RGB_CHARCOAL = bgRgb(54, 69, 79);
                /** Snow background, rgb(255, 250, 250). **/
                public static final String BG_RGB_SNOW = bgRgb(255, 250, 250);
    // -- Constructors: -----------------------------------------------------
    /** Private constructor — This class is a static utility class and cannot be created. **/
    private Ansi(){}
    // -- Utility Methods: --------------------------------------------------
    /**
    * Returns a foreground 256-color escape code.
    *
    * <p>If the given code is out of range (0–255), an internal diagnostic warning is emitted and the default code is used.</p>
    *
    * @param code the 256-color code (0–255)
    * @return the ANSI escape string for the given foreground color
    **/
    public static String fg256(int code){
        return (FG_256_PREFIX + clean256(code, "fg256") + SUFFIX);
    }
    /**
    * Returns a background 256-color escape code.
    *
    * <p>If the given code is out of range (0–255), an internal diagnostic warning is emitted and the default code is used.</p>
    *
    * @param code the 256-color code (0–255)
    * @return the ANSI escape string for the given background color
    **/
    public static String bg256(int code){
        return (BG_256_PREFIX + clean256(code, "bg256") + SUFFIX);
    }
    /**
    * Returns a foreground RGB escape code.
    *
    * <p>If any of the given values are out of range (0–255), an internal diagnostic warning is emitted and the default value is used.</p>
    *
    * @param r the red channel (0–255)
    * @param g the green channel (0–255)
    * @param b the blue channel (0–255)
    * @return the ANSI escape string for the given RGB foreground color
    **/
    public static String fgRgb(int r, int g, int b){
        return (
            FG_RGB_PREFIX +
            cleanRgb(r, "fgRgb", "red") + SEMICOLON +
            cleanRgb(g, "fgRgb", "green") + SEMICOLON +
            cleanRgb(b, "fgRgb", "blue") + SUFFIX
        );
    }
    /**
    * Returns a background RGB escape code.
    *
    * <p>If any of the given values are out of range (0–255), an internal diagnostic warning is emitted and the default value is used.</p>
    *
    * @param r the red channel (0–255)
    * @param g the green channel (0–255)
    * @param b the blue channel (0–255)
    * @return the ANSI escape string for the given RGB background color
    **/
    public static String bgRgb(int r, int g, int b){
        return (
            BG_RGB_PREFIX +
            cleanRgb(r, "bgRgb", "red") + SEMICOLON +
            cleanRgb(g, "bgRgb", "green") + SEMICOLON +
            cleanRgb(b, "bgRgb", "blue") + SUFFIX
        );
    }
    /**
    * Wraps the given text with the given style and appends {@link #RESET}.
    *
    * @param style the ANSI style string, or {@code null} for no style
    * @param text the text to wrap, or {@code null} for empty string
    * @return the styled string ending with {@link #RESET}
    **/
    public static String wrap(String style, String text){
        return (((style == null)? "" : style) + ((text == null)? "" : text) + RESET);
    }
    /**
    * Wraps the string representation of the given value with the given style.
    * A {@code null} value is treated as an empty string, consistent with
    * {@link #wrap(String, String)}.
    *
    * @param style the ANSI style string, or {@code null} for no style
    * @param value the value to wrap, or {@code null} for empty string
    * @return the styled string ending with {@link #RESET}
    **/
    public static String wrap(String style, Object value){
        return wrap(style, ((value == null)? null : String.valueOf(value)));
    }
    /**
    * Returns {@code true} if the given code is a valid 256-color index.
    *
    * @param code the code to validate
    * @return {@code true} if {@code code} is in range 0–255, otherwise {@code false}
    **/
    public static boolean isValid256(int code){
        return (code >= MIN_256 && code <= MAX_256);
    }
    /**
    * Returns {@code true} if the given value is a valid RGB channel value.
    *
    * @param value the value to validate
    * @return {@code true} if {@code value} is in range 0–255, otherwise {@code false}
    **/
    public static boolean isValidRgbChannel(int value){
        return (value >= MIN_RGB && value <= MAX_RGB);
    }
    // -- Private Helper Methods: -------------------------------------------
    /**
    * Validates {@code code} against the valid 256-color range (0–255).
    *
    * <p>
    *   If out of range, an internal diagnostic warning is emitted
    *   and {@link #DEFAULT_256} is returned as a fallback.
    * </p>
    *
    * @param code the code to validate
    * @param callerName the calling method name, for the warning message
    * @return a valid 256-color index
    **/
    private static int clean256(int code, String callerName){
        if(code < MIN_256 || code > MAX_256){
            InternalDiagnostic.warn("Ansi." + callerName + ": code (" + code + ") out of range (0-255) -> returning DEFAULT_256=" + DEFAULT_256);
            return DEFAULT_256;
        }
        return code;
    }
    /**
    * Validates {@code value} against the valid RGB channel range (0–255).
    *
    * <p>
    *   If out of range, an internal diagnostic warning is emitted
    *   and {@link #DEFAULT_RGB} is returned as a fallback.
    * </p>
    *
    * @param value the channel value to validate
    * @param callerName the calling method name, for the warning message
    * @param channel the channel name (red/green/blue), for the warning message
    * @return a valid RGB channel value
    **/
    private static int cleanRgb(int value, String callerName, String channel){
        if(value < MIN_RGB || value > MAX_RGB){
            InternalDiagnostic.warn(
                "Ansi." + callerName + ": " + channel + " channel value (" + value + ") out of range (0-255) -> returning DEFAULT_RGB=" + DEFAULT_RGB
            );
            return DEFAULT_RGB;
        }
        return value;
    }
}