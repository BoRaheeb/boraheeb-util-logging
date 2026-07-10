package boraheeb.util.logging.bench;
// -- Libraries: --------------------------------------------------------
import boraheeb.util.logging.JsonLogFormatter;
import boraheeb.util.logging.LogFormatter;
import boraheeb.util.logging.LogLevel;
import boraheeb.util.logging.LogRecord;
import boraheeb.util.logging.TextLogFormatter;
// -- FormatterBenchmark Class: ---------------------------------------------
/**
* Standalone benchmark measuring {@link TextLogFormatter} (PLAIN and DEFAULT presets)
* and {@link JsonLogFormatter#DEFAULT} {@code format(LogRecord)} throughput across
* three record shapes: a plain message, a record with several structured fields,
* and a record with an attached exception (stack trace formatting cost).
*
* <p>Run directly: {@code java -cp target/classes;target/test-classes boraheeb.util.logging.bench.FormatterBenchmark}</p>
**/
public final class FormatterBenchmark{
    // -- Constants: --------------------------------------------------------
    private static final int WARMUP_ITERATIONS = 200_000;
    private static final int MAIN_ITERATIONS = 1_500_000;
    // -- Constructors: -----------------------------------------------------
    private FormatterBenchmark(){}
    // -- Entry Point: --------------------------------------------------------
    public static void main(String[] args){
        BenchSupport.printHeader("FormatterBenchmark");

        LogRecord plainRecord = LogRecord.builder()
            .level(LogLevel.INFO)
            .loggerName("boraheeb.app.UserService")
            .sourceName("UserService.java")
            .lineNumber(204)
            .message("User loaded successfully")
            .build();

        LogRecord structuredRecord = LogRecord.builder()
            .level(LogLevel.WARN)
            .loggerName("boraheeb.app.PaymentService")
            .sourceName("PaymentService.java")
            .lineNumber(88)
            .message("Payment retried after transient failure")
            .field("requestId", "REQ-8821")
            .field("userId", "9021")
            .field("amountCents", 4599)
            .field("gateway", "stripe")
            .field("attempt", 2)
            .build();

        LogRecord exceptionRecord = LogRecord.builder()
            .level(LogLevel.ERROR)
            .loggerName("boraheeb.app.PaymentService")
            .sourceName("PaymentService.java")
            .lineNumber(142)
            .message("Payment failed permanently")
            .field("requestId", "REQ-8822")
            .throwable(buildSampleException())
            .build();

        LogFormatter[] formatters = {
            TextLogFormatter.PLAIN,
            TextLogFormatter.DEFAULT,
            JsonLogFormatter.DEFAULT
        };
        String[] formatterNames = {
            "TextLogFormatter.PLAIN",
            "TextLogFormatter.DEFAULT",
            "JsonLogFormatter.DEFAULT"
        };

        String[] scenarioNames = {"plain message", "structured fields", "attached exception"};
        LogRecord[] scenarioRecords = {plainRecord, structuredRecord, exceptionRecord};

        for(int f = 0; f < formatters.length; f++){
            BenchSupport.printSection(formatterNames[f]);
            for(int s = 0; s < scenarioRecords.length; s++)
                runFormatScenario(formatterNames[f] + " / " + scenarioNames[s], formatters[f], scenarioRecords[s]);
        }

        BenchSupport.printSection("Done");
        System.out.println();
    }
    // -- Helpers: --------------------------------------------------------
    private static void runFormatScenario(String label, LogFormatter formatter, LogRecord record){
        long lengthSink = 0;
        for(int i = 0; i < WARMUP_ITERATIONS; i++)
            lengthSink += formatter.format(record).length();

        long start = System.nanoTime();
        for(int i = 0; i < MAIN_ITERATIONS; i++)
            lengthSink += formatter.format(record).length();
        long elapsed = System.nanoTime() - start;

        BenchSupport.metricThroughput(label, MAIN_ITERATIONS, elapsed);
        if(lengthSink < 0) BenchSupport.metric(label + " sink", lengthSink); // prevents dead-code elimination
    }

    private static RuntimeException buildSampleException(){
        try{
            try{
                throw new IllegalStateException("gateway connection reset");
            }catch(IllegalStateException cause){
                throw new RuntimeException("payment processing failed", cause);
            }
        }catch(RuntimeException ex){
            return ex;
        }
    }
}
