package boraheeb.util.logging;
// -- Libraries: ----------------------------------------------------------
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
// -- RollingFileLogOutputTest Class: ---------------------------------------------
class RollingFileLogOutputTest{
    // -- Helper Methods: -------------------------------------------------------------
    private static LogRecord record(String message){
        return LogRecord.builder().level(LogLevel.INFO).message(message).build();
    }
    private static List<String> lines(Path path) throws IOException{
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }
    private static List<Path> siblingsMatching(Path dir, String stem, String extension) throws IOException{
        List<Path> found = new ArrayList<>();
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(dir, stem + "-*" + extension)){
            for(Path p : stream) found.add(p);
        }
        return found;
    }
    // -- Builder Default / Validation Tests: -------------------------------------------------------------
    @Test
    void builderDefaultsMatchDocumentedValues(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).build()){
            assertSame(TextLogFormatter.PLAIN, output.getFormatter());
            assertEquals(path, output.getPath());
            assertEquals(LogLevel.TRACE, output.getMinLevel());
            assertNull(output.getFilter());
            assertTrue(output.isAutoFlush());
            assertEquals(0, output.getMaxSizeBytes());
            assertEquals(RollingFileLogOutput.RollingPeriod.DAILY, output.getRollingPeriod());
            assertEquals(7, output.getMaxFiles());
            assertTrue(output.isOpen());
        }
    }
    @Test
    void pathNullDoesNotThrowWhenOverriddenAfterward(@TempDir Path tempDir){
        Path realPath = tempDir.resolve("real.log");
        assertDoesNotThrow(() -> {
            try(RollingFileLogOutput output = RollingFileLogOutput.builder().path((Path) null).path(realPath).build()){
                assertEquals(realPath, output.getPath());
            }
        });
    }
    @Test
    void pathStringBlankDoesNotThrowWhenOverriddenAfterward(@TempDir Path tempDir){
        Path realPath = tempDir.resolve("real2.log");
        assertDoesNotThrow(() -> {
            try(RollingFileLogOutput output = RollingFileLogOutput.builder().path("   ").path(realPath).build()){
                assertEquals(realPath, output.getPath());
            }
        });
    }
    @Test
    void pathStringTrimsWhitespace(@TempDir Path tempDir){
        Path realPath = tempDir.resolve("trim.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path("  " + realPath + "  ").build()){
            assertEquals(realPath, output.getPath());
        }
    }
    @Test
    void formatterNullFallsBackToPlain(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).formatter(null).build()){
            assertSame(TextLogFormatter.PLAIN, output.getFormatter());
        }
    }
    @Test
    void minLevelNullFallsBackToDefault(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).minLevel(LogLevel.ERROR).minLevel(null).build()){
            assertEquals(LogLevel.TRACE, output.getMinLevel());
        }
    }
    @Test
    void maxSizeBytesNegativeFallsBackToDisabled(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).maxSizeBytes(-5).build()){
            assertEquals(0, output.getMaxSizeBytes());
        }
    }
    @Test
    void maxFilesNegativeFallsBackToDisabled(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).maxFiles(-1).build()){
            assertEquals(0, output.getMaxFiles());
        }
    }
    @Test
    void rollingPeriodNullDisablesTimeRolling(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).rollingPeriod(null).build()){
            assertNull(output.getRollingPeriod());
        }
    }
    // -- Publish / Content Round-Trip Tests: -------------------------------------------------------------
    @Test
    void publishWritesFormattedMessageToFile(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).build()){
            output.publish(record("hello world"));
        }
        List<String> written = lines(path);
        assertEquals(1, written.size());
        assertTrue(written.get(0).contains("hello world"));
    }
    @Test
    void publishBelowMinLevelIsDropped(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).minLevel(LogLevel.WARN).build()){
            output.publish(LogRecord.builder().level(LogLevel.DEBUG).message("too low").build());
            output.publish(LogRecord.builder().level(LogLevel.ERROR).message("high enough").build());
        }
        List<String> written = lines(path);
        assertEquals(1, written.size());
        assertTrue(written.get(0).contains("high enough"));
    }
    @Test
    void publishRejectedByFilterIsDropped(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        LogFilter onlyA = r -> r.getMessage().startsWith("a");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).filter(onlyA).build()){
            output.publish(record("bXXX"));
            output.publish(record("aXXX"));
        }
        List<String> written = lines(path);
        assertEquals(1, written.size());
        assertTrue(written.get(0).contains("aXXX"));
    }
    @Test
    void publishNullRecordDoesNotThrow(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).build()){
            assertDoesNotThrow(() -> output.publish(null));
        }
    }
    @Test
    void publishAfterCloseIsIgnored(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).build();
        output.publish(record("before-close"));
        output.close();
        output.publish(record("after-close"));
        List<String> written = lines(path);
        assertEquals(1, written.size());
        assertFalse(output.isOpen());
    }
    @Test
    void closeIsIdempotent(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).build();
        output.close();
        assertDoesNotThrow(output::close);
        assertFalse(output.isOpen());
    }
    @Test
    void autoFlushFalseRequiresExplicitFlushForVisibleContent(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).autoFlush(false).build()){
            output.publish(record("buffered"));
            output.flush();
        }
        List<String> written = lines(path);
        assertEquals(1, written.size());
        assertTrue(written.get(0).contains("buffered"));
    }
    @Test
    void setAutoFlushTogglesRuntimeBehavior(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).autoFlush(true).build()){
            assertTrue(output.isAutoFlush());
            output.setAutoFlush(false);
            assertFalse(output.isAutoFlush());
        }
    }
    // -- Append Mode Tests: -------------------------------------------------------------
    @Test
    void appendModeKeepsContentAcrossReopen(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput first = RollingFileLogOutput.builder().path(path).append(true).build()){
            first.publish(record("first-line"));
        }
        try(RollingFileLogOutput second = RollingFileLogOutput.builder().path(path).append(true).build()){
            second.publish(record("second-line"));
        }
        List<String> written = lines(path);
        assertEquals(2, written.size());
        assertTrue(written.get(0).contains("first-line"));
        assertTrue(written.get(1).contains("second-line"));
    }
    @Test
    void appendFalseOverwritesExistingContent(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput first = RollingFileLogOutput.builder().path(path).append(false).build()){
            first.publish(record("old-content"));
        }
        try(RollingFileLogOutput second = RollingFileLogOutput.builder().path(path).append(false).build()){
            second.publish(record("new-content"));
        }
        List<String> written = lines(path);
        assertEquals(1, written.size());
        assertTrue(written.get(0).contains("new-content"));
    }
    // -- Size-Based Rolling Tests: -------------------------------------------------------------
    @Test
    void sizeBasedRollCreatesRolledFileAndContinuesWriting(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).maxSizeBytes(40).rollingPeriod(null).build()){
            for(int i = 0; i < 10; i++) output.publish(record("line-" + i));
        }
        List<Path> rolled = siblingsMatching(tempDir, "app", ".log");
        assertFalse(rolled.isEmpty(), "expected at least one rolled file");
        assertTrue(Files.exists(path), "active file should still exist at the base path");
    }
    @Test
    void contentRoundTripsAcrossRollBoundary(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        int total = 30;
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).maxSizeBytes(60).rollingPeriod(null).maxFiles(0).build()){
            for(int i = 0; i < total; i++) output.publish(record("entry-" + i));
        }
        List<String> allLines = new ArrayList<>();
        for(Path rolledPath : siblingsMatching(tempDir, "app", ".log")) allLines.addAll(lines(rolledPath));
        if(Files.exists(path)) allLines.addAll(lines(path));
        assertEquals(total, allLines.size());
        for(int i = 0; i < total; i++){
            final int idx = i;
            assertTrue(allLines.stream().anyMatch(l -> l.contains("entry-" + idx)), "missing entry-" + idx);
        }
    }
    @Test
    void maxSizeBytesZeroDisablesSizeRolling(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).maxSizeBytes(0).rollingPeriod(null).build()){
            for(int i = 0; i < 50; i++) output.publish(record("line-" + i));
        }
        List<Path> rolled = siblingsMatching(tempDir, "app", ".log");
        assertTrue(rolled.isEmpty(), "size rolling disabled -> no rolled files expected");
        assertEquals(50, lines(path).size());
    }
    @Test
    void setMaxSizeBytesNegativeIsIgnored(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).maxSizeBytes(100).build()){
            output.setMaxSizeBytes(-10);
            assertEquals(100, output.getMaxSizeBytes());
        }
    }
    // -- Max Files Pruning Tests: -------------------------------------------------------------
    @Test
    void maxFilesPrunesOldestRolledFiles(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).maxSizeBytes(20).maxFiles(2).rollingPeriod(null).build()){
            for(int i = 0; i < 40; i++) output.publish(record("line-" + i));
        }
        List<Path> rolled = siblingsMatching(tempDir, "app", ".log");
        assertTrue(rolled.size() <= 2, "expected at most 2 rolled files, found " + rolled.size());
    }
    @Test
    void maxFilesZeroDisablesPruning(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).maxSizeBytes(20).maxFiles(0).rollingPeriod(null).build()){
            for(int i = 0; i < 40; i++) output.publish(record("line-" + i));
        }
        List<Path> rolled = siblingsMatching(tempDir, "app", ".log");
        assertTrue(rolled.size() >= 3, "expected multiple rolled files retained when pruning disabled");
    }
    @Test
    void setMaxFilesNegativeIsIgnored(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).maxFiles(3).build()){
            output.setMaxFiles(-1);
            assertEquals(3, output.getMaxFiles());
        }
    }
    // -- Runtime Mutator Tests: -------------------------------------------------------------
    @Test
    void setMinLevelNullIsIgnored(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).minLevel(LogLevel.WARN).build()){
            output.setMinLevel(null);
            assertEquals(LogLevel.WARN, output.getMinLevel());
        }
    }
    @Test
    void setFilterNullAcceptsAllRecords(@TempDir Path tempDir) throws IOException{
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).filter(LogFilter.REJECT_ALL).build()){
            output.setFilter(null);
            assertNull(output.getFilter());
            output.publish(record("through"));
        }
        assertEquals(1, lines(path).size());
    }
    @Test
    void setRollingPeriodRecomputesGetter(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        try(RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).rollingPeriod(RollingFileLogOutput.RollingPeriod.DAILY).build()){
            output.setRollingPeriod(RollingFileLogOutput.RollingPeriod.HOURLY);
            assertEquals(RollingFileLogOutput.RollingPeriod.HOURLY, output.getRollingPeriod());
            output.setRollingPeriod(null);
            assertNull(output.getRollingPeriod());
        }
    }
    // -- Failure Path Tests: -------------------------------------------------------------
    @Test
    void openFailureAtConstructionLeavesOutputClosedButUsable(@TempDir Path tempDir) throws IOException{
        Path blockerFile = tempDir.resolve("blocker");
        Files.writeString(blockerFile, "not a directory");
        Path path = blockerFile.resolve("app.log");
        RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).build();
        try{
            assertFalse(output.isOpen());
            assertDoesNotThrow(() -> output.publish(record("dropped")));
            assertDoesNotThrow(output::flush);
        }finally{
            output.close();
        }
    }
    @Test
    void isOpenReflectsClosedState(@TempDir Path tempDir){
        Path path = tempDir.resolve("app.log");
        RollingFileLogOutput output = RollingFileLogOutput.builder().path(path).build();
        assertTrue(output.isOpen());
        output.close();
        assertFalse(output.isOpen());
    }
}
