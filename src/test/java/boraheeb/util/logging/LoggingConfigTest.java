package boraheeb.util.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class LoggingConfigTest{

    private LoggingConfig fromString(String contents){
        Properties properties = new Properties();
        try{
            properties.load(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
        }catch(IOException ex){
            throw new RuntimeException(ex);
        }
        return LoggingConfig.load(properties);
    }

    @Test
    void loadWithNullPropertiesReturnsEmptyConfig(){
        LoggingConfig config = LoggingConfig.load((Properties) null);
        assertNull(config.getRootLevel());
        assertTrue(config.getExactRules().isEmpty());
        assertTrue(config.getPrefixRules().isEmpty());
    }

    @Test
    void loadWithNullPathReturnsEmptyConfig(){
        LoggingConfig config = LoggingConfig.load((Path) null);
        assertNull(config.getRootLevel());
    }

    @Test
    void loadWithNullInputStreamReturnsEmptyConfig(){
        LoggingConfig config = LoggingConfig.load((InputStream) null);
        assertNull(config.getRootLevel());
    }

    @Test
    void loadWithNonexistentPathReturnsEmptyConfig(){
        LoggingConfig config = LoggingConfig.load(Path.of("does-not-exist-12345.properties"));
        assertNull(config.getRootLevel());
        assertTrue(config.getExactRules().isEmpty());
    }

    @Test
    void unrelatedKeysAreIgnored(){
        LoggingConfig config = fromString("some.other.key=value\n");
        assertNull(config.getRootLevel());
        assertTrue(config.getExactRules().isEmpty());
        assertTrue(config.getPrefixRules().isEmpty());
    }

    @Test
    void rootRuleIsParsed(){
        LoggingConfig config = fromString("log.level.root=WARN\n");
        assertEquals(LogLevel.WARN, config.getRootLevel());
    }

    @Test
    void exactRuleIsParsed(){
        LoggingConfig config = fromString("log.level.boraheeb.ui.MainWindow=DEBUG\n");
        assertEquals(LogLevel.DEBUG, config.getExactRules().get("boraheeb.ui.MainWindow"));
    }

    @Test
    void prefixRuleStripsTrailingStarButKeepsDot(){
        LoggingConfig config = fromString("log.level.boraheeb.ui.*=DEBUG\n");
        assertEquals(LogLevel.DEBUG, config.getPrefixRules().get("boraheeb.ui."));
    }

    @Test
    void levelParsingIsCaseInsensitive(){
        LoggingConfig config = fromString("log.level.root=warn\n");
        assertEquals(LogLevel.WARN, config.getRootLevel());
    }

    @Test
    void invalidLevelValueIsIgnored(){
        LoggingConfig config = fromString("log.level.root=NOT_A_LEVEL\n");
        assertNull(config.getRootLevel());
    }

    @Test
    void emptyLoggerNameRuleIsIgnored(){
        LoggingConfig config = fromString("log.level.=INFO\n");
        assertTrue(config.getExactRules().isEmpty());
        assertNull(config.getRootLevel());
    }

    @Test
    void levelForResolvesExactRuleFirst(){
        LoggingConfig config = fromString(
            "log.level.root=INFO\n" +
            "log.level.boraheeb.ui.*=DEBUG\n" +
            "log.level.boraheeb.ui.MainWindow=ERROR\n"
        );
        assertEquals(LogLevel.ERROR, config.levelFor("boraheeb.ui.MainWindow"));
    }

    @Test
    void levelForFallsBackToLongestMatchingPrefix(){
        LoggingConfig config = fromString(
            "log.level.boraheeb.*=INFO\n" +
            "log.level.boraheeb.ui.*=DEBUG\n"
        );
        assertEquals(LogLevel.DEBUG, config.levelFor("boraheeb.ui.dialog.Alert"));
    }

    @Test
    void levelForFallsBackToRootWhenNoOtherMatch(){
        LoggingConfig config = fromString("log.level.root=WARN\n");
        assertEquals(LogLevel.WARN, config.levelFor("completely.unrelated.Logger"));
    }

    @Test
    void levelForReturnsNullWhenNoRuleApplies(){
        LoggingConfig config = fromString("log.level.boraheeb.ui.*=DEBUG\n");
        assertNull(config.levelFor("other.Logger"));
    }

    @Test
    void prefixMatchingIsSegmentAware(){
        LoggingConfig config = fromString("log.level.boraheeb.ui.*=DEBUG\n");
        assertNull(config.levelFor("boraheeb.uiteam.SomeClass"));
    }

    @Test
    void prefixDoesNotMatchTheBoundaryNodeItself(){
        LoggingConfig config = fromString("log.level.boraheeb.ui.*=DEBUG\n");
        assertNull(config.levelFor("boraheeb.ui"));
    }

    @Test
    void exactRuleOnBoundaryNodeWorks(){
        LoggingConfig config = fromString("log.level.boraheeb.ui=DEBUG\n");
        assertEquals(LogLevel.DEBUG, config.levelFor("boraheeb.ui"));
    }

    @Test
    void levelForWithNullOrBlankNameReturnsNull(){
        LoggingConfig config = fromString("log.level.root=INFO\n");
        assertNull(config.levelFor(null));
        assertNull(config.levelFor("   "));
    }

    @Test
    void applyToWithNullRegistryIsIgnored(){
        LoggingConfig config = fromString("log.level.root=WARN\n");
        assertDoesNotThrow(() -> config.applyTo(null));
    }

    @Test
    void applyToOnlyAffectsAlreadyRegisteredLoggers(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger existing = registry.getLogger("boraheeb.ui.MainWindow");
        LoggingConfig config = fromString("log.level.boraheeb.ui.*=ERROR\n");

        config.applyTo(registry);

        assertEquals(LogLevel.ERROR, existing.getMinLevel());
    }

    @Test
    void applyToDoesNotCreateNewLoggers(){
        LoggerRegistry registry = new LoggerRegistry();
        LoggingConfig config = fromString("log.level.boraheeb.ui.*=ERROR\n");

        config.applyTo(registry);

        assertEquals(0, registry.size());
    }

    @Test
    void applyToLeavesUnmatchedLoggersUntouched(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger unrelated = registry.getLogger("some.other.Logger");
        unrelated.setMinLevel(LogLevel.DEBUG);
        LoggingConfig config = fromString("log.level.boraheeb.ui.*=ERROR\n");

        config.applyTo(registry);

        assertEquals(LogLevel.DEBUG, unrelated.getMinLevel());
    }

    @Test
    void applyToIsIdempotent(){
        LoggerRegistry registry = new LoggerRegistry();
        Logger logger = registry.getLogger("app");
        LoggingConfig config = fromString("log.level.app=WARN\n");

        config.applyTo(registry);
        config.applyTo(registry);

        assertEquals(LogLevel.WARN, logger.getMinLevel());
    }

    @Test
    void loadFromPathRoundTripsThroughRealFile(@TempDir Path dir) throws IOException{
        Path file = dir.resolve("logging.properties");
        Files.writeString(file, "log.level.root=ERROR\n");

        LoggingConfig config = LoggingConfig.load(file);

        assertEquals(LogLevel.ERROR, config.getRootLevel());
    }

    @Test
    void loadFromInputStreamRoundTrips() throws IOException{
        String contents = "log.level.root=DEBUG\n";
        try(InputStream in = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))){
            LoggingConfig config = LoggingConfig.load(in);
            assertEquals(LogLevel.DEBUG, config.getRootLevel());
        }
    }

    @Test
    void getExactRulesIsUnmodifiable(){
        LoggingConfig config = fromString("log.level.app=INFO\n");
        assertThrows(UnsupportedOperationException.class, () -> config.getExactRules().put("x", LogLevel.WARN));
    }

    @Test
    void getPrefixRulesIsUnmodifiable(){
        LoggingConfig config = fromString("log.level.app.*=INFO\n");
        assertThrows(UnsupportedOperationException.class, () -> config.getPrefixRules().put("x.", LogLevel.WARN));
    }
}
