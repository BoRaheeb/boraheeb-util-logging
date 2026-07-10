package boraheeb.util.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogFilterTest{

    private LogRecord record(){
        return LogRecord.builder().message("m").build();
    }

    @Test
    void acceptAllAcceptsEveryRecord(){
        assertTrue(LogFilter.ACCEPT_ALL.accept(record()));
    }

    @Test
    void rejectAllRejectsEveryRecord(){
        assertFalse(LogFilter.REJECT_ALL.accept(record()));
    }

    @Test
    void andCombinesBothTrueToTrue(){
        LogFilter combined = LogFilter.ACCEPT_ALL.and(LogFilter.ACCEPT_ALL);
        assertTrue(combined.accept(record()));
    }

    @Test
    void andCombinesOneFalseToFalse(){
        LogFilter combined = LogFilter.ACCEPT_ALL.and(LogFilter.REJECT_ALL);
        assertFalse(combined.accept(record()));
    }

    @Test
    void andShortCircuitsAndDoesNotEvaluateOtherIfThisIsFalse(){
        boolean[] otherCalled = {false};
        LogFilter other = r -> {
            otherCalled[0] = true;
            return true;
        };
        LogFilter combined = LogFilter.REJECT_ALL.and(other);
        assertFalse(combined.accept(record()));
        assertFalse(otherCalled[0]);
    }

    @Test
    void andWithNullOtherReturnsThisFilterUnchanged(){
        LogFilter combined = LogFilter.REJECT_ALL.and(null);
        assertFalse(combined.accept(record()));
        LogFilter combinedAccept = LogFilter.ACCEPT_ALL.and(null);
        assertTrue(combinedAccept.accept(record()));
    }

    @Test
    void orCombinesEitherTrueToTrue(){
        LogFilter combined = LogFilter.REJECT_ALL.or(LogFilter.ACCEPT_ALL);
        assertTrue(combined.accept(record()));
    }

    @Test
    void orCombinesBothFalseToFalse(){
        LogFilter combined = LogFilter.REJECT_ALL.or(LogFilter.REJECT_ALL);
        assertFalse(combined.accept(record()));
    }

    @Test
    void orWithNullOtherReturnsThisFilterUnchanged(){
        LogFilter combined = LogFilter.ACCEPT_ALL.or(null);
        assertTrue(combined.accept(record()));
    }

    @Test
    void negateInvertsAcceptAll(){
        assertFalse(LogFilter.ACCEPT_ALL.negate().accept(record()));
    }

    @Test
    void negateInvertsRejectAll(){
        assertTrue(LogFilter.REJECT_ALL.negate().accept(record()));
    }

    @Test
    void doubleNegateIsIdentity(){
        assertTrue(LogFilter.ACCEPT_ALL.negate().negate().accept(record()));
    }

    @Test
    void customLambdaFilterWorks(){
        LogFilter onlyLongMessages = r -> r.getMessage().length() > 3;
        assertTrue(onlyLongMessages.accept(LogRecord.builder().message("hello").build()));
        assertFalse(onlyLongMessages.accept(LogRecord.builder().message("hi").build()));
    }
}
