package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoggerNameFilterTest{

    private LogRecord named(String loggerName){
        return LogRecord.builder().loggerName(loggerName).build();
    }

    @Test
    void includeAcceptsExactMatches(){
        LogFilter filter = LoggerNameFilter.include("db", "auth");
        assertTrue(filter.accept(named("db")));
        assertTrue(filter.accept(named("auth")));
    }

    @Test
    void includeRejectsNonMatches(){
        LogFilter filter = LoggerNameFilter.include("db");
        assertFalse(filter.accept(named("db.pool")));
        assertFalse(filter.accept(named("other")));
    }

    @Test
    void includeWithNullNamesRejectsAll(){
        LogFilter filter = LoggerNameFilter.include((String[]) null);
        assertFalse(filter.accept(named("anything")));
    }

    @Test
    void includeWithEmptyArrayRejectsAll(){
        LogFilter filter = LoggerNameFilter.include();
        assertFalse(filter.accept(named("anything")));
    }

    @Test
    void includeSkipsNullAndBlankEntries(){
        LogFilter filter = LoggerNameFilter.include(null, "  ", "db");
        assertTrue(filter.accept(named("db")));
        assertFalse(filter.accept(named("other")));
    }

    @Test
    void includeWithAllInvalidNamesRejectsAll(){
        LogFilter filter = LoggerNameFilter.include(null, "   ");
        assertFalse(filter.accept(named("anything")));
    }

    @Test
    void includeTrimsWhitespaceInNames(){
        LogFilter filter = LoggerNameFilter.include("  db  ");
        assertTrue(filter.accept(named("db")));
    }

    @Test
    void prefixMatchesHierarchy(){
        LogFilter filter = LoggerNameFilter.prefix("boraheeb.ui");
        assertTrue(filter.accept(named("boraheeb.ui.dialog")));
        assertTrue(filter.accept(named("boraheeb.ui.MainWindow")));
        assertTrue(filter.accept(named("boraheeb.ui")));
    }

    @Test
    void prefixIsPlainStringPrefixMatchingNotSegmentAware(){
        // LoggerNameFilter.prefix uses String.startsWith directly (unlike LoggingConfig's
        // dot-terminated prefix rules), so a matching prefix also matches a longer,
        // unrelated segment such as "boraheeb.uiteam".
        LogFilter filter = LoggerNameFilter.prefix("boraheeb.ui");
        assertTrue(filter.accept(named("boraheeb.uiteam.SomeClass")));
    }

    @Test
    void prefixDoesNotMatchUnrelatedSibling(){
        LogFilter filter = LoggerNameFilter.prefix("boraheeb.ui");
        assertFalse(filter.accept(named("boraheeb.net.Client")));
    }

    @Test
    void prefixIsCaseSensitive(){
        LogFilter filter = LoggerNameFilter.prefix("boraheeb.ui");
        assertFalse(filter.accept(named("Boraheeb.UI")));
    }

    @Test
    void prefixWithNullArrayRejectsAll(){
        LogFilter filter = LoggerNameFilter.prefix((String[]) null);
        assertFalse(filter.accept(named("anything")));
    }

    @Test
    void prefixWithEmptyArrayRejectsAll(){
        LogFilter filter = LoggerNameFilter.prefix();
        assertFalse(filter.accept(named("anything")));
    }

    @Test
    void prefixSkipsNullAndBlankEntries(){
        LogFilter filter = LoggerNameFilter.prefix(null, "  ", "boraheeb");
        assertTrue(filter.accept(named("boraheeb.ui")));
    }

    @Test
    void excludeRejectsExactMatches(){
        LogFilter filter = LoggerNameFilter.exclude("noisy.lib");
        assertFalse(filter.accept(named("noisy.lib")));
    }

    @Test
    void excludeAcceptsNonMatches(){
        LogFilter filter = LoggerNameFilter.exclude("noisy.lib");
        assertTrue(filter.accept(named("app")));
    }

    @Test
    void excludeWithNullNamesAcceptsAll(){
        LogFilter filter = LoggerNameFilter.exclude((String[]) null);
        assertTrue(filter.accept(named("anything")));
    }

    @Test
    void excludeWithEmptyArrayAcceptsAll(){
        LogFilter filter = LoggerNameFilter.exclude();
        assertTrue(filter.accept(named("anything")));
    }

    @Test
    void excludeWithAllInvalidNamesAcceptsAll(){
        LogFilter filter = LoggerNameFilter.exclude(null, "  ");
        assertTrue(filter.accept(named("anything")));
    }

    @Test
    void filtersComposeWithLevelFilter(){
        LogFilter filter = LoggerNameFilter.include("db").and(LevelFilter.atLeast(LogLevel.WARN));
        assertTrue(filter.accept(LogRecord.builder().loggerName("db").level(LogLevel.ERROR).build()));
        assertFalse(filter.accept(LogRecord.builder().loggerName("db").level(LogLevel.INFO).build()));
        assertFalse(filter.accept(LogRecord.builder().loggerName("other").level(LogLevel.ERROR).build()));
    }
}
