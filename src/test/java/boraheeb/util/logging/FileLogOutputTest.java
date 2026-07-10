package boraheeb.util.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileLogOutputTest{

    private LogRecord record(LogLevel level, String message){
        return LogRecord.builder().level(level).loggerName("app").message(message).build();
    }

    @Test
    void builderDefaultsUsePlainFormatterAndAppendMode(){
        FileLogOutput output = FileLogOutput.builder().build();
        assertEquals(TextLogFormatter.PLAIN, output.getFormatter());
        assertEquals(LogLevel.TRACE, output.getMinLevel());
        assertTrue(output.isAutoFlush());
        output.close();
    }

    @Test
    void publishAppendsFormattedLineToFile(@TempDir Path dir) throws IOException{
        Path file = dir.resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(file).formatter(TextLogFormatter.PLAIN).build();

        output.publish(record(LogLevel.INFO, "hello file"));
        output.close();

        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("hello file"));
    }

    @Test
    void parentDirectoriesAreCreatedAutomatically(@TempDir Path dir){
        Path nested = dir.resolve("a").resolve("b").resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(nested).build();

        assertTrue(Files.exists(nested));
        output.close();
    }

    @Test
    void pathStringOverloadWorks(@TempDir Path dir){
        Path file = dir.resolve("string-path.log");
        FileLogOutput output = FileLogOutput.builder().path(file.toString()).build();
        assertEquals(file, output.getPath());
        output.close();
    }

    @Test
    void pathStringNullOrBlankFallsBackToDefault(){
        FileLogOutput output = FileLogOutput.builder().path((String) null).build();
        assertEquals(Path.of("logs", "app.log"), output.getPath());
        output.close();
        deleteQuietly(Path.of("logs", "app.log"));
    }

    @Test
    void pathNullFallsBackToDefault(){
        FileLogOutput output = FileLogOutput.builder().path((Path) null).build();
        assertEquals(Path.of("logs", "app.log"), output.getPath());
        output.close();
        deleteQuietly(Path.of("logs", "app.log"));
    }

    @Test
    void recordsBelowMinLevelAreDropped(@TempDir Path dir) throws IOException{
        Path file = dir.resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(file).minLevel(LogLevel.ERROR).formatter(TextLogFormatter.PLAIN).build();

        output.publish(record(LogLevel.INFO, "filtered"));
        output.close();

        assertEquals(0, Files.readAllLines(file).size());
    }

    @Test
    void filterDropsNonMatchingRecords(@TempDir Path dir) throws IOException{
        Path file = dir.resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(file).filter(LogFilter.REJECT_ALL).formatter(TextLogFormatter.PLAIN).build();

        output.publish(record(LogLevel.INFO, "filtered"));
        output.close();

        assertEquals(0, Files.readAllLines(file).size());
    }

    @Test
    void nullRecordIsIgnored(@TempDir Path dir){
        Path file = dir.resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(file).build();
        assertDoesNotThrow(() -> output.publish(null));
        output.close();
    }

    @Test
    void appendModeKeepsExistingContentAcrossReopen(@TempDir Path dir) throws IOException{
        Path file = dir.resolve("app.log");
        FileLogOutput first = FileLogOutput.builder().path(file).formatter(TextLogFormatter.PLAIN).build();
        first.publish(record(LogLevel.INFO, "first line"));
        first.close();

        FileLogOutput second = FileLogOutput.builder().path(file).append(true).formatter(TextLogFormatter.PLAIN).build();
        second.publish(record(LogLevel.INFO, "second line"));
        second.close();

        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size());
    }

    @Test
    void overwriteModeTruncatesExistingContent(@TempDir Path dir) throws IOException{
        Path file = dir.resolve("app.log");
        FileLogOutput first = FileLogOutput.builder().path(file).formatter(TextLogFormatter.PLAIN).build();
        first.publish(record(LogLevel.INFO, "will be truncated"));
        first.close();

        FileLogOutput second = FileLogOutput.builder().path(file).append(false).formatter(TextLogFormatter.PLAIN).build();
        second.publish(record(LogLevel.INFO, "only line"));
        second.close();

        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("only line"));
    }

    @Test
    void autoFlushFalseStillWritesOnClose(@TempDir Path dir) throws IOException{
        Path file = dir.resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(file).autoFlush(false).formatter(TextLogFormatter.PLAIN).build();

        output.publish(record(LogLevel.INFO, "buffered"));
        output.close();

        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
    }

    @Test
    void setAutoFlushChangesBehaviorAtRuntime(@TempDir Path dir){
        Path file = dir.resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(file).autoFlush(false).build();
        output.setAutoFlush(true);
        assertTrue(output.isAutoFlush());
        output.close();
    }

    @Test
    void setMinLevelWithNullIsIgnored(@TempDir Path dir){
        Path file = dir.resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(file).minLevel(LogLevel.WARN).build();
        output.setMinLevel(null);
        assertEquals(LogLevel.WARN, output.getMinLevel());
        output.close();
    }

    @Test
    void closeIsIdempotent(@TempDir Path dir){
        Path file = dir.resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(file).build();
        output.close();
        assertDoesNotThrow(output::close);
        assertFalse(output.isOpen());
    }

    @Test
    void publishAfterCloseIsIgnored(@TempDir Path dir) throws IOException{
        Path file = dir.resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(file).formatter(TextLogFormatter.PLAIN).build();
        output.close();

        output.publish(record(LogLevel.INFO, "after close"));

        assertEquals(0, Files.readAllLines(file).size());
    }

    @Test
    void isOpenReturnsFalseWhenFileCannotBeOpened(@TempDir Path dir) throws IOException{
        // A directory path cannot be opened as a writable file.
        Path directoryAsFile = dir.resolve("iAmADirectory");
        Files.createDirectory(directoryAsFile);
        FileLogOutput output = FileLogOutput.builder().path(directoryAsFile).build();

        assertFalse(output.isOpen());
        assertDoesNotThrow(() -> output.publish(record(LogLevel.INFO, "should be dropped")));
        assertDoesNotThrow(output::flush);
        assertDoesNotThrow(output::close);
    }

    @Test
    void concurrentPublishesFromMultipleThreadsWriteAllLines(@TempDir Path dir) throws Exception{
        Path file = dir.resolve("app.log");
        FileLogOutput output = FileLogOutput.builder().path(file).formatter(TextLogFormatter.PLAIN).build();
        int threadCount = 6;
        int perThread = 100;
        Thread[] threads = new Thread[threadCount];
        for(int t = 0; t < threadCount; t++){
            threads[t] = new Thread(() -> {
                for(int i = 0; i < perThread; i++)
                    output.publish(record(LogLevel.INFO, "line"));
            });
            threads[t].start();
        }
        for(Thread thread : threads) thread.join();
        output.close();

        assertEquals(threadCount * perThread, Files.readAllLines(file).size());
    }

    private static void deleteQuietly(Path path){
        try{
            Files.deleteIfExists(path);
            if(path.getParent() != null) Files.deleteIfExists(path.getParent());
        }catch(IOException ignored){}
    }
}
