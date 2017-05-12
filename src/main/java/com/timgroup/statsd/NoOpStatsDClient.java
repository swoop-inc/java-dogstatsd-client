package com.timgroup.statsd;

/**
 * A No-Op StatsDClient, which can be substituted in when metrics are not
 * required.
 *
 * @author Tom Denley
 *
 */
public class NoOpStatsDClient implements StatsDClient {
    @Override
    public void close() {}

    @Override
    public void count(String aspect, long delta, String... tags) {}

    @Override
    public void count(String aspect, long delta, double sampleRate, String... tags) {}

    @Override
    public void recordGaugeValue(String aspect, double value, String... tags) {}

    @Override
    public void recordGaugeValue(String aspect, double value, double sampleRate, String... tags) {}

    @Override
    public void recordGaugeValue(String aspect, long value, String... tags) {}

    @Override
    public void recordGaugeValue(String aspect, long value, double sampleRate, String... tags) {}

    @Override
    public void recordExecutionTime(String aspect, long timeInMs, String... tags) {}

    @Override
    public void recordExecutionTime(String aspect, long timeInMs, double sampleRate, String... tags) {}

    @Override
    public void recordHistogramValue(String aspect, double value, String... tags) {}

    @Override
    public void recordHistogramValue(String aspect, double value, double sampleRate, String... tags) {}

    @Override
    public void recordHistogramValue(String aspect, long value, String... tags) {}

    @Override
    public void recordHistogramValue(String aspect, long value, double sampleRate, String... tags) {}

    @Override
    public void recordEvent(Event event, String... tags) {}

    @Override
    public void recordServiceCheckRun(ServiceCheck sc) {}

    @Override
    public void recordSetValue(String aspect, String value, String... tags) {}
}
