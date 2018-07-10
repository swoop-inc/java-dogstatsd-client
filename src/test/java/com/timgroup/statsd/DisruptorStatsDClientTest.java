package com.timgroup.statsd;

import org.junit.Before;

import java.net.ServerSocket;

import static org.junit.Assert.assertEquals;

public class DisruptorStatsDClientTest extends StringMessageStatsDClientTest {
    private String prefix;
    private int localPort;
    private DummyStatsDServer server;

    @Override
    @Before
    public void setUp() throws Exception {
        ServerSocket socket = new ServerSocket(0);
        int localPort = socket.getLocalPort();
        this.server = new DummyStatsDServer(localPort);
        this.localPort = localPort;
        this.prefix = "disruptor.prefix";
    }

    @Override
    protected StringMessageStatsDClient client() {
        return new DisruptorStatsDClient(prefix, "localhost", localPort);
    }

    @Override
    protected void assertRawMessageReceived(String expectedMessage) {
        try {
            assertEquals(expectedMessage, server.nextMessage());
        } catch (InterruptedException ignore) {}
    }

    @Override
    protected void assertMessageReceived(String expectedMessage) {
        assertRawMessageReceived(prefix + "." + expectedMessage);
    }

    @Override
    public void recordEvent() {
        // we need to override this test from the parent test class due to the prefix
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
        assertRawMessageReceived("_e{23,12}:disruptor.prefix.title1|text1\\nline2|d:1234567|h:host1|k:key1|p:low|t:error");

        client().recordEvent(event, "tag-1", "tag-2");
        assertRawMessageReceived("_e{23,12}:disruptor.prefix.title1|text1\\nline2|d:1234567|h:host1|k:key1|p:low|t:error|#tag-1,tag-2");
    }
}
