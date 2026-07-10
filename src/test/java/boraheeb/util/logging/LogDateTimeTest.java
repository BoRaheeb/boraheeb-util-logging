package boraheeb.util.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LogDateTimeTest{

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-13T20:45:12.123456789Z");

    @Test
    void ofPresetUsesDefaultLocaleAndSystemZone(){
        LogDateTime dateTime = LogDateTime.ofPreset(LogDateTimePreset.LOG_READABLE);
        assertEquals(Locale.ENGLISH, dateTime.getLocale());
        assertEquals(ZoneId.systemDefault(), dateTime.getZone());
        assertEquals("yyyy-MM-dd HH:mm:ss", dateTime.getPattern());
    }

    @Test
    void ofPresetNullFallsBackToDefaultPreset(){
        LogDateTime dateTime = LogDateTime.ofPreset(null);
        assertEquals(LogDateTimePreset.DEFAULT_PRESET.getPattern(), dateTime.getPattern());
    }

    @Test
    void ofPresetWithZoneAppliesGivenZone(){
        LogDateTime dateTime = LogDateTime.ofPreset(LogDateTimePreset.SYSTEM_ISO_UTC, Locale.ENGLISH, ZoneOffset.UTC);
        assertEquals(ZoneOffset.UTC, dateTime.getZone());
        assertEquals("2026-01-13T20:45:12.123Z", dateTime.format(FIXED_INSTANT));
    }

    @Test
    void ofPatternFormatsInstantCorrectlyInUtc(){
        LogDateTime dateTime = LogDateTime.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH, ZoneOffset.UTC);
        assertEquals("2026-01-13 20:45:12", dateTime.format(FIXED_INSTANT));
    }

    @Test
    void ofPatternNullFallsBackToDefaultPreset(){
        LogDateTime dateTime = LogDateTime.ofPattern(null, Locale.ENGLISH, ZoneOffset.UTC);
        assertEquals(LogDateTimePreset.DEFAULT_PRESET.getPattern(), dateTime.getPattern());
    }

    @Test
    void ofPatternBlankFallsBackToDefaultPreset(){
        LogDateTime dateTime = LogDateTime.ofPattern("   ", Locale.ENGLISH, ZoneOffset.UTC);
        assertEquals(LogDateTimePreset.DEFAULT_PRESET.getPattern(), dateTime.getPattern());
    }

    @Test
    void ofPatternTrimsPattern(){
        LogDateTime dateTime = LogDateTime.ofPattern("  yyyy-MM-dd  ", Locale.ENGLISH, ZoneOffset.UTC);
        assertEquals("yyyy-MM-dd", dateTime.getPattern());
    }

    @Test
    void ofPatternInvalidPatternFallsBackToDefaultPreset(){
        LogDateTime dateTime = LogDateTime.ofPattern("not[[a valid pattern", Locale.ENGLISH, ZoneOffset.UTC);
        assertEquals(LogDateTimePreset.DEFAULT_PRESET.getPattern(), dateTime.getPattern());
    }

    @Test
    void ofPatternNullLocaleFallsBackToDefaultLocale(){
        LogDateTime dateTime = LogDateTime.ofPattern("yyyy-MM-dd", null, ZoneOffset.UTC);
        assertEquals(Locale.ENGLISH, dateTime.getLocale());
    }

    @Test
    void ofPatternNullZoneFallsBackToSystemDefaultZone(){
        LogDateTime dateTime = LogDateTime.ofPattern("yyyy-MM-dd", Locale.ENGLISH, null);
        assertEquals(ZoneId.systemDefault(), dateTime.getZone());
    }

    @Test
    void formatWithNullTemporalReturnsEmptyString(){
        LogDateTime dateTime = LogDateTime.ofPattern("yyyy-MM-dd");
        assertEquals("", dateTime.format(null));
    }

    @Test
    void formatWithIncompatibleTemporalReturnsEmptyString(){
        LogDateTime dateTime = LogDateTime.ofPattern("HH:mm:ss");
        assertEquals("", dateTime.format(LocalDate.of(2026, 1, 13)));
    }

    @Test
    void isCompatibleWithReturnsTrueForCompatibleTemporal(){
        LogDateTime dateTime = LogDateTime.ofPattern("yyyy-MM-dd");
        assertTrue(dateTime.isCompatibleWith(FIXED_INSTANT));
    }

    @Test
    void isCompatibleWithReturnsFalseForIncompatibleTemporal(){
        LogDateTime dateTime = LogDateTime.ofPattern("HH:mm:ss");
        assertFalse(dateTime.isCompatibleWith(LocalDate.of(2026, 1, 13)));
    }

    @Test
    void isCompatibleWithReturnsFalseForNullTemporal(){
        LogDateTime dateTime = LogDateTime.ofPattern("yyyy-MM-dd");
        assertFalse(dateTime.isCompatibleWith(null));
    }

    @Test
    void getFormatterHasZoneAlreadyApplied(){
        LogDateTime dateTime = LogDateTime.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH, ZoneOffset.UTC);
        assertEquals("2026-01-13 20:45:12", dateTime.getFormatter().format(FIXED_INSTANT));
    }

    @Test
    void toStringContainsPatternLocaleAndZone(){
        LogDateTime dateTime = LogDateTime.ofPattern("yyyy-MM-dd", Locale.ENGLISH, ZoneOffset.UTC);
        String result = dateTime.toString();
        assertTrue(result.contains("yyyy-MM-dd"));
        assertTrue(result.contains("UTC") || result.contains(ZoneOffset.UTC.toString()));
    }

    @Test
    void literalTextInPatternIsPreservedVerbatim(){
        LogDateTime dateTime = LogDateTime.ofPattern("'Year:' yyyy", Locale.ENGLISH, ZoneOffset.UTC);
        assertEquals("Year: 2026", dateTime.format(FIXED_INSTANT));
    }

    @Test
    void millisSymbolTruncatesNanoPrecision(){
        LogDateTime dateTime = LogDateTime.ofPattern("SSS", Locale.ENGLISH, ZoneOffset.UTC);
        assertEquals("123", dateTime.format(FIXED_INSTANT));
    }

    @Test
    void printPatternSymbolsFindsTheBundledResourceOnTheClasspath(){
        assertTrue(LogDateTime.printPatternSymbols());
    }

    @Test
    void downloadPatternSymbolsWithNullTargetReturnsFalse(){
        assertFalse(LogDateTime.downloadPatternSymbols((Path) null));
    }

    @Test
    void downloadPatternSymbolsToPathCopiesTheBundledResource(@TempDir Path tempDir) throws IOException{
        Path target = tempDir.resolve("symbols.txt");
        assertTrue(LogDateTime.downloadPatternSymbols(target));
        assertTrue(Files.exists(target));
        assertTrue(Files.size(target) > 0);
    }

    @Test
    void downloadPatternSymbolsOverwritesAnExistingFile(@TempDir Path tempDir) throws IOException{
        Path target = tempDir.resolve("symbols.txt");
        Files.writeString(target, "stale content");
        assertTrue(LogDateTime.downloadPatternSymbols(target));
        assertNotEquals("stale content", Files.readString(target));
    }
}
