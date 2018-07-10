package com.timgroup.statsd;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.ServerSocket;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class NonBlockingStatsDClientTest {

    private static int statsdServerPort;
    private static DummyStatsDServer server;
    private static NonBlockingStatsDClient client;

    @BeforeClass
    public static void start() throws Exception {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();

        statsdServerPort = port;
        server = new DummyStatsDServer(port);
        client = new NonBlockingStatsDClient("my.prefix", "localhost", port);
    }

    @AfterClass
    public static void stop() {
        client.close();
        server.close();
    }

    @After
    public void clear() {
        server.clear();
    }

    @Test(timeout=5000L) public void
    sends_counter_value_to_statsd() throws Exception {
        client.count("mycount", 24);

        assertEquals("my.prefix.mycount:24|c", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_counter_value_with_sample_rate_to_statsd() throws Exception {
    	client.count("mycount", 24, 1);

        assertEquals("my.prefix.mycount:24|c|@1.000000", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_counter_value_to_statsd_with_null_tags() throws Exception {
        client.count("mycount", 24, (java.lang.String[]) null);

        assertEquals("my.prefix.mycount:24|c", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_counter_value_to_statsd_with_empty_tags() throws Exception {
        client.count("mycount", 24);

        assertEquals("my.prefix.mycount:24|c", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_counter_value_to_statsd_with_tags() throws Exception {
        client.count("mycount", 24, "foo:bar", "baz");

        assertEquals("my.prefix.mycount:24|c|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_counter_value_with_sample_rate_to_statsd_with_tags() throws Exception {
        client.count("mycount", 24, 1, "foo:bar", "baz");

        assertEquals("my.prefix.mycount:24|c|@1.000000|#foo:bar,baz", server.nextMessage());
    }


    @Test(timeout=5000L) public void
    sends_counter_increment_to_statsd() throws Exception {
        client.incrementCounter("myinc");

        assertEquals("my.prefix.myinc:1|c", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_counter_increment_to_statsd_with_tags() throws Exception {
        client.incrementCounter("myinc", "foo:bar", "baz");

        assertEquals("my.prefix.myinc:1|c|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_counter_increment_with_sample_rate_to_statsd_with_tags() throws Exception {
        client.incrementCounter("myinc", 1, "foo:bar", "baz");

        assertEquals("my.prefix.myinc:1|c|@1.000000|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_counter_decrement_to_statsd() throws Exception {
        client.decrementCounter("mydec");

        assertEquals("my.prefix.mydec:-1|c", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_counter_decrement_to_statsd_with_tags() throws Exception {
        client.decrementCounter("mydec", "foo:bar", "baz");

        assertEquals("my.prefix.mydec:-1|c|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_counter_decrement_with_sample_rate_to_statsd_with_tags() throws Exception {
        client.decrementCounter("mydec", 1, "foo:bar", "baz");

        assertEquals("my.prefix.mydec:-1|c|@1.000000|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_gauge_to_statsd() throws Exception {
        client.recordGaugeValue("mygauge", 423);

        assertEquals("my.prefix.mygauge:423|g", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_gauge_with_sample_rate_to_statsd() throws Exception {
        client.recordGaugeValue("mygauge", 423, 1);

        assertEquals("my.prefix.mygauge:423|g|@1.000000", server.nextMessage());
    }

    @SuppressWarnings("FloatingPointLiteralPrecision")
    @Test(timeout=5000L) public void
    sends_large_double_gauge_to_statsd() throws Exception {
        client.recordGaugeValue("mygauge", 123456789012345.67890);

        assertEquals("my.prefix.mygauge:123456789012345.67|g", server.nextMessage());
    }

    @SuppressWarnings("FloatingPointLiteralPrecision")
    @Test(timeout=5000L) public void
    sends_exact_double_gauge_to_statsd() throws Exception {
        client.recordGaugeValue("mygauge", 123.45678901234567890);

        assertEquals("my.prefix.mygauge:123.456789|g", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_double_gauge_to_statsd() throws Exception {


        client.recordGaugeValue("mygauge", 0.423);

        assertEquals("my.prefix.mygauge:0.423|g", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_gauge_to_statsd_with_tags() throws Exception {


        client.recordGaugeValue("mygauge", 423, "foo:bar", "baz");

        assertEquals("my.prefix.mygauge:423|g|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_gauge_with_sample_rate_to_statsd_with_tags() throws Exception {


        client.recordGaugeValue("mygauge", 423, 1, "foo:bar", "baz");

        assertEquals("my.prefix.mygauge:423|g|@1.000000|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_double_gauge_to_statsd_with_tags() throws Exception {


        client.recordGaugeValue("mygauge", 0.423, "foo:bar", "baz");

        assertEquals("my.prefix.mygauge:0.423|g|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_histogram_to_statsd() throws Exception {

        client.recordHistogramValue("myhistogram", 423);

        assertEquals("my.prefix.myhistogram:423|h", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_double_histogram_to_statsd() throws Exception {


        client.recordHistogramValue("myhistogram", 0.423);

        assertEquals("my.prefix.myhistogram:0.423|h", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_histogram_to_statsd_with_tags() throws Exception {


        client.recordHistogramValue("myhistogram", 423, "foo:bar", "baz");

        assertEquals("my.prefix.myhistogram:423|h|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_histogram_with_sample_rate_to_statsd_with_tags() throws Exception {


        client.recordHistogramValue("myhistogram", 423, 1, "foo:bar", "baz");

        assertEquals("my.prefix.myhistogram:423|h|@1.000000|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_double_histogram_to_statsd_with_tags() throws Exception {


        client.recordHistogramValue("myhistogram", 0.423, "foo:bar", "baz");

        assertEquals("my.prefix.myhistogram:0.423|h|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_double_histogram_with_sample_rate_to_statsd_with_tags() throws Exception {


        client.recordHistogramValue("myhistogram", 0.423, 1, "foo:bar", "baz");

        assertEquals("my.prefix.myhistogram:0.423|h|@1.000000|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_timer_to_statsd() throws Exception {


        client.recordExecutionTime("mytime", 123);

        assertEquals("my.prefix.mytime:123|ms", server.nextMessage());
    }

    /**
     * A regression test for <a href="https://github.com/indeedeng/java-dogstatsd-client/issues/3">this i18n number formatting bug</a>
     */
    @Test public void
    sends_timer_to_statsd_from_locale_with_unamerican_number_formatting() throws Exception {

        Locale originalDefaultLocale = Locale.getDefault();

        // change the default Locale to one that uses something other than a '.' as the decimal separator (Germany uses a comma)
        Locale.setDefault(Locale.GERMANY);

        try {
            client.recordExecutionTime("mytime", 123, "foo:bar", "baz");

            assertEquals("my.prefix.mytime:123|ms|#foo:bar,baz", server.nextMessage());
        } finally {
            // reset the default Locale in case changing it has side-effects
            Locale.setDefault(originalDefaultLocale);
        }
    }


    @Test(timeout=5000L) public void
    sends_timer_to_statsd_with_tags() throws Exception {


        client.recordExecutionTime("mytime", 123, "foo:bar", "baz");

        assertEquals("my.prefix.mytime:123|ms|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_timer_with_sample_rate_to_statsd_with_tags() throws Exception {


        client.recordExecutionTime("mytime", 123, 1, "foo:bar", "baz");

        assertEquals("my.prefix.mytime:123|ms|@1.000000|#foo:bar,baz", server.nextMessage());
    }


    @Test(timeout=5000L) public void
    sends_gauge_mixed_tags() throws Exception {

        final NonBlockingStatsDClient empty_prefix_client = new NonBlockingStatsDClient("my.prefix", "localhost", statsdServerPort, Integer.MAX_VALUE, "instance:foo", "app:bar");
        empty_prefix_client.gauge("value", 423, "baz");

        assertEquals("my.prefix.value:423|g|#instance:foo,app:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_gauge_mixed_tags_with_sample_rate() throws Exception {

        final NonBlockingStatsDClient empty_prefix_client = new NonBlockingStatsDClient("my.prefix", "localhost", statsdServerPort, Integer.MAX_VALUE, "instance:foo", "app:bar");
        empty_prefix_client.gauge("value", 423,1, "baz");

        assertEquals("my.prefix.value:423|g|@1.000000|#instance:foo,app:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_gauge_constant_tags_only() throws Exception {

        final NonBlockingStatsDClient empty_prefix_client = new NonBlockingStatsDClient("my.prefix", "localhost", statsdServerPort, Integer.MAX_VALUE, "instance:foo", "app:bar");
        empty_prefix_client.gauge("value", 423);

        assertEquals("my.prefix.value:423|g|#instance:foo,app:bar", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_gauge_empty_prefix() throws Exception {

        final NonBlockingStatsDClient empty_prefix_client = new NonBlockingStatsDClient("", "localhost", statsdServerPort);
        empty_prefix_client.gauge("top.level.value", 423);

        assertEquals("top.level.value:423|g", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_gauge_null_prefix() throws Exception {

        final NonBlockingStatsDClient null_prefix_client = new NonBlockingStatsDClient(null, "localhost", statsdServerPort);
        null_prefix_client.gauge("top.level.value", 423);

        assertEquals("top.level.value:423|g", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_event() throws Exception {

        final Event event = Event.builder()
                .withTitle("title1")
                .withText("text1\nline2")
                .withDate(1234567000)
                .withHostname("host1")
                .withPriority(Event.Priority.LOW)
                .withAggregationKey("key1")
                .withAlertType(Event.AlertType.ERROR)
                .build();
        client.recordEvent(event);

        assertEquals("_e{16,12}:my.prefix.title1|text1\\nline2|d:1234567|h:host1|k:key1|p:low|t:error", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_partial_event() throws Exception {

        final Event event = Event.builder()
                .withTitle("title1")
                .withText("text1")
                .withDate(1234567000)
                .build();
        client.recordEvent(event);

        assertEquals("_e{16,5}:my.prefix.title1|text1|d:1234567", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_event_with_tags() throws Exception {

        final Event event = Event.builder()
                .withTitle("title1")
                .withText("text1")
                .withDate(1234567000)
                .withHostname("host1")
                .withPriority(Event.Priority.LOW)
                .withAggregationKey("key1")
                .withAlertType(Event.AlertType.ERROR)
                .build();
        client.recordEvent(event, "foo:bar", "baz");

        assertEquals("_e{16,5}:my.prefix.title1|text1|d:1234567|h:host1|k:key1|p:low|t:error|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_partial_event_with_tags() throws Exception {

        final Event event = Event.builder()
                .withTitle("title1")
                .withText("text1")
                .withDate(1234567000)
                .build();
        client.recordEvent(event, "foo:bar", "baz");

        assertEquals("_e{16,5}:my.prefix.title1|text1|d:1234567|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_event_empty_prefix() throws Exception {

        final NonBlockingStatsDClient empty_prefix_client = new NonBlockingStatsDClient("", "localhost", statsdServerPort);
        final Event event = Event.builder()
                .withTitle("title1")
                .withText("text1")
                .withDate(1234567000)
                .withHostname("host1")
                .withPriority(Event.Priority.LOW)
                .withAggregationKey("key1")
                .withAlertType(Event.AlertType.ERROR)
                .build();
        empty_prefix_client.recordEvent(event, "foo:bar", "baz");
        assertEquals("_e{6,5}:title1|text1|d:1234567|h:host1|k:key1|p:low|t:error|#foo:bar,baz", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_service_check() throws Exception {
        final String inputMessage = "\u266c \u2020\u00f8U \n\u2020\u00f8U \u00a5\u00bau|m: T0\u00b5 \u266a"; // "♬ †øU \n†øU ¥ºu|m: T0µ ♪"
        final String outputMessage = "\u266c \u2020\u00f8U \\n\u2020\u00f8U \u00a5\u00bau|m\\: T0\u00b5 \u266a"; // note the escaped colon
        final String[] tags = {"key1:val1", "key2:val2"};
        final ServiceCheck sc = ServiceCheck.builder()
                .withName("my_check.name")
                .withStatus(ServiceCheck.Status.WARNING)
                .withMessage(inputMessage)
                .withHostname("i-abcd1234")
                .withTags(tags)
                .withTimestamp(1420740000)
                .build();

        assertEquals(outputMessage, sc.getEscapedMessage());

        client.serviceCheck(sc);

        assertEquals(String.format("_sc|my_check.name|1|d:1420740000|h:i-abcd1234|#key1:val1,key2:val2|m:%s",
                outputMessage), server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_nan_gauge_to_statsd() throws Exception {
        client.recordGaugeValue("mygauge", Double.NaN);

        assertEquals("my.prefix.mygauge:NaN|g", server.nextMessage());
    }

    @Test(timeout=5000L) public void
    sends_set_to_statsd() throws Exception {
        client.recordSetValue("myset", "myuserid");

        assertEquals("my.prefix.myset:myuserid|s", server.nextMessage());

    }

    @Test(timeout=5000L) public void
    sends_set_to_statsd_with_tags() throws Exception {
        client.recordSetValue("myset", "myuserid", "foo:bar", "baz");

        assertEquals("my.prefix.myset:myuserid|s|#foo:bar,baz", server.nextMessage());
    }
}
