package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LevelFilterTest{

    private LogRecord at(LogLevel level){
        return LogRecord.builder().level(level).build();
    }

    @Test
    void atLeastAcceptsEqualLevel(){
        assertTrue(LevelFilter.atLeast(LogLevel.WARN).accept(at(LogLevel.WARN)));
    }

    @Test
    void atLeastAcceptsHigherLevel(){
        assertTrue(LevelFilter.atLeast(LogLevel.WARN).accept(at(LogLevel.ERROR)));
    }

    @Test
    void atLeastRejectsLowerLevel(){
        assertFalse(LevelFilter.atLeast(LogLevel.WARN).accept(at(LogLevel.INFO)));
    }

    @Test
    void atLeastWithNullLevelUsesDefaultLevel(){
        LogFilter filter = LevelFilter.atLeast(null);
        assertTrue(filter.accept(at(LogLevel.DEFAULT_LEVEL)));
        assertFalse(filter.accept(at(LogLevel.TRACE)));
    }

    @Test
    void atMostAcceptsEqualLevel(){
        assertTrue(LevelFilter.atMost(LogLevel.WARN).accept(at(LogLevel.WARN)));
    }

    @Test
    void atMostAcceptsLowerLevel(){
        assertTrue(LevelFilter.atMost(LogLevel.WARN).accept(at(LogLevel.INFO)));
    }

    @Test
    void atMostRejectsHigherLevel(){
        assertFalse(LevelFilter.atMost(LogLevel.WARN).accept(at(LogLevel.ERROR)));
    }

    @Test
    void atMostWithNullLevelUsesDefaultLevel(){
        LogFilter filter = LevelFilter.atMost(null);
        assertTrue(filter.accept(at(LogLevel.DEFAULT_LEVEL)));
        assertFalse(filter.accept(at(LogLevel.CRITICAL)));
    }

    @Test
    void exactlyAcceptsOnlyExactLevel(){
        LogFilter filter = LevelFilter.exactly(LogLevel.WARN);
        assertTrue(filter.accept(at(LogLevel.WARN)));
        assertFalse(filter.accept(at(LogLevel.ERROR)));
        assertFalse(filter.accept(at(LogLevel.INFO)));
    }

    @Test
    void exactlyWithNullLevelUsesDefaultLevel(){
        LogFilter filter = LevelFilter.exactly(null);
        assertTrue(filter.accept(at(LogLevel.DEFAULT_LEVEL)));
    }

    @Test
    void betweenAcceptsInclusiveRange(){
        LogFilter filter = LevelFilter.between(LogLevel.DEBUG, LogLevel.WARN);
        assertTrue(filter.accept(at(LogLevel.DEBUG)));
        assertTrue(filter.accept(at(LogLevel.INFO)));
        assertTrue(filter.accept(at(LogLevel.WARN)));
        assertFalse(filter.accept(at(LogLevel.TRACE)));
        assertFalse(filter.accept(at(LogLevel.ERROR)));
    }

    @Test
    void betweenSwapsWhenMinIsMoreSevereThanMax(){
        LogFilter filter = LevelFilter.between(LogLevel.ERROR, LogLevel.DEBUG);
        assertTrue(filter.accept(at(LogLevel.INFO)));
        assertTrue(filter.accept(at(LogLevel.DEBUG)));
        assertTrue(filter.accept(at(LogLevel.ERROR)));
        assertFalse(filter.accept(at(LogLevel.CRITICAL)));
        assertFalse(filter.accept(at(LogLevel.TRACE)));
    }

    @Test
    void betweenWithNullMinUsesDefaultLevel(){
        LogFilter filter = LevelFilter.between(null, LogLevel.CRITICAL);
        assertTrue(filter.accept(at(LogLevel.DEFAULT_LEVEL)));
        assertFalse(filter.accept(at(LogLevel.TRACE)));
    }

    @Test
    void betweenWithNullMaxUsesDefaultLevel(){
        LogFilter filter = LevelFilter.between(LogLevel.TRACE, null);
        assertTrue(filter.accept(at(LogLevel.DEFAULT_LEVEL)));
        assertFalse(filter.accept(at(LogLevel.CRITICAL)));
    }

    @Test
    void betweenSingleLevelRangeAcceptsOnlyThatLevel(){
        LogFilter filter = LevelFilter.between(LogLevel.INFO, LogLevel.INFO);
        assertTrue(filter.accept(at(LogLevel.INFO)));
        assertFalse(filter.accept(at(LogLevel.WARN)));
        assertFalse(filter.accept(at(LogLevel.DEBUG)));
    }

    @Test
    void filtersComposeWithLogFilterAnd(){
        LogFilter filter = LevelFilter.atLeast(LogLevel.WARN).and(LevelFilter.atMost(LogLevel.ERROR));
        assertTrue(filter.accept(at(LogLevel.ERROR)));
        assertFalse(filter.accept(at(LogLevel.CRITICAL)));
        assertFalse(filter.accept(at(LogLevel.INFO)));
    }
}
