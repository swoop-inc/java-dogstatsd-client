package com.timgroup.statsd;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class StringMessageStatsDClientTest {

    private String prefix;
    private BlockingQueue<String> messages;

    @Before
    public void setUp() throws Exception {
        this.prefix = "my.prefix.";
        this.messages = new LinkedBlockingQueue<>();
    }

    protected StringMessageStatsDClient client() {
        return new StringMessageStatsDClient() {
            @Override
            protected void send(String message) {
                messages.offer(message);
            }

            @Override
            public String getPrefix() {
                return prefix;
            }

            @Override
            public void close() {}
        };
    }

    protected void assertRawMessageReceived(String expectedMessage) {
        try {
            String messageReceived = messages.poll(5, TimeUnit.SECONDS);
            assertEquals(expectedMessage, messageReceived);
        } catch (InterruptedException e) {
            throw new AssertionError("no message received after waiting for 5 seconds", e);
        }
    }

    protected void assertMessageReceived(String expectedMessage) {
        assertRawMessageReceived(prefix + expectedMessage);
    }

    @Test
    public void close() throws Exception {
        client().close();
    }

    @Test
    public void count() {
        client().count("mycount", 24);
        assertMessageReceived("mycount:24|c");

        client().count("mycount", 42, (String[]) null);
        assertMessageReceived("mycount:42|c");

        client().count("mycount", 48, "tag-1", "tag-2");
        assertMessageReceived("mycount:48|c|#tag-1,tag-2");
    }

    @Test
    public void countSampled() {
        client().count("mycount", 24, 1.0);
        assertMessageReceived("mycount:24|c|@1.000000");

        client().count("mycount", 42, 1.0, (String[]) null);
        assertMessageReceived("mycount:42|c|@1.000000");

        client().count("mycount", 48, 1.0, "tag-1", "tag-2");
        assertMessageReceived("mycount:48|c|@1.000000|#tag-1,tag-2");
    }

    @Test
    public void recordGaugeValueDouble() {
        client().recordGaugeValue("mygauge", 123.456);
        assertMessageReceived("mygauge:123.456|g");

        client().recordGaugeValue("mygauge", 234.567, (String[]) null);
        assertMessageReceived("mygauge:234.567|g");

        client().recordGaugeValue("mygauge", 345.678, "tag-1", "tag-2");
        assertMessageReceived("mygauge:345.678|g|#tag-1,tag-2");
    }

    @Test
    public void recordGaugeValueDoubleSampled() {
        client().recordGaugeValue("mygauge", 123.456, 1.0);
        assertMessageReceived("mygauge:123.456|g|@1.000000");

        client().recordGaugeValue("mygauge", 234.567, 1.0, (String[]) null);
        assertMessageReceived("mygauge:234.567|g|@1.000000");

        client().recordGaugeValue("mygauge", 345.678, 1.0, "tag-1", "tag-2");
        assertMessageReceived("mygauge:345.678|g|@1.000000|#tag-1,tag-2");
    }

    @Test
    public void recordGaugeValueLong() {
        client().recordGaugeValue("mygauge", 123L, 1.0);
        assertMessageReceived("mygauge:123|g|@1.000000");

        client().recordGaugeValue("mygauge", 234L, 1.0, (String[]) null);
        assertMessageReceived("mygauge:234|g|@1.000000");

        client().recordGaugeValue("mygauge", 345L, 1.0, "tag-1", "tag-2");
        assertMessageReceived("mygauge:345|g|@1.000000|#tag-1,tag-2");
    }

    @Test
    public void recordGaugeValueLongSampled() {
        client().recordGaugeValue("mygauge", 123L, 1.0);
        assertMessageReceived("mygauge:123|g|@1.000000");

        client().recordGaugeValue("mygauge", 234L, 1.0, (String[]) null);
        assertMessageReceived("mygauge:234|g|@1.000000");

        client().recordGaugeValue("mygauge", 345L, 1.0, "tag-1", "tag-2");
        assertMessageReceived("mygauge:345|g|@1.000000|#tag-1,tag-2");
    }

    @Test
    public void recordExecutionTime() {
        client().recordExecutionTime("mytimer", 123L);
        assertMessageReceived("mytimer:123|ms");

        client().recordExecutionTime("mytimer", 123L, (String[]) null);
        assertMessageReceived("mytimer:123|ms");

        client().recordExecutionTime("mytimer", 123L, "tag-1", "tag-2");
        assertMessageReceived("mytimer:123|ms|#tag-1,tag-2");
    }

    @Test
    public void recordExecutionTimeSampled() {
        client().recordExecutionTime("mytimer", 123L, 1.0);
        assertMessageReceived("mytimer:123|ms|@1.000000");

        client().recordExecutionTime("mytimer", 123L, 1.0, (String[]) null);
        assertMessageReceived("mytimer:123|ms|@1.000000");

        client().recordExecutionTime("mytimer", 123L, 1.0, "tag-1", "tag-2");
        assertMessageReceived("mytimer:123|ms|@1.000000|#tag-1,tag-2");
    }

    @Test
    public void recordHistogramValueDouble() {
        client().recordHistogramValue("myhistogram", 123.456);
        assertMessageReceived("myhistogram:123.456|h");

        client().recordHistogramValue("myhistogram", 234.567, (String[]) null);
        assertMessageReceived("myhistogram:234.567|h");

        client().recordHistogramValue("myhistogram", 345.678, "tag-1", "tag-2");
        assertMessageReceived("myhistogram:345.678|h|#tag-1,tag-2");
    }

    @Test
    public void recordHistogramValueDoubleSampled() {
        client().recordHistogramValue("myhistogram", 123.456, 1.0);
        assertMessageReceived("myhistogram:123.456|h|@1.000000");

        client().recordHistogramValue("myhistogram", 234.567, 1.0, (String[]) null);
        assertMessageReceived("myhistogram:234.567|h|@1.000000");

        client().recordHistogramValue("myhistogram", 345.678, 1.0, "tag-1", "tag-2");
        assertMessageReceived("myhistogram:345.678|h|@1.000000|#tag-1,tag-2");
    }

    @Test
    public void recordHistogramValueLong() {
        client().recordHistogramValue("myhistogram", 123L);
        assertMessageReceived("myhistogram:123|h");

        client().recordHistogramValue("myhistogram", 234L, (String[]) null);
        assertMessageReceived("myhistogram:234|h");

        client().recordHistogramValue("myhistogram", 345L, "tag-1", "tag-2");
        assertMessageReceived("myhistogram:345|h|#tag-1,tag-2");
    }

    @Test
    public void recordHistogramValueLongSampled() {
        client().recordHistogramValue("myhistogram", 123L, 1.0);
        assertMessageReceived("myhistogram:123|h|@1.000000");

        client().recordHistogramValue("myhistogram", 234L, 1.0, (String[]) null);
        assertMessageReceived("myhistogram:234|h|@1.000000");

        client().recordHistogramValue("myhistogram", 345L, 1.0, "tag-1", "tag-2");
        assertMessageReceived("myhistogram:345|h|@1.000000|#tag-1,tag-2");
    }

    @Test
    public void recordEvent() {
        Event event = Event.builder()
                .withTitle("title1")
                .withText("text1\nline2")
                .withDate(1234567000)
                .withHostname("host1")
                .withPriority(Event.Priority.LOW)
                .withAggregationKey("key1")
                .withAlertType(Event.AlertType.ERROR)
                .build();

        client().recordEvent(event);
        assertRawMessageReceived("_e{16,12}:my.prefix.title1|text1\\nline2|d:1234567|h:host1|k:key1|p:low|t:error");

        client().recordEvent(event, "tag-1", "tag-2");
        assertRawMessageReceived("_e{16,12}:my.prefix.title1|text1\\nline2|d:1234567|h:host1|k:key1|p:low|t:error|#tag-1,tag-2");
    }

    @Test
    public void recordServiceCheckRun() {
        ServiceCheck check = ServiceCheck.builder()
                .withName("some-name")
                .withStatus(ServiceCheck.Status.OK)
                .withTimestamp(1000)
                .withHostname("some-hostname")
                .withCheckRunId(123)
                .withMessage("some-message")
                .withTags(new String[]{"tag-1", "tag-2"})
                .build();

        client().recordServiceCheckRun(check);
        assertRawMessageReceived("_sc|some-name|0|d:1000|h:some-hostname|#tag-1,tag-2|m:some-message");
    }

    @Test
    public void recordSetValue() {
        client().recordSetValue("my-record", "some-value");
        assertMessageReceived("my-record:some-value|s");

        client().recordSetValue("my-record", "some-value", "tag-1", "tag-2");
        assertMessageReceived("my-record:some-value|s|#tag-1,tag-2");
    }

    @Test
    public void tagString() {
        assertEquals("", StringMessageStatsDClient.tagString(null, null));
        assertEquals("", StringMessageStatsDClient.tagString(null, ""));
        assertEquals("some-prefix", StringMessageStatsDClient.tagString(null, "some-prefix"));
        assertEquals("some-prefix", StringMessageStatsDClient.tagString(new String[]{}, "some-prefix"));
        assertEquals("some-prefix", StringMessageStatsDClient.tagString(new String[]{null}, "some-prefix"));
        assertEquals("some-prefix", StringMessageStatsDClient.tagString(new String[]{""}, "some-prefix"));
        assertEquals("some-prefix,tag-1", StringMessageStatsDClient.tagString(new String[]{"tag-1"}, "some-prefix"));
        assertEquals("some-prefix,tag-1,tag-2",
                StringMessageStatsDClient.tagString(new String[]{"tag-1", "tag-2"}, "some-prefix"));

        assertEquals("", StringMessageStatsDClient.tagString(new String[]{null}, null));
        assertEquals("", StringMessageStatsDClient.tagString(new String[]{"", ""}, null));
        assertEquals("|#tag-1", StringMessageStatsDClient.tagString(new String[]{"tag-1"}, null));
        assertEquals("|#tag-1,tag-2", StringMessageStatsDClient.tagString(new String[]{"tag-1", "tag-2"}, null));
    }
}
